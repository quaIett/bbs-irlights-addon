package qualet.irlite.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

public class SpotlightForm extends LightForm
{
    public static final Link FORM_ID = new Link("irlite", "spotlight");

    public final ValueFloat range = new ValueFloat("range", 12F, 0.1F, 128F);
    public final ValueFloat radius = new ValueFloat("radius", 35F, 1F, 179F);
    public final ValueFloat innerRadius = new ValueFloat("inner_radius", 25F, 1F, 179F);

    /**
     * Gobo / light-cookie: a grayscale image projected from the cone as a light
     * multiplier (white = pass, black = block) — a projected mask, NOT a shadow.
     * Spot-only (a point light has no projection frustum). The cookie is OFF
     * unless {@link #cookie} points at a texture: an empty/null link resolves to
     * array layer -1 and the shader skips it. All four are BBS Value* so they
     * keyframe in the film editor (e.g. spin the gobo by animating the rotation).
     */
    public final ValueLink cookie = new ValueLink("cookie", null);
    public final ValueFloat cookieRotation = new ValueFloat("cookie_rotation", 0F, 0F, 360F); // DEGREES (UI); -> radians at collect
    public final ValueFloat cookieScale = new ValueFloat("cookie_scale", 1F, 0.1F, 4F);       // 1 = mask fills the cone
    public final ValueBoolean cookieInvert = new ValueBoolean("cookie_invert", false);

    public SpotlightForm()
    {
        this.addLightValue(this.range);
        this.addLightValue(this.radius);
        this.addLightValue(this.innerRadius);
        this.addCommonLightValues();
        this.addLightValue(this.cookie);
        this.addLightValue(this.cookieRotation);
        this.addLightValue(this.cookieScale);
        this.addLightValue(this.cookieInvert);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Spotlight";
    }
}
