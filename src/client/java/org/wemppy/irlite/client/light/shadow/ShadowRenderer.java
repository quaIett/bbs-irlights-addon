package org.wemppy.irlite.client.light.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Bakes spotlight shadow depth maps. Phase 1: perspective depth tile per spot,
 * entity occluders only (vanilla render dispatcher), hard shadow. Ported from
 * the original IRLights ShadowRenderer (spot + entity path).
 *
 * Usage: beginSpot(...) -> renderEntity(...) * -> endPass().
 */
public final class ShadowRenderer
{
    private static final float NEAR = 0.05f;
    private static final int FULL_LIGHT = LightmapTextureManager.pack(15, 15);

    private static boolean inPass = false;

    private static int savedFbo;
    private static final int[] savedViewport = new int[4];
    private static boolean savedScissorEnabled;
    private static final int[] savedScissorBox = new int[4];
    private static Matrix4f savedProj;
    private static VertexSorter savedSorter;
    private static boolean savedMaskR, savedMaskG, savedMaskB, savedMaskA;

    private static final Matrix4f currentView = new Matrix4f();

    private ShadowRenderer()
    {}

    public static void beginSpot(int tile,
                                 float lpx, float lpy, float lpz,
                                 float ldx, float ldy, float ldz,
                                 float range, float outerDeg)
    {
        savePassState();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, SpotlightDepthAtlas.getFboId());
        int px = SpotlightDepthAtlas.tilePixelX(tile);
        int py = SpotlightDepthAtlas.tilePixelY(tile);
        int ts = SpotlightDepthAtlas.TILE_SIZE;
        GL11.glViewport(px, py, ts, ts);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px, py, ts, ts);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        float fovDeg = Math.max(outerDeg, 1.0f);
        float far = Math.max(range, NEAR + 0.1f);
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fovDeg), 1.0f, NEAR, far);

        Vector3f up = pickStableUp(ldy);
        currentView.identity().lookAt(
            lpx, lpy, lpz,
            lpx + ldx, lpy + ldy, lpz + ldz,
            up.x, up.y, up.z
        );

        applyMatrices(proj);
    }

    public static void beginPointFace(int slot, int face,
                                      float lpx, float lpy, float lpz,
                                      float radius)
    {
        savePassState();

        PointShadowArray.bindFaceForRender(slot, face);
        int fs = PointShadowArray.FACE_SIZE;
        GL11.glViewport(0, 0, fs, fs);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, fs, fs);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        float far = Math.max(radius, NEAR + 0.1f);
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(90.0), 1.0f, NEAR, far);

        float dx, dy, dz, ux, uy, uz;
        switch (face)
        {
            case 0:  dx =  1; dy =  0; dz =  0; ux = 0; uy = -1; uz =  0; break; // +X
            case 1:  dx = -1; dy =  0; dz =  0; ux = 0; uy = -1; uz =  0; break; // -X
            case 2:  dx =  0; dy =  1; dz =  0; ux = 0; uy =  0; uz =  1; break; // +Y
            case 3:  dx =  0; dy = -1; dz =  0; ux = 0; uy =  0; uz = -1; break; // -Y
            case 4:  dx =  0; dy =  0; dz =  1; ux = 0; uy = -1; uz =  0; break; // +Z
            default: dx =  0; dy =  0; dz = -1; ux = 0; uy = -1; uz =  0; break; // -Z
        }

        currentView.identity().lookAt(
            lpx, lpy, lpz,
            lpx + dx, lpy + dy, lpz + dz,
            ux, uy, uz
        );

        applyMatrices(proj);
    }

    public static final int CASTER_ENTITY = 0;
    public static final int CASTER_MODEL_BLOCK = 1;

    public static void renderCaster(Object caster, int casterType, float tickDelta)
    {
        if (caster == null || !inPass)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = new MatrixStack();

        // Form-renderer paths inherit GL state, so pin what we need per caster.
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        ShadowBakeState.setBaking(true);
        try
        {
            switch (casterType)
            {
                case CASTER_MODEL_BLOCK:
                    drawModelBlock((ModelBlockEntity) caster, matrices, immediate, camera, tickDelta);
                    break;
                case CASTER_ENTITY:
                default:
                    drawEntity((Entity) caster, matrices, immediate, tickDelta);
                    break;
            }
            immediate.draw();
        }
        catch (Throwable t)
        {
            // swallow — a single bad caster must not abort the whole bake
        }
        finally
        {
            ShadowBakeState.setBaking(false);
        }
    }

    private static void drawEntity(Entity entity, MatrixStack matrices, VertexConsumerProvider.Immediate immediate, float tickDelta)
    {
        double cx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double cy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double cz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        float yaw = entity.getYaw(tickDelta);

        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (dispatcher != null)
        {
            dispatcher.render(entity, cx, cy, cz, yaw, tickDelta, matrices, immediate, FULL_LIGHT);
        }
    }

    private static void drawModelBlock(ModelBlockEntity mbe, MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Camera camera, float tickDelta)
    {
        if (mbe.getProperties() == null)
        {
            return;
        }
        Form form = mbe.getProperties().getForm();
        if (form == null)
        {
            return;
        }
        Transform t = mbe.getProperties().getTransform();

        double feetX = mbe.getPos().getX() + 0.5 + (t == null ? 0 : t.translate.x);
        double feetY = mbe.getPos().getY() + (t == null ? 0 : t.translate.y);
        double feetZ = mbe.getPos().getZ() + 0.5 + (t == null ? 0 : t.translate.z);

        matrices.push();
        matrices.translate(feetX, feetY, feetZ);
        if (t != null)
        {
            MatrixStackUtils.applyTransform(matrices, t);
        }
        FormRenderer<?> renderer = FormUtilsClient.getRenderer(form);
        if (renderer != null)
        {
            renderer.render(new FormRenderingContext()
                .set(FormRenderType.MODEL_BLOCK, mbe.getEntity(), matrices, FULL_LIGHT, OverlayTexture.DEFAULT_UV, tickDelta)
                .camera(camera));
        }
        matrices.pop();
    }

    public static void endPass()
    {
        if (!inPass)
        {
            return;
        }

        MatrixStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pop();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProj, savedSorter);

        GL11.glColorMask(savedMaskR, savedMaskG, savedMaskB, savedMaskA);

        if (savedScissorEnabled)
        {
            GL11.glScissor(savedScissorBox[0], savedScissorBox[1], savedScissorBox[2], savedScissorBox[3]);
        }
        else
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);

        inPass = false;
    }

    private static void savePassState()
    {
        savedFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);
        savedScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (savedScissorEnabled)
        {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, savedScissorBox);
        }
        savedProj = RenderSystem.getProjectionMatrix();
        savedSorter = RenderSystem.getVertexSorting();

        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush())
        {
            java.nio.IntBuffer maskBuf = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, maskBuf);
            savedMaskR = maskBuf.get(0) != 0;
            savedMaskG = maskBuf.get(1) != 0;
            savedMaskB = maskBuf.get(2) != 0;
            savedMaskA = maskBuf.get(3) != 0;
        }

        inPass = true;
    }

    private static void applyMatrices(Matrix4f proj)
    {
        GL11.glColorMask(true, true, true, true);

        RenderSystem.setProjectionMatrix(proj, VertexSorter.BY_DISTANCE);

        MatrixStack mvStack = RenderSystem.getModelViewStack();
        mvStack.push();
        mvStack.loadIdentity();
        mvStack.multiplyPositionMatrix(currentView);
        RenderSystem.applyModelViewMatrix();
    }

    private static Vector3f pickStableUp(float dy)
    {
        return Math.abs(dy) > 0.99f ? new Vector3f(0f, 0f, 1f) : new Vector3f(0f, 1f, 0f);
    }
}
