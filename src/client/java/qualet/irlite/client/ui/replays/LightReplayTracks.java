package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.ReplayCategory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import qualet.irlite.forms.LightForm;

import java.util.List;

/**
 * The "Light" tab of the replay editor: which tracks land in it.
 *
 * <p>The tab itself is a {@link ReplayCategory} constant appended to BBS's enum at class
 * load ({@code ReplayCategoryMixin}); {@code UIReplaysLightCategoryMixin} routes the
 * light forms' own tracks into it and shows the tab only while the replay has a light
 * somewhere in its form tree.</p>
 */
public final class LightReplayTracks
{
    private static ReplayCategory category;

    private LightReplayTracks()
    {}

    public static ReplayCategory category()
    {
        if (category == null)
        {
            category = ReplayCategory.valueOf("IRLITE_LIGHTS");
        }

        return category;
    }

    /** Whether a timeline row is one of a light form's own tracks. */
    public static boolean owns(UIKeyframeSheet sheet)
    {
        return sheet != null && sheet.property != null
            && FormUtils.getForm(sheet.property) instanceof LightForm light
            && light.isLightTrack(sheet.property);
    }

    /** Whether a catalog entry is one of a light form's own tracks. */
    public static boolean owns(TrackDescriptor track)
    {
        return track.kind() == TrackKind.PROPERTY
            && track.owner() instanceof LightForm light
            && light.isLightTrack(track.property());
    }

    public static boolean hasLight(List<TrackDescriptor> catalog)
    {
        for (TrackDescriptor track : catalog)
        {
            if (owns(track))
            {
                return true;
            }
        }

        return false;
    }
}
