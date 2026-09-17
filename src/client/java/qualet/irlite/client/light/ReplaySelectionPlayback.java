package qualet.irlite.client.light;

import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import qualet.irlite.forms.ValueReplaySelection;

/** New selection keys start at their own tick; legacy channels retain BBS's extrapolation. */
public final class ReplaySelectionPlayback
{
    private ReplaySelectionPlayback()
    {}

    public static boolean startsAfter(KeyframeChannel<?> channel, float tick)
    {
        Keyframe<?> first = channel.get(0);

        return first != null && tick < first.getTick() && first.getValue() instanceof String value
            && ReplaySelection.decode(value).mode() != ReplaySelection.Mode.LEGACY;
    }

    public static boolean resetBeforeFirst(TrackContext context, TrackId track, KeyframeChannel<?> channel, float tick, float blend)
    {
        if (!(track.subject().equals("light_replays") || track.subject().equals("outline_replays")))
        {
            return false;
        }

        if (!startsAfter(channel, tick))
        {
            return false;
        }

        if (!(FormUtils.getProperty(context.root(), track.toKey()) instanceof ValueReplaySelection property))
        {
            return false;
        }

        if (blend >= 1F)
        {
            property.setRuntimeValue(null);
        }

        return true;
    }
}
