package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import qualet.irlite.forms.LightForm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Light" tab of the replay editor: which tracks land in it.
 *
 * <p>The tab is a {@link TrackCategory} registered with BBS's own registry during
 * {@code RegisterTrackCategoriesEvent} (see {@code IrlightsClientAddon}) — BBS builds the
 * button, the numeric shortcut and the "show the tab only while the part has such tracks"
 * rule from that registration. It used to be a constant appended to BBS's
 * {@code ReplayCategory} enum at class load; the enum is gone in BBS 2.7.</p>
 *
 * <p>BBS asks "whose tab is this track?" with a {@link TrackId} and nothing else, and a
 * light's properties are spelled the way they always were ({@code color},
 * {@code intensity}...), so the address alone cannot answer it. The catalog can: it knows
 * the owning form. {@link LightTrackLayout#decorate(List)} runs on every catalog BBS
 * builds and leaves the light tracks it found here, and the classifier reads that. The
 * catalog of a timeline is always built immediately before its tracks are sorted into
 * tabs, so what is recorded here is the answer for the tracks being asked about.</p>
 */
public final class LightReplayTracks
{
    public static final TrackCategory CATEGORY = new TrackCategory("irlights:light", Icons.LIGHT,
        IKey.constant("Light"),
        IKey.constant("Light keyframes: colour, intensity, beam, shadows, the light's own outline and volumetric settings, and its replay lists"));

    /** Addresses of the light tracks in the catalog BBS built last. Rebuilt whole, never appended to. */
    private static final Set<TrackId> TRACKS = new HashSet<>();

    private LightReplayTracks()
    {}

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

    /** What BBS's category registry asks: is this address one of the lights' own tracks? */
    public static boolean owns(TrackId track, boolean owned)
    {
        return owned && track != null && TRACKS.contains(track);
    }

    /** Called by {@link LightTrackLayout#decorate(List)} with the light tracks of the catalog it just walked. */
    static void remember(List<TrackId> tracks)
    {
        TRACKS.clear();
        TRACKS.addAll(tracks);
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
