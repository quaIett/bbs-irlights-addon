package org.qualet.irl.light.shadow;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.LightCollector;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;
import qualet.irlite.mixin.client.bbs.FilmsAccessor;
import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * The IRLite {@link ShadowCasterSource}: BBS Form/Film/Morph silhouettes — the
 * distinguishing CAST half of the seam that makes a baked shadow match the BBS
 * in-editor preview (custom morph), where the redactor casts the real vanilla
 * model. Three caster kinds, union of three {@code collect} arms and three
 * {@code emitOccluder} draw arms:
 *
 * <ul>
 *   <li>ENTITY — world {@link LivingEntity}/{@link ItemEntity}, drawn BBS-morph
 *       first ({@link MorphRenderer#renderPlayer}/{@link MorphRenderer#renderLivingEntity}),
 *       vanilla {@link EntityRenderDispatcher} fallback.</li>
 *   <li>MODEL_BLOCK — BBS {@link ModelBlockEntity} props, drawn via the BBS
 *       {@link FormRenderer}.</li>
 *   <li>REPLAY — active Film replay stubs (non-actor), drawn via the BBS
 *       {@link FormRenderer}.</li>
 * </ul>
 *
 * <p>The orchestration ({@link ShadowBaker}/{@link ShadowRenderer}) is
 * variant-agnostic and unchanged; this is the only IRLite-specific file. See
 * {@code irl-core/docs/shadow-caster-seam-spec.md} for the 5 invariants. Source-side
 * BBS reflection/accessor try/catch lives INSIDE {@code collect} (INVARIANT 4
 * scoping); the {@code emitOccluder} draw arms NEVER catch — a throw propagates
 * to the shared {@code ShadowRenderer.emitCaster} wrapper for run isolation.
 */
public final class IRLiteBbsCasterSource implements ShadowCasterSource
{
    /** Match the light collector's camera horizon. This removes the old 72-block
     *  mismatch for co-located lamp/caster scenes; the global bounded pool remains
     *  intentionally camera-prioritized for casters beyond this horizon. */
    private static final double COLLECT_DIST = LightCollector.MAX_DIST;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;
    private static final int FULL_LIGHT = LightmapTextureManager.pack(15, 15);

    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;

    /** Mirror of {@code ShadowBaker.OVERLAP_MARGIN} (private there). The model-block
     *  arm computes its bounding sphere by hand and emits via the raw
     *  {@link OccluderSink#emit} escape, which (unlike {@code emitFromBox}) does NOT
     *  add the cull slack — so add it here to match the entity/replay arms. Keep in sync. */
    private static final float OVERLAP_MARGIN = 0.5f;

    // ===================================================================== //
    //  collect — WHAT casts (entity -> model-block -> replay; stable arm      //
    //  order preserves deterministic equal-distance ties in the bounded set).//
    // ===================================================================== //

    @Override
    public void collect(ClientWorld world, Vec3d camPos, float tickDelta, OccluderSink sink)
    {
        double camX = camPos.x, camY = camPos.y, camZ = camPos.z;

        // --- Arm 1: world entities (vanilla / BBS-morph render path) ---
        for (Entity entity : world.getEntities())
        {
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity))
            {
                continue;
            }

            double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
            double dx = ex - camX, dy = ey - camY, dz = ez - camZ;
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
            {
                continue;
            }

            // emitFromBox raises the center to mid-height and derives the
            // circumscribing box-diagonal radius (INVARIANT 5); the sink retains
            // the bounded nearest set. Entities are always dynamic -> isStatic
            // false, staticHash 0 (INVARIANT 2). A morphed player/actor DRAWS
            // its BBS form transformed by the form chain (feet + R_yaw·(T_f +
            // R_f·S_f·geom)) while the vanilla box does not follow it — a
            // non-identity chain gets the replay-arm treatment on the vanilla
            // box dims: fold + primary-rotation expansion, yaw-symmetrized
            // horizontals, signed vertical center. Identity chains (all plain
            // entities) keep the legacy emitFromBox channel (tight rh/hv).
            Form morphForm = entityMorphForm(entity);
            Transform morphFt = morphForm == null ? null : foldFormChain(morphForm);
            if (morphForm == null || foldIsIdentity(morphFt))
            {
                sink.emitFromBox(entity, CasterType.ENTITY, false, ex, ey, ez, entity.getBoundingBox(), 1f, 0L);
            }
            else
            {
                Box box = entity.getBoundingBox();
                float bhx = (float) (box.maxX - box.minX) * 0.5f;
                float bhy = (float) (box.maxY - box.minY) * 0.5f;
                float bhz = (float) (box.maxZ - box.minZ) * 0.5f;
                expandInnerBox(morphFt, bhx * foldScale[0], bhy * foldScale[1], bhz * foldScale[2]);
                float hh = (float) (Math.sqrt((double) innerExt[0] * innerExt[0] + (double) innerExt[2] * innerExt[2])
                    + Math.sqrt(innerCenter[0] * innerCenter[0] + innerCenter[2] * innerCenter[2]));
                float rad = (float) Math.sqrt((double) hh * hh + (double) innerExt[1] * innerExt[1]) + OVERLAP_MARGIN;
                sink.emit(entity, CasterType.ENTITY, false,
                    (float) ex, (float) (ey + innerCenter[1]), (float) ez, rad, 0L);
            }
        }

        // --- Arm 2: BBS model blocks (BlockEntity, not in world.getEntities()) ---
        collectModelBlocks(world, camX, camY, camZ, sink);

        // --- Arm 3: BBS film replays (non-actor stubs; actors come via Arm 1) ---
        collectFilmReplays(camX, camY, camZ, tickDelta, sink);
    }

    private static void collectModelBlocks(ClientWorld world, double camX, double camY, double camZ, OccluderSink sink)
    {
        // INVARIANT 4 scoping: all BBS reflection/accessor try/catch stays INSIDE
        // this collect arm. A throwing collect for one caster degrades to
        // "absent", never aborts the bake.
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

        for (int idx = 0, n = tickers.size(); idx < n; idx++)
        {
            BlockEntityTickInvoker invoker = tickers.get(idx);
            if (invoker == null)
            {
                continue;
            }
            BlockPos pos = invoker.getPos();
            if (pos == null)
            {
                continue;
            }

            double dx = pos.getX() + 0.5 - camX;
            double dy = pos.getY() + 0.5 - camY;
            double dz = pos.getZ() + 0.5 - camZ;
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
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
            Form form = props.getForm();
            if (!hasShadowGeometry(form))
            {
                continue;
            }
            Transform t = props.getTransform();
            emitModelBlock(sink, mbe, form, t);
        }
    }

    private static void collectFilmReplays(double camX, double camY, double camZ, float tickDelta, OccluderSink sink)
    {
        // INVARIANT 4 scoping: all BBS reflection/accessor try/catch stays INSIDE.
        Films films;
        try { films = BBSModClient.getFilms(); }
        catch (Throwable t) { return; }
        if (films == null)
        {
            return;
        }

        List<BaseFilmController> ctrls;
        try { ctrls = ((FilmsAccessor) (Object) films).irlite$getControllers(); }
        catch (Throwable t) { ctrls = null; }

        FilmEditorController editor = getActiveEditorController();
        int worldN = ctrls == null ? 0 : ctrls.size();
        int total = worldN + (editor != null ? 1 : 0);

        for (int ci = 0; ci < total; ci++)
        {
            BaseFilmController ctrl = ci < worldN ? ctrls.get(ci) : editor;
            if (ctrl == null || ctrl.film == null || ctrl.film.replays == null)
            {
                continue;
            }

            List<Replay> replays;
            try { replays = ctrl.film.replays.getList(); }
            catch (Throwable t) { continue; }
            if (replays == null || replays.isEmpty())
            {
                continue;
            }

            for (IntObjectMap.PrimitiveEntry<IEntity> e : ctrl.getEntities().entries())
            {
                int rid = e.key();
                if (rid < 0 || rid >= replays.size())
                {
                    continue;
                }
                Replay replay = replays.get(rid);
                if (replay == null || replay.actor.get())
                {
                    // Skip actor replays — real actors come via the entity arm.
                    continue;
                }
                IEntity ent = e.value();
                if (ent == null)
                {
                    continue;
                }
                Form form = ent.getForm();
                if (!hasShadowGeometry(form))
                {
                    continue;
                }

                double wx = MathHelper.lerp(tickDelta, ent.getPrevX(), ent.getX());
                double wy = MathHelper.lerp(tickDelta, ent.getPrevY(), ent.getY());
                double wz = MathHelper.lerp(tickDelta, ent.getPrevZ(), ent.getZ());

                double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
                if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
                {
                    continue;
                }

                // INVARIANT 5: replay form hitbox, grown by the form chain the
                // DRAW applies (form.transform + overlays + config scale — the
                // old raw-hitbox box under-bounded any scaled replay form), then
                // rotation-expanded by the primary form rotation. Body yaw is a
                // per-frame rotation about the feet axis, so the horizontal
                // extent is SYMMETRIZED (box corner radius + horizontal center
                // offset) to stay yaw-invariant; the vertical center keeps the
                // form-translate offset. Non-identity chains use the raw emit
                // because that sphere is neither feet-centered nor expressible
                // as emitFromBox's raised box (rh=hv=radius — conservative for
                // the spot dyn-rect); identity chains keep emitFromBox. Replays
                // are always dynamic -> isStatic false, staticHash 0 (INVARIANT 2).
                float hbW = Math.max(0.1f, form.hitboxWidth.get());
                float hbH = Math.max(0.1f, form.hitboxHeight.get());
                Transform ft = foldFormChain(form);
                if (foldIsIdentity(ft))
                {
                    // Identity chain (the common untransformed replay): keep the
                    // legacy emitFromBox channel — its tight per-axis rh/hv keep
                    // the spot dyn-rect small; the raw-sphere fallback would
                    // quadruple the rect area for every ordinary replay.
                    Box box = new Box(-hbW * 0.5, 0, -hbW * 0.5, hbW * 0.5, hbH, hbW * 0.5);
                    sink.emitFromBox(ent, CasterType.REPLAY, false, wx, wy, wz, box, 1f, 0L);
                }
                else
                {
                    expandInnerBox(ft, hbW * 0.5f * foldScale[0], hbH * 0.5f * foldScale[1], hbW * 0.5f * foldScale[2]);
                    float hh = (float) (Math.sqrt((double) innerExt[0] * innerExt[0] + (double) innerExt[2] * innerExt[2])
                        + Math.sqrt(innerCenter[0] * innerCenter[0] + innerCenter[2] * innerCenter[2]));
                    float rad = (float) Math.sqrt((double) hh * hh + (double) innerExt[1] * innerExt[1]) + OVERLAP_MARGIN;
                    sink.emit(ent, CasterType.REPLAY, false,
                        (float) wx, (float) (wy + innerCenter[1]), (float) wz, rad, 0L);
                }
            }
        }
    }

    /**
     * Light forms deliberately emit no depth during a custom shadow bake, but
     * their body parts are still rendered by BBS after render3D returns. Drop a
     * form only when its structure consists entirely of Point/Spot lights. A
     * non-light descendant is conservatively retained, while pure light hosts no
     * longer consume bounded-pool slots.
     */
    private static boolean hasShadowGeometry(Form form)
    {
        if (form == null)
        {
            return false;
        }
        if (!(form instanceof PointLightForm) && !(form instanceof SpotlightForm))
        {
            return true;
        }

        List<BodyPart> parts = form.parts.getAllTyped();
        if (parts == null)
        {
            return false;
        }
        for (int i = 0, n = parts.size(); i < n; i++)
        {
            BodyPart part = parts.get(i);
            if (part != null && hasShadowGeometry(part.getForm()))
            {
                return true;
            }
        }
        return false;
    }

    private static FilmEditorController getActiveEditorController()
    {
        try
        {
            // Only when the dashboard is actually open. getDashboardIfCreated
            // returns the cached instance even after closing to the world, so
            // without this its film panel keeps reporting the replay stub and
            // we'd bake a shadow for a replay that's no longer rendered.
            if (MinecraftClient.getInstance().currentScreen == null)
            {
                return null;
            }

            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
            if (dashboard == null)
            {
                return null;
            }
            if (!(dashboard.getPanels().panel instanceof UIFilmPanel filmPanel))
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

    /**
     * INVARIANT 5: append a model block's occluder with a bounding sphere that
     * CIRCUMSCRIBES the geometry exactly as {@link #drawModelBlock} draws it.
     *
     * <p>TWO transform layers stack between the hitbox and the drawn pixels.
     * OUTER (the block transform {@code t}): the draw moves to the block center,
     * then {@link MatrixStackUtils#applyTransform} translates by
     * {@code t.translate}, rotates ({@code Rz·Ry·Rx},
     * {@link Transform#createRotationMatrix}) and scales — pivot = block center
     * + 1×translate, matching the BBS render (the old 2× fold was a draw-arm
     * bug, fixed with the 2026-08-10 review). INNER (applied
     * by BBS inside {@code FormRenderer.render}, which the old sphere ignored —
     * the root cause of the scaled-form clip/vanish bug): {@code form.transform}
     * + {@code transformOverlay} + {@code additionalTransforms} (additive fold)
     * and the bbmodel config scale. {@link #foldFormChain} folds those into the
     * hitbox half-extents (scale RATCHETED &gt;= 1 — the sphere may only grow
     * vs the old math) and the inner box is expanded by the primary form
     * rotation and offset by the folded translate; the result then rides the
     * outer chain exactly as before. Radius = |outer-expanded half-extents| +
     * slack; the slack keeps covering what no transform reports (animation
     * poses, geometry authored past the hitbox).
     *
     * <p>Uses the raw {@link OccluderSink#emit} escape because the sphere is
     * neither feet-centered nor x/z-symmetric, so {@code emitFromBox} cannot
     * express it. INVARIANT 2/3 unchanged (all dynamic, 0L).
     */
    private static void emitModelBlock(OccluderSink sink, ModelBlockEntity mbe, Form form, Transform t)
    {
        float hbW = Math.max(0.1f, form.hitboxWidth.get());
        float hbH = Math.max(0.1f, form.hitboxHeight.get());

        // Inner chain first: hitbox half-extents grown by the form-chain fold,
        // expanded by the primary form rotation, offset by the folded translate.
        // Feet at y=0, centered in x/z -> inner box center = (0, ihy, 0) pre-fold.
        Transform ft = foldFormChain(form);
        expandInnerBox(ft, hbW * 0.5f * foldScale[0], hbH * 0.5f * foldScale[1], hbW * 0.5f * foldScale[2]);
        float fex = innerExt[0], fey = innerExt[1], fez = innerExt[2];
        double ficx = innerCenter[0], ficy = innerCenter[1], ficz = innerCenter[2];

        // Outer (block) chain: abs scale for extents, SIGNED scale for the
        // center product (a mirror flip moves the center with the geometry).
        float sx = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.x));
        float sy = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.y));
        float sz = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.z));
        float hx = fex * sx;
        float hy = fey * sy;
        float hz = fez * sz;
        double scx = (t == null ? 1.0 : t.scale.x) * ficx;
        double scy = (t == null ? 1.0 : t.scale.y) * ficy;
        double scz = (t == null ? 1.0 : t.scale.z) * ficz;

        BlockPos pos = mbe.getPos();
        double tx = t == null ? 0 : t.translate.x;
        double ty = t == null ? 0 : t.translate.y;
        double tz = t == null ? 0 : t.translate.z;
        // Pivot = block center + ONE t.translate, matching drawModelBlock (which
        // matches the BBS render since the 2026-08-10 review: translate applied
        // once, by applyTransform). Keep the two in lockstep.
        double pivotX = pos.getX() + 0.5 + tx;
        double pivotY = pos.getY() + ty;
        double pivotZ = pos.getZ() + 0.5 + tz;

        float ehx, ehy, ehz;       // abs-rotation-expanded half-extents (radius source)
        double offX, offY, offZ;   // inner center carried through the pivot rotation: R·(scx,scy,scz)
        if (t == null)
        {
            ehx = hx; ehy = hy; ehz = hz;
            offX = scx; offY = scy; offZ = scz;
        }
        else
        {
            // R = Rz·Ry·Rx (createRotationMatrix), identical to applyTransform's order.
            Matrix3f rot = t.createRotationMatrix();
            // half-extent in world axis i = Σ_j |M[i][j]| · h[j]; JOML Matrix3f is
            // column-major (m<col><row>), so the world-axis-i row is (m0i, m1i, m2i).
            ehx = Math.abs(rot.m00) * hx + Math.abs(rot.m10) * hy + Math.abs(rot.m20) * hz;
            ehy = Math.abs(rot.m01) * hx + Math.abs(rot.m11) * hy + Math.abs(rot.m21) * hz;
            ehz = Math.abs(rot.m02) * hx + Math.abs(rot.m12) * hy + Math.abs(rot.m22) * hz;
            // R·(scx,scy,scz) = colX·scx + colY·scy + colZ·scz (columns of the
            // column-major JOML matrix).
            offX = rot.m00 * scx + rot.m10 * scy + rot.m20 * scz;
            offY = rot.m01 * scx + rot.m11 * scy + rot.m21 * scz;
            offZ = rot.m02 * scx + rot.m12 * scy + rot.m22 * scz;
        }

        double cx = pivotX + offX;
        double cy = pivotY + offY;
        double cz = pivotZ + offZ;
        // Minimal circumscribing radius = half the rotated box's diagonal =
        // |(ehx,ehy,ehz)|, + cull slack. The plain OVERLAP_MARGIN is not enough here:
        // the sphere derives from the form's HITBOX, and the drawn model routinely
        // extends past it, so at a cone/reach boundary the caster culls out ENTIRELY
        // (the whole shadow blinks — the partial-tile rect slack can't help, it only
        // widens the rect of casters that survived the cull). Mirror that rect slack,
        // max(OVERLAP_MARGIN, poseReach·ehy) incl. the sanitizer, on the emitted
        // sphere: growing a cull sphere is strictly conservative, oversize only
        // costs bake speed.
        float poseReach = IrliteConfig.shadowPoseReach();
        if (!(poseReach >= 0f))
        {
            poseReach = 1.0f;
        }
        float slack = Math.max(OVERLAP_MARGIN, poseReach * ehy);
        float radius = (float) Math.sqrt((double) ehx * ehx + (double) ehy * ehy + (double) ehz * ehz) + slack;

        // INVARIANT 2 (CONSERVATIVE, LOCKED): mark ALL model blocks dynamic. An
        // animated model block left isStatic=true would FREEZE its shadow at the first
        // pose; isStatic=false is guaranteed-correct. occType stays MODEL_BLOCK
        // regardless of isStatic (independent axes).
        // TODO(Ф3 perf-debt, Open Q1): mark genuinely-static model-blocks isStatic=true
        // once a safe BBS animation probe is confirmed — see shadow-phase3-port-plan.md §8.
        boolean isStatic = false;
        // INVARIANT 3: per-caster CONTENT only (the order-independent cross-caster
        // avalanche+count fold lives in the shared ShadowBaker). Non-static casters
        // supply EXACTLY 0L; dead today (isStatic hardcoded false).
        long staticHash = isStatic
            ? modelBlockHash((float) cx, (float) cy, (float) cz, t, System.identityHashCode(form))
            : 0L;

        sink.emit(mbe, CasterType.MODEL_BLOCK, isStatic, (float) cx, (float) cy, (float) cz, radius, staticHash);
    }

    /** Scratch of {@link #foldFormChain} (collect runs on the render thread only):
     *  per-component scale fold (RATCHETED &gt;= 1) and additive translate fold. */
    private static final float[] foldScale = new float[3];
    private static final float[] foldTranslate = new float[3];
    /** Sign of the folded Y scale: a mirrored (negative-Y) chain draws BELOW the
     *  feet, so the inner box center must flip with it (extents stay abs). */
    private static float foldSignY = 1f;

    /** Inner-box expansion scratch of {@link #expandInnerBox} (render thread only). */
    private static final float[] innerExt = new float[3];
    private static final double[] innerCenter = new double[3];

    /** BBS 2.3 exposes the ModelInstance config scale as a public FIELD; BBS 2.4
     *  replaced it with getScale() — a direct field read compiles against the
     *  2.3.1 jar but throws NoSuchFieldError on 2.4 hosts. One addon jar spans
     *  both (the UITrackpad#limit precedent), so resolve whichever accessor
     *  exists ONCE and cache it. */
    private static Field cfgScaleField;
    private static Method cfgScaleGetter;
    private static boolean cfgScaleResolved;

    /**
     * Fold every transform source BBS stacks between the hitbox and the drawn
     * pixels INSIDE the caster's anchor transform: {@code form.transform} +
     * {@code transformOverlay} + {@code additionalTransforms} (additive scale
     * and translate fold, mirroring {@code FormRenderer.createTransform}) and
     * the bbmodel config scale (multiplicative; PURE cache read — never
     * {@code ModelManager.getModel}, which enqueues a load for models no render
     * path asked for — through the version-bridged {@link #modelConfigScale},
     * inside its own inner try so config drift degrades only that factor).
     * Scale components are RATCHETED to &gt;= 1: the fold may
     * only GROW the old hitbox-only sphere, never shrink it, so a scaled-DOWN
     * form keeps today's bound and no previously-working scene can regress.
     * The primary form rotation is returned for the callers' |R| expansion
     * (overlay/additional rotations are recording-pose tracks, near-identity
     * in practice; the pose slack covers the residual). INVARIANT 4: BBS drift
     * lands in the catch and degrades to the identity fold.
     */
    private static Transform foldFormChain(Form form)
    {
        foldScale[0] = foldScale[1] = foldScale[2] = 1f;
        foldTranslate[0] = foldTranslate[1] = foldTranslate[2] = 0f;
        foldSignY = 1f;
        try
        {
            Transform ft = form.transform.get();
            Transform ov = form.transformOverlay.get();
            float sx = ft == null ? 1f : ft.scale.x;
            float sy = ft == null ? 1f : ft.scale.y;
            float sz = ft == null ? 1f : ft.scale.z;
            float tx = ft == null ? 0f : ft.translate.x;
            float ty = ft == null ? 0f : ft.translate.y;
            float tz = ft == null ? 0f : ft.translate.z;
            if (ov != null)
            {
                sx += ov.scale.x - 1f;
                sy += ov.scale.y - 1f;
                sz += ov.scale.z - 1f;
                tx += ov.translate.x;
                ty += ov.translate.y;
                tz += ov.translate.z;
            }
            List<ValueTransform> extra = form.additionalTransforms;
            if (extra != null)
            {
                for (int i = 0, n = extra.size(); i < n; i++)
                {
                    ValueTransform vt = extra.get(i);
                    Transform at = vt == null ? null : vt.get();
                    if (at != null)
                    {
                        sx += at.scale.x - 1f;
                        sy += at.scale.y - 1f;
                        sz += at.scale.z - 1f;
                        tx += at.translate.x;
                        ty += at.translate.y;
                        tz += at.translate.z;
                    }
                }
            }

            float cfgX = 1f, cfgY = 1f, cfgZ = 1f;
            if (form instanceof ModelForm mf)
            {
                // Own try: config-scale drift must degrade ONLY the config
                // factor, never the user transform fold already computed above.
                try
                {
                    ModelInstance model = BBSModClient.getModels().models.get(mf.model.get());
                    Vector3f s = model == null ? null : modelConfigScale(model);
                    if (s != null)
                    {
                        cfgX = s.x; cfgY = s.y; cfgZ = s.z;
                    }
                }
                catch (Throwable ignored)
                {
                }
            }

            foldSignY = sy * cfgY < 0f ? -1f : 1f;
            foldScale[0] = Math.max(1f, Math.abs(sx * cfgX));
            foldScale[1] = Math.max(1f, Math.abs(sy * cfgY));
            foldScale[2] = Math.max(1f, Math.abs(sz * cfgZ));
            foldTranslate[0] = tx;
            foldTranslate[1] = ty;
            foldTranslate[2] = tz;
            return ft;
        }
        catch (Throwable t)
        {
            foldScale[0] = foldScale[1] = foldScale[2] = 1f;
            foldTranslate[0] = foldTranslate[1] = foldTranslate[2] = 0f;
            foldSignY = 1f;
            return null;
        }
    }

    /** @see #cfgScaleField */
    private static Vector3f modelConfigScale(ModelInstance model)
    {
        try
        {
            if (!cfgScaleResolved)
            {
                try
                {
                    cfgScaleField = ModelInstance.class.getField("scale");
                }
                catch (Throwable t)
                {
                    cfgScaleGetter = ModelInstance.class.getMethod("getScale");
                }
                cfgScaleResolved = true;
            }
            Object s = cfgScaleField != null ? cfgScaleField.get(model)
                : cfgScaleGetter != null ? cfgScaleGetter.invoke(model) : null;
            return s instanceof Vector3f v ? v : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /**
     * Expand the folded inner (form-space) box through the primary form
     * rotation: half-extents {@code ih*} (already fold-scaled, feet-anchored,
     * centered in x/z, growing along {@code foldSignY}·Y) become the
     * |R|-expanded {@link #innerExt}, and {@link #innerCenter} = folded
     * translate + R·(0, ihy·signY, 0) — the box center carried through the
     * feet-pivot rotation. {@code ft == null} = identity rotation.
     */
    private static void expandInnerBox(Transform ft, float ihx, float ihy, float ihz)
    {
        float sy = ihy * foldSignY;
        if (ft == null)
        {
            innerExt[0] = ihx; innerExt[1] = ihy; innerExt[2] = ihz;
            innerCenter[0] = foldTranslate[0];
            innerCenter[1] = foldTranslate[1] + sy;
            innerCenter[2] = foldTranslate[2];
            return;
        }
        Matrix3f rf = ft.createRotationMatrix();
        innerExt[0] = Math.abs(rf.m00) * ihx + Math.abs(rf.m10) * ihy + Math.abs(rf.m20) * ihz;
        innerExt[1] = Math.abs(rf.m01) * ihx + Math.abs(rf.m11) * ihy + Math.abs(rf.m21) * ihz;
        innerExt[2] = Math.abs(rf.m02) * ihx + Math.abs(rf.m12) * ihy + Math.abs(rf.m22) * ihz;
        innerCenter[0] = foldTranslate[0] + rf.m10 * sy;
        innerCenter[1] = foldTranslate[1] + rf.m11 * sy;
        innerCenter[2] = foldTranslate[2] + rf.m12 * sy;
    }

    /** True when the fold is the identity (unit ratcheted scale, positive sign,
     *  zero translate) and the primary rotation is zero — callers then keep the
     *  legacy emitFromBox channel, whose tight per-axis extents keep the spot
     *  dyn-rect small (the raw-sphere fallback would inflate its area ~4x for
     *  every untransformed caster). */
    private static boolean foldIsIdentity(Transform ft)
    {
        if (foldScale[0] != 1f || foldScale[1] != 1f || foldScale[2] != 1f
            || foldSignY != 1f
            || foldTranslate[0] != 0f || foldTranslate[1] != 0f || foldTranslate[2] != 0f)
        {
            return false;
        }
        return ft == null || (ft.rotate.x == 0f && ft.rotate.y == 0f && ft.rotate.z == 0f);
    }

    /** The BBS form a world entity is DRAWN as, or null for a plain vanilla
     *  entity: player morphs via {@code Morph.getMorph}, living entities via
     *  the {@code SelectorOwner} path — the same order {@link #drawEntity}
     *  renders them in. Pure reads only — never {@code SelectorOwner.check()},
     *  which mutates selector state. INVARIANT 4: throws degrade to null. */
    private static Form entityMorphForm(Entity entity)
    {
        try
        {
            if (entity instanceof AbstractClientPlayerEntity player)
            {
                Morph morph = Morph.getMorph(player);
                return morph == null ? null : morph.getForm();
            }
            if (entity instanceof ISelectorOwnerProvider provider)
            {
                SelectorOwner owner = provider.getOwner();
                return owner == null ? null : owner.getForm();
            }
            return null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Static-occluder signature for a model block: which form it shows plus its
     *  baked center plus the scale + rotation the center alone doesn't capture.
     *  Per-caster CONTENT ONLY — the order-independent cross-caster fold lives in
     *  the shared ShadowBaker (INVARIANT 3). */
    private static long modelBlockHash(float wx, float wy, float wz, Transform t, int formIdentity)
    {
        long h = FNV_OFFSET;
        h = (h ^ (formIdentity & 0xffffffffL)) * FNV_PRIME;
        h = mix(h, wx); h = mix(h, wy); h = mix(h, wz);
        if (t != null)
        {
            h = mix(h, t.scale.x); h = mix(h, t.scale.y); h = mix(h, t.scale.z);
            h = mix(h, t.rotate.x); h = mix(h, t.rotate.y); h = mix(h, t.rotate.z);
            // BBS 2.2.1 removed Transform.rotate2 — the legacy second rotation now
            // folds into the single `rotate`, so hashing `rotate` alone is sufficient.
        }
        // WARNING (Ф3 audit): this deliberately covers only form-IDENTITY + Transform,
        // NOT the form's internal morph/animation pose. It is dead today (isStatic is
        // hardcoded false). If the Open-Q1 animation probe is ever enabled, isStatic=true
        // MUST be gated on a confirmed NON-animating morph — a stable Transform alone is
        // NOT sufficient, or the old animated-block freeze (INVARIANT 2) returns.
        return h;
    }

    private static long mix(long h, float v)
    {
        return (h ^ (Float.floatToRawIntBits(v) & 0xffffffffL)) * FNV_PRIME;
    }

    // ===================================================================== //
    //  emitOccluder — HOW to draw ONE shortlisted caster. The shared wrapper  //
    //  owns the null/inPass guard, depth pins, the setBaking gate, the once-   //
    //  per-pass flush, INVARIANT 1 matrix re-establish, and the INVARIANT 4    //
    //  try/catch + terminateRun. We ONLY append geometry; we NEVER catch our   //
    //  own throw, flush, or repair matrices.                                  //
    // ===================================================================== //

    @Override
    public void emitOccluder(Object caster, int type, float tickDelta, OccluderBatch batch)
    {
        ImmediateOccluderBatch b = (ImmediateOccluderBatch) batch;
        Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        switch (type)
        {
            case CasterType.MODEL_BLOCK ->
                drawModelBlock((ModelBlockEntity) caster, b.matrices(), b.immediate(), cam, tickDelta);
            case CasterType.REPLAY ->
                drawReplay((IEntity) caster, b.matrices(), cam, tickDelta);
            default /* ENTITY */ ->
                drawEntity((Entity) caster, b.matrices(), b.immediate(), tickDelta);
        }
    }

    private static void drawEntity(Entity entity, MatrixStack matrices, VertexConsumerProvider.Immediate immediate, float tickDelta)
    {
        // Light-relative bake: emit geometry relative to the pass anchor (= the
        // light view's origin, ShadowRenderer.currentOrigin*). Subtract in double
        // BEFORE the float cast inside matrices.translate / dispatcher.render so
        // the offsets stay sub-block-magnitude far from the world origin.
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double cx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - ox;
        double cy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - oy;
        double cz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - oz;
        float yaw = entity.getYaw(tickDelta);

        // BBS morph first so morphed players/actors bake their visible
        // silhouette; vanilla dispatcher is the fallback.
        boolean rendered = false;
        if (entity instanceof AbstractClientPlayerEntity player)
        {
            matrices.push();
            matrices.translate(cx, cy, cz);
            rendered = MorphRenderer.renderPlayer(player, yaw, tickDelta, matrices, immediate, FULL_LIGHT);
            matrices.pop();
        }
        if (!rendered && entity instanceof LivingEntity living)
        {
            int overlay = LivingEntityRenderer.getOverlay(living, 0f);
            matrices.push();
            matrices.translate(cx, cy, cz);
            rendered = MorphRenderer.renderLivingEntity(living, yaw, tickDelta, matrices, immediate, FULL_LIGHT, overlay);
            matrices.pop();
        }
        if (!rendered)
        {
            EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
            if (dispatcher != null)
            {
                dispatcher.render(entity, cx, cy, cz, yaw, tickDelta, matrices, immediate, FULL_LIGHT);
            }
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

        // Feet = block center at pos.getY() (no +0.5 in Y); t.translate is NOT
        // folded in — applyTransform below applies it ONCE, exactly like the
        // BBS render (ModelBlockEntityRenderer: translate(0.5,0,0.5) then
        // applyTransform). The old code folded translate into the feet AND let
        // applyTransform add it again, displacing every baked silhouette by one
        // translate vector from the visible model (review 2026-08-10; the
        // sphere pivot in emitModelBlock moved to 1× in the same commit — the
        // two halves must always agree or the cull starts blinking). Light-
        // relative bake: subtract the pass anchor (= the light view origin) in
        // double before matrices.translate's float cast.
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double feetX = mbe.getPos().getX() + 0.5 - ox;
        double feetY = mbe.getPos().getY() - oy;
        double feetZ = mbe.getPos().getZ() + 0.5 - oz;

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

    private static void drawReplay(IEntity stub, MatrixStack matrices, Camera camera, float tickDelta)
    {
        Form form = stub.getForm();
        if (form == null)
        {
            return;
        }

        // Light-relative bake: subtract the pass anchor (= the light view origin)
        // in double before matrices.translate's float cast. The Y-rotation below
        // is unaffected (it is about the anchor-relative feet, orientation-only).
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double fx = MathHelper.lerp(tickDelta, stub.getPrevX(), stub.getX()) - ox;
        double fy = MathHelper.lerp(tickDelta, stub.getPrevY(), stub.getY()) - oy;
        double fz = MathHelper.lerp(tickDelta, stub.getPrevZ(), stub.getZ()) - oz;
        float bodyYaw = MathHelper.lerp(tickDelta, stub.getPrevBodyYaw(), stub.getBodyYaw());

        matrices.push();
        matrices.translate(fx, fy, fz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        FormRenderer<?> renderer = FormUtilsClient.getRenderer(form);
        if (renderer != null)
        {
            renderer.render(new FormRenderingContext()
                .set(FormRenderType.ENTITY, stub, matrices, FULL_LIGHT, OverlayTexture.DEFAULT_UV, tickDelta)
                .camera(camera));
        }
        matrices.pop();
    }
}
