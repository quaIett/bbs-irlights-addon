package qualet.irlite.mixin;

import mchorse.bbs_mod.BBSMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

@Mixin(BBSMod.class)
public class BBSModMixin
{
    @Inject(method = "onInitialize", at = @At("TAIL"))
    private void irlite$registerForms(CallbackInfo ci)
    {
        BBSMod.getForms().register(PointLightForm.FORM_ID, PointLightForm.class, null);
        BBSMod.getForms().register(SpotlightForm.FORM_ID, SpotlightForm.class, null);
    }
}
