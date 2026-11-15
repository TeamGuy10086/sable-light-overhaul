package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.ServerTakeOver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a registered lamp report no emission from the server's light engine.
 *
 * <p>This is the mechanism that keeps a lamp's level out of the light data the server writes. It is deliberately the
 * only emission hook in the mod: the paths that bypass this method were tried one at a time -- the source scan, its
 * call site, and the positionless method every other path ends in -- and none of them changed what a client draws. The
 * account of those attempts, and of what they ruled out, is in {@code docs/DEVELOPMENT.zh-CN.md} section 6.9.
 */
@Mixin(BlockLightEngine.class)
public abstract class ServerBlockLightMixin {
    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void slo$serverEmission(long pos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        Object world = ((ServerLightEngineAccessor) this).slo$serverChunks().getLevel();
        if (!(world instanceof ServerLevel server)) return;
        if (ServerTakeOver.isLamp(server, pos)) cir.setReturnValue(0);
    }
}