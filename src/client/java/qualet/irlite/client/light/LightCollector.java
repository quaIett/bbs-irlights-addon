package qualet.irlite.client.light;

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
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.light.cookie.CookieArray;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;
import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import org.qualet.irl.light.ClusterGridBuffer;
import org.qualet.irl.light.LightBuffer;
import org.qualet.irl.light.LightMath;
import org.qualet.irl.light.LightRegistry;
import org.qualet.irl.light.VlGlobalsBuffer;

import qualet.irlite.IrliteConfig;

import java.util.ArrayList;
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
    /** Shared camera-space horizon for lights and the casters that may shadow them. */
    public static final double MAX_DIST = 256.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;
    private static final ThreadLocal<TraversalScratch> SCRATCH = ThreadLocal.withInitial(TraversalScratch::new);

    /** Storage only: every matrix/vector is overwritten from current form values.
     *  A stack keeps parents and siblings separate and also permits a nested
     *  collection from BBS callbacks. No form, world or pose is retained here. */
    static final class TraversalScratch
    {
        private final List<ScratchFrame> frames = new ArrayList<>();
        private int depth;

        int mark() { return depth; }
        ScratchFrame push()
        {
            if (depth == frames.size()) frames.add(new ScratchFrame());
            return frames.get(depth++);
        }
        void rewind(int mark) { depth = mark; }
    }

    static final class ScratchFrame
    {
        final Matrix4f local = new Matrix4f();
        final Matrix4f transform = new Matrix4f();
        final Matrix4f child = new Matrix4f();
        final Vector4f origin = new Vector4f();
        final Vector4f forward = new Vector4f();
    }

    /** VL depth-aware bilateral upsample (UBO flags bit6): always on, no UI knob —
     *  at IRLITE_VL_RESOLUTION 1.0 it converges to plain bilinear, below 1.0 it is
     *  what makes reduced res viable. Dev A/B kill-switch (needs restart):
     *  -Dirlite.vlNoBilateral=true. Mirrored by VlSweep.overrideVlGlobals. */
    public static final boolean VL_BILATERAL = !Boolean.getBoolean("irlite.vlNoBilateral");

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
        // Track the "max shader lights" slider each frame: caps how many lights the
        // flush packs into the SSBO (registration + shadow caches still see them all).
        LightRegistry.setUploadCap(IrliteConfig.maxShaderLights());
        // Clustering has no knob: it is always on (core default), the image is
        // identical either way and it only ever makes the per-pixel loop cheaper.
        // For an A/B measurement, start with -Dirlite.noClustering=true.
        // Track the VL intensity slider each frame: lands in the SSBO header on
        // upload, so patched shaders read it live without a recompile.
        LightBuffer.setVlGlobalIntensity(IrliteConfig.vlIntensity());
        // Track the live VL toggles each frame: packed as header bit flags
        // (bit0 = VL shadows, bit1 = VL noise) read by runtime-flag patches.
        LightBuffer.setVlFlags((IrliteConfig.vlShadowsLive() ? 1 : 0) | (IrliteConfig.vlNoiseLive() ? 2 : 0));
        // Track the full VL knob set each frame: lands in the globals UBO
        // (binding 7) on upload, so UBO-era patches read every VL number and
        // flag live without a recompile (bit0 = VL shadows, bit1 = VL noise,
        // bit2 = blue-noise dither, bit3 = temporal dither rotation,
        // bit4 = VL cluster culling, bit5 = Hi-Z skip, bit6 = bilateral
        // upsample). The two header pushes above stay for pre-UBO patches
        // until the fleet is regenerated.
        VlGlobalsBuffer.set(
            IrliteConfig.vlIntensity(),
            IrliteConfig.vlMaxDist(),
            IrliteConfig.vlTipBoost(),
            IrliteConfig.vlTipRadius(),
            IrliteConfig.vlNoiseAmount(),
            IrliteConfig.vlNoiseScale(),
            IrliteConfig.vlNoiseSpeed(),
            IrliteConfig.vlNoiseMorph(),
            IrliteConfig.vlSteps(),
            IrliteConfig.vlShadowStride(),
            IrliteConfig.vlNoiseStride(),
            (IrliteConfig.vlShadowsLive() ? 1 : 0) | (IrliteConfig.vlNoiseLive() ? 2 : 0)
                | (IrliteConfig.vlBlueNoise() ? 4 : 0) | (IrliteConfig.vlDitherTemporal() ? 8 : 0)
                | (IrliteConfig.vlClusterCull() ? 16 : 0) | (IrliteConfig.vlShadowHiz() ? 32 : 0)
                | (VL_BILATERAL ? 64 : 0)
        );
        // Outline knobs ride the same UBO but push separately: the sweep below
        // rebuilds the VL flag word from the VL toggles alone, so folding the
        // outline bits into that argument would let a sweep clear them. The core
        // ORs the two flag words together at upload instead.
        VlGlobalsBuffer.setOutline(
            IrliteConfig.outline(),
            IrliteConfig.outlineTarget(),
            IrliteConfig.outlineStrength(),
            IrliteConfig.outlineFresnelPower(),
            IrliteConfig.outlineBack(),
            IrliteConfig.outlineFront(),
            IrliteConfig.outlineFrontStrength(),
            IrliteConfig.outlineGlow(),
            IrliteConfig.outlineGlowStrength(),
            IrliteConfig.outlinePixelSize()
        );
        VlGlobalsBuffer.setShadow(IrliteConfig.shadowsLive(), IrliteConfig.shadowSoftness());
        VlGlobalsBuffer.setSurface(
            IrliteConfig.diffuse(),
            IrliteConfig.intensity(),
            IrliteConfig.specular(),
            IrliteConfig.specularIntensity(),
            IrliteConfig.toon(),
            IrliteConfig.toonBands(),
            IrliteConfig.toonSmooth()
        );
        // Dev VL profiler sweep (-Dirlite.profileVl=true): may re-issue the push
        // above with per-config flag overrides — last write wins before upload.
        // New VlGlobalsBuffer.set args must be mirrored in VlSweep.overrideVlGlobals.
        // setOutline/setShadow/setSurface are NOT mirrored there by design — the
        // sweep only varies VL, and their flag words are separate from set()'s.
        VlProfiler.overrideVlGlobals();

        if (world == null || cameraPos == null)
        {
            return;
        }

        TraversalScratch scratch = SCRATCH.get();
        scanBlockEntities(world, cameraPos, tickDelta, scratch);
        scanFilmReplays(cameraPos, tickDelta, scratch);
    }

    private static void scanBlockEntities(ClientWorld world, Vec3d cameraPos, float tickDelta, TraversalScratch scratch)
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

            // Keep the (potentially huge) world coordinate OUT of the float matrix:
            // build the form tree RELATIVE to the block cell and carry the block's
            // world position as a double base, added back only at emit. At X=100000 a
            // float matrix would quantize the position to ~8 mm; a double base does not.
            int mark = scratch.mark();
            try
            {
                ScratchFrame frame = scratch.push();
                Matrix4f root = frame.local.identity();
                root.translate(0.5F, 0F, 0.5F);
                Transform propsT = props.getTransform();
                if (propsT != null)
                {
                    propsT.setupMatrix(frame.transform.identity());
                    root.mul(frame.transform);
                }

                walk(rootForm, root, pos.getX(), pos.getY(), pos.getZ(), tickDelta, scratch);
            }
            finally
            {
                scratch.rewind(mark);
            }
        }
    }

    /** {@code base[XYZ]} is the form tree's world origin, carried in double so a far-
     *  from-origin coordinate never enters the float {@code parent} matrix; it is added
     *  back to the matrix-local offset at emit. The matrix therefore only ever holds
     *  small, block-local (or actor-local) values.
     *
     *  {@code transition} is the frame's partial tick, forwarded into
     *  {@link Form#applyStates} so a form's animation states drive the light exactly
     *  as they drive the visible render. */
    private static void walk(Form form, Matrix4f parent, double baseX, double baseY, double baseZ, float transition,
                             TraversalScratch scratch)
    {
        if (form == null)
        {
            return;
        }

        // Overlay this form's animation states before reading its transform, exactly
        // as FormRenderer.render() does (applyStates -> read transforms + walk the
        // subtree -> unapplyStates). BBS lays an animation frame onto a form's Value
        // fields (transform, visible, ...) as a transient runtime override that lives
        // ONLY inside that render window; the scanner runs at renderWorld HEAD, wholly
        // outside it, so without this it would read the static base pose and a
        // ModelBlock-placed light would never follow its form's animation. No-op for
        // a form with no active state players. unapplyStates() sits in finally so the
        // pair stays balanced (including the early returns below), leaving a clean
        // base for the later real render to re-apply from. States are re-applied per
        // form on the recursion, matching the render's per-form apply nesting.
        form.applyStates(transition);
        int mark = scratch.mark();
        try
        {
            if (!form.visible.get())
            {
                return;
            }

            ScratchFrame frame = scratch.push();
            Matrix4f local = frame.local.set(parent);
            Transform t = form.transform.get();
            if (t != null)
            {
                t.setupMatrix(frame.transform.identity());
                local.mul(frame.transform);
            }

            if (form instanceof PointLightForm point)
            {
                emitPoint(point, local, baseX, baseY, baseZ, frame.origin);
            }
            else if (form instanceof SpotlightForm spot)
            {
                emitSpot(spot, local, baseX, baseY, baseZ, frame.origin, frame.forward);
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

                Matrix4f childM = frame.child.set(local);
                Transform pt = part.transform.get();
                if (pt != null)
                {
                    pt.setupMatrix(frame.transform.identity());
                    childM.mul(frame.transform);
                }

                walk(child, childM, baseX, baseY, baseZ, transition, scratch);
            }
        }
        finally
        {
            try
            {
                form.unapplyStates();
            }
            finally
            {
                scratch.rewind(mark);
            }
        }
    }

    /**
     * Registers lamps from the dashboard film-editor preview replays in pure
     * world coordinates (roll-independent). Covers ONLY the dashboard editor's
     * non-actor replays — in-world replays and live actors keep registering via
     * the form-renderer path, where the rig pose is available. Gated on the
     * dashboard being open so we never light a viewport that isn't showing.
     */
    private static void scanFilmReplays(Vec3d cameraPos, float tickDelta, TraversalScratch scratch)
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

        // BBS 2.6 keys the film's entities by the replay's stable id, not its list index.
        java.util.Map<String, IEntity> entities = editor.getEntities();
        for (int replayId = 0; replayId < replays.size(); replayId++)
        {
            Replay replay = replays.get(replayId);
            if (replay == null || replay.actor.get())
            {
                continue;
            }

            IEntity ent = entities.get(replay.getId());
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

            // Rotation-only root; the actor's world position rides along as a double base
            // (kept out of the float matrix so it stays precise far from origin). The base
            // is added AFTER the rotation at emit, reproducing the old translate*rotate.
            float bodyYaw = MathHelper.lerp(tickDelta, ent.getPrevBodyYaw(), ent.getBodyYaw());
            int mark = scratch.mark();
            try
            {
                Matrix4f root = scratch.push().local.identity();
                root.rotateY((float) Math.toRadians(-bodyYaw));
                walk(rootForm, root, wx, wy, wz, tickDelta, scratch);
            }
            finally
            {
                scratch.rewind(mark);
            }
        }
    }

    public static FilmEditorController getActiveEditorController()
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

    private static void emitPoint(PointLightForm form, Matrix4f matrix, double baseX, double baseY, double baseZ,
                                  Vector4f origin)
    {
        matrix.transform(origin.set(0F, 0F, 0F, 1F));

        // origin is the small matrix-local offset; add the double base back to recover
        // the absolute world position without the far-from-origin float quantization.
        Color c = form.color.get();
        LightRegistry.registerPoint(baseX + origin.x, baseY + origin.y, baseZ + origin.z, c.r, c.g, c.b, form.intensity.get(), form.radius.get(), form.entitiesOnly.get(), form.blocksOnly.get(), form.anisotropy.get(), form.vlDensity.get(), form.beamStrength.get(), form.bulbSize.get(), form.shadows.get(), System.identityHashCode(form));
        LightEffectsRegistration.apply(form);
    }

    private static void emitSpot(SpotlightForm form, Matrix4f matrix, double baseX, double baseY, double baseZ,
                                 Vector4f origin, Vector4f forward)
    {
        matrix.transform(origin.set(0F, 0F, 0F, 1F));

        // Local +Z = the direction the spotlight points (matches the editor gizmo).
        matrix.transform(forward.set(0F, 0F, 1F, 0F));
        LightMath.normalizeDir(forward.x, forward.y, forward.z, 0F, 0F, 1F, forward);
        float dx = forward.x, dy = forward.y, dz = forward.z;

        LightMath.Cone cone = LightMath.cone(form.radius.get(), form.innerRadius.get());
        float cosOuter = cone.cosOuter();
        float cosInner = cone.cosInner();

        // Resolve the gobo texture (BBS Link) to its texture-array layer (loads on
        // first use, cached after); -1 = no mask, so the cookie is OFF unless a
        // texture is picked. Rotation is stored in degrees on the form (UI/keyframe
        // friendly) -> radians for the SSBO. Cheap per-frame: a link->layer lookup.
        int cookieLayer = CookieArray.resolve(form.cookie.get());
        float cookieRot = (float) Math.toRadians(form.cookieRotation.get());
        float cookieFlags = form.cookieInvert.get() ? 1F : 0F;

        // Direction (w=0) is translation-invariant; only the origin gets the double base
        // added back to recover the absolute world position without float quantization.
        Color c = form.color.get();
        LightRegistry.registerSpot(baseX + origin.x, baseY + origin.y, baseZ + origin.z, dx, dy, dz, c.r, c.g, c.b, form.intensity.get(), form.range.get(), cosOuter, cosInner, form.entitiesOnly.get(), form.blocksOnly.get(), form.anisotropy.get(), form.vlDensity.get(), form.beamStrength.get(), form.bulbSize.get(), form.shadows.get(), (float) cookieLayer, cookieRot, form.cookieScale.get(), cookieFlags, System.identityHashCode(form));
        LightEffectsRegistration.apply(form);
    }
}
