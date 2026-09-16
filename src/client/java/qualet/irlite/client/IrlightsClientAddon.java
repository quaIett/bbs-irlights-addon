package qualet.irlite.client;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterKeyframeEditorsEvent;
import qualet.irlite.client.ui.replays.UIReplaySelectionKeyframeFactory;

/**
 * The client half of the BBS addon ({@code bbs-client-addon} entrypoint): the keyframe
 * editors of the light forms' replay-list tracks. Picked by property name, which is how
 * two string tracks get different controls without a keyframe type of their own.
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
}
