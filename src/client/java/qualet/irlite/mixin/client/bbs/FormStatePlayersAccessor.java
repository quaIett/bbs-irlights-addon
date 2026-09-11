package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.forms.Form;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(value = Form.class, remap = false)
public interface FormStatePlayersAccessor
{
    @Accessor("statePlayers") List<?> irlite$statePlayers();
}
