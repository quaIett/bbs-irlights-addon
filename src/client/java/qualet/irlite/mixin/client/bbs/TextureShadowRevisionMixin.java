package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.light.ShadowResourceRevision;
import qualet.irlite.client.light.ShadowResourceVersions;

@Mixin(value = Texture.class, remap = false)
public class TextureShadowRevisionMixin implements ShadowResourceRevision
{
    @Unique private long irlite$revision;

    // The common upload leaf includes updates from the texture painter and file
    // reloads. HEAD also invalidates partially failed writes. Older BBS may lack
    // an optional leaf; the sampler is enabled only on the audited BBS version.
    @Inject(method = {"uploadTexture(IIIILjava/nio/ByteBuffer;)V", "setSize(II)V",
        "setParameter(II)V", "generateMipmap()V", "delete()V"}, at = @At("HEAD"), require = 0)
    private void irlite$changed(CallbackInfo ci) { irlite$revision = ShadowResourceVersions.next(); }

    @Override public long irlite$shadowRevision() { return irlite$revision; }
}
