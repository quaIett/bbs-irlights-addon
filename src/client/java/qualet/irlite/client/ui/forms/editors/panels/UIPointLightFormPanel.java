package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import qualet.irlite.forms.PointLightForm;

public class UIPointLightFormPanel extends UIFormPanel<PointLightForm>
{
    public UIColor color;
    public UITrackpad intensity;
    public UITrackpad radius;
    public UITrackpad beamStrength;
    public UITrackpad anisotropy;
    public UITrackpad vlDensity;
    public UITrackpad bulbSize;
    public UIToggle entitiesOnly;
    public UIToggle blocksOnly;
    public UIToggle shadows;

    public UIPointLightFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.intensity = IrliteTrackpads.create((v) -> this.form.intensity.set(v.floatValue()), 0, 20);
        this.radius = IrliteTrackpads.create((v) -> this.form.radius.set(v.floatValue()), 0.1, 64);
        this.beamStrength = IrliteTrackpads.create((v) -> this.form.beamStrength.set(v.floatValue()), 0, 50);
        this.anisotropy = IrliteTrackpads.create((v) -> this.form.anisotropy.set(v.floatValue()), -0.95, 0.95);
        this.vlDensity = IrliteTrackpads.create((v) -> this.form.vlDensity.set(v.floatValue()), 0.005, 0.5);
        this.bulbSize = IrliteTrackpads.create((v) -> this.form.bulbSize.set(v.floatValue()), 0, 2);
        // "Entities only" and "Blocks only" are mutually exclusive (both on = light lights nothing).
        this.entitiesOnly = new UIToggle(IKey.constant("Entities only"), (b) -> {
            this.form.entitiesOnly.set(b.getValue());
            if (b.getValue())
            {
                this.form.blocksOnly.set(false);
                this.blocksOnly.setValue(false);
            }
        });
        this.blocksOnly = new UIToggle(IKey.constant("Blocks only"), (b) -> {
            this.form.blocksOnly.set(b.getValue());
            if (b.getValue())
            {
                this.form.entitiesOnly.set(false);
                this.entitiesOnly.setValue(false);
            }
        });
        this.shadows = new UIToggle(IKey.constant("Shadows"), (b) -> this.form.shadows.set(b.getValue()));

        // Collapsible sections need BBS's UISection (newer 2.3.x builds only). On older
        // BBS the class is absent, so fall back to a flat option list — see IrliteBbsCompat.
        if (IrliteBbsCompat.SECTIONS)
        {
            this.options.add(
                IrliteFormSections.section("Light",
                    UI.label(IKey.constant("Color")), this.color,
                    UI.label(IKey.constant("Intensity")), this.intensity,
                    UI.label(IKey.constant("Radius")), this.radius
                ),
                IrliteFormSections.spaced("Volumetric beam",
                    UI.label(IKey.constant("Beam strength")), this.beamStrength,
                    UI.label(IKey.constant("Anisotropy")), this.anisotropy,
                    UI.label(IKey.constant("VL density")), this.vlDensity
                ),
                IrliteFormSections.spaced("Shadows",
                    this.shadows,
                    UI.label(IKey.constant("Bulb size (shadow softness)")), this.bulbSize
                ),
                IrliteFormSections.spaced("Affects", this.entitiesOnly, this.blocksOnly)
            );
        }
        else
        {
            this.options.add(UI.label(IKey.constant("Color")), this.color);
            this.options.add(UI.label(IKey.constant("Intensity")), this.intensity);
            this.options.add(UI.label(IKey.constant("Radius")), this.radius);
            this.options.add(UI.label(IKey.constant("Beam strength")), this.beamStrength);
            this.options.add(UI.label(IKey.constant("Anisotropy")), this.anisotropy);
            this.options.add(UI.label(IKey.constant("VL density")), this.vlDensity);
            this.options.add(UI.label(IKey.constant("Bulb size (shadow softness)")), this.bulbSize);
            this.options.add(this.entitiesOnly);
            this.options.add(this.blocksOnly);
            this.options.add(this.shadows);
        }
    }

    @Override
    public void startEdit(PointLightForm form)
    {
        super.startEdit(form);

        this.color.setColor(form.color.get().getARGBColor());
        this.intensity.setValue(form.intensity.get());
        this.radius.setValue(form.radius.get());
        this.beamStrength.setValue(form.beamStrength.get());
        this.anisotropy.setValue(form.anisotropy.get());
        this.vlDensity.setValue(form.vlDensity.get());
        this.bulbSize.setValue(form.bulbSize.get());
        this.entitiesOnly.setValue(form.entitiesOnly.get());
        this.blocksOnly.setValue(form.blocksOnly.get());
        this.shadows.setValue(form.shadows.get());
    }
}
