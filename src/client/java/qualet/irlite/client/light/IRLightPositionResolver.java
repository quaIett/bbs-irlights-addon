package qualet.irlite.client.light;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

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
        net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        // 1.21: RenderSystem.getInverseViewRotationMatrix() is gone — rebuild the
        // inverse view rotation from the camera orientation (incl. BBS roll), exactly
        // as BBS itself does (new Matrix3f().rotation(camera.getRotation())).
        Matrix4f matrix = new Matrix4f(new org.joml.Matrix3f().rotation(camera.getRotation()));
        matrix.mul(context.stack.peek().getPositionMatrix());
        Vector3f offset = matrix.getTranslation(new Vector3f());

        net.minecraft.util.math.Vec3d cam = camera.getPos();

        return new Vector3d(cam.x + offset.x, cam.y + offset.y, cam.z + offset.z);
    }
}
