import qualet.irlite.client.patcher.IrlPatch;
import qualet.irlite.client.patcher.IrlPatchApplier;
import qualet.irlite.client.patcher.IrlPatchParser;
import qualet.irlite.client.patcher.PatchResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Applies or validates an .irlights patch against a pack outside Minecraft.
 *   PatchHarness <patch> <sourcePack> <outputPack>   apply
 *   PatchHarness -validate <patch> <sourcePack>      dry-run only
 */
public class PatchHarness {
    public static void main(String[] args) throws Exception {
        boolean validate = args[0].equals("-validate");
        String text = new String(Files.readAllBytes(Paths.get(args[validate ? 1 : 0])), StandardCharsets.UTF_8);
        IrlPatch patch = IrlPatchParser.parse(text);
        PatchResult r = validate
            ? IrlPatchApplier.validate(Paths.get(args[2]), patch)
            : IrlPatchApplier.apply(Paths.get(args[1]), Paths.get(args[2]), patch);
        System.out.println("ok=" + r.ok + " :: " + r.summary);
        for (String l : r.log) System.out.println("  " + l);
        System.exit(r.ok ? 0 : 1);
    }
}
