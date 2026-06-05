package org.wemppy.irlite.client.light.shadow;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import org.wemppy.irlite.client.light.LightRegistry;
import org.wemppy.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import java.util.List;

/**
 * Shadow bake driver. Collects nearby occluders (world entities + BBS model
 * blocks) and, for each spot/point in the {@link LightRegistry}, bakes its depth
 * map(s) and records the atlas tile / cube slot back into the registry
 * (-> SSBO vlParams.w). Runs at renderWorld HEAD, before Iris activates.
 *
 * Model blocks bake via the BBS FormRenderer (correct morph silhouette);
 * world entities bake via the vanilla render dispatcher. Bakes every frame
 * (no static/adaptive cache yet); film replays + entity morphs are TODO.
 */
public final class ShadowBaker
{
    private static final int MAX_OCCLUDERS = 32;
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;
    private static final float OVERLAP_MARGIN = 0.5f;

    private static final Object[] occ = new Object[MAX_OCCLUDERS];
    private static final int[] occType = new int[MAX_OCCLUDERS];
    private static final float[] ox = new float[MAX_OCCLUDERS];
    private static final float[] oy = new float[MAX_OCCLUDERS];
    private static final float[] oz = new float[MAX_OCCLUDERS];
    private static final float[] orad = new float[MAX_OCCLUDERS];
    private static int occCount;

    private ShadowBaker()
    {}

    public static void bake(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        if (world == null || cameraPos == null)
        {
            return;
        }
        if (LightRegistry.getCount() == 0)
        {
            return;
        }

        collect(world, cameraPos, tickDelta);
        if (occCount == 0)
        {
            return;
        }

        int n = LightRegistry.getCount();

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
            if (range < 1e-3f || countInRange(lx, ly, lz, range) == 0)
            {
                continue;
            }

            float dx = LightRegistry.getDirX(i);
            float dy = LightRegistry.getDirY(i);
            float dz = LightRegistry.getDirZ(i);
            float outerDeg = (float) Math.toDegrees(Math.acos(MathHelper.clamp(LightRegistry.getCosOuter(i), -1f, 1f)) * 2.0);

            ShadowRenderer.beginSpot(tile, lx, ly, lz, dx, dy, dz, range, outerDeg);
            renderInRange(lx, ly, lz, range, tickDelta);
            ShadowRenderer.endPass();

            LightRegistry.setShadowTile(i, tile);
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
            if (radius < 1e-3f || countInRange(lx, ly, lz, radius) == 0)
            {
                continue;
            }

            for (int face = 0; face < 6; face++)
            {
                ShadowRenderer.beginPointFace(layer, face, lx, ly, lz, radius);
                renderInRange(lx, ly, lz, radius, tickDelta);
                ShadowRenderer.endPass();
            }

            LightRegistry.setShadowTile(i, layer);
            layer++;
        }
    }

    private static int countInRange(float lx, float ly, float lz, float reachBase)
    {
        int c = 0;
        for (int k = 0; k < occCount; k++)
        {
            float ddx = ox[k] - lx, ddy = oy[k] - ly, ddz = oz[k] - lz;
            float reach = reachBase + orad[k];
            if (ddx * ddx + ddy * ddy + ddz * ddz <= reach * reach)
            {
                c++;
            }
        }
        return c;
    }

    private static void renderInRange(float lx, float ly, float lz, float reachBase, float tickDelta)
    {
        for (int k = 0; k < occCount; k++)
        {
            float ddx = ox[k] - lx, ddy = oy[k] - ly, ddz = oz[k] - lz;
            float reach = reachBase + orad[k];
            if (ddx * ddx + ddy * ddy + ddz * ddz <= reach * reach)
            {
                ShadowRenderer.renderCaster(occ[k], occType[k], tickDelta);
            }
        }
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
            occCount++;
        }

        // --- BBS model blocks (BlockEntity, not in world.getEntities()) ---
        collectModelBlocks(world, camX, camY, camZ);
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
            occCount++;
        }
    }
}
