package qualet.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public final class IrliteConfig
{
    public static ValueBoolean showGuides;
    public static ValueInt shadowQuality;
    public static ValueBoolean shadowBlocks;
    public static ValueInt maxShaderLights;
    public static ValueFloat vlIntensity;
    public static ValueInt vlSteps;
    public static ValueFloat vlMaxDist;
    public static ValueBoolean vlShadowsLive;
    public static ValueInt vlShadowStride;
    public static ValueFloat vlTipBoost;
    public static ValueFloat vlTipRadius;
    public static ValueBoolean vlNoiseLive;
    public static ValueFloat vlNoiseAmount;
    public static ValueFloat vlNoiseScale;
    public static ValueFloat vlNoiseSpeed;
    public static ValueFloat vlNoiseMorph;
    public static ValueInt vlNoiseStride;
    public static ValueBoolean vlBlueNoise;
    public static ValueBoolean vlDitherTemporal;
    public static ValueBoolean vlClusterCull;
    public static ValueBoolean vlShadowHiz;
    public static ValueBoolean outline;
    public static ValueInt outlineTarget;
    public static ValueFloat outlineStrength;
    public static ValueInt outlinePixelSize;
    public static ValueFloat outlineFresnelPower;
    public static ValueFloat outlineBack;
    public static ValueBoolean outlineFront;
    public static ValueFloat outlineFrontStrength;
    public static ValueBoolean outlineGlow;
    public static ValueFloat outlineGlowStrength;
    public static ValueBoolean shadowsLive;
    public static ValueFloat shadowSoftness;
    public static ValueBoolean shadowPartialTile;
    public static ValueBoolean diffuse;
    public static ValueFloat intensity;
    public static ValueBoolean specular;
    public static ValueFloat specularIntensity;
    public static ValueBoolean toon;
    public static ValueInt toonBands;
    public static ValueFloat toonSmooth;

    private IrliteConfig()
    {}

    public static boolean showGuides()
    {
        return showGuides != null && showGuides.get();
    }

    /** Shadow maps are only re-baked when the scene changes. Always on — the
     *  cache is transparent (a stale map is a bug to fix, not a mode to pick),
     *  so it lost its BBS toggle. */
    public static boolean shadowCache()
    {
        return true;
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

    /** Block-shadow collection radius in blocks. World blocks farther than this
     *  from a light cast no shadow even when the light's range is larger — it
     *  bounds the per-light bbox walk. Fixed at 24: no BBS knob. */
    public static int shadowBlockRadius()
    {
        return 24;
    }

    /** Max full static shadow bakes started per frame before the rest are
     *  deferred to a later frame (default 4). Spreads a mass invalidation (a
     *  block edit near a cluster of lamps) across frames instead of one spike;
     *  the deferred lamps keep their existing (slightly stale) map until baked.
     *  &lt;= 0 disables throttling (bake everything every frame). First bakes and
     *  tile-reassign bakes are never deferred (they would sample a blank or
     *  foreign map); dynamic overlays and static-&gt;live copies are never
     *  budgeted (they must run every frame). Fixed at 4: no BBS knob. */
    public static int shadowBakeBudget()
    {
        return 4;
    }

    /** Pose/oversize slack of the partial-tile shadow rect (both axes), as a
     *  fraction of the caster's half-height. Covers wide animation poses (arms
     *  out) and forms drawn bigger than their hitbox; oversized bounds only cost
     *  bake speed and fall back to the full-tile path, which never clips.
     *  Fixed at 1.0 — the value calibrated against the visual gate. */
    public static float shadowPoseReach()
    {
        return 1.0F;
    }

    /** Partial-tile spot overlay: on a steady overlay frame only the moving
     *  subject's projected rect is copied, redrawn and refiltered instead of the
     *  whole tile. The rect is built from caster HITBOXES (+ the pose slack), so
     *  a form drawn far past its hitbox can clip at the rect edge, moving with
     *  the subject — switch OFF to trade that risk for the full-tile per-frame
     *  cost. Default on. */
    public static boolean shadowPartialTile()
    {
        return shadowPartialTile == null || shadowPartialTile.get();
    }

    /** Max lights uploaded to the shader SSBO per frame; the injected shader loops
     *  over every uploaded light per fragment, so fewer = cheaper. When more lights
     *  are in range than this, the nearest (highest-priority) ones win; the rest are
     *  skipped for lighting but stay registered and keep casting/receiving shadows.
     *  0 disables the cap (upload everything), which is the default — and no
     *  quality preset touches this knob, so the cap stays wherever it is put. */
    public static int maxShaderLights()
    {
        return maxShaderLights != null ? maxShaderLights.get() : 0;
    }

    /** Global multiplier on IRLite volumetric light brightness, applied live
     *  through the SSBO header each frame — no shader reload. 1.0 = pack
     *  default, 0 = IRLite volumetrics off. Default 1.0. */
    public static float vlIntensity()
    {
        return vlIntensity != null ? vlIntensity.get() : 1F;
    }

    /** Runtime toggle for VL shadows, applied live through the SSBO header
     *  flags each frame — no shader reload. Default on. */
    public static boolean vlShadowsLive()
    {
        return vlShadowsLive == null || vlShadowsLive.get();
    }

    /** Runtime toggle for VL noise, applied live through the SSBO header
     *  flags each frame — no shader reload. Default on. */
    public static boolean vlNoiseLive()
    {
        return vlNoiseLive == null || vlNoiseLive.get();
    }

    /** Ray-march steps per light in the VL pass, applied live through the
     *  globals UBO each frame — no shader reload. Default 48 (pack's
     *  IRLITE_VL_STEPS default). */
    public static int vlSteps()
    {
        return vlSteps != null ? vlSteps.get() : 48;
    }

    /** Maximum VL ray distance in blocks, applied live through the globals
     *  UBO each frame — no shader reload. Default 96 (pack's
     *  IRLITE_VL_MAX_DIST default). */
    public static float vlMaxDist()
    {
        return vlMaxDist != null ? vlMaxDist.get() : 96F;
    }

    /** Tap the shadow maps every Nth VL march step, applied live through the
     *  globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_SHADOW_STRIDE default). */
    public static int vlShadowStride()
    {
        return vlShadowStride != null ? vlShadowStride.get() : 2;
    }

    /** Extra VL glow near the light source itself, applied live through the
     *  globals UBO each frame — no shader reload. Default 1.5 (pack's
     *  IRLITE_VL_TIP_BOOST default). */
    public static float vlTipBoost()
    {
        return vlTipBoost != null ? vlTipBoost.get() : 1.5F;
    }

    /** Radius of the tip glow in blocks, applied live through the globals
     *  UBO each frame — no shader reload. Default 1.5 (pack's
     *  IRLITE_VL_TIP_RADIUS default). */
    public static float vlTipRadius()
    {
        return vlTipRadius != null ? vlTipRadius.get() : 1.5F;
    }

    /** How strongly the noise modulates the VL beam, applied live through the
     *  globals UBO each frame — no shader reload. Default 0.6 (pack's
     *  IRLITE_VL_NOISE_AMOUNT default). */
    public static float vlNoiseAmount()
    {
        return vlNoiseAmount != null ? vlNoiseAmount.get() : 0.6F;
    }

    /** Approximate size of the VL noise puffs in blocks, applied live through
     *  the globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_NOISE_SCALE default). */
    public static float vlNoiseScale()
    {
        return vlNoiseScale != null ? vlNoiseScale.get() : 2F;
    }

    /** How fast the VL noise puffs drift, applied live through the globals
     *  UBO each frame — no shader reload. The core quantizes it to 0.25 steps
     *  (whole field-periods per wind wrap cycle) so the fog never pops when
     *  the shader's time counter wraps. Default 0.25 (pack's
     *  IRLITE_VL_NOISE_SPEED default). */
    public static float vlNoiseSpeed()
    {
        return vlNoiseSpeed != null ? vlNoiseSpeed.get() : 0.25F;
    }

    /** How fast the VL noise puffs reshape (morph) on top of the drift,
     *  applied live through the globals UBO each frame — no shader reload.
     *  0 = classic drifting-only fog. The core quantizes it to 0.25 steps so
     *  the morph crossfade phase stays slice-congruent when the shader's time
     *  counter wraps. Default 0 = morph off (runtime-only knob, no pack define);
     *  no quality preset touches it. */
    public static float vlNoiseMorph()
    {
        return vlNoiseMorph != null ? vlNoiseMorph.get() : 0F;
    }

    /** Sample the VL density noise every Nth march step, applied live through
     *  the globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_NOISE_STRIDE default). */
    public static int vlNoiseStride()
    {
        return vlNoiseStride != null ? vlNoiseStride.get() : 2;
    }

    /** Runtime toggle for the blue-noise VL march dither, applied live through
     *  the globals UBO flags each frame — no shader reload. On replaces the
     *  pack's hash dither with the mod's blue-noise texture (banding pushed
     *  into fine grain the eye discards); off falls back to the pack's own
     *  dither. Default on. */
    public static boolean vlBlueNoise()
    {
        return vlBlueNoise == null || vlBlueNoise.get();
    }

    /** Runtime toggle for per-frame rotation of the VL blue-noise dither,
     *  applied live through the globals UBO flags each frame — no shader
     *  reload. Default on; switch it off for a shot that shimmers/boils on
     *  moving lamps without temporal anti-aliasing. */
    public static boolean vlDitherTemporal()
    {
        return vlDitherTemporal == null || vlDitherTemporal.get();
    }

    /** Runtime toggle for cluster culling in the VL march, applied live through
     *  the globals UBO flags each frame — no shader reload. On reuses the
     *  per-frame screen-tile light grid so each pixel's volumetric march skips
     *  lights whose on-screen bounds miss its tile — the image is identical,
     *  only the cost drops. Default on. */
    public static boolean vlClusterCull()
    {
        return vlClusterCull == null || vlClusterCull.get();
    }

    /** Runtime toggle for the VL shadow Hi-Z segment skip, applied live
     *  through the globals UBO flags each frame — no shader reload. On
     *  classifies each pixel's march segment once against the coarse min/max
     *  spot shadow pyramid: segments provably fully lit skip every per-step
     *  shadow-map tap, segments provably fully occluded skip the light
     *  entirely; anything ambiguous falls back to the per-step taps. Spot
     *  lights only — the image is identical, only the cost drops. Default
     *  on. */
    public static boolean vlShadowHiz()
    {
        return vlShadowHiz == null || vlShadowHiz.get();
    }

    /* ---- outline (wave 1: moved off the Iris screen into the globals UBO) ----
     *
     * All ten apply live through the UBO each frame — no shader reload. They only
     * do anything on a pack patched with the runtime globals block; on the other
     * six packs the UBO is absent and these silently do nothing, the same way the
     * VL knobs already behave. Defaults mirror the pack's compile-time defines,
     * so a fresh install looks identical to the pre-migration build. */

    /** Master toggle for the light-driven rim outline. Default on. */
    public static boolean outline()
    {
        return outline == null || outline.get();
    }

    /** What the outline is drawn on: 0 all, 1 entities only, 2 blocks only.
     *  Default 1 (pack's IRLITE_OUTLINE_TARGET). */
    public static int outlineTarget()
    {
        return outlineTarget != null ? outlineTarget.get() : 1;
    }

    /** Overall rim brightness multiplier. Default 0.65. */
    public static float outlineStrength()
    {
        return outlineStrength != null ? outlineStrength.get() : 0.65F;
    }

    /** Depth-edge detector tap offset in pixels — larger = thicker, coarser
     *  silhouette. Default 6 (the slider maximum): picked by eye 2026-07-21. */
    public static int outlinePixelSize()
    {
        return outlinePixelSize != null ? outlinePixelSize.get() : 6;
    }

    /** Fresnel falloff exponent; higher = the rim hugs grazing angles more
     *  tightly. Default 2.2. */
    public static float outlineFresnelPower()
    {
        return outlineFresnelPower != null ? outlineFresnelPower.get() : 2.2F;
    }

    /** Base rim strength on surfaces facing AWAY from the light (backlight rim).
     *  0 = off; it is slider-only by the pack's own idiom, no companion toggle.
     *  Default 1.0. */
    public static float outlineBack()
    {
        return outlineBack != null ? outlineBack.get() : 1F;
    }

    /** Toggle for the catch-light rim on surfaces facing TOWARD the light.
     *  Default off. Kept as its own toggle rather than folded into
     *  outlineFrontStrength == 0 so switching it off preserves the slider
     *  value to come back to. */
    public static boolean outlineFront()
    {
        return outlineFront != null && outlineFront.get();
    }

    /** Strength of the front catch-light rim. Default 0.3. */
    public static float outlineFrontStrength()
    {
        return outlineFrontStrength != null ? outlineFrontStrength.get() : 0.3F;
    }

    /** Toggle for the soft inner Fresnel halo that feeds the pack's bloom.
     *  Default off. Same rationale as outlineFront for keeping the toggle. */
    public static boolean outlineGlow()
    {
        return outlineGlow != null && outlineGlow.get();
    }

    /** Strength of the inner glow halo. Default 0.12. */
    public static float outlineGlowStrength()
    {
        return outlineGlowStrength != null ? outlineGlowStrength.get() : 0.12F;
    }

    /* ---- shadows, live half (wave 3) ---------------------------------------- */

    /** Everyday on/off for shadows cast by IRLights lights, applied live through
     *  the globals UBO each frame — no shader reload. This is NOT the same as the
     *  pack's IRLITE_SHADOWS define, which stays on the Iris screen purely as a
     *  compile-time escape hatch for drivers that reject samplerCubeArray: with
     *  that one off there is no shadow code left for this toggle to gate.
     *  Default on. */
    public static boolean shadowsLive()
    {
        return shadowsLive == null || shadowsLive.get();
    }

    /** Apparent size of the light source, which sets how quickly the shadow edge
     *  spreads with distance from the caster (PCSS penumbra width). 0 = hard
     *  edges. A light with its own bulb size set still overrides this. Applied
     *  live through the globals UBO each frame — no shader reload. Default 0.10
     *  (pack's IRLITE_SHADOW_SIZE default). */
    public static float shadowSoftness()
    {
        return shadowSoftness != null ? shadowSoftness.get() : 0.10F;
    }

    /* ---- surface lighting (wave 2: moved off the Iris screen into the globals UBO) ----
     *
     * Same deal as the outline block: live through the UBO each frame, silently
     * inert on a pack without the surface block, defaults equal to the pack's
     * former compile-time defaults. */

    /** Diffuse light from IRLights lights on surfaces. Default on. */
    public static boolean diffuse()
    {
        return diffuse == null || diffuse.get();
    }

    /** Master multiplier for diffuse, specular and the outline rim; the
     *  volumetric beams keep their own vlIntensity. Default 1.0. */
    public static float intensity()
    {
        return intensity != null ? intensity.get() : 1F;
    }

    /** Specular highlight from IRLights lights. Default on. */
    public static boolean specular()
    {
        return specular == null || specular.get();
    }

    /** Extra multiplier on the specular highlight only. Default 1.0. */
    public static float specularIntensity()
    {
        return specularIntensity != null ? specularIntensity.get() : 1F;
    }

    /** Toon (cel) banding of the diffuse light. Default off. */
    public static boolean toon()
    {
        return toon != null && toon.get();
    }

    /** Number of toon brightness bands. Default 3. */
    public static int toonBands()
    {
        return toonBands != null ? toonBands.get() : 3;
    }

    /** Softness of the toon band edges; 0 = hard steps. Default 0.10. */
    public static float toonSmooth()
    {
        return toonSmooth != null ? toonSmooth.get() : 0.10F;
    }
}
