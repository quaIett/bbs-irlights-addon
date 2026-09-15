package org.qualet.irl.light.shadow;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

/**
 * MC 1.21.11 occluder capture for the BBS caster source. irl-core's 1.21.11 line
 * rasterizes casters from POSITION triangles ({@link RawOccluderBatch}) instead of an
 * Immediate draw, so a caster is drawn through BBS's own immediate pipeline under a
 * {@link FormRenderCapture} session: every BBS emitter (cubic models, BOBJ meshes, the
 * buffered provider behind blocks/items/vanilla mob queues) ends at
 * {@code RenderLayer#draw(BuiltBuffer)}, which BBS's RenderLayerMixin turns into a CPU
 * copy while the session is open. The positions come out exactly as the draw's
 * MatrixStack left them, so a caster drawn on an anchor-relative stack yields
 * anchor-relative triangles.
 *
 * <p>The translucent queue is suspended for the span: a faded or see-through form would
 * otherwise defer part of its geometry to the end of the frame, dropping it from the
 * capture and drawing it at the caster's shadow-space position in the main pass. Any
 * enclosing capture session is set aside the same way, so the bake never leaks into
 * (or steals from) an item-model capture in progress.</p>
 *
 * <p>Render thread only. Exceptions are NOT caught (INVARIANT 4 — the shared wrapper
 * rewinds the batch); the finally block only restores the BBS session state.</p>
 */
final class BbsOccluderCapture
{
    private static final float[] EMPTY = new float[0];

    /** Reused output scratch, grown to the largest caster seen. */
    private static float[] scratch = new float[1 << 14];

    private BbsOccluderCapture()
    {}

    /** Run {@code draw} under a capture session and return its triangles (stride 3). */
    static float[] capture(Runnable draw)
    {
        boolean queueWasActive = FormTranslucentQueue.suspend();
        FormRenderCapture.Suspended outer = FormRenderCapture.suspend();
        Map<RenderLayer, List<FormRenderCapture.Captured>> captured;

        FormRenderCapture.begin();
        try
        {
            draw.run();
        }
        finally
        {
            captured = FormRenderCapture.end();
            FormRenderCapture.restore(outer);
            FormTranslucentQueue.restore(queueWasActive);
        }

        return triangles(captured);
    }

    private static float[] triangles(Map<RenderLayer, List<FormRenderCapture.Captured>> captured)
    {
        if (captured == null || captured.isEmpty())
        {
            return EMPTY;
        }

        int w = 0;
        for (List<FormRenderCapture.Captured> list : captured.values())
        {
            for (int i = 0, n = list.size(); i < n; i++)
            {
                w = append(list.get(i), w);
            }
        }

        if (w == 0)
        {
            return EMPTY;
        }
        float[] out = new float[w];
        System.arraycopy(scratch, 0, out, 0, w);
        return out;
    }

    /** Append one captured buffer's triangles at {@code w}; returns the new write cursor.
     *  QUADS split into two triangles, TRIANGLES pass through; lines and other modes
     *  carry no silhouette and are skipped. */
    private static int append(FormRenderCapture.Captured captured, int w)
    {
        BuiltBuffer.DrawParameters params = captured.params();
        VertexFormat.DrawMode mode = params.mode();
        boolean quads = mode == VertexFormat.DrawMode.QUADS;
        if (!quads && mode != VertexFormat.DrawMode.TRIANGLES)
        {
            return w;
        }

        VertexFormat format = params.format();
        int posOffset = -1;
        for (VertexFormatElement element : format.getElements())
        {
            if (element.usage() == VertexFormatElement.Usage.POSITION)
            {
                posOffset = format.getOffset(element);
                break;
            }
        }
        if (posOffset < 0)
        {
            return w;
        }

        int stride = format.getVertexSize();
        int count = params.vertexCount();
        int prims = quads ? count / 4 : count / 3;
        int need = w + prims * (quads ? 18 : 9);
        if (need > scratch.length)
        {
            float[] grown = new float[Math.max(need, scratch.length * 2)];
            System.arraycopy(scratch, 0, grown, 0, w);
            scratch = grown;
        }

        // FormRenderCapture.copy already restored native order, but a duplicate() view
        // would reset it to BIG_ENDIAN — read through an explicitly ordered view.
        ByteBuffer data = captured.data().duplicate().order(ByteOrder.nativeOrder());
        float[] out = scratch;
        if (quads)
        {
            for (int q = 0; q < prims; q++)
            {
                int v0 = q * 4;
                w = put(out, w, data, (v0) * stride + posOffset);
                w = put(out, w, data, (v0 + 1) * stride + posOffset);
                w = put(out, w, data, (v0 + 2) * stride + posOffset);
                w = put(out, w, data, (v0) * stride + posOffset);
                w = put(out, w, data, (v0 + 2) * stride + posOffset);
                w = put(out, w, data, (v0 + 3) * stride + posOffset);
            }
        }
        else
        {
            for (int v = 0, n = prims * 3; v < n; v++)
            {
                w = put(out, w, data, v * stride + posOffset);
            }
        }
        return w;
    }

    private static int put(float[] out, int w, ByteBuffer data, int offset)
    {
        out[w] = data.getFloat(offset);
        out[w + 1] = data.getFloat(offset + 4);
        out[w + 2] = data.getFloat(offset + 8);
        return w + 3;
    }
}
