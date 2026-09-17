package qualet.irlite.client.ui.debug;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import qualet.irlite.client.diag.VlProfiler;

/** Debug controls rendered at the bottom of the IRLights presets section. */
public final class UIDebugSection
{
    /** Debug UI is hidden by default. Opt back in with {@code -Dirlite.debug=true}
     *  — the profiler itself and its {@code -Dirlite.profileVl} boot switch are
     *  untouched; this gate only decides whether the settings button is surfaced.
     *  Kept (not deleted) so the section returns with a single flag. */
    private static final boolean DEBUG_UI = Boolean.getBoolean("irlite.debug");

    private UIDebugSection()
    {}

    public static void append(UIScrollView options, Runnable rebuild)
    {
        if (!DEBUG_UI)
        {
            return;
        }

        UILabel header = UI.label(IKey.constant("Debug"));
        header.marginTop(6);
        options.add(header);

        boolean on = VlProfiler.isEnabledOrPending();
        UIButton button = new UIButton(IKey.constant(on
            ? "Hide performance overlay"
            : "Show performance overlay"), (b) ->
        {
            VlProfiler.toggle();

            if (rebuild != null)
            {
                rebuild.run();
            }
        });

        button.tooltip(IKey.constant("Per-pass GPU milliseconds in the top-left corner: the shadow "
            + "bake segments, every Iris fullscreen pass and the VL march, plus CPU frame time and "
            + "VRAM residency. Costs a GL timer query per pass, so leave it off for recording. "
            + "Takes effect on the next frame."));
        options.add(button);
    }
}
