package dev.cbcmuzzlelight.lighting;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.cbcmuzzlelight.SableRecoil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries out a recoil at the only moment it can work: inside the physics step.
 *
 * <p>Sable hands this entity its own sub-level and rigid body once per step, so a kick registered when
 * the gun fired is applied here, on the body the marker was placed on, without any guessing about
 * which body that is or when it is safe to push it.
 */
public class HullMarkerBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
 public HullMarkerBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.HULL_MARKER_ENTITY.get(),pos,state);}
 @Override public void sable$physicsTick(ServerSubLevel subLevel,RigidBodyHandle handle,double dt){
  SableRecoil.step(subLevel,handle);
 }
}
