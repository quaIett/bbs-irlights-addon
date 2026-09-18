package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.LightEffectsRegistration;
import qualet.irlite.forms.LightEffects;
import qualet.irlite.forms.LightForm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A light's own volumetric settings (one tab) or its own outline settings plus the
 * outline replay list (the other tab). Shared by the point light and spotlight editors.
 *
     * <p>With the "own settings" switch off the light follows the global IRLights settings
     * unless numeric keyframes activate that section. "Copy global settings" seeds it from
 * the current globals and switches the light over, so tweaking from the global look is
 * one click. Every number here is also a track in the replay's Light tab.</p>
 */
public final class UILightEffectsPanel<T extends LightForm> extends UIFormPanel<T>
{
    private final List<Runnable> refresh = new ArrayList<>();
    private final LightReplayWidgets outlineReplays;

    /**
     * @param vl true = the volumetric tab, false = the outline tab
     */
    public UILightEffectsPanel(UIForm editor, boolean vl)
    {
        super(editor);

        if (vl)
        {
            this.outlineReplays = null;

            UIElement[] beam = {
                UI.label(IKey.constant("Off: global settings. Keyframes activate own settings.")),
                this.toggle("Own volumetric settings", (e) -> e.customVl),
                new UIButton(IKey.constant("Copy global settings"), (b) ->
                {
                    LightEffectsRegistration.copyGlobals(this.form.effects, true);
                    this.refreshAll();
                }),
                this.toggle("Beam", (e) -> e.vlEnabled),
                UI.label(IKey.constant("Beam intensity")), this.trackpad((e) -> e.vlIntensity, 0, 5),
                UI.label(IKey.constant("Max distance")), this.trackpad((e) -> e.vlMaxDist, 1, 256),
                this.toggle("Beam shadows", (e) -> e.vlShadows),
                UI.label(IKey.constant("Tip glow")), this.trackpad((e) -> e.vlTipBoost, 0, 4),
                UI.label(IKey.constant("Tip radius")), this.trackpad((e) -> e.vlTipRadius, 0.01, 4)
            };
            UIElement[] noise = {
                this.toggle("Beam noise", (e) -> e.vlNoise),
                UI.label(IKey.constant("Noise amount")), this.trackpad((e) -> e.vlNoiseAmount, 0, 1),
                UI.label(IKey.constant("Noise scale")), this.trackpad((e) -> e.vlNoiseScale, 0.01, 6),
                UI.label(IKey.constant("Drift speed")), this.trackpad((e) -> e.vlNoiseSpeed, 0, 3),
                UI.label(IKey.constant("Noise morph")), this.trackpad((e) -> e.vlNoiseMorph, 0, 3)
            };

            this.layout("Volumetric (this light)", beam, "Beam noise", noise, null, null);
        }
        else
        {
            UICirculate target = new UICirculate((c) -> this.form.effects.outlineTarget.set(c.getValue()));

            target.addLabel(IKey.constant("Draw on: all"));
            target.addLabel(IKey.constant("Draw on: entities"));
            target.addLabel(IKey.constant("Draw on: blocks"));
            this.refresh.add(() -> target.setValue(this.form.effects.outlineTarget.get()));

            this.outlineReplays = new LightReplayWidgets(this,
                "Outline: selected replays only", "Choose outlined replays...",
                () -> this.form.effects.selectedReplays, () -> this.form.effects.outlineReplays);

            UIElement[] rim = {
                UI.label(() -> this.form.effects.customOutline.get() ? "Own outline settings are active."
                    : "Global outline: " + (IrliteConfig.outline() ? "ON" : "OFF") + ". Keyframes activate own settings."),
                this.toggle("Own outline settings", (e) -> e.customOutline),
                new UIButton(IKey.constant("Copy global settings"), (b) ->
                {
                    LightEffectsRegistration.copyGlobals(this.form.effects, false);
                    this.refreshAll();
                }),
                this.toggle("Outline", (e) -> e.outline),
                target,
                UI.label(IKey.constant("Strength")), this.trackpad((e) -> e.outlineStrength, 0, 3),
                UI.label(IKey.constant("Thickness (px, 0 = global)")), this.trackpad((e) -> e.outlinePixelSize, 0, 6),
                UI.label(IKey.constant("Fresnel falloff")), this.trackpad((e) -> e.outlineFresnel, 1, 4),
                UI.label(IKey.constant("Backlight rim")), this.trackpad((e) -> e.outlineBack, 0, 2)
            };
            UIElement[] extras = {
                this.toggle("Front catch-light", (e) -> e.outlineFront),
                UI.label(IKey.constant("Front strength")), this.trackpad((e) -> e.outlineFrontStrength, 0, 1.5),
                this.toggle("Inner glow", (e) -> e.outlineGlow),
                UI.label(IKey.constant("Glow strength")), this.trackpad((e) -> e.outlineGlowStrength, 0, 0.75)
            };

            /* Inherited values are inactive: don't present local controls as working overrides. */
            for (int i = 3; i < rim.length; i++)
            {
                this.ownOutlineOnly(rim[i]);
            }
            for (UIElement element : extras)
            {
                this.ownOutlineOnly(element);
            }

            this.layout("Outline (this light)", rim, "Front rim & glow", extras, "Replays", this.outlineReplays.elements());
        }
    }

    private void ownOutlineOnly(UIElement element)
    {
        element.valueBinding(() -> element.setEnabled(this.form.effects.customOutline.get()));
    }

    private void layout(String first, UIElement[] a, String second, UIElement[] b, String third, UIElement[] c)
    {
        // Collapsible sections need BBS's UISection (newer builds only); see IrliteBbsCompat.
        if (IrliteBbsCompat.SECTIONS)
        {
            this.options.add(IrliteFormSections.section(first, a), IrliteFormSections.spaced(second, b));

            if (c != null)
            {
                this.options.add(IrliteFormSections.spaced(third, c));
            }
        }
        else
        {
            this.options.add(a);
            this.options.add(b);

            if (c != null)
            {
                this.options.add(c);
            }
        }
    }

    private UIToggle toggle(String label, Function<LightEffects, ValueBoolean> value)
    {
        UIToggle toggle = new UIToggle(IKey.constant(label), (b) -> value.apply(this.form.effects).set(b.getValue()));

        this.refresh.add(() -> toggle.setValue(value.apply(this.form.effects).get()));

        return toggle;
    }

    private UITrackpad trackpad(Function<LightEffects, ValueFloat> value, double min, double max)
    {
        UITrackpad trackpad = IrliteTrackpads.create((v) -> value.apply(this.form.effects).set(v.floatValue()), min, max);

        this.refresh.add(() -> trackpad.setValue(value.apply(this.form.effects).get()));

        return trackpad;
    }

    private void refreshAll()
    {
        for (Runnable runnable : this.refresh)
        {
            runnable.run();
        }

        if (this.outlineReplays != null)
        {
            this.outlineReplays.refresh();
        }
    }

    @Override
    public void startEdit(T form)
    {
        super.startEdit(form);

        this.refreshAll();
    }
}
