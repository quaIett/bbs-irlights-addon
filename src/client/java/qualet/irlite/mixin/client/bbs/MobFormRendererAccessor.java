package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.utils.pose.Pose;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MobFormRenderer.class, remap = false)
public interface MobFormRendererAccessor
{
    @Invoker("ensureEntity") void irlite$ensureEntity();
    @Accessor("entity") Entity irlite$entity();
    @Accessor("currentPose") static void irlite$pose(Pose pose) { throw new AssertionError(); }
    @Accessor("currentPoseOverlay") static void irlite$overlay(Pose pose) { throw new AssertionError(); }
}
