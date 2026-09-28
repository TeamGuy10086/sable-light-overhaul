package dev.cbcmuzzlelight.mixin;
import dev.cbcmuzzlelight.client.LightWorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets="com.copycatsplus.copycats.foundation.copycat.model.FilteredBlockAndTintGetter",remap=false)
public abstract class CopycatFilteredLightViewMixin implements LightWorldView {
 @Shadow @Final public BlockAndTintGetter wrapped;
 public BlockPos muzzle$realPos(BlockPos pos){return pos;}
 public BlockAndTintGetter muzzle$wrapped(){return wrapped;}
}
