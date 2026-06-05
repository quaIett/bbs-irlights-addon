package org.wemppy.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import org.wemppy.irlite.forms.PointLightForm;

public class UIPointLightFormPanel extends UIFormPanel<PointLightForm>
{
    public UIColor color;
    public UITrackpad intensity;
    public UITrackpad radius;
    public UIToggle entitiesOnly;

    public UIPointLightFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.intensity = new UITrackpad((v) -> this.form.intensity.set(v.floatValue())).limit(0, 20);
        this.radius = new UITrackpad((v) -> this.form.radius.set(v.floatValue())).limit(0.1, 64);
        this.entitiesOnly = new UIToggle(IKey.constant("Entities only"), (b) -> this.form.entitiesOnly.set(b.getValue()));

        this.options.add(UI.label(IKey.constant("Color")), this.color);
        this.options.add(UI.label(IKey.constant("Intensity")), this.intensity);
        this.options.add(UI.label(IKey.constant("Radius")), this.radius);
        this.options.add(this.entitiesOnly);
    }

    @Override
    public void startEdit(PointLightForm form)
    {
        super.startEdit(form);

        this.color.setColor(form.color.get().getARGBColor());
        this.intensity.setValue(form.intensity.get());
        this.radius.setValue(form.radius.get());
        this.entitiesOnly.setValue(form.entitiesOnly.get());
    }
}
