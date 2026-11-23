package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelightoverhaul.BlastDamageTracking;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
abstract class BlastDamageTrackingMixin implements BlastDamageTracking {
    @Unique private IntConsumer slo$completion;
    @Unique private int slo$removed;

    @Override
    public void slo$afterBlockDestruction(IntConsumer callback) {
        slo$completion = callback;
    }

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void slo$countDestroyed(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                   BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        original.call(state, level, pos, explosion, drops);
        if (slo$completion != null && !state.isAir()) {
            BlockState remaining = level.getBlockState(pos);
            if (remaining != state && (remaining.isAir() || remaining == state.getFluidState().createLegacyBlock()))
                slo$removed++;
        }
    }

    @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    private void slo$sendResult(boolean particles, CallbackInfo ci) {
        IntConsumer callback = slo$completion;
        if (callback != null) {
            slo$completion = null;
            callback.accept(slo$removed);
        }
    }
}
