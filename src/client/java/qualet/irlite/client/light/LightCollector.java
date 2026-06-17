package qualet.irlite.client.light;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;
import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import org.qualet.irl.light.LightRegistry;

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

    /**
     * Ownership gate between the two registration paths. The scanner owns
     * ModelBlock forms AND dashboard-editor preview replays (both registered
     * here in clean world coords); the form render-path owns everything else
     * (live actors, in-world film replays). This prevents the same lamp
     * registering twice with diverging coordinate frames.
     *
     * The dashboard preview is the critical case: its viewport applies the
     * BBS camera roll to the matrix stack, but {@code getInverseViewRotationMatrix}
     * does NOT reflect that preview roll, so the render-path's
     * {@code inverseViewRot * stack.peek} leaves the roll baked into the light's
     * position and direction — the lamp orbits/rotates with camera roll. Routing
     * dashboard-editor entities through the scanner (pure world coords, never
     * touching the view stack) makes them roll-independent and fixes that.
     */
    public static boolean isHandledByScanner(FormRenderingContext context)
    {
        if (context == null)
        {
            return false;
        }
        if (context.type == FormRenderType.MODEL_BLOCK)
        {
            return true;
        }
        if (context.type == FormRenderType.ENTITY)
        {
            IEntity entity = context.entity;
            if (entity == null)
            {
                return false;
            }
            FilmEditorController editor = getActiveEditorController();
            if (editor == null)
            {
                return false;
            }
            try
            {
                for (IEntity rosterEntity : editor.getEntities().values())
                {
                    if (rosterEntity == entity)
                    {
                        return true;
                    }
                }
            }
            catch (Throwable t)
            {
                return false;
            }
        }
        return false;
    }

    public static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        if (world == null || cameraPos == null)
        {
            return;
        }

        scanBlockEntities(world, cameraPos);
        scanFilmReplays(cameraPos, tickDelta);
    }

    private static void scanBlockEntities(ClientWorld world, Vec3d cameraPos)
    {
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

    /**
     * Registers lamps from the dashboard film-editor preview replays in pure
     * world coordinates (roll-independent). Covers ONLY the dashboard editor's
     * non-actor replays — in-world replays and live actors keep registering via
     * the form-renderer path, where the rig pose is available. Gated on the
     * dashboard being open so we never light a viewport that isn't showing.
     */
    private static void scanFilmReplays(Vec3d cameraPos, float tickDelta)
    {
        FilmEditorController editor = getActiveEditorController();
        if (editor == null || editor.film == null || editor.film.replays == null)
        {
            return;
        }

        List<Replay> replays = editor.film.replays.getList();
        if (replays == null || replays.isEmpty())
        {
            return;
        }

        for (IntObjectMap.PrimitiveEntry<IEntity> entry : editor.getEntities().entries())
        {
            int replayId = entry.key();
            if (replayId < 0 || replayId >= replays.size())
            {
                continue;
            }

            Replay replay = replays.get(replayId);
            if (replay == null || replay.actor.get())
            {
                continue;
            }

            IEntity ent = entry.value();
            if (ent == null)
            {
                continue;
            }

            Form rootForm = ent.getForm();
            if (rootForm == null)
            {
                continue;
            }

            double wx = MathHelper.lerp(tickDelta, ent.getPrevX(), ent.getX());
            double wy = MathHelper.lerp(tickDelta, ent.getPrevY(), ent.getY());
            double wz = MathHelper.lerp(tickDelta, ent.getPrevZ(), ent.getZ());

            double dx = wx - cameraPos.x;
            double dy = wy - cameraPos.y;
            double dz = wz - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DIST_SQ)
            {
                continue;
            }

            float bodyYaw = MathHelper.lerp(tickDelta, ent.getPrevBodyYaw(), ent.getBodyYaw());
            Matrix4f root = new Matrix4f().identity();
            root.translate((float) wx, (float) wy, (float) wz);
            root.rotateY((float) Math.toRadians(-bodyYaw));

            walk(rootForm, root);
        }
    }

    private static FilmEditorController getActiveEditorController()
    {
        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || !(mc.currentScreen instanceof mchorse.bbs_mod.ui.framework.UIScreen))
            {
                return null;
            }

            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
            if (dashboard == null || !(dashboard.getPanels().panel instanceof UIFilmPanel filmPanel))
            {
                return null;
            }

            UIFilmController uiCtrl = filmPanel.getController();
            return uiCtrl == null ? null : uiCtrl.editorController;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static void emitPoint(PointLightForm form, Matrix4f matrix)
    {
        Vector4f origin = new Vector4f(0F, 0F, 0F, 1F);
        matrix.transform(origin);

        Color c = form.color.get();
        LightRegistry.registerPoint(origin.x, origin.y, origin.z, c.r, c.g, c.b, form.intensity.get(), form.radius.get(), form.entitiesOnly.get(), form.blocksOnly.get(), form.anisotropy.get(), form.vlDensity.get(), form.beamStrength.get(), form.bulbSize.get(), form.shadows.get(), System.identityHashCode(form));
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
        LightRegistry.registerSpot(origin.x, origin.y, origin.z, dx, dy, dz, c.r, c.g, c.b, form.intensity.get(), form.range.get(), cosOuter, cosInner, form.entitiesOnly.get(), form.blocksOnly.get(), form.anisotropy.get(), form.vlDensity.get(), form.beamStrength.get(), form.bulbSize.get(), form.shadows.get(), System.identityHashCode(form));
    }
}
