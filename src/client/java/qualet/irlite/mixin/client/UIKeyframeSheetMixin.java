package qualet.irlite.mixin.client;

import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.ui.replays.LightTrackLayout;

@Mixin(UIKeyframeSheet.class)
public abstract class UIKeyframeSheetMixin
{
    @Shadow public int color;
    @Shadow @Final public TrackDescriptor descriptor;

    @Inject(method = "getRowColor", at = @At("HEAD"), cancellable = true)
    private void irlite$customLightGroupColor(CallbackInfoReturnable<Integer> cir)
    {
        if (LightTrackLayout.hasCustomGroupColor(this.descriptor))
        {
            cir.setReturnValue(this.color);
        }
    }
}
