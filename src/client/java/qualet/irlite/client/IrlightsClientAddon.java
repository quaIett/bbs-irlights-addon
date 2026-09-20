package qualet.irlite.client;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.BBSApi;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterKeyframeEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterTrackCategoriesEvent;
import qualet.irlite.client.ui.replays.LightReplayTracks;
import qualet.irlite.client.ui.replays.UIReplaySelectionKeyframeFactory;

/**
 * The client half of the BBS addon ({@code bbs-client-addon} entrypoint): the keyframe
 * editors of the light forms' replay-list tracks, and the timeline's "Light" tab.
 *
 * <p>The subscriber must stay public with exactly one parameter — BBS's EventBus reflects
 * over getDeclaredMethods() and dispatches by exact event class (see IrlightsAddon).</p>
 */
public class IrlightsClientAddon implements BBSAddonMod
{
    @Subscribe
    public void registerKeyframeEditors(RegisterKeyframeEditorsEvent event)
    {
        event.registerProperty("outline_replays", UIReplaySelectionKeyframeFactory::new);
        event.registerProperty("light_replays", UIReplaySelectionKeyframeFactory::new);
    }

    /**
     * The tab the lights' own tracks live in. BBS builds the button, its numeric shortcut and
     * the "only while this part has such tracks" rule from this one registration; it is
     * rejected outright after startup, so it has to happen here rather than when a timeline
     * is first built.
     */
    @Subscribe
    public void registerTrackCategories(RegisterTrackCategoriesEvent event)
    {
        BBSApi.requireVersion("irlite", 2);

        event.register(LightReplayTracks.CATEGORY, LightReplayTracks::owns);
    }
}
