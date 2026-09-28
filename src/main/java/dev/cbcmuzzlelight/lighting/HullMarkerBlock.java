package dev.cbcmuzzlelight.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hull marker, with a block entity behind it.
 *
 * <p>The block entity is the whole point: Sable calls {@code sable$physicsTick} on a block entity that
 * implements its actor interface once per physics step, and that is the only moment a force applied to
 * a body is not thrown away. Writing an impulse from the moment a gun fires does nothing, because the
 * physics pipeline rebuilds the body from its own state; a force has to land inside the step, which is
 * exactly how the flight-control mods do it.
 */
public class HullMarkerBlock extends MarkerBlock implements EntityBlock {
 public HullMarkerBlock(){super();}
 @Override protected boolean single(){return true;}
 @Override protected MapCodec<? extends Block> codec(){return MarkerBlock.Hull.CODEC;}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new HullMarkerBlockEntity(pos,state);}
}
