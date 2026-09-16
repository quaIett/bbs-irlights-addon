package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.ReplaySelection;
import qualet.irlite.forms.LightEffects;
import qualet.irlite.forms.LightForm;

import java.util.List;
import java.util.function.Function;

/**
 * Keyframe editor of a replay-list track ({@code outline_replays}, {@code light_replays}):
 * a button that opens the native Orbit/Tracker actor picker and a summary of the current list.
 * Registered by property name from {@code IrlightsClientAddon}.
 */
public final class UIReplaySelectionKeyframeFactory extends UIKeyframeFactory<String>
{
    public UIReplaySelectionKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        /* A list has no in-between: BBS's string channel already holds the previous value. */
        this.interp.removeFromParent();

        boolean outline = this.isOutline();
        this.scroll.add(this.mode(outline ? "Outline: selected replays only" : "Light: selected replays only",
            (e) -> outline ? e.selectedReplays : e.selectedLightReplays));
        this.scroll.add(UI.label(() -> this.filterStatus()));

        if (outline)
        {
            this.scroll.add(this.mode("Own outline settings", (e) -> e.customOutline));
            UIToggle enabled = this.mode("Outline", (e) -> e.outline);
            enabled.valueBinding(() ->
            {
                LightEffects effects = this.effects();
                enabled.setValue(effects != null && (effects.customOutline.get() ? effects.outline.get() : IrliteConfig.outline()));
                enabled.setEnabled(effects != null && effects.customOutline.get());
            });
            this.scroll.add(enabled);
            this.scroll.add(UI.label(() -> this.outlineStatus()));
            this.scroll.add(UI.label(IKey.constant("Independent of the Lit Replays list.")));
        }

        UIButton choose = new UIButton(IKey.constant("Choose replays..."), (b) -> this.pick());
        choose.tooltip(IKey.constant("Click a replay to add or remove it. None clears the list. Changes apply immediately."));
        this.scroll.add(choose);
        this.scroll.add(UI.label(() -> "Selected: " + this.describe()));
        this.scroll.add(UI.label(IKey.constant("The list switches at this keyframe.")));
        this.scroll.add(UI.label(IKey.constant("Switches apply to the whole light.")));
    }

    private boolean isOutline()
    {
        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

        return sheet != null && sheet.property != null && sheet.property.getId().equals("outline_replays");
    }

    private LightEffects effects()
    {
        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

        if (sheet == null)
        {
            return null;
        }

        Form owner = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

        return owner instanceof LightForm light ? light.effects : null;
    }

    private UIToggle mode(String label, Function<LightEffects, ValueBoolean> property)
    {
        UIToggle toggle = new UIToggle(IKey.constant(label), (b) ->
        {
            LightEffects effects = this.effects();

            if (effects != null)
            {
                property.apply(effects).set(b.getValue());
            }
        });
        toggle.tooltip(IKey.constant("This switch applies to the whole light and is not animated by this keyframe."));
        toggle.valueBinding(() ->
        {
            LightEffects effects = this.effects();
            toggle.setEnabled(effects != null);
            toggle.setValue(effects != null && property.apply(effects).get());
        });

        return toggle;
    }

    private String filterStatus()
    {
        LightEffects effects = this.effects();
        boolean active = effects != null && (this.isOutline() ? effects.selectedReplays.get() : effects.selectedLightReplays.get());

        return active ? "Filter ON: empty list = nobody." : "Filter OFF: list ignored.";
    }

    private String outlineStatus()
    {
        LightEffects effects = this.effects();

        if (effects == null)
        {
            return "";
        }

        return effects.customOutline.get()
            ? "Own outline: " + (effects.outline.get() ? "ON" : "OFF")
            : "Global outline: " + (IrliteConfig.outline() ? "ON" : "OFF");
    }

    private UIFilmPanel filmPanel()
    {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);

        if (panel == null && this.getContext() != null)
        {
            List<UIFilmPanel> panels = this.getContext().menu.main.getChildren(UIFilmPanel.class);

            panel = panels.isEmpty() ? null : panels.get(0);
        }

        return panel;
    }

    private String describe()
    {
        UIFilmPanel panel = this.filmPanel();
        ReplaySelection.Selection selection = ReplaySelection.decode(this.getDisplayValue());

        return ReplaySelection.describe(selection, panel == null ? null : panel.getData());
    }

    private void pick()
    {
        UIFilmPanel panel = this.filmPanel();

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        ReplaySelection.Selection current = ReplaySelection.decode(this.getDisplayValue());

        LightReplayPicker.open(this.getContext(), panel, current, this::setValue);
    }
}
