package org.wemppy.irlite.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(L10n.class)
public class L10nMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void irlite$registerStrings(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        self.getKey("bbs.config.irlite.title", "IRLite");
        self.getKey("bbs.config.irlite.tooltip", "IRLite light addon settings");
        self.getKey("bbs.config.irlite.show_guides", "Show light guides in world");
        self.getKey("bbs.config.irlite.shadow_quality", "Shadow quality");
    }
}
