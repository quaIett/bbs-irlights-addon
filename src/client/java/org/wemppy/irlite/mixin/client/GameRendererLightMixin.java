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
import org.wemppy.irlite.client.light.LightBuffer;
import org.wemppy.irlite.client.light.LightCollector;

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

        LightBuffer.begin();
        LightCollector.collect(world, cameraPos);
        LightBuffer.upload();

        int count = LightBuffer.getCount();
        if (count != irlite$lastLoggedCount)
        {
            irlite$lastLoggedCount = count;
            IRLITE_LOG.info("Collected {} light(s) into SSBO", count);
        }
    }
}
