package qualet.irlite.forms;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * A light's own volumetric and outline settings, plus the two replay lists that
 * restrict who it lights and who it outlines.
 *
 * <p>This is only a holder of definitions: {@link LightForm} registers every leaf
 * directly on itself, because BBS collects animation tracks from a form's direct
 * properties only — a nested group would keep these off the timeline.</p>
 *
 * <p>Modes (the booleans and the outline target) are form-editor switches and are
 * hidden from the timeline; the numbers animate. The look knobs mirror the global
 * IRLights settings one to one; the quality knobs (march steps and tap strides)
 * deliberately stay global — see {@code LightEffectsRegistration}.</p>
 */
public final class LightEffects extends ValueGroup
{
    /* Volumetric: OFF = global settings unless numeric tracks are animating this section. */
    public final ValueBoolean customVl = new ValueBoolean("custom_vl", false);
    public final ValueBoolean vlEnabled = new ValueBoolean("vl_enabled", true);
    public final ValueFloat vlIntensity = new ValueFloat("vl_intensity", 1F, 0F, 5F);
    public final ValueFloat vlMaxDist = new ValueFloat("vl_max_dist", 96F, 1F, 256F);
    public final ValueBoolean vlShadows = new ValueBoolean("vl_shadows", true);
    public final ValueFloat vlTipBoost = new ValueFloat("vl_tip_boost", 1.5F, 0F, 4F);
    public final ValueFloat vlTipRadius = new ValueFloat("vl_tip_radius", 1.5F, 0.01F, 4F);
    public final ValueBoolean vlNoise = new ValueBoolean("vl_noise", true);
    public final ValueFloat vlNoiseAmount = new ValueFloat("vl_noise_amount", 0.6F, 0F, 1F);
    public final ValueFloat vlNoiseScale = new ValueFloat("vl_noise_scale", 2F, 0.01F, 6F);
    public final ValueFloat vlNoiseSpeed = new ValueFloat("vl_noise_speed", 0.25F, 0F, 3F);
    public final ValueFloat vlNoiseMorph = new ValueFloat("vl_noise_morph", 0F, 0F, 3F);

    /* Outline: OFF = global settings unless numeric tracks or automatic replay selection override it. */
    public final ValueBoolean customOutline = new ValueBoolean("custom_outline", false);
    public final ValueBoolean outline = new ValueBoolean("outline", true);
    public final ValueInt outlineTarget = new ValueInt("outline_target", 1, 0, 2);
    public final ValueFloat outlineStrength = new ValueFloat("outline_strength", 0.65F, 0F, 3F);
    /* Zero inherits the global thickness, preserving films saved before this property existed. */
    /* Float tracks retain interpolation in BBS; the renderer rounds to whole pixels. */
    public final ValueFloat outlinePixelSize = new ValueFloat("outline_pixel_size", 0F, 0F, 6F);
    public final ValueFloat outlineFresnel = new ValueFloat("outline_fresnel", 2.2F, 1F, 4F);
    public final ValueFloat outlineBack = new ValueFloat("outline_back", 1F, 0F, 2F);
    public final ValueBoolean outlineFront = new ValueBoolean("outline_front", false);
    public final ValueFloat outlineFrontStrength = new ValueFloat("outline_front_strength", 0.3F, 0F, 1.5F);
    public final ValueBoolean outlineGlow = new ValueBoolean("outline_glow", false);
    public final ValueFloat outlineGlowStrength = new ValueFloat("outline_glow_strength", 0.12F, 0F, 0.75F);

    /* Replay linking. Each list is a stepped string track: a JSON of the film id and the
     * stable ids of the chosen replays (see ReplaySelection). Enabled + empty = nobody;
     * disabled keeps the list but imposes nothing. */
    public final ValueBoolean selectedReplays = new ValueBoolean("selected_replays", false);
    public final ValueReplaySelection outlineReplays = new ValueReplaySelection("outline_replays");
    public final ValueBoolean selectedLightReplays = new ValueBoolean("selected_light_replays", false);
    public final ValueReplaySelection lightReplays = new ValueReplaySelection("light_replays");

    public LightEffects()
    {
        super("effects");

        this.add(this.customVl);
        this.add(this.vlEnabled);
        this.add(this.vlIntensity);
        this.add(this.vlMaxDist);
        this.add(this.vlShadows);
        this.add(this.vlTipBoost);
        this.add(this.vlTipRadius);
        this.add(this.vlNoise);
        this.add(this.vlNoiseAmount);
        this.add(this.vlNoiseScale);
        this.add(this.vlNoiseSpeed);
        this.add(this.vlNoiseMorph);

        this.add(this.customOutline);
        this.add(this.outline);
        this.add(this.outlineTarget);
        this.add(this.outlineStrength);
        this.add(this.outlinePixelSize);
        this.add(this.outlineFresnel);
        this.add(this.outlineBack);
        this.add(this.outlineFront);
        this.add(this.outlineFrontStrength);
        this.add(this.outlineGlow);
        this.add(this.outlineGlowStrength);

        this.add(this.selectedReplays);
        this.add(this.outlineReplays);
        this.add(this.selectedLightReplays);
        this.add(this.lightReplays);

        /* Switches are edited in the form editor, not keyframed: BBS lists and creates
         * tracks only for visible values (TrackCatalog / FormProperties.create). */
        for (BaseValue value : this.getAll())
        {
            if (value instanceof ValueBoolean || value == this.outlineTarget)
            {
                value.invisible();
            }
        }
    }

    /** Whether the light asks the renderer for anything beyond the global settings. */
    public boolean isDefault()
    {
        return !this.customVl.get() && !this.customOutline.get()
            && !this.selectedReplays.get() && !this.selectedLightReplays.get();
    }
}
