package dev.cbcmuzzlelight.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbcmuzzlelight.client.AdvancedLights;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Read-only packed-light overlay, including Sable's vanilla mesh path. Never edits the world light engine. */
@Mixin(LevelRenderer.class)
public abstract class AdvancedRenderMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I")
    private static int muzzle$unwrap(BlockAndTintGetter level,BlockState state,BlockPos pos,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Integer> original) {
        for(int depth=0;depth<16 && level instanceof dev.cbcmuzzlelight.client.LightWorldView;depth++) {
            var view=(dev.cbcmuzzlelight.client.LightWorldView)level;pos=view.muzzle$realPos(pos);var next=view.muzzle$wrapped();if(next==level)break;level=next;
        }
        return original.call(level,state,pos);
    }
    @ModifyReturnValue(method="getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",at=@At("RETURN"))
    private static int muzzle$area(int original,BlockAndTintGetter level,BlockState state,BlockPos pos) {
        return AdvancedLights.lightmap(pos,original);
    }
}
