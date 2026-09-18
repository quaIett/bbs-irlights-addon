package qualet.irlite.client.forms;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** World guides are editor overlays: composite them after Iris, never into its G-buffer.
 * Only immutable shape/pose snapshots are queued; no form, context or native vertex buffer
 * survives the original draw. Preview geometry and colour-id picking remain immediate. */
public final class WorldLightGuideOverlay
{
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("irlite", "pipeline/world_light_guides"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private record Guide(Matrix4f pose, Matrix4f modelView, GpuBufferSlice projection,
                         Color color, float range, float outer, float inner, boolean spot)
    {}

    private static final List<Guide> pending = new ArrayList<>();
    private static Guide drawing;

    private WorldLightGuideOverlay()
    {}

    public static void beginFrame()
    {
        // A failed/skipped frame must not leave overlays from an older scene behind.
        pending.clear();
        drawing = null;
    }

    static boolean defer(MatrixStack stack, Color color, float range, float outer, float inner, boolean spot)
    {
        // Framebuffer-form children keep ENTITY/MODEL_BLOCK context types, but their
        // geometry belongs to that offscreen texture, not to the world overlay.
        if (!BBSRendering.isIrisWorldForms())
        {
            return false;
        }

        pending.add(new Guide(new Matrix4f(stack.peek().getPositionMatrix()),
            new Matrix4f(RenderSystem.getModelViewMatrix()), RenderSystem.getProjectionMatrixBuffer(),
            color.copy(), range, outer, inner, spot));

        return true;
    }

    public static void flush()
    {
        try
        {
            for (Guide guide : pending)
            {
                drawing = guide;
                MatrixStack stack = new MatrixStack();
                stack.peek().getPositionMatrix().set(guide.pose());

                if (guide.spot())
                {
                    LightGuideRenderer.renderSpotlight(stack, guide.color(), guide.range(), guide.outer(), guide.inner());
                }
                else
                {
                    LightGuideRenderer.renderPointLight(stack, guide.color(), guide.range());
                }
            }
        }
        finally
        {
            drawing = null;
            pending.clear();
        }
    }

    /** Own the buffer only in the deferred pass. A direct pass avoids BBS form capture,
     * render-layer queues and any stale Iris output override. It also leaves the global
     * projection/model-view untouched (renderWorld already installed the hand matrices). */
    static boolean draw(BufferBuilder builder)
    {
        if (drawing == null)
        {
            return false;
        }

        try (BuiltBuffer buffer = builder.endNullable())
        {
            if (buffer == null)
            {
                return true;
            }

            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(drawing.modelView(),
                new Vector4f(1F), new Vector3f(), new Matrix4f());
            GpuBuffer vertices = PIPELINE.getVertexFormat().uploadImmediateVertexBuffer(buffer.getBuffer());
            RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());
            GpuBuffer indices = sequential.getIndexBuffer(buffer.getDrawParameters().indexCount());

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "irlite:world_light_guides", MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty()))
            {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("Projection", drawing.projection());
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, sequential.getIndexType());
                pass.drawIndexed(0, 0, buffer.getDrawParameters().indexCount(), 1);
            }
        }

        return true;
    }
}
