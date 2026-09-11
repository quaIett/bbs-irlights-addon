package qualet.irlite.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL30;
import org.qualet.irl.light.IrlSamplers;
import org.qualet.irl.light.shadow.IRLiteBbsCasterSource;
import org.qualet.irl.light.shadow.ShadowBakeProbe;
import org.qualet.irl.light.shadow.ShadowEngine;
import org.qualet.irl.patcher.Patcher;
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.light.cookie.CookieArray;
import qualet.irlite.client.light.ShadowResourceVersions;
import qualet.irlite.client.patcher.BbsPatcherHost;

public class IrliteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Wire the shared patcher core to BBS (UIUtils + Iris + bundled assets).
        Patcher.install(new BbsPatcherHost());

        // VL profiler HUD: live per-pass GPU ms in the corner. Registered
        // unconditionally now that the settings UI can turn the profiler on
        // mid-session (-Dirlite.profileVl still picks the starting state); every
        // hook below early-returns while it is off. The bake probe partitions
        // the shadow-bake GPU bracket into sibling segments at the core
        // bakeInner seams and feeds the per-window work counters.
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> VlProfiler.renderHud(ctx));
        ShadowEngine.installBakeProbe(new ShadowBakeProbe() {
            @Override
            public boolean detailedTimings() {
                return VlProfiler.detailedTimings();
            }

            @Override
            public void section(String name) {
                VlProfiler.switchPass(name);
            }

            @Override
            public void counter(String key, int amount) {
                VlProfiler.counter(key, amount);
            }
        });

        // Install the BBS Form/Film/Morph shadow caster source + config so the shared
        // irl-core shadow orchestration can reach this mod's per-mod pieces.
        ShadowEngine.install(new IRLiteBbsCasterSource(), IrliteShadowConfig.INSTANCE);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("irlite", "shadow_silhouettes"); }
            @Override public void reload(ResourceManager manager) { ShadowResourceVersions.reloaded(); }
        });

        // Register the per-mod gobo/cookie mask array into the shared sampler registry;
        // rebound from its 2D registration to GL_TEXTURE_2D_ARRAY at bind time.
        IrlSamplers.register("irl_cookieArray", CookieArray::getGlTextureId, GL30.GL_TEXTURE_2D_ARRAY);
    }
}
