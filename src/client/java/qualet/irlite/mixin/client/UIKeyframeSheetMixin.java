package qualet.irlite.mixin.client;

import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.ui.replays.LightTrackLayout;

/**
 * Puts a light's tracks into their sections (Light, Beam, Affects...) as the rows are made.
 *
 * <p>A section is a property of the timeline row, not of the catalog entry — BBS sets it the
 * same way for its own curated replay channels — and every row built from a catalog entry
 * comes through this one constructor, so this is the whole of it.</p>
 */
@Mixin(UIKeyframeSheet.class)
public abstract class UIKeyframeSheetMixin
{
    @Shadow public UIKeyframeSheet.Section section;

    @Inject(method = "<init>(Lmchorse/bbs_mod/film/replays/tracks/TrackDescriptor;)V", at = @At("TAIL"))
    private void irlite$lightSection(TrackDescriptor track, CallbackInfo ci)
    {
        UIKeyframeSheet.Section section = LightTrackLayout.sectionFor(track);

        if (section != null)
        {
            this.section = section;
        }
    }
}
