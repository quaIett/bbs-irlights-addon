package qualet.irlite.mixin.client;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.ReplayCategory;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.ui.replays.LightReplayTracks;

import java.util.List;
import java.util.Map;

/**
 * Routes the light forms' own tracks into the "Light" tab (see {@link LightReplayTracks})
 * and shows that tab only while the replay's form tree has a light in it — the same
 * treatment BBS gives its IK and physics tabs.
 */
@Mixin(UIReplaysEditor.class)
public abstract class UIReplaysLightCategoryMixin
{
    @Shadow public UIElement iconBar;
    @Shadow public Map<ReplayCategory, UIIcon> tabButtons;
    @Shadow private ReplayCategory category;
    @Shadow private Replay replay;

    @Inject(method = "categoryOf(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet;)Lmchorse/bbs_mod/ui/film/replays/UIReplaysEditor$ReplayCategory;", at = @At("HEAD"), cancellable = true)
    private static void irlite$lightCategory(UIKeyframeSheet sheet, CallbackInfoReturnable<ReplayCategory> cir)
    {
        if (LightReplayTracks.owns(sheet))
        {
            cir.setReturnValue(LightReplayTracks.category());
        }
    }

    /**
     * Right after BBS has decided the IK and physics tabs (and before it filters the
     * sheets by the active tab), decide ours the same way.
     */
    @Inject(method = "updateChannelsList", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/film/replays/UIReplaysEditor;updateTab(Lmchorse/bbs_mod/ui/film/replays/UIReplaysEditor$ReplayCategory;Ljava/util/List;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void irlite$updateLightTab(CallbackInfo ci)
    {
        ReplayCategory light = LightReplayTracks.category();
        UIIcon button = this.tabButtons.get(light);

        if (button == null || this.replay == null)
        {
            return;
        }

        List<TrackDescriptor> catalog = TrackCatalog.of(this.replay.form.get(), this.replay.properties);
        boolean has = LightReplayTracks.hasLight(catalog);
        boolean present = button.getParent() != null;

        if (has != present)
        {
            if (has)
            {
                this.iconBar.add(button);
            }
            else
            {
                button.removeFromParent();
            }

            this.iconBar.resize();
        }

        if (!has && this.category == light)
        {
            this.category = ReplayCategory.FORM;
        }
    }
}
