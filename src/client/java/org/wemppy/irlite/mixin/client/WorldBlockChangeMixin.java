package org.wemppy.irlite.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wemppy.irlite.client.light.shadow.BlockShadowCache;
import org.wemppy.irlite.client.light.shadow.ShadowBaker;

/**
 * Keeps block shadows fresh when the world changes. Without this, placing or
 * breaking a slab next to a static lamp wouldn't update its shadow:
 * ShadowBaker.sceneHash() only sums light + occluder positions, so a block
 * edit that moves nothing leaves the hash unchanged (dirty=false) and the
 * depth maps get reused stale.
 *
 * Two coupled actions (Stage B):
 *   - BlockShadowCache.invalidateAt(pos) drops only the cached block lists of
 *     the lamps whose collection sphere covers the edit, so re-collection stays
 *     precise (a far-away edit invalidates nothing and returns false).
 *   - markBlocksDirty() is still needed when something WAS invalidated, because
 *     IRLite's depth-map reuse is gated on the global position-only sceneHash,
 *     which can't see a block edit. invalidateAt only fixes the CPU
 *     re-collection; this triggers the (global) GL re-render. Block edits are
 *     infrequent, so a global rebake on each is fine.
 *
 * Targets the base World method so the hook fires for every code path that
 * writes a block (vanilla placement, server-sync, BBS edits). Gated on
 * world.isClient so the integrated server's World instances (same JVM in
 * singleplayer) don't touch the render-thread state.
 */
@Mixin(World.class)
public class WorldBlockChangeMixin
{
    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void irlite$invalidateBlockShadows(
        BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir)
    {
        World self = (World) (Object) this;
        if (self.isClient && BlockShadowCache.invalidateAt(pos))
        {
            ShadowBaker.markBlocksDirty();
        }
    }
}
