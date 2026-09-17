package qualet.irlite.mixin.client.iris;

import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.light.ReplayOutlineContext;

/**
 * {@code uniform int irlite_replayId}: the tag of the replay being drawn (0 = none), for
 * the patched gbuffers to write into the replay attachment and for the forward surface
 * pass to read directly. Dynamic, so it is re-uploaded mid-pass whenever the tag changes.
 */
@Mixin(value = CommonUniforms.class, remap = false)
public class ReplayUniformMixin
{
    @Inject(method = "addDynamicUniforms", at = @At("TAIL"))
    private static void irlite$replayUniform(DynamicUniformHolder uniforms, FogMode fog, CallbackInfo ci)
    {
        uniforms.uniform1i("irlite_replayId", ReplayOutlineContext::currentId, ReplayOutlineContext.NOTIFIER);
    }
}
