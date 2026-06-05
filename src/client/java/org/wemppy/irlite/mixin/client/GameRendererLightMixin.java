package org.wemppy.irlite.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wemppy.irlite.client.light.IRLightRenderState;
import org.wemppy.irlite.client.light.LightBuffer;
import org.wemppy.irlite.client.light.LightCollector;
import org.wemppy.irlite.client.light.LightRegistry;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    private static final Logger IRLITE_LOG = LoggerFactory.getLogger("irlite");
    private static int irlite$lastLoggedCount = -1;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera != null ? camera.getPos() : Vec3d.ZERO;

        // Render-path registrations (live actors / replays) accumulated during the
        // PREVIOUS frame's world render are still in the registry; the scanner adds
        // this frame's ModelBlocks, then we flush all of them to the GPU.
        IRLightRenderState.beginFrame();
        LightCollector.collect(world, cameraPos);
        LightRegistry.flush();

        int count = LightBuffer.getCount();
        if (count != irlite$lastLoggedCount)
        {
            irlite$lastLoggedCount = count;
            IRLITE_LOG.info("Collected {} light(s) into SSBO", count);
        }
    }
}
