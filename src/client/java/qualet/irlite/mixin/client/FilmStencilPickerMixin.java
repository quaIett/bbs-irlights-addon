package qualet.irlite.mixin.client;

import mchorse.bbs_mod.ui.film.controller.FilmStencilPicker;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.forms.SpotGuideDrag;

/**
 * Drives the film-editor spotlight guide drag once per frame; the click routing stays in
 * {@link UIFilmControllerMixin}. BBS 2.6 moved the picking pass out of
 * {@code UIFilmController#renderPickingPreview} into {@code FilmStencilPicker#renderPreview}.
 *
 * <p>HEAD (not TAIL) on purpose: the pass returns early while flying, and mid-drag the
 * cursor often leaves the handle's own pixels.</p>
 */
@Mixin(FilmStencilPicker.class)
public abstract class FilmStencilPickerMixin
{
    @Shadow @Final private UIFilmController controller;

    @Inject(method = "renderPreview", at = @At("HEAD"))
    private void irlite$updateGuideDrag(UIContext context, Area area, CallbackInfo ci)
    {
        SpotGuideDrag.update(this.controller, context);
    }
}
