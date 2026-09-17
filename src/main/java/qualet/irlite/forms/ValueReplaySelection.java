package qualet.irlite.forms;

import mchorse.bbs_mod.settings.values.core.ValueString;

/**
 * A replay list as a stepped string track: the film id and the stable ids of the
 * chosen replays, encoded as one JSON document so a keyframe swaps the whole
 * selection atomically. BBS's string channel holds the previous value between
 * keyframes, which is exactly the stepped behaviour a list wants.
 *
 * <p>The client registers its own keyframe editor for these two property names
 * (a multi-select replay picker) — see {@code IrlightsClientAddon}.</p>
 */
public final class ValueReplaySelection extends ValueString
{
    public ValueReplaySelection(String id)
    {
        super(id, "");
    }
}
