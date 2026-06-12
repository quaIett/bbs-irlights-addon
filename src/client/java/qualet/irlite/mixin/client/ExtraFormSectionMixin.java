package qualet.irlite.mixin.client;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.sections.ExtraFormSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

@Mixin(ExtraFormSection.class)
public class ExtraFormSectionMixin
{
    @Shadow
    private FormCategory extra;

    @Inject(method = "initiate", at = @At("TAIL"))
    private void irlite$addLightForms(CallbackInfo ci)
    {
        this.extra.addForm(new PointLightForm());
        this.extra.addForm(new SpotlightForm());
    }
}
