package qualet.irlite.mixin.client;

import mchorse.bbs_mod.forms.FormUtilsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.forms.PointLightFormRenderer;
import qualet.irlite.client.forms.SpotlightFormRenderer;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

@Mixin(FormUtilsClient.class)
public class FormUtilsClientMixin
{
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void irlite$registerRenderers(CallbackInfo ci)
    {
        FormUtilsClient.register(PointLightForm.class, PointLightFormRenderer::new);
        FormUtilsClient.register(SpotlightForm.class, SpotlightFormRenderer::new);
    }
}
