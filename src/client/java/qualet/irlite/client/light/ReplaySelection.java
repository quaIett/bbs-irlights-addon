package qualet.irlite.client.light;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import qualet.irlite.forms.LightForm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The replay list a light's outline/light linking is restricted to, and how it is
 * written into a string track.
 *
 * <p>A selection names its film and the <em>stable ids</em> of the chosen replays —
 * the same reference BBS 2.6's own anchor ("orbit") picker hands out — so it survives
 * replays being reordered or removed around it. The picker still shows list positions,
 * because that is how an animator counts actors.</p>
 *
 * <p>Wire format: {@code {"film":"<id>","replays":["<id>",...],"mode":"selected"}}.
 * New keys use selected (automatic activation) or inherit (the form's saved settings).
 * A missing mode preserves the old form-switch semantics. Anything unparsable
 * decodes to an empty selection of no film, which "enabled + empty = nobody" then
 * renders as: the light touches no replay at all rather than everyone.</p>
 */
public final class ReplaySelection
{
    /** Missing mode is the original format, whose switches still live on the form. */
    public enum Mode { LEGACY, SELECTED, INHERIT }

    public record Selection(String film, Set<String> replays, Mode mode)
    {
        public static final Selection EMPTY = new Selection("", Set.of(), Mode.LEGACY);

        public boolean isEmpty()
        {
            return this.replays.isEmpty();
        }

        /** Whether this selection was made in {@code film} (a list from another film matches nobody). */
        public boolean belongsTo(Film film)
        {
            return film != null && this.film.equals(film.getId());
        }
    }

    private ReplaySelection()
    {}

    /**
     * Only replays that are not themselves a bare light can be lit or outlined. A model
     * with lights hung off its body parts stays selectable (the lights are not what is
     * drawn), and the runtime tagging applies the same rule so a stale list cannot target
     * a light either.
     */
    public static boolean isSelectable(Form root)
    {
        return !(root instanceof LightForm);
    }

    public static Selection decode(String value)
    {
        if (value == null || value.isBlank())
        {
            return Selection.EMPTY;
        }

        try
        {
            JsonObject json = JsonParser.parseString(value).getAsJsonObject();
            Set<String> replays = new LinkedHashSet<>();
            JsonElement list = json.get("replays");

            if (list != null && list.isJsonArray())
            {
                for (JsonElement item : list.getAsJsonArray())
                {
                    String id = item.getAsString();

                    if (!id.isEmpty())
                    {
                        replays.add(id);
                    }
                }
            }

            JsonElement film = json.get("film");

            JsonElement mode = json.get("mode");
            Mode decodedMode = mode == null ? Mode.LEGACY
                : "inherit".equals(mode.getAsString()) ? Mode.INHERIT
                : "selected".equals(mode.getAsString()) ? Mode.SELECTED : Mode.LEGACY;

            return new Selection(film == null ? "" : film.getAsString(), replays, decodedMode);
        }
        catch (RuntimeException e)
        {
            /* Malformed or hand-edited: fail closed (see the class comment). */
            return Selection.EMPTY;
        }
    }

    public static String encode(String film, Collection<String> replays)
    {
        return encode(film, replays, Mode.LEGACY);
    }

    public static String encode(String film, Collection<String> replays, Mode mode)
    {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();

        for (String id : new LinkedHashSet<>(replays))
        {
            if (!id.isEmpty())
            {
                array.add(id);
            }
        }

        json.addProperty("film", film == null ? "" : film);
        json.add("replays", array);

        if (mode != Mode.LEGACY)
        {
            json.addProperty("mode", mode == Mode.INHERIT ? "inherit" : "selected");
        }

        return json.toString();
    }

    /** A short human summary for labels: the list positions of the chosen replays in {@code film}. */
    public static String describe(Selection selection, Film film)
    {
        if (selection.isEmpty())
        {
            return "nobody";
        }

        if (!selection.belongsTo(film))
        {
            return selection.replays().size() + " replay(s) of another film";
        }

        List<Replay> replays = film.replays.getList();
        StringBuilder out = new StringBuilder();
        int missing = 0;

        for (String id : selection.replays())
        {
            int index = -1;

            for (int i = 0; i < replays.size(); i++)
            {
                if (replays.get(i).getId().equals(id))
                {
                    index = i;

                    break;
                }
            }

            if (index < 0)
            {
                missing++;

                continue;
            }

            if (out.length() > 0)
            {
                out.append(", ");
            }

            out.append(index);
        }

        if (missing > 0)
        {
            out.append(out.length() > 0 ? " (+" : "(").append(missing).append(" removed)");
        }

        return out.toString();
    }
}
