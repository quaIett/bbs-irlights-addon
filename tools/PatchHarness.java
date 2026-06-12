import org.wemppy.irlite.client.patcher.IrlPatch;
import org.wemppy.irlite.client.patcher.IrlPatchApplier;
import org.wemppy.irlite.client.patcher.IrlPatchParser;
import org.wemppy.irlite.client.patcher.PatchResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Applies an .irlpatch to a pristine pack outside Minecraft, for validation. */
public class PatchHarness {
    public static void main(String[] args) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        IrlPatch patch = IrlPatchParser.parse(text);
        PatchResult r = IrlPatchApplier.apply(Paths.get(args[1]), Paths.get(args[2]), patch);
        System.out.println("ok=" + r.ok + " :: " + r.summary);
        for (String l : r.log) System.out.println("  " + l);
        System.exit(r.ok ? 0 : 1);
    }
}
