package qualet.irlite.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

public class PointLightForm extends LightForm
{
    public static final Link FORM_ID = new Link("irlite", "point_light");

    public final ValueFloat radius = new ValueFloat("radius", 6F, 0.1F, 64F);

    public PointLightForm()
    {
        this.addLightValue(this.radius);
        this.addCommonLightValues();
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return "Point light";
    }
}
