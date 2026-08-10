package qualet.irlite.client;

import org.qualet.irl.light.shadow.ShadowConfig;
import qualet.irlite.IrliteConfig;

/**
 * Client-side {@link ShadowConfig} adapter handed to
 * {@link org.qualet.irl.light.shadow.ShadowEngine} at init: the shadow-relevant subset
 * of {@link IrliteConfig}, delegating to its BBS-{@code Value*}-backed getters.
 *
 * <p>Why a separate class here (vs the redactor's nested {@code LightConfig.SHADOW}):
 * {@link IrliteConfig} lives in the main (server-safe) source set, which does not see
 * the client-scoped irl-core dependency. This adapter lives in the client source set
 * where {@link ShadowConfig} is visible, and reads {@code IrliteConfig}'s static
 * getters (visible from client).
 */
public final class IrliteShadowConfig
{
    public static final ShadowConfig INSTANCE = ShadowConfig.builder()
            .shadowQuality(IrliteConfig::shadowQuality)
            .shadowCache(IrliteConfig::shadowCache)
            .shadowBakeBudget(IrliteConfig::shadowBakeBudget)
            .shadowBlocks(IrliteConfig::shadowBlocks)
            .shadowBlockRadius(IrliteConfig::shadowBlockRadius)
            .shadowPoseReach(IrliteConfig::shadowPoseReach)
            .shadowPartialTile(IrliteConfig::shadowPartialTile)
            // Bake stays alive while EITHER surface shadows or volumetric beam
            // shadows are on — the VL pass samples the same maps (vlC.w bit0), so
            // gating on shadowsLive() alone would blank beam shadows when a user
            // only meant to drop surface shadows.
            .shadowsEnabled(() -> IrliteConfig.shadowsLive() || IrliteConfig.vlShadowsLive())
            .build();

    private IrliteShadowConfig()
    {}
}
