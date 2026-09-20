package qualet.irlite.mixin.client;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.ui.replays.LightTrackLayout;

import java.util.Map;

/**
 * Opens a light's main sections the first time a replay's timeline is shown, the way BBS
 * opens the position/rotation section of its own curated channels.
 *
 * <p>Which tab the light's tracks land in, and whether that tab is shown at all, is BBS's
 * own doing since 2.7 — the category is registered with its registry (see
 * {@code LightReplayTracks}) instead of being bolted onto an enum.</p>
 */
@Mixin(UIReplaysEditor.class)
public abstract class UIReplaysLightFoldsMixin
{
    @Shadow private Replay replay;
    @Shadow @Final private Map<String, FoldState<String>> expandedTracksByReplay;

    @Unique private boolean irlite$newFoldState;

    @Inject(method = "getExpandedTracks", at = @At("HEAD"))
    private void irlite$rememberFreshFoldState(CallbackInfoReturnable<FoldState<String>> cir)
    {
        String replayId = this.replay == null ? "" : this.replay.getId();

        this.irlite$newFoldState = !this.expandedTracksByReplay.containsKey(replayId);
    }

    @Inject(method = "getExpandedTracks", at = @At("RETURN"))
    private void irlite$expandMainLightSections(CallbackInfoReturnable<FoldState<String>> cir)
    {
        if (!this.irlite$newFoldState || this.replay == null)
        {
            return;
        }

        FoldState<String> folds = cir.getReturnValue();

        for (TrackDescriptor track : TrackCatalog.of(this.replay.form.get(), this.replay.properties))
        {
            String section = LightTrackLayout.expandedByDefault(track);

            if (section != null)
            {
                folds.set(section, true);
            }
        }
    }
}
