package qualet.irlite.client.ui.forms.editors.panels;

/**
 * Runtime capability probes for the host BBS build.
 *
 * <p>Some BBS UI classes only exist in newer 2.3.x builds. This addon is compiled
 * against a build that has them, but users may run an older BBS jar that carries the
 * same version string ({@code 2.3.1-1.20.1}) yet lacks the classes, so we must probe
 * at runtime instead of trusting the reported version.</p>
 *
 * <p>This class deliberately holds no direct reference to the probed BBS types (only
 * their names, as strings), so it links and loads on any BBS build.</p>
 */
public final class IrliteBbsCompat
{
    /**
     * Whether {@code mchorse.bbs_mod.ui.framework.elements.UISection} (collapsible form
     * sections) is present. When {@code false}, the light form panels fall back to a
     * flat option list instead of grouped sections. See {@link IrliteFormSections}.
     */
    public static final boolean SECTIONS =
        classExists("mchorse.bbs_mod.ui.framework.elements.UISection");

    private static boolean classExists(String name)
    {
        try
        {
            Class.forName(name, false, IrliteBbsCompat.class.getClassLoader());
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    private IrliteBbsCompat()
    {
    }
}
