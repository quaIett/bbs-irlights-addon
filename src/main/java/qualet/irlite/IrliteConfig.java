package qualet.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public final class IrliteConfig
{
    public static ValueBoolean showGuides;
    public static ValueInt shadowQuality;
    public static ValueBoolean shadowCache;
    public static ValueBoolean shadowBlocks;
    public static ValueInt shadowBlockRadius;

    private IrliteConfig()
    {}

    public static boolean showGuides()
    {
        return showGuides != null && showGuides.get();
    }

    /** When on, shadow maps are only re-baked when the scene changes (default on). */
    public static boolean shadowCache()
    {
        return shadowCache == null || shadowCache.get();
    }

    /** When on, world blocks cast shadows by their real shape, and cutout
     *  blocks skip transparent texels (default on). */
    public static boolean shadowBlocks()
    {
        return shadowBlocks == null || shadowBlocks.get();
    }

    /** Shadow resolution preset ordinal (0 LOW .. 3 ULTRA), default 1 (MEDIUM). */
    public static int shadowQuality()
    {
        return shadowQuality != null ? shadowQuality.get() : 1;
    }

    /** Block-shadow collection radius in blocks (default 24). World blocks farther
     *  than this from a light cast no shadow even when the light's range is larger
     *  — it bounds the per-light bbox walk. Higher = bigger lights shadow correctly
     *  but each re-collection (light move / nearby block edit) costs more. */
    public static int shadowBlockRadius()
    {
        return shadowBlockRadius != null ? shadowBlockRadius.get() : 24;
    }
}
