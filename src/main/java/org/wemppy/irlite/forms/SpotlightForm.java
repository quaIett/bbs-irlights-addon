package org.wemppy.irlite.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

public class SpotlightForm extends Form
{
    public static final Link FORM_ID = new Link("irlite", "spotlight");

    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueFloat intensity = new ValueFloat("intensity", 1F, 0F, 20F);
    public final ValueFloat range = new ValueFloat("range", 12F, 0.1F, 128F);
    public final ValueFloat radius = new ValueFloat("radius", 35F, 1F, 179F);
    public final ValueFloat innerRadius = new ValueFloat("inner_radius", 25F, 1F, 179F);

    public SpotlightForm()
    {
        this.add(this.color);
        this.add(this.intensity);
        this.add(this.range);
        this.add(this.radius);
        this.add(this.innerRadius);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Spotlight";
    }
}
