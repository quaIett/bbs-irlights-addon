package qualet.irlite.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.HashSet;
import java.util.Set;

/**
 * What a point light and a spotlight share: colour, intensity, the beam and shadow
 * knobs, the affect masks, and the light's own effects ({@link LightEffects}).
 *
 * <p>The serialized property names are the ones the two forms always had, so films
 * saved before this class existed load unchanged. The effects' leaves are registered
 * flat on the form (not as the nested group) so BBS can keyframe them.</p>
 */
public abstract class LightForm extends Form
{
    /** Ids of the properties that are "light" tracks in a replay timeline (vs BBS's own transform/visible/...). */
    private final Set<String> lightValues = new HashSet<>();

    public final LightEffects effects = new LightEffects();

    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueFloat intensity = new ValueFloat("intensity", 1F, 0F, 20F);
    public final ValueFloat beamStrength = new ValueFloat("beam_strength", 1F, 0F, 50F);
    public final ValueFloat anisotropy = new ValueFloat("anisotropy", 0.4F, -0.95F, 0.95F);
    public final ValueFloat vlDensity = new ValueFloat("vl_density", 0.05F, 0.005F, 0.5F);
    public final ValueFloat bulbSize = new ValueFloat("bulb_size", 0F, 0F, 2F);
    public final ValueBoolean entitiesOnly = new ValueBoolean("entities_only", false);
    public final ValueBoolean blocksOnly = new ValueBoolean("blocks_only", false);
    public final ValueBoolean shadows = new ValueBoolean("shadows", true);

    protected LightForm()
    {
        this.addLightValue(this.color);
        this.addLightValue(this.intensity);
    }

    /**
     * The shared values after the subclass's own shape values, so the tracks keep their
     * historical order (colour, intensity, shape, beam, shadows, masks) in the timeline.
     */
    protected final void addCommonLightValues()
    {
        this.addLightValue(this.beamStrength);
        this.addLightValue(this.anisotropy);
        this.addLightValue(this.vlDensity);
        this.addLightValue(this.bulbSize);
        this.addLightValue(this.entitiesOnly);
        this.addLightValue(this.blocksOnly);
        this.addLightValue(this.shadows);

        for (BaseValue value : this.effects.getAll())
        {
            this.addLightValue(value);
        }
    }

    protected final void addLightValue(BaseValue value)
    {
        this.add(value);
        this.lightValues.add(value.getId());
    }

    /**
     * Client-side scratch attached to this very instance (the renderer's decoded profile).
     * Forms are values — {@code equals} compares contents — so a map keyed by the form
     * could not tell two identical lights apart; the slot on the instance can.
     */
    private transient Object renderCache;

    public final Object renderCache()
    {
        return this.renderCache;
    }

    public final void setRenderCache(Object cache)
    {
        this.renderCache = cache;
    }

    /** Whether {@code value} is one of this light's own animatable properties (a "light" track). */
    public final boolean isLightTrack(BaseValue value)
    {
        return value != null && value.getParent() == this && value.isVisible() && this.lightValues.contains(value.getId());
    }
}
