package org.wemppy.irlite.mixin.client;

import mchorse.bbs_mod.l10n.L10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(L10n.class)
public class L10nMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void irlite$registerStrings(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        self.getKey("bbs.config.irlite.title", "IRLite");
        self.getKey("bbs.config.irlite.tooltip", "IRLite light addon settings");

        self.getKey("bbs.config.irlite_patcher.title", "Shader Patcher");
        self.getKey("bbs.config.irlite_patcher.tooltip", "Apply IRLite .irlpatch files onto shaderpacks");

        self.getKey("bbs.config.irlite.show_guides", "Show light guides in world");
        self.getKey("bbs.config.irlite.show_guides-comment", "Draw wireframe gizmos for placed PointLight and Spotlight forms in the world.");

        self.getKey("bbs.config.irlite.shadow_quality", "Shadow quality");
        self.getKey("bbs.config.irlite.shadow_quality-comment", "Resolution of the shadow depth maps. Higher is sharper but uses more VRAM (LOW ~40 MiB ... ULTRA ~2.5 GiB).");

        self.getKey("bbs.config.irlite.shadow_cache", "Cache static shadows");
        self.getKey("bbs.config.irlite.shadow_cache-comment", "Only re-bake shadow maps when lights or occluders move. Big FPS win for static/paused scenes. Turn off if shadows ever look stale.");

        self.getKey("bbs.config.irlite.shadow_blocks", "Block shadows");
        self.getKey("bbs.config.irlite.shadow_blocks-comment", "Cast shadows from world blocks: partial blocks (slabs, stairs, fences) by their real shape, and cutout blocks (leaves, bars, glass doors) without shadowing transparent texels. Heavier until the per-light cache lands.");
    }
}
