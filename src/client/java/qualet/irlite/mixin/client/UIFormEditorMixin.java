package qualet.irlite.mixin.client;

import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.ui.forms.editors.forms.UIPointLightForm;
import qualet.irlite.client.ui.forms.editors.forms.UISpotlightForm;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

@Mixin(UIFormEditor.class)
public class UIFormEditorMixin
{
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void irlite$registerUi(CallbackInfo ci)
    {
        UIFormEditor.register(PointLightForm.class, UIPointLightForm::new);
        UIFormEditor.register(SpotlightForm.class, UISpotlightForm::new);
    }
}
