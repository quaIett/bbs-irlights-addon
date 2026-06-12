package qualet.irlite.client.patcher;

import mchorse.bbs_mod.ui.utils.UIUtils;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** The folder where users drop .irlpatch files: {@code <gameDir>/irlite/patches}. */
public final class PatchLibrary
{
    public static final String EXTENSION = ".irlpatch";

    private PatchLibrary()
    {}

    public static Path dir()
    {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("irlite").resolve("patches");
        try
        {
            Files.createDirectories(dir);
        }
        catch (IOException ignored)
        {}
        return dir;
    }

    public static List<Path> list()
    {
        List<Path> patches = new ArrayList<>();
        Path dir = dir();

        try (Stream<Path> stream = Files.list(dir))
        {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(EXTENSION))
                .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                .forEach(patches::add);
        }
        catch (IOException ignored)
        {}

        return patches;
    }

    public static void openFolder()
    {
        UIUtils.openFolder(dir().toFile());
    }
}
