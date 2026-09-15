package qualet.irlite.mixin.client.iris;

import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
import org.qualet.irl.light.FramePipeline;

/**
 * Hands this frame's gbuffer matrices to the light-cluster grid builder. Iris
 * captures the gbuffer modelView then projection back-to-back, once per frame, at
 * renderLevel HEAD (MixinLevelRenderer: setGbufferModelView then
 * setGbufferProjection — the only caller of either setter in the runtime jar,
 * verified 1.7.2+1.20.4). So at setGbufferProjection TAIL both are this frame's;
 * we forward them to the core, whose frame-consistency guard rebuilds the cluster
 * masks only when this frame's light snapshot is fresh (recorded earlier in
 * renderWorld by FramePipeline.uploadIfPending) and leaves the previous masks
 * untouched otherwise. The modelView captured here is the full one fragments use
 * (it includes view bobbing), which is why the masks must be built from it rather
 * than a hand-rolled camera rotation.
 *
 * <p>Cheap by design: both getters return the stored Matrix4f field with no
 * allocation, and there is no per-frame logging.</p>
 */
@Mixin(value = CapturedRenderingState.class, remap = false)
public class CapturedRenderingStateClusterMixin
{
    /** Iris 1.10.7 hands the matrices out as read-only Matrix4fc; copied into these
     *  render-thread scratches (allocation-free) for the core, which takes Matrix4f. */
    @Unique private static final Matrix4f irlite$modelView = new Matrix4f();
    @Unique private static final Matrix4f irlite$projection = new Matrix4f();

    @Inject(method = "setGbufferProjection", at = @At("TAIL"), remap = false)
    private void irlite$captureGbufferMatrices(CallbackInfo ci)
    {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        FramePipeline.onGbufferMatricesCaptured(irlite$modelView.set(state.getGbufferModelView()), irlite$projection.set(state.getGbufferProjection()));
    }
}
