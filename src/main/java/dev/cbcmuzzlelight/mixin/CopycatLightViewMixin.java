package dev.cbcmuzzlelight.mixin;
import dev.cbcmuzzlelight.client.LightWorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets="com.copycatsplus.copycats.foundation.copycat.model.ScaledBlockAndTintGetter",remap=false)
public abstract class CopycatLightViewMixin implements LightWorldView {
 @Shadow public abstract BlockPos getTruePos(BlockPos pos);
 @Shadow public abstract BlockAndTintGetter getWrapped();
 public BlockPos muzzle$realPos(BlockPos pos){return getTruePos(pos);}
 public BlockAndTintGetter muzzle$wrapped(){return getWrapped();}
}
