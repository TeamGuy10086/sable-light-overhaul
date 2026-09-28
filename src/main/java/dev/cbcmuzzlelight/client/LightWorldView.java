package dev.cbcmuzzlelight.client;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
public interface LightWorldView {
 BlockPos muzzle$realPos(BlockPos pos);
 BlockAndTintGetter muzzle$wrapped();
}
