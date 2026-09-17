package qualet.irlite.client.light;

import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Resolves a light form's absolute world position on the render path.
 *
 * <p>Primary source is BBS's second, parallel matrix stack — {@code context.world} —
 * kept in ABSOLUTE world coordinates: based on the actor's interpolated world position
 * + body yaw, and pushed with the same bone / body-part / form transforms as
 * {@code context.stack}. So the form origin read straight out of it is the true world
 * position, with no camera / view-rotation reconstruction (1.21 removed
 * {@code RenderSystem.getInverseViewRotationMatrix()}, and a camera-rebuilt inverse
 * desyncs from rig sub-stacks — see port/1.21.1).</p>
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
        net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        // The rotation part still comes from the camera: callers transform directions
        // with it (a translation-free use the view-rotation desync does not affect).
        matrix.set(new Matrix3f().rotation(camera.getRotation()));
        matrix.mul(context.stack.peek().getPositionMatrix());

        // context.world carries the actor world base (pos + yaw) for ENTITY renders.
        if (context.world != null && context.type == FormRenderType.ENTITY)
        {
            Vector3f p = context.world.peek().getPositionMatrix().transformPosition(new Vector3f());

            return new Vector3d(p.x, p.y, p.z);
        }

        // Fallback (no based world stack): camera position + the de-rotated stack offset.
        net.minecraft.util.math.Vec3d cam = camera.getPos();

        return new Vector3d(cam.x + matrix.m30(), cam.y + matrix.m31(), cam.z + matrix.m32());
    }
}
