package dev.cbcmuzzlelight.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbcmuzzlelight.client.LightWorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
/** Resolve virtual positions before the vanilla AO cache keys them, not only after a cache miss. */
@Mixin(targets="net.minecraft.client.renderer.block.ModelBlockRenderer$Cache")
public abstract class CopycatCacheLightMixin {
 @WrapMethod(method="getLightColor")
 private int muzzle$position(BlockState state,BlockAndTintGetter level,BlockPos pos,Operation<Integer> original){
  for(int depth=0;depth<16 && level instanceof LightWorldView;depth++){
   var view=(LightWorldView)level;pos=view.muzzle$realPos(pos);var next=view.muzzle$wrapped();if(next==level)break;level=next;
  }
  return original.call(state,level,pos);
 }
}
