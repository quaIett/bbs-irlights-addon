package qualet.irlite.client.light;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Resolves a form's absolute world position from the render-path matrix stack.
 * inverseViewRot * stack.peek strips the view rotation (incl. BBS camera roll),
 * leaving the world-frame offset from the camera.
 */
public final class IRLightPositionResolver
{
    private IRLightPositionResolver()
    {}

    public static Vector3d resolve(FormRenderingContext context)
    {
        return resolve(context, new Matrix4f());
    }

    /** Also fills a caller-owned camera-relative world matrix so a spotlight can
     *  transform its direction without rebuilding the same product. The matrix
     *  must not be the context stack's own position matrix. */
    public static Vector3d resolve(FormRenderingContext context, Matrix4f matrix)
    {
        matrix.set((Matrix3fc) RenderSystem.getInverseViewRotationMatrix());
        matrix.mul(context.stack.peek().getPositionMatrix());

        net.minecraft.util.math.Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        return new Vector3d(cam.x + matrix.m30(), cam.y + matrix.m31(), cam.z + matrix.m32());
    }
}
