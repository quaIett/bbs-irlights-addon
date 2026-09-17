package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.utils.UI;
import qualet.irlite.client.light.ReplaySelection;
import qualet.irlite.client.ui.replays.LightReplayPicker;
import qualet.irlite.forms.ValueReplaySelection;

import java.util.List;
import java.util.function.Supplier;

/**
 * The form-editor half of a replay list: the "selected replays only" switch and a
 * button that opens the native Orbit/Tracker actor picker on the list's default value (the
 * value the light has when no keyframe of that track is active). The keyframed
 * lists are edited on the replay timeline, in the track's own keyframe editor.
 */
public final class LightReplayWidgets
{
    private final UIElement owner;
    private final Supplier<ValueBoolean> flag;
    private final Supplier<ValueReplaySelection> list;
    private final UIToggle toggle;
    private final UIButton choose;
    private final UIElement summary;
    private final UIElement status;

    public LightReplayWidgets(UIElement owner, String toggleLabel, String buttonLabel, Supplier<ValueBoolean> flag, Supplier<ValueReplaySelection> list)
    {
        this.owner = owner;
        this.flag = flag;
        this.list = list;
        this.toggle = new UIToggle(IKey.constant(toggleLabel), (b) -> flag.get().set(b.getValue()));
        this.toggle.valueBinding(() -> this.toggle.setValue(this.flag.get().get()));
        this.choose = new UIButton(IKey.constant(buttonLabel), (b) -> this.pick());
        this.choose.tooltip(IKey.constant("Click a replay to add or remove it. None clears the list. Changes apply immediately."));
        this.summary = UI.label(() -> "Default list: " + this.describe());
        this.status = UI.label(() -> this.flag.get().get()
            ? "Filter ON: empty list = nobody." : "Filter OFF: list ignored.");
    }

    public UIElement[] elements()
    {
        return new UIElement[] {this.toggle, this.status, this.choose, this.summary};
    }

    public void refresh()
    {
        this.toggle.setValue(this.flag.get().get());
    }

    private UIFilmPanel filmPanel()
    {
        UIContext context = this.owner.getContext();

        if (context == null)
        {
            return null;
        }

        UIFilmPanel panel = this.owner.getParent(UIFilmPanel.class);

        if (panel == null)
        {
            List<UIFilmPanel> panels = context.menu.main.getChildren(UIFilmPanel.class);

            panel = panels.isEmpty() ? null : panels.get(0);
        }

        return panel;
    }

    private String describe()
    {
        UIFilmPanel panel = this.filmPanel();

        return ReplaySelection.describe(ReplaySelection.decode(this.list.get().get()), panel == null ? null : panel.getData());
    }

    private void pick()
    {
        UIFilmPanel panel = this.filmPanel();

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        ReplaySelection.Selection current = ReplaySelection.decode(this.list.get().get());

        LightReplayPicker.open(this.owner.getContext(), panel, current, (value) -> this.list.get().set(value));
    }
}
