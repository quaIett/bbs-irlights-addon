package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import qualet.irlite.forms.LightForm;

import java.util.Map;

/** Rendering limits, not the form editor's suggested ranges. Unbounded light intensity,
 * range, density and beam strength must keep their ordinary keyframe trackpads. */
public final class LightKeyframeRanges
{
    public record Range(double min, double max)
    {
        public float clamp(double value)
        {
            return (float) (Double.isNaN(value) ? min : Math.max(min, Math.min(max, value)));
        }
    }

    /* LightProfile.write clamps these before GPU upload; anisotropy is clamped in GLSL.
     * Thickness additionally reserves zero for inheritance from the global setting. */
    private static final Map<String, Range> RANGES = Map.ofEntries(
        Map.entry("anisotropy", new Range(-.95, .95)),
        Map.entry("vl_intensity", new Range(0, 5)),
        Map.entry("vl_max_dist", new Range(1, 256)),
        Map.entry("vl_tip_boost", new Range(0, 4)),
        Map.entry("vl_tip_radius", new Range(.01, 4)),
        Map.entry("vl_noise_amount", new Range(0, 1)),
        Map.entry("vl_noise_scale", new Range(.01, 6)),
        Map.entry("vl_noise_speed", new Range(0, 3)),
        Map.entry("vl_noise_morph", new Range(0, 3)),
        Map.entry("outline_strength", new Range(0, 3)),
        Map.entry("outline_pixel_size", new Range(0, 6)),
        Map.entry("outline_fresnel", new Range(.001, 4)),
        Map.entry("outline_back", new Range(0, 2)),
        Map.entry("outline_front_strength", new Range(0, 1.5)),
        Map.entry("outline_glow_strength", new Range(0, .75))
    );

    private LightKeyframeRanges()
    {}

    public static Range of(BaseValue property)
    {
        return property instanceof ValueFloat && FormUtils.getForm(property) instanceof LightForm light
            && light.isLightTrack(property) ? RANGES.get(property.getId()) : null;
    }
}
