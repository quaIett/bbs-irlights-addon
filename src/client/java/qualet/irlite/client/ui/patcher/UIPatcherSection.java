package qualet.irlite.client.ui.patcher;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.qualet.irl.patcher.IrlPatch;
import org.qualet.irl.patcher.IrlPatchApplier;
import org.qualet.irl.patcher.IrlPatchParser;
import org.qualet.irl.patcher.PatchLibrary;
import org.qualet.irl.patcher.PatchResult;
import org.qualet.irl.patcher.Shaderpacks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The IRLite shader patcher, rendered as controls inside the IRLite settings section. */
public final class UIPatcherSection
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    private static final int OK_COLOR = 0x55FF55;
    private static final int ERR_COLOR = 0xFF5555;
    private static final int META_COLOR = 0xAAAAAA;
    private static final int WARN_COLOR = 0xFFAA33;

    // Selection persists across settings refreshes.
    private static String selectedPack;
    private static Path selectedPatch;
    private static boolean createNew = false;
    private static String status = "Select a shaderpack and a patch for it.";
    private static int statusColor = Colors.WHITE;

    private static UILabel metaLabel;
    private static UILabel statusLabel;
    private static Runnable rebuild;

    private UIPatcherSection()
    {}

    public static void append(UIScrollView options, Runnable rebuildCallback)
    {
        rebuild = rebuildCallback;

        List<String> packs = Shaderpacks.list();
        List<Path> patches = PatchLibrary.list();

        // --- shaderpack list (header row: label + refresh + open folder) ---
        UIIcon refresh = new UIIcon(Icons.REFRESH, (b) ->
        {
            if (rebuild != null)
            {
                rebuild.run();
            }
        });
        refresh.tooltip(IKey.constant("Refresh lists"));

        UIIcon openPacks = new UIIcon(Icons.FOLDER, (b) -> Shaderpacks.openFolder());
        openPacks.tooltip(IKey.constant("Open shaderpacks folder"));

        options.add(headerRow("Shaderpacks", refresh, openPacks));

        UILabelList<String> packList = new UILabelList<>((selected) ->
        {
            if (!selected.isEmpty())
            {
                selectedPack = selected.get(0).value;
                updateMeta(null);
            }
        });
        packList.background();
        packList.h(96);
        for (String pack : packs)
        {
            packList.add(IKey.constant(pack), pack);
        }
        if (selectedPack != null)
        {
            packList.setCurrentValue(selectedPack);
        }
        options.add(packList);

        // --- patch list (header row: label + open folder) ---
        UIIcon openPatches = new UIIcon(Icons.FOLDER, (b) -> PatchLibrary.openFolder());
        openPatches.tooltip(IKey.constant("Open patches folder"));

        options.add(headerRow("Patches", openPatches));

        UILabelList<Path> patchList = new UILabelList<>((selected) ->
        {
            if (!selected.isEmpty())
            {
                selectedPatch = selected.get(0).value;
                updateMeta(packList);
            }
        });
        patchList.background();
        patchList.h(96);
        for (Path patch : patches)
        {
            patchList.add(IKey.constant(patch.getFileName().toString()), patch);
        }
        if (selectedPatch != null)
        {
            patchList.setCurrentValue(selectedPatch);
        }
        options.add(patchList);

        // --- selected-patch meta (which shaderpack it's for + match state) ---
        metaLabel = new UILabel(IKey.constant(""), META_COLOR);
        metaLabel.h(28);
        options.add(metaLabel);
        updateMeta(packList);

        // --- options + primary actions ---
        UIToggle createNewToggle = new UIToggle(IKey.constant("Create new pack each time"), (t) -> createNew = t.getValue());
        createNewToggle.setValue(createNew);
        options.add(createNewToggle);

        UIButton validate = new UIButton(IKey.constant("Validate"), (b) -> runValidate());
        validate.tooltip(IKey.constant("Dry-run: check every op against the selected pack, write nothing"));
        UIButton patch = new UIButton(IKey.constant("Patch"), (b) -> runPatch());
        options.add(UI.row(validate, patch));

        statusLabel = new UILabel(IKey.constant(status), statusColor);
        statusLabel.h(28);
        options.add(statusLabel);
    }

    /** Parses the selected patch for the metadata line; auto-selects a matching pack when none is chosen. */
    private static void updateMeta(UILabelList<String> packList)
    {
        if (metaLabel == null)
        {
            return;
        }
        if (selectedPatch == null)
        {
            setMeta("", META_COLOR);
            return;
        }

        IrlPatch parsed;
        try
        {
            parsed = IrlPatchParser.parse(Files.readString(selectedPatch, StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            LOG.warn("failed to parse patch {}", selectedPatch, e);
            setMeta("Couldn't read this patch.", ERR_COLOR);
            return;
        }

        if (packList != null && selectedPack == null && !parsed.target.isEmpty())
        {
            List<String> matching = new ArrayList<>();
            for (String pack : Shaderpacks.list())
            {
                if (packMatchesTarget(pack, parsed.target))
                {
                    matching.add(pack);
                }
            }
            if (matching.size() == 1)
            {
                selectedPack = matching.get(0);
                packList.setCurrentValue(selectedPack);
            }
        }

        // Friendly, plain-language meta line: which shaderpack the patch is for and
        // whether the selected pack matches. Colour carries the signal (green = matches,
        // amber = different pack, red = unreadable) — no jargon, no op counts.
        boolean hasTarget = !parsed.target.isEmpty();

        if (selectedPack == null)
        {
            // A patch is chosen but no shaderpack yet — point the user at the right one.
            setMeta(hasTarget
                ? "This patch is for the " + parsed.target + " shaderpack. Select it above."
                : "Select a shaderpack above to continue.", META_COLOR);
        }
        else if (hasTarget && !packMatchesTarget(selectedPack, parsed.target))
        {
            setMeta("This patch is for a different shaderpack (" + parsed.target + ").", WARN_COLOR);
        }
        else
        {
            setMeta("This patch is made for the " + (hasTarget ? parsed.target : selectedPack)
                + " shaderpack.", OK_COLOR);
        }
    }

    /** "Photon_v1.2.zip" matches target "Photon": lowercase, alphanumerics only, substring. */
    private static boolean packMatchesTarget(String pack, String target)
    {
        String p = norm(pack);
        String t = norm(target);
        return t.isEmpty() || p.contains(t);
    }

    private static String norm(String s)
    {
        String lower = s.toLowerCase();
        if (lower.endsWith(".zip"))
        {
            lower = lower.substring(0, lower.length() - 4);
        }
        return lower.replaceAll("[^a-z0-9]", "");
    }

    /** Shared head of Validate/Patch: both need a pack, a patch and a successful parse. */
    private static IrlPatch parseSelected()
    {
        if (selectedPack == null)
        {
            setStatus(false, "Select a shaderpack from the list.");
            return null;
        }
        if (selectedPatch == null)
        {
            setStatus(false, "Select a patch for the shaderpack.");
            return null;
        }

        try
        {
            return IrlPatchParser.parse(Files.readString(selectedPatch, StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            LOG.warn("failed to parse patch {}", selectedPatch, e);
            setStatus(false, "Couldn't read the selected patch.");
            return null;
        }
    }

    private static void runValidate()
    {
        IrlPatch parsed = parseSelected();
        if (parsed == null)
        {
            return;
        }

        PatchResult result = IrlPatchApplier.validate(Shaderpacks.packPath(selectedPack), parsed);
        for (String line : result.log)
        {
            LOG.info("[validate] {}", line);
        }
        applyResult(true, result, null);
    }

    private static void runPatch()
    {
        IrlPatch parsed = parseSelected();
        if (parsed == null)
        {
            return;
        }

        String outName = outputName(selectedPack);
        Path source = Shaderpacks.packPath(selectedPack);
        Path output = Shaderpacks.dir().resolve(outName);
        PatchResult result = IrlPatchApplier.apply(source, output, parsed);

        for (String line : result.log)
        {
            LOG.info("[patch] {}", line);
        }
        applyResult(false, result, outName);

        // Re-read lists so a newly created patched pack shows up.
        if (rebuild != null)
        {
            rebuild.run();
        }
    }

    private static String outputName(String packName)
    {
        String base = packName;
        if (base.toLowerCase().endsWith(".zip"))
        {
            base = base.substring(0, base.length() - 4);
        }
        base = base + "_IRLights";

        if (!createNew)
        {
            return base;
        }

        if (!Files.exists(Shaderpacks.dir().resolve(base)))
        {
            return base;
        }
        for (int i = 2; i < 1000; i++)
        {
            String candidate = base + "_" + i;
            if (!Files.exists(Shaderpacks.dir().resolve(candidate)))
            {
                return candidate;
            }
        }
        return base;
    }

    /** Maps the shared irl-core {@link PatchResult} into one friendly status line.
     *  The full per-op detail still goes to the {@code irlite} log; this keeps the
     *  shared engine text untouched (the user never sees the raw English summary). */
    private static void applyResult(boolean validate, PatchResult result, String outputName)
    {
        if (result.ok)
        {
            setStatus(true, validate
                ? "It fits! Press Patch to create the light version of the pack."
                : "Done! Pack \"" + outputName + "\" created. Select it in Iris settings.");
            return;
        }

        String s = result.summary == null ? "" : result.summary.toLowerCase(Locale.ROOT);
        String message;
        if (s.contains("already patched") || s.contains("already exists"))
        {
            message = "This shaderpack already has the light. Pick the original (clean) pack.";
        }
        else if (s.contains("contract"))
        {
            message = "Patch isn't compatible with this mod version. Update the mod or the patch.";
        }
        else if (s.contains("not a folder or .zip") || s.contains("no shaders/"))
        {
            message = "Couldn't open the shaderpack. Make sure a valid pack is selected.";
        }
        else if (s.contains("io error"))
        {
            message = "File error. Close the pack in other programs and try again.";
        }
        else
        {
            message = "This patch didn't fit the selected pack, maybe it's a different version.";
        }
        setStatus(false, message);
    }

    private static void setMeta(String message, int color)
    {
        if (metaLabel != null)
        {
            metaLabel.label = IKey.constant(message);
            metaLabel.color(color, true);
        }
    }

    private static void setStatus(boolean ok, String message)
    {
        status = message;
        statusColor = ok ? OK_COLOR : ERR_COLOR;
        if (statusLabel != null)
        {
            statusLabel.label = IKey.constant(message);
            statusLabel.color(statusColor, true);
        }
    }

    /** A header row with the label flexing on the left and fixed icon buttons on the right (icons vertically centered with the text). */
    private static UIElement headerRow(String text, UIIcon... icons)
    {
        int rowH = 18;

        UIElement row = new UIElement();
        row.row(4).preferred(0);
        row.h(rowH);
        row.marginTop(6);

        // Vertically center the label text so it lines up with the centered icons.
        // anchorY centers within (area.h - fontHeight), so the label must span the
        // full row height — UILabel otherwise auto-sizes to the font height.
        UILabel header = UI.label(IKey.constant(text));
        header.labelAnchor(0F, 0.5F);
        header.h(rowH);
        row.add(header);
        for (UIIcon icon : icons)
        {
            icon.w(20).h(rowH);
            row.add(icon);
        }

        return row;
    }
}
