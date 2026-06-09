package org.wemppy.irlite.client.light.shadow;

import io.netty.util.collection.IntObjectMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.wemppy.irlite.IrliteConfig;
import org.wemppy.irlite.client.light.LightRegistry;
import org.wemppy.irlite.mixin.client.bbs.FilmsAccessor;
import org.wemppy.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import java.util.Collections;
import java.util.List;

/**
 * Shadow bake driver. Collects nearby occluders (world entities + BBS model
 * blocks) and, for each spot/point in the {@link LightRegistry}, bakes its depth
 * map(s) and records the atlas tile / cube slot back into the registry
 * (-> SSBO vlParams.w). Runs at renderWorld HEAD, before Iris activates.
 *
 * Occluders: world entities (BBS morph silhouette, vanilla fallback), BBS model
 * blocks, and film replays (all via the BBS FormRenderer / MorphRenderer). A
 * per-light signature cache (see {@link #bake}) skips the GL depth render for
 * any light whose own geometry, in-range static occluders and world blocks are
 * unchanged; a light with a live entity/replay in range always re-bakes.
 */
public final class ShadowBaker
{
    private static final int MAX_OCCLUDERS = 32;
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;
    private static final float OVERLAP_MARGIN = 0.5f;

    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;

    private static final Object[] occ = new Object[MAX_OCCLUDERS];
    private static final int[] occType = new int[MAX_OCCLUDERS];
    private static final float[] ox = new float[MAX_OCCLUDERS];
    private static final float[] oy = new float[MAX_OCCLUDERS];
    private static final float[] oz = new float[MAX_OCCLUDERS];
    private static final float[] orad = new float[MAX_OCCLUDERS];
    /** Per-occluder signature of everything that moves a STATIC (model-block)
     *  caster's baked silhouette but isn't its center: transform translate/
     *  scale/rotate. Folded into a light's signature for model blocks in range;
     *  unused (0) for entity/replay casters, which are always treated dirty. */
    private static final long[] ostatichash = new long[MAX_OCCLUDERS];
    private static int occCount;

    // --- Per-light dirty cache (replaces the old global scene hash) ----------
    // Keyed by LightRegistry.getId (stable identity), like BlockShadowCache.
    // lastSig:   light geometry + sum of in-range model-block hashes last baked.
    // lastTile:  the atlas tile / cube slot the light last baked into. Also the
    //            "have we ever baked this light?" marker (containsKey).
    // lastBlocks:the world-block list instance last baked. BlockShadowCache
    //            returns the SAME instance until a block in range changes, so a
    //            reference compare detects terrain edits precisely.
    private static final Long2LongOpenHashMap lastSig = new Long2LongOpenHashMap();
    private static final Long2IntOpenHashMap lastTile = new Long2IntOpenHashMap();
    private static final Long2ObjectOpenHashMap<List<BlockShadowEntry>> lastBlocks = new Long2ObjectOpenHashMap<>();
    /** Ids whose last bake included a dynamic (entity/replay) occluder. Dynamic
     *  casters aren't in lastSig (they force a re-bake every frame instead), so
     *  when the subject leaves range the signature returns to its earlier value
     *  and the cache would otherwise reuse the last map with the subject still
     *  baked in. This forces ONE trailing re-bake to clear it. */
    private static final LongOpenHashSet wasDynamic = new LongOpenHashSet();

    /** Set true by {@link #scanInRange} when any in-range occluder is an entity
     *  or film replay (a dynamic subject) -> the light re-bakes every frame. */
    private static boolean dynamicInRangeScratch;
    /** Set by {@link #scanInRange} to the order-independent sum of the in-range
     *  model-block {@link #ostatichash} values. */
    private static long staticOccSigScratch;

    /** Ids that actually baked / were assigned a tile this frame. Drives
     *  dirty-state eviction: a light that skipped (shadows off, or nothing in
     *  range) drops its state, so when it next casts it is a first-bake and
     *  re-renders into its (possibly reused) tile instead of sampling another
     *  light's depth map. */
    private static final LongOpenHashSet bakedIds = new LongOpenHashSet();
    /** Reusable scratch set of all current light ids, used to evict the block-
     *  list + VBO caches for lights that disappeared. */
    private static final LongOpenHashSet liveIds = new LongOpenHashSet();

    private ShadowBaker()
    {}

    public static void bake(ClientWorld world, Vec3d cameraPos, Vec3d cameraForward, float tickDelta)
    {
        if (world == null || cameraPos == null)
        {
            return;
        }
        if (LightRegistry.getCount() == 0)
        {
            // No lights — drain every cache so nothing lingers in VRAM/heap
            // after walking away from all lamps. Empty sets make each retain
            // a full drain.
            bakedIds.clear();
            retainDirtyState(bakedIds);
            liveIds.clear();
            BlockShadowCache.retainOnly(liveIds);
            ShadowRenderer.retainBlockVbos(liveIds);
            return;
        }

        // Apply the shadow resolution preset (no-op unless it changed).
        IRLShadowQuality.applyFromSetting(IrliteConfig.shadowQuality());

        collect(world, cameraPos, tickDelta);
        // NOTE: no early-out on occCount == 0 — a light shining only on world
        // blocks (no entity/model/replay occluders nearby) still needs its
        // block silhouette baked. The per-light skip below also checks blocks.

        int n = LightRegistry.getCount();
        boolean cache = IrliteConfig.shadowCache();
        bakedIds.clear();
        ShadowRenderer.beginBake();

        // Behind-camera cull inputs: a light whose whole influence sphere is
        // behind the camera plane lights no on-screen surface (diffuse/specular
        // only sample its shadow map for in-range fragments, and volumetrics
        // ignore the map), so its bake is skipped entirely (the per-light test
        // in each loop below). Conservative: only the fully-behind half-space is
        // culled, never a side-of-frustum light, so no shadow can go missing.
        double camX = cameraPos.x, camY = cameraPos.y, camZ = cameraPos.z;
        boolean haveFwd = cameraForward != null;
        double fwdX = haveFwd ? cameraForward.x : 0.0;
        double fwdY = haveFwd ? cameraForward.y : 0.0;
        double fwdZ = haveFwd ? cameraForward.z : 0.0;

        // --- spotlights: one perspective atlas tile each ---
        int tile = 0;
        for (int i = 0; i < n && tile < SpotlightDepthAtlas.MAX_TILES; i++)
        {
            if (LightRegistry.getType(i) != 1)
            {
                continue;
            }

            float lx = LightRegistry.getX(i);
            float ly = LightRegistry.getY(i);
            float lz = LightRegistry.getZ(i);
            float range = LightRegistry.getRange(i);
            if (range < 1e-3f)
            {
                continue;
            }
            // Whole sphere behind the camera -> skip (re-bakes on first sight
            // when the camera turns back; its tile stays -1 = unshadowed while
            // off, which is never sampled).
            if (haveFwd && (lx - camX) * fwdX + (ly - camY) * fwdY + (lz - camZ) * fwdZ < -range)
            {
                continue;
            }
            boolean castsShadows = LightRegistry.getShadows(i);
            long id = LightRegistry.getId(i);

            // Spot axis + cone half-angle drive the cone cull in scanInRange /
            // renderInRangeCone: an occluder fully outside the lit cone can only
            // shadow unlit fragments, so it need not be baked (and an out-of-cone
            // subject must not dirty the light). Dir is stored normalized;
            // re-normalize defensively and disable the cull on a degenerate dir.
            float dx = LightRegistry.getDirX(i);
            float dy = LightRegistry.getDirY(i);
            float dz = LightRegistry.getDirZ(i);
            float cosOuter = LightRegistry.getCosOuter(i);
            float coneTheta = (float) Math.acos(MathHelper.clamp(cosOuter, -1f, 1f));
            float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean cone = dlen > 1e-4f;
            float ndx = cone ? dx / dlen : 0f;
            float ndy = cone ? dy / dlen : 0f;
            float ndz = cone ? dz / dlen : 0f;

            // "Shadows" toggle (default on): when off this light casts no shadow
            // at all — neither entities nor world blocks. Forcing both inputs
            // empty drops it into the same "nothing in range" skip below, leaving
            // its shadow tile unassigned (-1 = none in the SSBO -> unshadowed).
            int entInRange = castsShadows ? scanInRange(lx, ly, lz, range, ndx, ndy, ndz, coneTheta, cone) : 0;
            // Collect blocks every frame (NOT gated on dirty): the skip/tile
            // decision must match the frame that actually baked, or the atlas
            // tile a light points to in the SSBO could drift off its baked
            // depth map. Cached by id -> O(1) on a hit, and the returned list
            // instance is stable until a block in range changes.
            List<BlockShadowEntry> blocks = castsShadows ? collectBlocks(id, world, lx, ly, lz, range) : Collections.emptyList();
            if (entInRange == 0 && blocks.isEmpty())
            {
                continue;
            }

            int myTile = tile;
            long sig = lightGeomSig(lx, ly, lz, dx, dy, dz, range, cosOuter, castsShadows) + staticOccSigScratch;

            boolean dirty = !cache
                || !lastTile.containsKey(id)        // first bake
                || dynamicInRangeScratch            // live entity/replay in range
                || wasDynamic.contains(id)          // dynamic subject just left -> clear it
                || lastSig.get(id) != sig           // geometry / static occluder moved
                || lastBlocks.get(id) != blocks     // terrain in range changed
                || lastTile.get(id) != myTile;       // assigned a different tile

            if (dirty)
            {
                float outerDeg = (float) Math.toDegrees(coneTheta * 2.0);
                ShadowRenderer.beginSpot(myTile, lx, ly, lz, dx, dy, dz, range, outerDeg);
                if (entInRange > 0)
                {
                    renderInRangeCone(lx, ly, lz, range, ndx, ndy, ndz, coneTheta, tickDelta);
                }
                if (!blocks.isEmpty())
                {
                    ShadowRenderer.renderBlocksDepth(id, blocks);
                }
                ShadowRenderer.endPass();
            }

            LightRegistry.setShadowTile(i, myTile);
            lastSig.put(id, sig);
            lastTile.put(id, myTile);
            lastBlocks.put(id, blocks);
            bakedIds.add(id);
            if (dynamicInRangeScratch)
            {
                wasDynamic.add(id);
            }
            else
            {
                wasDynamic.remove(id);
            }
            tile++;
        }

        // --- point lights: cube-array, 6 faces each ---
        int layer = 0;
        for (int i = 0; i < n && layer < PointShadowArray.MAX_SHADOWS; i++)
        {
            if (LightRegistry.getType(i) != 0)
            {
                continue;
            }

            float lx = LightRegistry.getX(i);
            float ly = LightRegistry.getY(i);
            float lz = LightRegistry.getZ(i);
            float radius = LightRegistry.getRange(i);
            if (radius < 1e-3f)
            {
                continue;
            }
            // Behind-camera cull (see the spot loop).
            if (haveFwd && (lx - camX) * fwdX + (ly - camY) * fwdY + (lz - camZ) * fwdZ < -radius)
            {
                continue;
            }
            boolean castsShadows = LightRegistry.getShadows(i);
            long id = LightRegistry.getId(i);

            // See the spot loop: "Shadows" off -> no entities, no blocks.
            // Points are omnidirectional -> no cone cull (cone=false); the 6 cube
            // faces are culled individually in renderInRangeFace below.
            int entInRange = castsShadows ? scanInRange(lx, ly, lz, radius, 0f, 0f, 0f, 0f, false) : 0;
            // Collected once, reused across all 6 cube faces (see spot note).
            List<BlockShadowEntry> blocks = castsShadows ? collectBlocks(id, world, lx, ly, lz, radius) : Collections.emptyList();
            if (entInRange == 0 && blocks.isEmpty())
            {
                continue;
            }

            int myLayer = layer;
            long sig = lightGeomSig(lx, ly, lz, 0f, 0f, 0f, radius, 1f, castsShadows) + staticOccSigScratch;

            boolean dirty = !cache
                || !lastTile.containsKey(id)
                || dynamicInRangeScratch
                || wasDynamic.contains(id)
                || lastSig.get(id) != sig
                || lastBlocks.get(id) != blocks
                || lastTile.get(id) != myLayer;

            if (dirty)
            {
                for (int face = 0; face < 6; face++)
                {
                    ShadowRenderer.beginPointFace(myLayer, face, lx, ly, lz, radius);
                    if (entInRange > 0)
                    {
                        renderInRangeFace(lx, ly, lz, radius, face, tickDelta);
                    }
                    if (!blocks.isEmpty())
                    {
                        ShadowRenderer.renderBlocksDepth(id, blocks);
                    }
                    ShadowRenderer.endPass();
                }
            }

            LightRegistry.setShadowTile(i, myLayer);
            lastSig.put(id, sig);
            lastTile.put(id, myLayer);
            lastBlocks.put(id, blocks);
            bakedIds.add(id);
            if (dynamicInRangeScratch)
            {
                wasDynamic.add(id);
            }
            else
            {
                wasDynamic.remove(id);
            }
            layer++;
        }

        // Evict the per-light dirty state for lights that did NOT bake this
        // frame (gone, shadows off, or nothing in range) — see bakedIds.
        retainDirtyState(bakedIds);

        // Block-list + VBO caches: keep ALL registered lights so a momentary
        // out-of-range frame doesn't thrash the (expensive) block re-collect.
        // Empty set when the feature is off -> both caches drain.
        liveIds.clear();
        if (IrliteConfig.shadowBlocks())
        {
            for (int i = 0; i < n; i++)
            {
                liveIds.add(LightRegistry.getId(i));
            }
        }
        BlockShadowCache.retainOnly(liveIds);
        ShadowRenderer.retainBlockVbos(liveIds);
    }

    /** Drop per-light dirty state for ids not in {@code keep}. An empty set
     *  drains it. */
    private static void retainDirtyState(LongSet keep)
    {
        if (!lastSig.isEmpty())
        {
            lastSig.keySet().retainAll(keep);
        }
        if (!lastTile.isEmpty())
        {
            lastTile.keySet().retainAll(keep);
        }
        if (!lastBlocks.isEmpty())
        {
            lastBlocks.keySet().retainAll(keep);
        }
        if (!wasDynamic.isEmpty())
        {
            wasDynamic.retainAll(keep);
        }
    }

    /** Block occluders around a light, clamped to the configurable
     *  {@link IrliteConfig#shadowBlockRadius()} (default 24). Empty when the
     *  feature is off. Backed by the identity-keyed {@link BlockShadowCache}
     *  (O(1) on a hit; recollects on light-move, radius change, or a block change
     *  in range). */
    private static List<BlockShadowEntry> collectBlocks(long id, ClientWorld world, float lx, float ly, float lz, float range)
    {
        if (!IrliteConfig.shadowBlocks() || world == null)
        {
            return Collections.emptyList();
        }
        return BlockShadowCache.getOrCompute(id, world, lx, ly, lz, Math.min(range, (float) IrliteConfig.shadowBlockRadius()));
    }

    /** FNV-1a fold of one float (raw bits) into a running hash. */
    private static long mix(long h, float v)
    {
        return (h ^ (Float.floatToRawIntBits(v) & 0xffffffffL)) * FNV_PRIME;
    }

    /** Signature of a light's own bake-relevant geometry: position, direction,
     *  range, spot cone (cosOuter), and the shadows flag. Any change re-bakes. */
    private static long lightGeomSig(float lx, float ly, float lz, float dx, float dy, float dz, float range, float cosOuter, boolean shadows)
    {
        long h = FNV_OFFSET;
        h = mix(h, lx); h = mix(h, ly); h = mix(h, lz);
        h = mix(h, dx); h = mix(h, dy); h = mix(h, dz);
        h = mix(h, range); h = mix(h, cosOuter);
        h = (h ^ (shadows ? 1L : 0L)) * FNV_PRIME;
        return h;
    }

    /** Count in-range occluders and, as a side effect, set
     *  {@link #dynamicInRangeScratch} (any entity/replay in range) and
     *  {@link #staticOccSigScratch} (order-independent sum of in-range
     *  model-block hashes). reach = reachBase + occluderRadius. When {@code cone}
     *  is set (spotlights), occluders fully outside the lit cone (unit axis
     *  dirX/Y/Z, half-angle coneTheta) are skipped: they can only shadow unlit
     *  fragments, so excluding them both avoids the draw AND stops an out-of-cone
     *  subject from dirtying the light. Points pass cone=false (omnidirectional;
     *  the per-face frustum cull is in {@link #renderInRangeFace}). */
    private static int scanInRange(float lx, float ly, float lz, float reachBase,
                                   float dirX, float dirY, float dirZ, float coneTheta, boolean cone)
    {
        int c = 0;
        boolean dyn = false;
        long sig = 0L;
        for (int k = 0; k < occCount; k++)
        {
            float ddx = ox[k] - lx, ddy = oy[k] - ly, ddz = oz[k] - lz;
            float reach = reachBase + orad[k];
            if (ddx * ddx + ddy * ddy + ddz * ddz > reach * reach)
            {
                continue;
            }
            if (cone && !insideCone(dirX, dirY, dirZ, coneTheta, ddx, ddy, ddz, orad[k]))
            {
                continue;
            }
            c++;
            if (occType[k] == ShadowRenderer.CASTER_MODEL_BLOCK)
            {
                sig += ostatichash[k];
            }
            else
            {
                dyn = true; // entity or film replay -> dynamic subject
            }
        }
        dynamicInRangeScratch = dyn;
        staticOccSigScratch = sig;
        return c;
    }

    /** Render in-range occluders inside a spot's lit cone (see {@link #insideCone}).
     *  Must match {@link #scanInRange}'s cone test exactly, so the rendered set
     *  equals the counted set that gated this bake. */
    private static void renderInRangeCone(float lx, float ly, float lz, float reachBase,
                                          float dirX, float dirY, float dirZ, float coneTheta, float tickDelta)
    {
        for (int k = 0; k < occCount; k++)
        {
            float ddx = ox[k] - lx, ddy = oy[k] - ly, ddz = oz[k] - lz;
            float reach = reachBase + orad[k];
            if (ddx * ddx + ddy * ddy + ddz * ddz > reach * reach)
            {
                continue;
            }
            if (!insideCone(dirX, dirY, dirZ, coneTheta, ddx, ddy, ddz, orad[k]))
            {
                continue;
            }
            ShadowRenderer.renderCaster(occ[k], occType[k], tickDelta);
        }
    }

    /** Render in-range occluders that could intersect ONE point-cube face's 90°
     *  frustum (face index per {@link ShadowRenderer#beginPointFace}); the other
     *  five faces never see them, removing ~5/6 of the caster draws per point.
     *  The face is still cleared even when nothing renders, so a just-vacated
     *  face shows no stale shadow. */
    private static void renderInRangeFace(float lx, float ly, float lz, float reachBase, int face, float tickDelta)
    {
        for (int k = 0; k < occCount; k++)
        {
            float vx = ox[k] - lx, vy = oy[k] - ly, vz = oz[k] - lz;
            float reach = reachBase + orad[k];
            if (vx * vx + vy * vy + vz * vz > reach * reach)
            {
                continue;
            }
            if (!sphereTouchesFace(face, vx, vy, vz, orad[k] * SQRT2))
            {
                continue;
            }
            ShadowRenderer.renderCaster(occ[k], occType[k], tickDelta);
        }
    }

    /** Small angular slack (radians) added to the spot cone test so a subject
     *  right at the cone edge is never wrongly culled. */
    private static final float CONE_ANGLE_MARGIN = 0.05f;
    private static final float SQRT2 = 1.4142135f;

    /** True unless an occluder sphere (offset V = center - lightPos, radius r) is
     *  ENTIRELY outside the spot's lit cone (unit axis dir, half-angle coneTheta).
     *  phi = angle of V off the axis; alpha = the sphere's angular radius. If
     *  phi - alpha > coneTheta the whole sphere sits at a larger axis angle than
     *  any lit fragment, so it can shadow only unlit fragments and is safe to
     *  drop. Conservative otherwise (a kept occluder may still be clipped by the
     *  bake projection). */
    private static boolean insideCone(float dirX, float dirY, float dirZ, float coneTheta,
                                      float vx, float vy, float vz, float r)
    {
        float d2 = vx * vx + vy * vy + vz * vz;
        if (d2 <= r * r)
        {
            return true; // light sits inside the occluder sphere -> can't cull
        }
        float dist = (float) Math.sqrt(d2);
        float cosPhi = (vx * dirX + vy * dirY + vz * dirZ) / dist; // dir is unit
        float phi = (float) Math.acos(MathHelper.clamp(cosPhi, -1f, 1f));
        float alpha = (float) Math.asin(MathHelper.clamp(r / dist, 0f, 1f));
        return phi - alpha <= coneTheta + CONE_ANGLE_MARGIN;
    }

    /** Conservative sphere-vs-cube-face-frustum test. The 90° face frustum's four
     *  side planes pass through the light with inward normals (axis ± tangent);
     *  the sphere lies outside the frustum iff it is fully beyond one of them.
     *  {@code k} = sphere radius · √2 (the plane-normal magnitude folded in).
     *  pd = signed offset along the face axis, a/b = |offset| along the two
     *  tangents; keep iff pd + k reaches both tangents. */
    private static boolean sphereTouchesFace(int face, float vx, float vy, float vz, float k)
    {
        float pd, a, b;
        switch (face)
        {
            case 0:  pd =  vx; a = Math.abs(vy); b = Math.abs(vz); break; // +X
            case 1:  pd = -vx; a = Math.abs(vy); b = Math.abs(vz); break; // -X
            case 2:  pd =  vy; a = Math.abs(vx); b = Math.abs(vz); break; // +Y
            case 3:  pd = -vy; a = Math.abs(vx); b = Math.abs(vz); break; // -Y
            case 4:  pd =  vz; a = Math.abs(vx); b = Math.abs(vy); break; // +Z
            default: pd = -vz; a = Math.abs(vx); b = Math.abs(vy); break; // -Z
        }
        float lim = pd + k;
        return lim >= a && lim >= b;
    }

    private static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        occCount = 0;
        double camX = cameraPos.x, camY = cameraPos.y, camZ = cameraPos.z;

        // --- world entities (vanilla render path) ---
        for (Entity entity : world.getEntities())
        {
            if (occCount >= MAX_OCCLUDERS)
            {
                break;
            }
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

            Box box = entity.getBoundingBox();
            float rad = (float) (Math.max(box.getXLength(), Math.max(box.getYLength(), box.getZLength())) * 0.5) + OVERLAP_MARGIN;

            occ[occCount] = entity;
            occType[occCount] = ShadowRenderer.CASTER_ENTITY;
            ox[occCount] = (float) ex;
            oy[occCount] = (float) (ey + box.getYLength() * 0.5);
            oz[occCount] = (float) ez;
            orad[occCount] = rad;
            ostatichash[occCount] = 0L; // dynamic caster -> not part of any static signature
            occCount++;
        }

        // --- BBS model blocks (BlockEntity, not in world.getEntities()) ---
        collectModelBlocks(world, camX, camY, camZ);

        // --- BBS film replays (non-actor stubs; actors are real entities above) ---
        collectFilmReplays(camX, camY, camZ, tickDelta);
    }

    private static void collectFilmReplays(double camX, double camY, double camZ, float tickDelta)
    {
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

        for (int ci = 0; ci < total && occCount < MAX_OCCLUDERS; ci++)
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
                if (occCount >= MAX_OCCLUDERS)
                {
                    break;
                }
                int rid = e.key();
                if (rid < 0 || rid >= replays.size())
                {
                    continue;
                }
                Replay replay = replays.get(rid);
                if (replay == null || replay.actor.get())
                {
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

                float hbW = form.hitboxWidth.get();
                float hbH = form.hitboxHeight.get();
                float ey = Math.max(0.05f, hbH * 0.5f);
                float rad = Math.max(0.05f, Math.max(hbW * 0.5f, ey)) + OVERLAP_MARGIN;

                occ[occCount] = ent;
                occType[occCount] = ShadowRenderer.CASTER_REPLAY;
                ox[occCount] = (float) wx;
                oy[occCount] = (float) (wy + ey);
                oz[occCount] = (float) wz;
                orad[occCount] = rad;
                ostatichash[occCount] = 0L; // dynamic caster -> not part of any static signature
                occCount++;
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

    private static void collectModelBlocks(ClientWorld world, double camX, double camY, double camZ)
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

        for (int idx = 0, n = tickers.size(); idx < n && occCount < MAX_OCCLUDERS; idx++)
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

            float hbW = form.hitboxWidth.get();
            float hbH = form.hitboxHeight.get();
            float sx = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.x));
            float sy = t == null ? 1f : Math.max(0.001f, Math.abs(t.scale.y));
            float ey = Math.max(0.05f, hbH * 0.5f * sy);
            float rad = Math.max(0.05f, Math.max(hbW * 0.5f * sx, ey)) + OVERLAP_MARGIN;

            float wx = (float) (pos.getX() + 0.5 + (t == null ? 0 : t.translate.x));
            float wy = (float) (pos.getY() + (t == null ? 0 : t.translate.y) + ey);
            float wz = (float) (pos.getZ() + 0.5 + (t == null ? 0 : t.translate.z));

            occ[occCount] = mbe;
            occType[occCount] = ShadowRenderer.CASTER_MODEL_BLOCK;
            ox[occCount] = wx;
            oy[occCount] = wy;
            oz[occCount] = wz;
            orad[occCount] = rad;
            // Static signature: center (incl. translate) + scale + rotation. A
            // model block that only animates its INTERNAL morph pose (transform
            // unchanged) is treated static and its shadow is cached — same as
            // the old position-only hash; documented limitation, not a regression.
            ostatichash[occCount] = modelBlockHash(wx, wy, wz, t);
            occCount++;
        }
    }

    /** Static-occluder signature for a model block: its baked center plus the
     *  scale + rotation that the center alone doesn't capture. */
    private static long modelBlockHash(float wx, float wy, float wz, Transform t)
    {
        long h = FNV_OFFSET;
        h = mix(h, wx); h = mix(h, wy); h = mix(h, wz);
        if (t != null)
        {
            h = mix(h, t.scale.x); h = mix(h, t.scale.y); h = mix(h, t.scale.z);
            h = mix(h, t.rotate.x); h = mix(h, t.rotate.y); h = mix(h, t.rotate.z);
            h = mix(h, t.rotate2.x); h = mix(h, t.rotate2.y); h = mix(h, t.rotate2.z);
        }
        return h;
    }
}
