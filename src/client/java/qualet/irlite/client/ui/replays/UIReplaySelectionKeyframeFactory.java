package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import qualet.irlite.client.light.ReplaySelection;
import qualet.irlite.client.light.ReplaySelectionPlayback;
import qualet.irlite.forms.ValueReplaySelection;

import java.util.List;

/** The selection and its activation travel together in the ordinary, undoable string keyframe. */
public final class UIReplaySelectionKeyframeFactory extends UIKeyframeFactory<String>
{
    private static final String USE_LIGHT_SETTINGS = ReplaySelection.encode("", List.of(), ReplaySelection.Mode.INHERIT);

    public UIReplaySelectionKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.interp.removeFromParent();

        UIButton choose = new UIButton(IKey.constant("Choose replays..."), (b) -> this.pick());
        choose.tooltip(IKey.constant("Click replays to add or remove them. The selection activates automatically at this keyframe."));
        this.scroll.add(choose);
        this.scroll.add(UI.label(() -> this.describe()));
        this.scroll.add(new UIButton(IKey.constant("Nobody"), (b) ->
            this.setValue(ReplaySelection.encode("", List.of(), ReplaySelection.Mode.SELECTED))));
        UIButton inherit = new UIButton(IKey.constant("Use light settings"), (b) ->
            this.setValue(USE_LIGHT_SETTINGS));
        inherit.tooltip(IKey.constant("Return to this light's form settings at this keyframe."));
        this.scroll.add(inherit);
        this.scroll.add(UI.label(IKey.constant("The selection switches at this keyframe.")));

        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

        if (sheet != null && sheet.property != null && sheet.property.getId().equals("outline_replays"))
        {
            this.scroll.add(UI.label(IKey.constant("Outline activates on selected replays.")));
            this.scroll.add(UI.label(IKey.constant("Independent of the Lit Replays list.")));
        }
    }

    @Override
    protected String getDisplayValue()
    {
        Integer tick = this.editor.getGraph().getAutoKeyframeTick();
        UIKeyframeSheet sheet = tick == null ? null : this.editor.getGraph().getSheet(this.keyframe);

        if (sheet != null && ReplaySelectionPlayback.startsAfter(sheet.channel, tick))
        {
            return USE_LIGHT_SETTINGS;
        }

        return super.getDisplayValue();
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

    private ReplaySelection.Selection selection()
    {
        ReplaySelection.Selection selection = ReplaySelection.decode(this.getDisplayValue());

        if (selection.mode() == ReplaySelection.Mode.INHERIT)
        {
            UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

            if (sheet != null && sheet.property instanceof ValueReplaySelection property)
            {
                return ReplaySelection.decode(property.getOriginalValue());
            }
        }

        return selection;
    }

    private String describe()
    {
        if (this.getDisplayValue().isBlank())
        {
            return "Choose replays to activate";
        }

        ReplaySelection.Selection value = ReplaySelection.decode(this.getDisplayValue());

        if (value.mode() == ReplaySelection.Mode.INHERIT)
        {
            return "Using light settings";
        }

        UIFilmPanel panel = this.filmPanel();
        String summary = ReplaySelection.describe(value, panel == null ? null : panel.getData());

        return value.mode() == ReplaySelection.Mode.LEGACY
            ? "Saved selection: " + summary : "Selected: " + summary;
    }

    private void pick()
    {
        UIFilmPanel panel = this.filmPanel();

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        LightReplayPicker.open(this.getContext(), panel, this.selection(), (value) ->
        {
            ReplaySelection.Selection selected = ReplaySelection.decode(value);

            this.setValue(ReplaySelection.encode(selected.film(), selected.replays(), ReplaySelection.Mode.SELECTED));
        });
    }
}
