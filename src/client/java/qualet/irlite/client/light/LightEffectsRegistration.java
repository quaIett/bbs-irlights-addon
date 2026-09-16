package qualet.irlite.client.light;

import org.qualet.irl.light.LightProfile;
import org.qualet.irl.light.LightRegistry;
import qualet.irlite.IrliteConfig;
import qualet.irlite.forms.LightEffects;
import qualet.irlite.forms.LightForm;
import qualet.irlite.forms.ValueReplaySelection;

import java.util.Objects;

/**
 * Hands a light's own effects ({@link LightEffects}) to the engine as a
 * {@link LightProfile}, right after the light itself was registered.
 *
 * <p>Both registration paths — the scanner ({@code LightCollector}) and the form
 * renderers — call {@link #apply} with the same form, and the profile attaches to the
 * registration by the form's identity, the same key the light was registered under.
 * A light whose effects are all at their defaults sends no profile at all, so the
 * shader treats it exactly as before this feature existed.</p>
 *
 * <p>The quality knobs of the profile (march steps, tap strides, outline pixel size)
 * are filled from the global settings, not from the form: they are frame-cost knobs,
 * not look knobs, and stay global on purpose.</p>
 */
public final class LightEffectsRegistration
{
    private LightEffectsRegistration()
    {}

    /** Per-form scratch: the profile and the two decoded replay lists, kept on the form itself. */
    private static final class Cache
    {
        final LightProfile profile = new LightProfile();
        final Targets outline = new Targets();
        final Targets light = new Targets();
    }

    /** One replay list: decoded once per distinct string, re-resolved to tokens every frame. */
    private static final class Targets
    {
        private String value;
        private String film = "";
        private String[] replays = new String[0];
        private int[] resolved = new int[0];

        int[] resolve(ValueReplaySelection property)
        {
            String now = property.get();

            if (!Objects.equals(now, this.value))
            {
                ReplaySelection.Selection selection = ReplaySelection.decode(now);

                this.value = now;
                this.film = selection.film();
                this.replays = selection.replays().toArray(new String[0]);
                this.resolved = new int[this.replays.length];
            }

            for (int i = 0; i < this.replays.length; i++)
            {
                this.resolved[i] = ReplayOutlineContext.resolve(this.film, this.replays[i]);
            }

            return this.resolved;
        }
    }

    public static void apply(LightForm form)
    {
        LightEffects e = form.effects;
        long id = System.identityHashCode(form);

        if (e.isDefault())
        {
            LightRegistry.setProfile(id, null);

            return;
        }

        Cache cache = form.renderCache() instanceof Cache c ? c : new Cache();

        form.setRenderCache(cache);

        LightProfile p = cache.profile;

        p.customVl = e.customVl.get();
        p.customOutline = e.customOutline.get();
        p.selectedReplays = e.selectedReplays.get();
        p.selectedLightReplays = e.selectedLightReplays.get();

        /* Volumetric: the look knobs are the light's own, the cost knobs stay global. */
        p.intensity = e.vlEnabled.get() ? e.vlIntensity.get() : 0F;
        p.maxDist = e.vlMaxDist.get();
        p.tipBoost = e.vlTipBoost.get();
        p.tipRadius = e.vlTipRadius.get();
        p.noiseAmount = e.vlNoiseAmount.get();
        p.noiseScale = e.vlNoiseScale.get();
        p.noiseSpeed = e.vlNoiseSpeed.get();
        p.noiseMorph = e.vlNoiseMorph.get();
        p.steps = IrliteConfig.vlSteps();
        p.shadowStride = IrliteConfig.vlShadowStride();
        p.noiseStride = IrliteConfig.vlNoiseStride();

        /* Flags mirror VlGlobalsBuffer's bit layout: bit0 VL shadows, bit1 noise, bit8 outline,
         * bit9 front rim, bit10 glow, bits 11-12 target. */
        p.flags = (e.vlShadows.get() ? 1 : 0)
            | (e.vlNoise.get() ? 2 : 0)
            | (e.outline.get() ? 256 : 0)
            | (e.outlineFront.get() ? 512 : 0)
            | (e.outlineGlow.get() ? 1024 : 0)
            | (e.outlineTarget.get() << 11);

        p.strength = e.outlineStrength.get();
        p.fresnel = e.outlineFresnel.get();
        p.back = e.outlineBack.get();
        p.front = e.outlineFrontStrength.get();
        p.glow = e.outlineGlowStrength.get();
        p.pixelSize = IrliteConfig.outlinePixelSize();

        p.replayIds = cache.outline.resolve(e.outlineReplays);
        p.lightReplayIds = cache.light.resolve(e.lightReplays);

        LightRegistry.setProfile(id, p);
    }

    /** Fill one half of a light's own settings from the global IRLights settings and switch that half on. */
    public static void copyGlobals(LightEffects e, boolean vl)
    {
        if (vl)
        {
            e.vlIntensity.set(IrliteConfig.vlIntensity());
            e.vlMaxDist.set(IrliteConfig.vlMaxDist());
            e.vlShadows.set(IrliteConfig.vlShadowsLive());
            e.vlTipBoost.set(IrliteConfig.vlTipBoost());
            e.vlTipRadius.set(IrliteConfig.vlTipRadius());
            e.vlNoise.set(IrliteConfig.vlNoiseLive());
            e.vlNoiseAmount.set(IrliteConfig.vlNoiseAmount());
            e.vlNoiseScale.set(IrliteConfig.vlNoiseScale());
            e.vlNoiseSpeed.set(IrliteConfig.vlNoiseSpeed());
            e.vlNoiseMorph.set(IrliteConfig.vlNoiseMorph());
            e.vlEnabled.set(true);
            e.customVl.set(true);
        }
        else
        {
            e.outline.set(IrliteConfig.outline());
            e.outlineTarget.set(IrliteConfig.outlineTarget());
            e.outlineStrength.set(IrliteConfig.outlineStrength());
            e.outlineFresnel.set(IrliteConfig.outlineFresnelPower());
            e.outlineBack.set(IrliteConfig.outlineBack());
            e.outlineFront.set(IrliteConfig.outlineFront());
            e.outlineFrontStrength.set(IrliteConfig.outlineFrontStrength());
            e.outlineGlow.set(IrliteConfig.outlineGlow());
            e.outlineGlowStrength.set(IrliteConfig.outlineGlowStrength());
            e.customOutline.set(true);
        }
    }
}
