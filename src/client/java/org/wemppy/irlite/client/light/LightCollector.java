package org.wemppy.irlite.client.light;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.wemppy.irlite.forms.PointLightForm;
import org.wemppy.irlite.forms.SpotlightForm;
import org.wemppy.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import java.util.List;

/**
 * Walks the loaded ModelBlockEntity forms each frame in pure world coordinates
 * and feeds every PointLight / Spotlight form into {@link LightBuffer}.
 *
 * Scope for now: ModelBlock-placed lights only. Live actor entities and film
 * replays (which need render-path rig pose) are a later addition.
 */
public final class LightCollector
{
    private static final double MAX_DIST = 256.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;

    private LightCollector()
    {}

    public static void collect(ClientWorld world, Vec3d cameraPos)
    {
        if (world == null || cameraPos == null)
        {
            return;
        }

        List<BlockEntityTickInvoker> tickers;
        try
        {
            tickers = ((WorldBlockEntityTickersAccessor) (Object) world).irlite$getBlockEntityTickers();
        }
        catch (Throwable t)
        {
            return;
        }
        if (tickers == null)
        {
            return;
        }

        for (int i = 0, n = tickers.size(); i < n; i++)
        {
            BlockEntityTickInvoker invoker = tickers.get(i);
            if (invoker == null)
            {
                continue;
            }

            BlockPos pos = invoker.getPos();
            if (pos == null)
            {
                continue;
            }

            double dx = pos.getX() + 0.5 - cameraPos.x;
            double dy = pos.getY() + 0.5 - cameraPos.y;
            double dz = pos.getZ() + 0.5 - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DIST_SQ)
            {
                continue;
            }

            BlockEntity be;
            try { be = world.getBlockEntity(pos); }
            catch (Throwable t) { continue; }
            if (!(be instanceof ModelBlockEntity mbe))
            {
                continue;
            }

            ModelProperties props;
            try { props = mbe.getProperties(); }
            catch (Throwable t) { continue; }
            if (props == null || !props.isEnabled())
            {
                continue;
            }

            Form rootForm = props.getForm();
            if (rootForm == null)
            {
                continue;
            }

            Matrix4f root = new Matrix4f().identity();
            root.translate(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F);
            Transform propsT = props.getTransform();
            if (propsT != null)
            {
                Matrix4f propsM = new Matrix4f();
                propsT.setupMatrix(propsM);
                root.mul(propsM);
            }

            walk(rootForm, root);
        }
    }

    private static void walk(Form form, Matrix4f parent)
    {
        if (form == null || !form.visible.get())
        {
            return;
        }

        Matrix4f local = new Matrix4f(parent);
        Transform t = form.transform.get();
        if (t != null)
        {
            Matrix4f tm = new Matrix4f();
            t.setupMatrix(tm);
            local.mul(tm);
        }

        if (form instanceof PointLightForm point)
        {
            emitPoint(point, local);
        }
        else if (form instanceof SpotlightForm spot)
        {
            emitSpot(spot, local);
        }

        if (form.parts == null)
        {
            return;
        }
        List<BodyPart> parts = form.parts.getAllTyped();
        if (parts == null)
        {
            return;
        }

        for (int i = 0, n = parts.size(); i < n; i++)
        {
            BodyPart part = parts.get(i);
            if (part == null)
            {
                continue;
            }

            String bone = part.bone.get();
            if (bone != null && !bone.isEmpty())
            {
                continue;
            }

            Form child = part.getForm();
            if (child == null)
            {
                continue;
            }

            Matrix4f childM = new Matrix4f(local);
            Transform pt = part.transform.get();
            if (pt != null)
            {
                Matrix4f ptm = new Matrix4f();
                pt.setupMatrix(ptm);
                childM.mul(ptm);
            }

            walk(child, childM);
        }
    }

    private static void emitPoint(PointLightForm form, Matrix4f matrix)
    {
        Vector4f origin = new Vector4f(0F, 0F, 0F, 1F);
        matrix.transform(origin);

        Color c = form.color.get();
        LightBuffer.addPoint(origin.x, origin.y, origin.z, c.r, c.g, c.b, form.intensity.get(), form.radius.get());
    }

    private static void emitSpot(SpotlightForm form, Matrix4f matrix)
    {
        Vector4f origin = new Vector4f(0F, 0F, 0F, 1F);
        matrix.transform(origin);

        // Local +Z = the direction the spotlight points (matches the editor gizmo).
        Vector4f forward = new Vector4f(0F, 0F, 1F, 0F);
        matrix.transform(forward);
        float len = (float) Math.sqrt(forward.x * forward.x + forward.y * forward.y + forward.z * forward.z);
        float dx = 0F, dy = 0F, dz = 1F;
        if (len > 1e-4F)
        {
            dx = forward.x / len;
            dy = forward.y / len;
            dz = forward.z / len;
        }

        float outer = form.radius.get();
        float inner = Math.min(form.innerRadius.get(), outer);
        float cosOuter = (float) Math.cos(Math.toRadians(outer * 0.5F));
        float cosInner = (float) Math.cos(Math.toRadians(inner * 0.5F));

        Color c = form.color.get();
        LightBuffer.addSpot(origin.x, origin.y, origin.z, dx, dy, dz, c.r, c.g, c.b, form.intensity.get(), form.range.get(), cosOuter, cosInner);
    }
}
