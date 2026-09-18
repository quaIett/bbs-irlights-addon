package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import qualet.irlite.forms.LightForm;
import qualet.irlite.forms.SpotlightForm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a light form's tracks look in a replay timeline: readable titles instead of raw
 * property ids, a colour and icon per family, and the tracks folded under collapsible
 * group rows that mirror the sections of the form panel (Light, Beam, Affects,
 * Cookie, and the light's outline and volumetric profile sections).
 *
 * <p>The group rows reuse BBS's own body-part header mechanism: a {@link TrackKind#BODY_PART}
 * descriptor is a header — it names something rather than animating it, holds a throwaway
 * channel that is never saved, folds/unfolds in the dope sheet and is pruned when every
 * track under it is filtered away. The header's key is the light's form path plus a group
 * id, so a light nested as a body part gets its groups folded under the part's own row.</p>
 *
 * <p>Applied by {@code TrackCatalogMixin} to the list {@code TrackCatalog.of} returns, so
 * every consumer of the catalog (replay editor, animation state editor, the per-form
 * track filter) sees the same names and colours. BBS's own form properties (transform,
 * visible, anchor...) are left exactly as BBS lists them.</p>
 */
public final class LightTrackLayout
{
    /** One group row: its key segment, header title and icon. */
    private record Group(String key, String title, int color, Icon icon) {}

    /** One track's look: the group it folds under, its title, colour and icon. */
    private record Style(Group group, String title, int color, Icon icon) {}

    private static final Group LIGHT = new Group("irlights.light", "Light", 0xffd27f, Icons.LIGHT);
    private static final Group BEAM = new Group("irlights.beam", "Beam", 0x44ddee, Icons.FADING);
    private static final Group AFFECTS = new Group("irlights.affects", "Affects", 0xff5fa2, Icons.POINTER);
    private static final Group COOKIE = new Group("irlights.cookie", "Cookie / gobo", 0x8fe066, Icons.IMAGE);
    private static final Group OUTLINE_OWN = new Group("irlights.outline", "Outline", 0xb58cff, Icons.OUTLINE);
    private static final Group BEAM_OWN = new Group("irlights.beam_own", "Volumetric", 0x33ccaa, Icons.SUN);

    /** Group order in a timeline, whatever order the form registers its values in. */
    private static final List<Group> ORDER = List.of(LIGHT, BEAM, AFFECTS, COOKIE, BEAM_OWN, OUTLINE_OWN);

    /** Looks shared by both light forms, keyed by property id. */
    private static final Map<String, Style> COMMON = new LinkedHashMap<>();
    /** Looks that differ or exist only on one form. */
    private static final Map<String, Style> POINT = new HashMap<>();
    private static final Map<String, Style> SPOT = new HashMap<>();

    static
    {
        /* Light: warm oranges */
        COMMON.put("color", new Style(LIGHT, "Color", 0xffd27f, Icons.COLOR));
        COMMON.put("intensity", new Style(LIGHT, "Intensity", 0xffb347, Icons.SUN));
        POINT.put("radius", new Style(LIGHT, "Radius", 0xff9a3c, Icons.SPHERE));
        SPOT.put("range", new Style(LIGHT, "Range", 0xff9a3c, Icons.LINE));
        SPOT.put("radius", new Style(LIGHT, "Cone angle", 0xffc06a, Icons.FRUSTUM));
        SPOT.put("inner_radius", new Style(LIGHT, "Inner cone angle", 0xffcf8a, Icons.FRUSTUM));

        /* Volumetric beam: cyan / teal */
        COMMON.put("beam_strength", new Style(BEAM, "Beam strength", 0x44ddee, Icons.FADING));
        COMMON.put("anisotropy", new Style(BEAM, "Anisotropy", 0x66ccff, Icons.ARC));
        COMMON.put("vl_density", new Style(BEAM, "Density", 0x33ccaa, Icons.DROP));

        /* Shadow controls belong to the main light section. */
        COMMON.put("shadows", new Style(LIGHT, "Shadows", 0x9b6dff, Icons.OUTLINE_SPHERE));
        COMMON.put("bulb_size", new Style(LIGHT, "Softness", 0xb48cff, Icons.CIRCLE));

        /* Affects: pink / amber */
        COMMON.put("entities_only", new Style(AFFECTS, "Entities only", 0xff5fa2, Icons.PLAYER));
        COMMON.put("blocks_only", new Style(AFFECTS, "Blocks only", 0xffaa33, Icons.BLOCK));

        /* Cookie / gobo (spot only): greens */
        SPOT.put("cookie", new Style(COOKIE, "Cookie texture", 0x7ddc5a, Icons.IMAGE));
        SPOT.put("cookie_rotation", new Style(COOKIE, "Cookie rotation", 0x9be877, Icons.ORBIT));
        SPOT.put("cookie_scale", new Style(COOKIE, "Cookie scale", 0x8fe066, Icons.SCALE));
        SPOT.put("cookie_invert", new Style(COOKIE, "Invert cookie", 0xa8f08a, Icons.EXCHANGE));

        /* The light's own outline profile: violets */
        COMMON.put("custom_outline", new Style(OUTLINE_OWN, "Own outline settings", 0x9b6dff, Icons.OUTLINE));
        COMMON.put("outline", new Style(OUTLINE_OWN, "Outline", 0xb58cff, Icons.OUTLINE));
        COMMON.put("outline_target", new Style(OUTLINE_OWN, "Target (all / entities / blocks)", 0xa77dff, Icons.POINTER));
        COMMON.put("outline_strength", new Style(OUTLINE_OWN, "Strength", 0xc29aff, Icons.GRAPH));
        COMMON.put("outline_fresnel", new Style(OUTLINE_OWN, "Fresnel falloff", 0x8f6be8, Icons.ARC));
        COMMON.put("outline_back", new Style(OUTLINE_OWN, "Back rim", 0x7d5bd1, Icons.ARROW_LEFT));
        COMMON.put("outline_front", new Style(OUTLINE_OWN, "Front rim", 0xc7a6ff, Icons.ARROW_RIGHT));
        COMMON.put("outline_front_strength", new Style(OUTLINE_OWN, "Front rim strength", 0xd0b3ff, Icons.ARROW_RIGHT));
        COMMON.put("outline_glow", new Style(OUTLINE_OWN, "Inner glow", 0xd9c2ff, Icons.SUN));
        COMMON.put("outline_glow_strength", new Style(OUTLINE_OWN, "Glow strength", 0xe1d0ff, Icons.SUN));

        /* The light's own beam profile: blue-teals */
        COMMON.put("custom_vl", new Style(BEAM_OWN, "Own volumetric settings", 0x22bbdd, Icons.SUN));
        COMMON.put("vl_enabled", new Style(BEAM_OWN, "Beam", 0x22bbdd, Icons.SUN));
        COMMON.put("vl_intensity", new Style(BEAM_OWN, "Beam intensity", 0x33c4ee, Icons.SUN));
        COMMON.put("vl_max_dist", new Style(BEAM_OWN, "Max distance", 0x44ccee, Icons.LINE));
        COMMON.put("vl_tip_boost", new Style(BEAM_OWN, "Tip glow", 0x55ddee, Icons.SUN));
        COMMON.put("vl_tip_radius", new Style(BEAM_OWN, "Tip radius", 0x55ddee, Icons.CIRCLE));
        COMMON.put("vl_noise", new Style(BEAM_OWN, "Noise", 0x33ccaa, Icons.PARTICLE));
        COMMON.put("vl_noise_amount", new Style(BEAM_OWN, "Noise amount", 0x33ccaa, Icons.PARTICLE));
        COMMON.put("vl_noise_scale", new Style(BEAM_OWN, "Noise scale", 0x3fd6b8, Icons.SCALE));
        COMMON.put("vl_noise_speed", new Style(BEAM_OWN, "Drift speed", 0x4be0c4, Icons.ARROW_RIGHT));
        COMMON.put("vl_noise_morph", new Style(BEAM_OWN, "Noise morph", 0x57e8cc, Icons.REFRESH));
        COMMON.put("vl_shadows", new Style(BEAM_OWN, "Beam shadows", 0x66aadd, Icons.OUTLINE_SPHERE));

        /* Replay lists belong to Affects: amber for lighting, pink for outlines. */
        COMMON.put("selected_light_replays", new Style(AFFECTS, "Light: selected replays only", 0xffaa33, Icons.PLAYER));
        COMMON.put("light_replays", new Style(AFFECTS, "Lit replays", 0xffc266, Icons.PLAYER));
        COMMON.put("selected_replays", new Style(AFFECTS, "Outline: selected replays only", 0xff5fa2, Icons.OUTLINE));
        COMMON.put("outline_replays", new Style(AFFECTS, "Outlined replays", 0xff8fbd, Icons.OUTLINE));
    }

    private LightTrackLayout()
    {}

    public static boolean isLight(Form form)
    {
        return form instanceof LightForm;
    }

    private static Style styleFor(Form owner, String property)
    {
        Map<String, Style> own = owner instanceof SpotlightForm ? SPOT : POINT;
        Style style = own.get(property);

        return style != null ? style : COMMON.get(property);
    }

    /**
     * Rewrite the light forms' property tracks in {@code tracks} (in place): titled, coloured,
     * and folded under freshly inserted group headers. Tracks of other forms, and BBS's own
     * properties of a light form, are passed through untouched.
     */
    public static void decorate(List<TrackDescriptor> tracks)
    {
        tracks.removeIf((track) -> track.kind() == TrackKind.PROPERTY && isLight(track.owner())
            && (track.id().subject().equals("vl_intensity") || track.id().subject().equals("vl_max_dist")));

        boolean any = false;

        for (TrackDescriptor track : tracks)
        {
            if (isLight(track.owner()))
            {
                any = true;

                break;
            }
        }

        if (!any)
        {
            return;
        }

        List<TrackDescriptor> out = new ArrayList<>(tracks.size() + 8);
        Set<TrackId> headers = new HashSet<>();

        for (TrackDescriptor track : sortedByGroup(tracks))
        {
            Style style = track.kind() == TrackKind.PROPERTY && isLight(track.owner())
                ? styleFor(track.owner(), track.id().subject())
                : null;

            if (style == null)
            {
                out.add(track);

                continue;
            }

            String path = track.id().formPath();
            TrackId groupId = new TrackId(TrackKind.BODY_PART, StringUtilsCombine(path, style.group().key()), "", "");

            if (headers.add(groupId))
            {
                /* A nested light's groups hang off the part's own header row; a root light's stand alone. */
                TrackId parent = path.isEmpty() ? null : TrackId.bodyPart(path);

                out.add(new TrackDescriptor(groupId, new KeyframeChannel(groupId.toKey(), KeyframeFactories.FLOAT), track.owner(),
                    IKey.constant(style.group().title()), style.group().icon(), style.group().color(), null, null, parent));
            }

            out.add(new TrackDescriptor(track.id(), track.channel(), track.owner(), IKey.constant(style.title()),
                style.icon(), style.color(), track.property(), track.seed(), groupId));
        }

        tracks.clear();
        tracks.addAll(out);
    }

    /** Main light and beam groups start open the first time a replay timeline is shown. */
    public static boolean expandedByDefault(TrackDescriptor track)
    {
        if (track.kind() != TrackKind.BODY_PART)
        {
            return false;
        }

        String path = track.id().formPath();

        return path.equals(LIGHT.key()) || path.endsWith(FormUtils.PATH_SEPARATOR + LIGHT.key())
            || path.equals(BEAM.key()) || path.endsWith(FormUtils.PATH_SEPARATOR + BEAM.key());
    }

    /** BBS normally paints every header with its primary colour; our synthetic groups keep their descriptor colour. */
    public static boolean hasCustomGroupColor(TrackDescriptor track)
    {
        if (track == null || track.kind() != TrackKind.BODY_PART)
        {
            return false;
        }

        String path = track.id().formPath();

        for (Group group : ORDER)
        {
            if (path.equals(group.key()) || path.endsWith(FormUtils.PATH_SEPARATOR + group.key()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The same tracks with each light's own run of property tracks stably sorted by
     * {@link #ORDER}, so the group rows come out in the panel's order (Light, beam, shadows...)
     * rather than in the order the form happens to register its values. Everything else keeps
     * its place; a run is the consecutive property tracks of one light form.
     */
    private static List<TrackDescriptor> sortedByGroup(List<TrackDescriptor> tracks)
    {
        List<TrackDescriptor> out = new ArrayList<>(tracks.size());
        List<TrackDescriptor> run = new ArrayList<>();
        Form runOwner = null;

        for (TrackDescriptor track : tracks)
        {
            Style style = track.kind() == TrackKind.PROPERTY && isLight(track.owner())
                ? styleFor(track.owner(), track.id().subject())
                : null;

            if (style != null && (run.isEmpty() || track.owner() == runOwner))
            {
                run.add(track);
                runOwner = track.owner();

                continue;
            }

            flushRun(run, out);

            if (style != null)
            {
                run.add(track);
                runOwner = track.owner();
            }
            else
            {
                out.add(track);
            }
        }

        flushRun(run, out);

        return out;
    }

    private static void flushRun(List<TrackDescriptor> run, List<TrackDescriptor> out)
    {
        if (run.isEmpty())
        {
            return;
        }

        run.sort(Comparator.comparingInt((track) -> ORDER.indexOf(styleFor(track.owner(), track.id().subject()).group())));
        out.addAll(run);
        run.clear();
    }

    /** {@code path/segment}, or just {@code segment} for a root form — the same joining rule BBS uses for track paths. */
    private static String StringUtilsCombine(String path, String segment)
    {
        return path.isEmpty() ? segment : path + FormUtils.PATH_SEPARATOR + segment;
    }
}
