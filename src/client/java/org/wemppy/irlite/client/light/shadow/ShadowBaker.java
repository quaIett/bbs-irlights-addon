package org.wemppy.irlite.client.light.shadow;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.wemppy.irlite.client.light.LightRegistry;

/**
 * Phase 1 spotlight shadow bake driver. Collects nearby entity occluders and,
 * for each spot in the {@link LightRegistry}, bakes a perspective depth tile and
 * records the tile index back into the registry (-> SSBO vlParams.w). Runs at
 * renderWorld HEAD, before Iris activates, so vanilla entity rendering writes
 * into our depth FBO.
 *
 * Phase 1 scope: entity occluders only (vanilla render dispatcher), bake every
 * frame, no static/adaptive cache, no block/model-block/replay occluders.
 */
public final class ShadowBaker
{
    private static final int MAX_OCCLUDERS = 32;
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;
    private static final float OVERLAP_MARGIN = 0.5f;

    private static final Entity[] occ = new Entity[MAX_OCCLUDERS];
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
        if (!hasSpot())
        {
            return;
        }

        collect(world, cameraPos, tickDelta);
        if (occCount == 0)
        {
            return; // nothing casts; all spots stay tile -1 (unshadowed)
        }

        int tile = 0;
        int n = LightRegistry.getCount();
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

            // occluders within this spot's range
            int inRange = 0;
            for (int k = 0; k < occCount; k++)
            {
                float ddx = ox[k] - lx, ddy = oy[k] - ly, ddz = oz[k] - lz;
                float reach = range + orad[k];
                if (ddx * ddx + ddy * ddy + ddz * ddz <= reach * reach)
                {
                    inRange++;
                }
            }
            if (inRange == 0)
            {
                continue; // no caster -> leave unshadowed (tile -1)
            }

            float dx = LightRegistry.getDirX(i);
            float dy = LightRegistry.getDirY(i);
            float dz = LightRegistry.getDirZ(i);
            float outerDeg = (float) Math.toDegrees(Math.acos(MathHelper.clamp(LightRegistry.getCosOuter(i), -1f, 1f)) * 2.0);

            ShadowRenderer.beginSpot(tile, lx, ly, lz, dx, dy, dz, range, outerDeg);
            for (int k = 0; k < occCount; k++)
            {
                float ddx = ox[k] - lx, ddy = oy[k] - ly, ddz = oz[k] - lz;
                float reach = range + orad[k];
                if (ddx * ddx + ddy * ddy + ddz * ddz <= reach * reach)
                {
                    ShadowRenderer.renderEntity(occ[k], tickDelta);
                }
            }
            ShadowRenderer.endPass();

            LightRegistry.setShadowTile(i, tile);
            tile++;
        }
    }

    private static boolean hasSpot()
    {
        int n = LightRegistry.getCount();
        for (int i = 0; i < n; i++)
        {
            if (LightRegistry.getType(i) == 1)
            {
                return true;
            }
        }
        return false;
    }

    private static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        occCount = 0;
        double camX = cameraPos.x, camY = cameraPos.y, camZ = cameraPos.z;

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

            if (occCount >= MAX_OCCLUDERS)
            {
                break;
            }

            Box box = entity.getBoundingBox();
            float rad = (float) (Math.max(box.getXLength(), Math.max(box.getYLength(), box.getZLength())) * 0.5) + OVERLAP_MARGIN;

            occ[occCount] = entity;
            ox[occCount] = (float) ex;
            oy[occCount] = (float) (ey + box.getYLength() * 0.5);
            oz[occCount] = (float) ez;
            orad[occCount] = rad;
            occCount++;
        }
    }
}
