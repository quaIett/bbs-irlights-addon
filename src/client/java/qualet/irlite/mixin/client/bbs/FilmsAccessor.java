package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Films.class)
public interface FilmsAccessor
{
    @Accessor("controllers")
    List<BaseFilmController> irlite$getControllers();
}
