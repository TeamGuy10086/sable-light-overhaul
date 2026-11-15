package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.ClientFlashes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightMixin {
    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void slo$emission(long pos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        Object world = ((LightEngineAccessor) this).slo$chunks().getLevel();
        // A registered lamp gives its own light up: the block stops lighting itself and its
        // neighbours, so in the in-pack path the injected colour is all that is left of it.
        if (ClientFlashes.takenOver(world, pos)) cir.setReturnValue(0);
    }
}
