package org.wemppy.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import org.wemppy.irlite.forms.SpotlightForm;

public class UISpotlightFormPanel extends UIFormPanel<SpotlightForm>
{
    public UIColor color;
    public UITrackpad intensity;
    public UITrackpad range;
    public UITrackpad radius;
    public UITrackpad innerRadius;
    public UITrackpad beamStrength;
    public UITrackpad anisotropy;
    public UITrackpad vlDensity;
    public UIToggle entitiesOnly;

    public UISpotlightFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.intensity = new UITrackpad((v) -> this.form.intensity.set(v.floatValue())).limit(0, 20);
        this.range = new UITrackpad((v) -> this.form.range.set(v.floatValue())).limit(0.1, 128);
        this.radius = new UITrackpad((v) -> this.form.radius.set(v.floatValue())).limit(1, 179);
        this.innerRadius = new UITrackpad((v) -> this.form.innerRadius.set(v.floatValue())).limit(1, 179);
        this.beamStrength = new UITrackpad((v) -> this.form.beamStrength.set(v.floatValue())).limit(0, 5);
        this.anisotropy = new UITrackpad((v) -> this.form.anisotropy.set(v.floatValue())).limit(-0.95, 0.95);
        this.vlDensity = new UITrackpad((v) -> this.form.vlDensity.set(v.floatValue())).limit(0.005, 0.5);
        this.entitiesOnly = new UIToggle(IKey.constant("Entities only"), (b) -> this.form.entitiesOnly.set(b.getValue()));

        this.options.add(UI.label(IKey.constant("Color")), this.color);
        this.options.add(UI.label(IKey.constant("Intensity")), this.intensity);
        this.options.add(UI.label(IKey.constant("Range")), this.range);
        this.options.add(UI.label(IKey.constant("Radius")), this.radius);
        this.options.add(UI.label(IKey.constant("Inner radius")), this.innerRadius);
        this.options.add(UI.label(IKey.constant("Beam strength")), this.beamStrength);
        this.options.add(UI.label(IKey.constant("Anisotropy")), this.anisotropy);
        this.options.add(UI.label(IKey.constant("VL density")), this.vlDensity);
        this.options.add(this.entitiesOnly);
    }

    @Override
    public void startEdit(SpotlightForm form)
    {
        super.startEdit(form);

        this.color.setColor(form.color.get().getARGBColor());
        this.intensity.setValue(form.intensity.get());
        this.range.setValue(form.range.get());
        this.radius.setValue(form.radius.get());
        this.innerRadius.setValue(form.innerRadius.get());
        this.beamStrength.setValue(form.beamStrength.get());
        this.anisotropy.setValue(form.anisotropy.get());
        this.vlDensity.setValue(form.vlDensity.get());
        this.entitiesOnly.setValue(form.entitiesOnly.get());
    }
}
