package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;

/**
 * Factory for BBS collapsible {@link UISection} groups.
 *
 * <p>This is the <em>only</em> addon class that references {@link UISection}. It is
 * isolated so the form panels can be verified and loaded on older BBS builds where
 * {@code UISection} is absent: the JVM only links this class the first time one of its
 * methods is invoked, and the panels invoke them exclusively when
 * {@link IrliteBbsCompat#SECTIONS} is {@code true}.</p>
 *
 * <p>All method signatures use {@link UIElement} / {@link String} only — types that
 * exist in every BBS build — so a caller's bytecode never names {@code UISection}.</p>
 */
public final class IrliteFormSections
{
    /** Vertical gap between stacked sections; mirrors {@code UIConstants.SECTION_GAP}. */
    private static final int SECTION_GAP = 3;

    /** A collapsible section titled {@code title} containing {@code fields}. */
    public static UIElement section(String title, UIElement... fields)
    {
        UISection section = new UISection(IKey.constant(title));
        section.fields.add(fields);
        return section;
    }

    /** Like {@link #section} but with a top margin, for the 2nd+ section in a stack. */
    public static UIElement spaced(String title, UIElement... fields)
    {
        return section(title, fields).marginTop(SECTION_GAP);
    }

    private IrliteFormSections()
    {
    }
}
