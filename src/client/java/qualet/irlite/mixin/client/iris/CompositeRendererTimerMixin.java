package qualet.irlite.mixin.client.iris;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.diag.ProfileCapture;

import java.util.function.Supplier;

/**
 * Dev VL profiler (-Dirlite.profileVl=true): GL_TIME_ELAPSED brackets around
 * every Iris fullscreen pass. All five CompositeRenderer instances (begin,
 * prepare, deferred, composite, shadowcomp) share renderAll, and pass-name
 * prefixes already disambiguate the stage, so one mixin covers them all.
 *
 * <p>The Pass object carries no name at renderAll time, so the name is captured
 * at pipeline construction: createProgram RETURN pairs source.getName() with
 * the built Program, and the renderAll brackets look it up by Program identity.
 * Everything no-ops when the profiler flag is off.</p>
 *
 * <p>require = 0 on the redirect: it is the addon's only instruction-level
 * anchor into an Iris method body — if a future Iris reshapes renderAll it
 * must degrade to an inert profiler, not a mixin-apply crash in normal play
 * (the begin/end guards tolerate unpaired brackets).</p>
 */
@Mixin(value = CompositeRenderer.class, remap = false)
public class CompositeRendererTimerMixin
{
    @Inject(method = "createProgram", at = @At("RETURN"), remap = false)
    private void irlite$recordPassName(ProgramSource source, ImmutableSet<Integer> flipped,
                                       ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
                                       Supplier<?> shadowTargetsSupplier,
                                       CallbackInfoReturnable<Program> cir)
    {
        VlProfiler.registerPassName(cir.getReturnValue(), source.getName());
        if (cir.getReturnValue() != null) ProfileCapture.programSource(source);
    }

    @Redirect(method = "renderAll",
              at = @At(value = "INVOKE",
                       target = "Lnet/irisshaders/iris/gl/program/Program;use()V"),
              require = 0, expect = 1,
              remap = false)
    private void irlite$beginTimedPass(Program program)
    {
        // Iris 1.10.7 (MC 1.21.11) removed FullScreenQuadRenderer.renderQuad() — the quad
        // draws through a GpuBuffer — so there is no per-pass END anchor. Bracket each pass
        // from its program.use() to the NEXT pass's use(); the frame's trailing pass is
        // closed by the bake bracket's VlProfiler.frameTick() next frame.
        VlProfiler.endPass();
        VlProfiler.beginPass(VlProfiler.irisPassName(program));
        program.use();
    }
}
