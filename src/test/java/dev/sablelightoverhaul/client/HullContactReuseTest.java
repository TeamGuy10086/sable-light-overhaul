package dev.sablelightoverhaul.client;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullContactReuseTest {
 @Test void cachedContactMustBeInvalidatedByMotionOrRemovedSupport(){
  var block=new BlockPos(100,60,200);var cube=Shapes.block();
  var hit=cube.clip(new Vec3(100.5,61.3,200.5),new Vec3(100.5,60.7,200.5),block);assertNotNull(hit);
  assertTrue(HullSurfaceAnchor.reusableHit(hit,cube,cube,block,hit.getLocation()));
  assertFalse(HullSurfaceAnchor.reusableHit(hit,cube,Shapes.empty(),block,hit.getLocation()));
  assertFalse(HullSurfaceAnchor.reusableHit(hit,cube,Shapes.box(0,0,0,1,.5,1),block,hit.getLocation()));
  assertFalse(HullSurfaceAnchor.reusableHit(hit,cube,cube,block,hit.getLocation().add(.01,0,0)));
  assertFalse(HullSurfaceAnchor.reusableHit(hit,cube,cube,block.east(),hit.getLocation()));
 }
}
