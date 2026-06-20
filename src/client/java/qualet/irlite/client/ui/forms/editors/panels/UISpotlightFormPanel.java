package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import qualet.irlite.forms.SpotlightForm;

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
    public UITrackpad bulbSize;
    public UIToggle entitiesOnly;
    public UIToggle blocksOnly;
    public UIToggle shadows;

    public UIButton cookiePick;
    public UITrackpad cookieRotation;
    public UITrackpad cookieScale;
    public UIToggle cookieInvert;

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
        this.bulbSize = new UITrackpad((v) -> this.form.bulbSize.set(v.floatValue())).limit(0, 2);
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

        // Gobo / cookie: a projected grayscale mask (white = pass, black = block).
        // OFF until a texture is picked. All four fields keyframe in the film editor.
        this.cookiePick = new UIButton(IKey.constant("Cookie texture (gobo)"), (b) ->
            UITexturePicker.open(this.getContext(), this.form.cookie.get(), (l) -> this.form.cookie.set(l)));
        this.cookieRotation = new UITrackpad((v) -> this.form.cookieRotation.set(v.floatValue())).limit(0, 360);
        this.cookieScale = new UITrackpad((v) -> this.form.cookieScale.set(v.floatValue())).limit(0.1, 4);
        this.cookieInvert = new UIToggle(IKey.constant("Invert gobo"), (b) -> this.form.cookieInvert.set(b.getValue()));

        this.options.add(UI.label(IKey.constant("Color")), this.color);
        this.options.add(UI.label(IKey.constant("Intensity")), this.intensity);
        this.options.add(UI.label(IKey.constant("Range")), this.range);
        this.options.add(UI.label(IKey.constant("Radius")), this.radius);
        this.options.add(UI.label(IKey.constant("Inner radius")), this.innerRadius);
        this.options.add(UI.label(IKey.constant("Beam strength")), this.beamStrength);
        this.options.add(UI.label(IKey.constant("Anisotropy")), this.anisotropy);
        this.options.add(UI.label(IKey.constant("VL density")), this.vlDensity);
        this.options.add(UI.label(IKey.constant("Bulb size (shadow softness)")), this.bulbSize);
        this.options.add(this.entitiesOnly);
        this.options.add(this.blocksOnly);
        this.options.add(this.shadows);
        this.options.add(UI.label(IKey.constant("Cookie / gobo (spot mask)")), this.cookiePick);
        this.options.add(UI.label(IKey.constant("Cookie rotation")), this.cookieRotation);
        this.options.add(UI.label(IKey.constant("Cookie scale")), this.cookieScale);
        this.options.add(this.cookieInvert);
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
        this.bulbSize.setValue(form.bulbSize.get());
        this.entitiesOnly.setValue(form.entitiesOnly.get());
        this.blocksOnly.setValue(form.blocksOnly.get());
        this.shadows.setValue(form.shadows.get());
        this.cookieRotation.setValue(form.cookieRotation.get());
        this.cookieScale.setValue(form.cookieScale.get());
        this.cookieInvert.setValue(form.cookieInvert.get());
    }
}
