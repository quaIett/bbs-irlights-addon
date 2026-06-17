package qualet.irlite.client.light.shadow;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
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
import qualet.irlite.mixin.client.bbs.FilmsAccessor;
import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

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
    /** Max distance (from the camera) at which a caster is considered. */
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;
    private static final int FULL_LIGHT = LightmapTextureManager.pack(15, 15);

    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;

    // ===================================================================== //
    //  collect — WHAT casts (entity -> model-block -> replay; arm order is    //
    //  load-bearing for deterministic over-cap drops, INVARIANT 6).          //
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
            // circumscribing box-diagonal radius (INVARIANT 5); over-cap casters
            // are dropped by the sink. Entities are always dynamic -> isStatic
            // false, staticHash 0 (INVARIANT 2).
            sink.emitFromBox(entity, CasterType.ENTITY, false, ex, ey, ez, entity.getBoundingBox(), 1f, 0L);
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
            if (form == null)
            {
                continue;
            }
            Transform t = props.getTransform();

            // Caster CENTER (feet + translate): feet Y carries NO +0.5; X/Z get
            // the block center +0.5. emitFromBox raises Y to the box mid-height.
            double wx = pos.getX() + 0.5 + (t == null ? 0 : t.translate.x);
            double wy = pos.getY() + (t == null ? 0 : t.translate.y);
            double wz = pos.getZ() + 0.5 + (t == null ? 0 : t.translate.z);

            // INVARIANT 5: build a rotation-EXPANDED, anisotropically-scaled AABB
            // enclosing the post-scale/post-rotate geometry, with the feet at
            // box minY. The full scale + rotation is baked into the box edge
            // lengths so emitFromBox derives the true circumscribing diagonal
            // (we pass scale=1f to avoid double-scaling; a single uniform scale
            // param cannot represent anisotropic scale anyway).
            Box box = rotationExpandedBox(form, t);

            // INVARIANT 2 (CONSERVATIVE, LOCKED): mark ALL model blocks dynamic.
            // An animated model block left isStatic=true would FREEZE its shadow
            // at the first pose; isStatic=false is guaranteed-correct.
            // TODO(Ф3 perf-debt, Open Q1): mark genuinely-static model-blocks
            // isStatic=true once a safe BBS animation probe is confirmed — see
            // shadow-phase3-port-plan.md §8.
            boolean isStatic = false;

            // INVARIANT 3: modelBlockHash supplies per-caster CONTENT only (full
            // avalanche over form identity + center incl. translate + scale +
            // rotate). The order-independent cross-caster avalanche+count FOLD
            // lives in the shared ShadowBaker; we must NOT fold here. Non-static
            // casters supply EXACTLY 0L. occType stays MODEL_BLOCK regardless of
            // isStatic (independent axes).
            long staticHash = isStatic
                ? modelBlockHash((float) wx, (float) wy, (float) wz, t, System.identityHashCode(form))
                : 0L;

            sink.emitFromBox(mbe, CasterType.MODEL_BLOCK, isStatic, wx, wy, wz, box, 1f, staticHash);
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
                if (form == null)
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

                // INVARIANT 5: replay form hitbox -> box (feet at minY). Body yaw
                // is a rotation about Y; a width==depth box footprint is
                // Y-rotation-invariant, so no rotation expansion is needed. Pass
                // feet Y (wy) and let emitFromBox raise to mid-height (do NOT
                // double-raise). Replays are always dynamic -> isStatic false,
                // staticHash 0 (INVARIANT 2).
                float hbW = Math.max(0.1f, form.hitboxWidth.get());
                float hbH = Math.max(0.1f, form.hitboxHeight.get());
                Box box = new Box(-hbW * 0.5, 0, -hbW * 0.5, hbW * 0.5, hbH, hbW * 0.5);

                sink.emitFromBox(ent, CasterType.REPLAY, false, wx, wy, wz, box, 1f, 0L);
            }
        }
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
     * Rotation-expanded, anisotropically-scaled AABB enclosing a model block's
     * post-transform geometry, with the feet at {@code minY} (so emitFromBox can
     * raise the sphere center to box mid-height). The form's local box is
     * {@code [hbW × hbH × hbW]} (feet at 0); scale is applied per-axis, then the
     * box is expanded by the {@link Transform#rotate} euler rotation (ZYX, radians,
     * matching {@link MatrixStackUtils#applyTransform}) via the abs-rotation-matrix
     * AABB expansion (INVARIANT 5: under-bounding silently drops casters from the cone cull).
     *
     * <p>NOTE (Ф3 audit, fast-follow): this expands about the box CENTER, but
     * {@link MatrixStackUtils#applyTransform} rotates about the FEET pivot, which
     * displaces tall geometry the center-anchored sphere does not capture. It is a
     * safe over-bound for upright / body-yaw forms (the common case) but can slightly
     * UNDER-bound a tall model block tilted off-vertical (rotate.x/z ≳ 45–60°) near a
     * cull boundary — see shadow-phase3-port-plan.md §8 / shadow-phase3-audit-verdict.md
     * MAJOR-B. Correct fix: rotate the 8 feet-relative corners about the feet origin
     * and bound that AABB (emit() with a hand-computed sphere).
     */
    private static Box rotationExpandedBox(Form form, Transform t)
    {
        float hbW = Math.max(0.1f, form.hitboxWidth.get());
        float hbH = Math.max(0.1f, form.hitboxHeight.get());

        // Local half-extents (unscaled): width/depth half = hbW/2, height half = hbH/2.
        float lhx = hbW * 0.5f;
        float lhy = hbH * 0.5f;
        float lhz = hbW * 0.5f;

        // Anisotropic scale.
        float sx = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.x));
        float sy = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.y));
        float sz = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.z));
        float hx = lhx * sx;
        float hy = lhy * sy;
        float hz = lhz * sz;

        if (t == null)
        {
            // No rotation: feet at minY (= center - hy raised back to 0).
            return new Box(-hx, 0, -hz, hx, 2f * hy, hz);
        }

        // Rotation matrix (ZYX order, radians) — BBS's own helper, identical to
        // MatrixStackUtils.applyTransform's rotation order.
        Matrix3f rot = t.createRotationMatrix();

        // AABB expansion: new half-extent in axis i = Σ_j |M[i][j]| * h[j].
        // JOML Matrix3f is column-major: mXY = column X, row Y. The world-axis i
        // row is (m0i, m1i, m2i).
        float ehx = Math.abs(rot.m00) * hx + Math.abs(rot.m10) * hy + Math.abs(rot.m20) * hz;
        float ehy = Math.abs(rot.m01) * hx + Math.abs(rot.m11) * hy + Math.abs(rot.m21) * hz;
        float ehz = Math.abs(rot.m02) * hx + Math.abs(rot.m12) * hy + Math.abs(rot.m22) * hz;

        // Feet at minY: box spans [-ehx, 0, -ehz] .. [ehx, 2*ehy, ehz], so its
        // height is 2*ehy and emitFromBox raises the center by ehy.
        return new Box(-ehx, 0, -ehz, ehx, 2f * ehy, ehz);
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
        double cx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double cy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double cz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
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

        // Feet Y = pos.getY()+translate.y (no +0.5, no ey) — the DRAW feet-Y
        // differs from the CULL center-Y (raised to mid-height); preserved as in
        // the old IRLite renderer.
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

    private static void drawReplay(IEntity stub, MatrixStack matrices, Camera camera, float tickDelta)
    {
        Form form = stub.getForm();
        if (form == null)
        {
            return;
        }

        double fx = MathHelper.lerp(tickDelta, stub.getPrevX(), stub.getX());
        double fy = MathHelper.lerp(tickDelta, stub.getPrevY(), stub.getY());
        double fz = MathHelper.lerp(tickDelta, stub.getPrevZ(), stub.getZ());
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
