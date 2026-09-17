package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MobFormRenderer.class, remap = false)
public interface MobFormRendererAccessor
{
    @Invoker("ensureEntity") void irlite$ensureEntity();
    @Accessor("entity") Entity irlite$entity();
}
