package qualet.irlite.client.light;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.mixin.client.bbs.FilmsAccessor;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which replay is being drawn right now, as a small integer the shader can read.
 *
 * <p>Both replay filters (who a light lights, who it outlines) need the shader to know,
 * per pixel, which replay put that pixel there. This class hands out one token per
 * (film, replay) — stable for as long as the world lives, so a replay keeps its number
 * from frame to frame — tags every film entity with its token at the start of the frame,
 * and while a form renders exposes the token as the Iris uniform {@code irlite_replayId}.
 * The patched gbuffers write it (with depth) into an extra colour attachment for the
 * deferred outline; the forward surface pass reads the uniform directly.</p>
 *
 * <p>Nested body parts render with their actor's entity and so inherit its tag. Vanilla
 * batches entity geometry, so the batch is flushed at every tag change — otherwise a
 * form's triangles would be drawn later, under whatever tag was current then.</p>
 *
 * <p>Zero means "no replay": UI previews, picking, the Iris shadow pass and the mod's
 * own shadow bake all render untagged, and a bare light form is never tagged (it cannot
 * be a target — see {@link ReplaySelection#isSelectable}).</p>
 */
public final class ReplayOutlineContext
{
    /** Entities drawn this frame -> their token. Identity: an IEntity is not a value. */
    private static final IdentityHashMap<IEntity, Integer> ENTITIES = new IdentityHashMap<>();
    /** Tokens ever handed out in this world: film id -> replay id -> token. */
    private static final Map<String, Map<String, Integer>> TOKENS = new HashMap<>();
    /** Tokens of the replays that actually have an entity this frame. */
    private static final Map<String, Map<String, Integer>> ACTIVE = new HashMap<>();

    /** Tokens ride a float render target, so they must stay exactly representable. */
    private static final int MAX_TOKEN = 1 << 24;

    private static Object world;
    private static int nextId = 1;
    private static int currentId;
    private static Runnable listener;

    /** Lets Iris re-upload the uniform only when the tag actually changes. */
    public static final ValueUpdateNotifier NOTIFIER = (value) -> listener = value;

    private ReplayOutlineContext()
    {}

    public static int currentId()
    {
        return currentId;
    }

    /** The token of {@code replay} in {@code film} this frame, or 0 when it has no entity. */
    public static int resolve(String film, String replay)
    {
        Map<String, Integer> ids = ACTIVE.get(film);

        return ids == null ? 0 : ids.getOrDefault(replay, 0);
    }

    /** Once per frame, before any form renders: re-tag the film entities. */
    public static void beginFrame()
    {
        Object now = MinecraftClient.getInstance().world;

        if (world != now)
        {
            world = now;
            TOKENS.clear();
            nextId = 1;
        }

        ENTITIES.clear();
        ACTIVE.clear();
        set(0);

        if (now == null)
        {
            return;
        }

        Films films = BBSModClient.getFilms();

        if (films != null)
        {
            for (BaseFilmController controller : ((FilmsAccessor) (Object) films).irlite$getControllers())
            {
                collect(controller);
            }
        }

        collect(LightCollector.getActiveEditorController());
    }

    private static void collect(BaseFilmController controller)
    {
        if (controller == null || controller.film == null)
        {
            return;
        }

        String film = controller.film.getId();
        Map<String, IEntity> entities = controller.getEntities();
        Map<String, Integer> ids = TOKENS.computeIfAbsent(film, (k) -> new HashMap<>());
        Map<String, Integer> active = ACTIVE.computeIfAbsent(film, (k) -> new HashMap<>());

        for (Replay replay : controller.film.replays.getList())
        {
            IEntity entity = entities.get(replay.getId());

            if (entity == null || !replay.enabled.get())
            {
                continue;
            }

            if (!ReplaySelection.isSelectable(replay.form.get()) || !ReplaySelection.isSelectable(entity.getForm()))
            {
                continue;
            }

            int token = ids.computeIfAbsent(replay.getId(), (k) -> nextId++);

            if (token >= MAX_TOKEN)
            {
                throw new IllegalStateException("IRLights: replay tag precision exhausted (" + token + ")");
            }

            ENTITIES.put(entity, token);
            active.put(replay.getId(), token);
        }
    }

    private static void set(int id)
    {
        if (id == currentId)
        {
            return;
        }

        currentId = id;

        if (listener != null)
        {
            listener.run();
        }
    }

    /**
     * Render {@code renderer} under the tag of the context's entity. Called in place of
     * {@code FormRenderer.render} by {@code FormUtilsClientMixin}.
     */
    public static void render(FormRenderer<?> renderer, FormRenderingContext context)
    {
        int previous = currentId;
        boolean untagged = context.ui || context.isPicking() || BBSRendering.isIrisShadowPass() || ShadowBakeState.isBaking();
        int id = untagged ? 0 : ENTITIES.getOrDefault(context.entity, 0);

        if (id == previous)
        {
            renderer.render(context);

            return;
        }

        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        consumers.draw();
        set(id);

        try
        {
            renderer.render(context);
        }
        finally
        {
            try
            {
                consumers.draw();
            }
            finally
            {
                set(previous);
            }
        }
    }
}
