package dev.sablelightoverhaul.lighting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Optional Sable linkage: reuse its block collision contract, without rigid-body allocation. */
public final class HullDustCollision {
    private HullDustCollision(){}
    public static VoxelShape shape(Level level,BlockPos pos){
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape custom)
            return custom.getSubLevelCollisionShape(level,state);
        return state.getCollisionShape(level,pos,dev.ryanhcode.sable.api.physics.collider.SableCollisionContext.get());
    }
}
