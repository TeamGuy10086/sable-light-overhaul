package dev.cbcmuzzlelight.lighting;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
final class SableAnchors {
 static SourceAnchor capture(Level level,BlockPos pos) {
  var c=SubLevelContainer.getContainer(level);
  if(c!=null && c.inBounds(pos)) for(var s:c.getAllSubLevels()) if(s.getPlot().contains(new ChunkPos(pos))) {
   var o=s.getPlot().toGlobal(new ChunkPos(0,0));
   return new SourceAnchor(s.getUniqueId(),pos.offset(-o.getMinBlockX(),0,-o.getMinBlockZ()));
  }
  return new SourceAnchor(null,pos.immutable());
 }
 static BlockPos resolve(Level level,SourceAnchor a) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());
  if(s==null || s.isRemoved() || s.getPlot().getLoadedChunks().isEmpty()) return null;
  var o=s.getPlot().toGlobal(new ChunkPos(0,0));var pos=a.pos().offset(o.getMinBlockX(),0,o.getMinBlockZ());
  return s.getPlot().getChunk(s.getPlot().toLocal(new ChunkPos(pos)))==null ? null:pos;
 }
 static Vec3 world(Level level,SourceAnchor a,Vec3 point) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());if(s==null)return null;
  var v=s.logicalPose().bakeIntoMatrix(new org.joml.Matrix4d()).transformPosition(new Vector3d(point.x,point.y,point.z));
  return new Vec3(v.x,v.y,v.z);
 }
}
