package dev.sablelightoverhaul.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class LaserTestBlockEntity extends BlockEntity {
    public LaserTestBlockEntity(BlockPos pos, BlockState state) {
        super(LightingBlocks.LASER_TEST_ENTITY.get(), pos, state);
    }
}
