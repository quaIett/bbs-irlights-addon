package qualet.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/** Snapshot world guides before Iris can light, overwrite or discard them. Draw them
 * after renderWorld, while BBS still owns its film/export framebuffer. Preview and
 * picking draws stay immediate, as do guides inside framebuffer forms. */
public final class WorldLightGuideOverlay
{
    private record Guide(Matrix4f pose, Matrix4f modelView, Matrix4f projection,
                         Color color, float range, float outer, float inner, boolean spot)
    {}

    private static final List<Guide> pending = new ArrayList<>();

    private WorldLightGuideOverlay()
    {}

    public static void beginFrame()
    {
        pending.clear();
    }

    static boolean defer(MatrixStack stack, Color color, float range, float outer, float inner, boolean spot)
    {
        // A loaded pack alone is insufficient: nested framebuffer forms keep their
        // world context type but suspend the Iris override while drawing offscreen.
        if (!BBSRendering.isIrisWorldShadersEnabled())
        {
            return false;
        }

        pending.add(new Guide(new Matrix4f(stack.peek().getPositionMatrix()),
            new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()),
            color.copy(), range, outer, inner, spot));

        return true;
    }

    public static void flush()
    {
        if (pending.isEmpty())
        {
            return;
        }

        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f oldModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        VertexSorter oldSorting = RenderSystem.getVertexSorting();
        ShaderProgram oldShader = RenderSystem.getShader();
        float[] oldColor = RenderSystem.getShaderColor().clone();
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] oldViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
        boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean oldDepthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int oldSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int oldDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int oldSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int oldDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var modelView = RenderSystem.getModelViewStack();

        modelView.push();
        try
        {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

            for (Guide guide : pending)
            {
                modelView.peek().getPositionMatrix().set(guide.modelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(guide.projection(), VertexSorter.BY_Z);
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
            pending.clear();
            modelView.pop();
            // The applied matrix can differ from the stack top (BBS's UI does this).
            modelView.push();
            modelView.peek().getPositionMatrix().set(oldModelView);
            RenderSystem.applyModelViewMatrix();
            modelView.pop();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            RenderSystem.setShader(() -> oldShader);
            RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
            if (oldDepth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(oldDepthWrite);
            if (oldCull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(oldSrcRgb, oldDstRgb, oldSrcAlpha, oldDstAlpha);
            if (oldBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            RenderSystem.viewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
        }
    }
}
