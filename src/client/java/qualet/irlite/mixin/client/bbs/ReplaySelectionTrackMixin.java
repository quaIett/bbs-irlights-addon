package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.behaviours.PropertyTrack;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.light.ReplaySelectionPlayback;

@Mixin(PropertyTrack.class)
public class ReplaySelectionTrackMixin
{
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void irlite$selectionStartsAtKeyframe(TrackContext context, TrackId track, KeyframeChannel<?> channel,
        float tick, float blend, CallbackInfo ci)
    {
        if (ReplaySelectionPlayback.resetBeforeFirst(context, track, channel, tick, blend))
        {
            ci.cancel();
        }
    }
}
