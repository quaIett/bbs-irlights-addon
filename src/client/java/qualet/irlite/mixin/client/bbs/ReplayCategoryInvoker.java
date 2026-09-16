package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.ReplayCategory;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The enum constructor, for {@link ReplayCategoryMixin} to make the "Light" constant with. */
@Mixin(ReplayCategory.class)
public interface ReplayCategoryInvoker
{
    @Invoker("<init>")
    static ReplayCategory irlite$create(String name, int ordinal, Icon icon, IKey label, IKey tooltip)
    {
        throw new AssertionError();
    }
}
