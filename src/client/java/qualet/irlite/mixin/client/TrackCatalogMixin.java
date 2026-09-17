package qualet.irlite.mixin.client;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.forms.Form;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.ui.replays.LightTrackLayout;

import java.util.List;

/**
 * Gives the light forms' replay tracks their titles, colours, icons and collapsible
 * group rows. The catalog is the single place BBS answers "what tracks does this
 * form have" — the replay editor, the animation state editor and the per-form track
 * filter all read it — so decorating its answer here reaches every timeline at once.
 * See {@link LightTrackLayout}.
 */
@Mixin(TrackCatalog.class)
public class TrackCatalogMixin
{
    @Inject(method = "of(Lmchorse/bbs_mod/forms/forms/Form;Lmchorse/bbs_mod/film/replays/FormProperties;)Ljava/util/List;", at = @At("RETURN"))
    private static void irlite$decorateLightTracks(Form root, FormProperties properties, CallbackInfoReturnable<List<TrackDescriptor>> cir)
    {
        LightTrackLayout.decorate(cir.getReturnValue());
    }
}
