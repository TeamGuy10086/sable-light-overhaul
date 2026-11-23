package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class LaserTestBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<LaserTestBlock> CODEC = MapCodec.unit(LaserTestBlock::new);

    public LaserTestBlock() {
        super(Properties.of().strength(3.5f));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(BlockStateProperties.POWERED, false));
    }

    @Override public MapCodec<LaserTestBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.POWERED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection())
            .setValue(BlockStateProperties.POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                             BlockPos source, boolean moved) {
        updatePower(state, level, pos);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        if (!previous.is(this)) updatePower(state, level, pos);
    }

    private static void updatePower(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(BlockStateProperties.POWERED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserTestBlockEntity(pos, state);
    }
}
