package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.ReplayCategory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Appends a "Light" tab ({@code IRLITE_LIGHTS}) to the replay editor's categories.
 *
 * <p>The tab bar, its buttons and the positional shortcuts are built by BBS from
 * {@code ReplayCategory.values()}, so a constant appended to the enum's value array at
 * class load is all it takes. It goes at the current end, whatever that is — another
 * addon's constant keeps its place.</p>
 */
@Mixin(ReplayCategory.class)
public abstract class ReplayCategoryMixin
{
    @Shadow @Final @Mutable
    private static ReplayCategory[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void irlite$appendLightCategory(CallbackInfo ci)
    {
        for (ReplayCategory category : $VALUES)
        {
            if (category.name().equals("IRLITE_LIGHTS"))
            {
                return;
            }
        }

        int ordinal = $VALUES.length;
        ReplayCategory light = ReplayCategoryInvoker.irlite$create("IRLITE_LIGHTS", ordinal, Icons.LIGHT,
            IKey.constant("Light"),
            IKey.constant("Light keyframes: colour, intensity, beam, shadows, the light's own outline and volumetric settings, and its replay lists"));
        ReplayCategory[] expanded = Arrays.copyOf($VALUES, ordinal + 1);

        expanded[ordinal] = light;
        $VALUES = expanded;
    }
}
