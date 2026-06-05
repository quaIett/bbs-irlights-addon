package org.wemppy.irlite.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

public class PointLightForm extends Form
{
    public static final Link FORM_ID = new Link("irlite", "point_light");

    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueFloat intensity = new ValueFloat("intensity", 1F, 0F, 20F);
    public final ValueFloat radius = new ValueFloat("radius", 6F, 0.1F, 64F);
    public final ValueBoolean entitiesOnly = new ValueBoolean("entities_only", false);

    public PointLightForm()
    {
        this.add(this.color);
        this.add(this.intensity);
        this.add(this.radius);
        this.add(this.entitiesOnly);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Point light";
    }
}
