package qualet.irlite.client.patcher;

import mchorse.bbs_mod.ui.utils.UIUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import org.qualet.irl.patcher.PatcherHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link PatcherHost} for the BBS addon: the Minecraft game dir, Iris's shaderpacks
 * directory + listing, BBS's {@code UIUtils} folder-open, and the 7 .irlights bundled
 * under {@code assets/irlite/patches/} (embedded from {@code patches/} at build time).
 * Installed at client init via {@code Patcher.install(new BbsPatcherHost())}.
 */
public final class BbsPatcherHost implements PatcherHost
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    private static final List<String> BUNDLED = List.of(
        "bliss.irlights",
        "bsl.irlights",
        "complementaryreimagined.irlights",
        "eclipse.irlights",
        "iterationrp.irlights",
        "photon.irlights",
        "solas.irlights"
    );

    @Override
    public Path gameDir()
    {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path shaderpacksDir()
    {
        try
        {
            return Iris.getShaderpacksDirectory();
        }
        catch (Throwable t)
        {
            LOG.warn("Iris.getShaderpacksDirectory failed: {}", t.toString());
            return null;
        }
    }

    @Override
    public List<String> listShaderpacks()
    {
        try
        {
            return List.copyOf(Iris.getShaderpacksDirectoryManager().enumerate());
        }
        catch (Throwable t)
        {
            LOG.warn("Iris enumerate failed: {}", t.toString());
            return List.of();
        }
    }

    @Override
    public void openFolder(Path dir)
    {
        UIUtils.openFolder(dir.toFile());
    }

    @Override
    public String patchesDirName()
    {
        return "irlite";
    }

    @Override
    public List<String> bundledPatches()
    {
        return BUNDLED;
    }

    @Override
    public InputStream openBundledPatch(String name)
    {
        return BbsPatcherHost.class.getResourceAsStream("/assets/irlite/patches/" + name);
    }
}
