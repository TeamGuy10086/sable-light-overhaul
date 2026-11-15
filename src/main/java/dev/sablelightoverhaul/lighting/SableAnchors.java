package dev.sablelightoverhaul.lighting;
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
 /**
  * The plot-frame anchor a rendered position belongs to, or null when it belongs to no body.
  *
  * <p>A body is drawn where its pose puts it, not where its plot sits, so a click on a ship hands over a world
  * position its plot does not contain. This undoes the pose and says which body's plot the result lands in -- and
  * nothing else: capture() keeps deciding identity, because turning a *ground* position into a body anchor by
  * the same arithmetic would relabel ground lamps as hull lamps whenever a body is nearby.
  */
 /** One line per body: its pose position, the clicked point in its own frame, and whether that lands in its plot. */
 static String probe(Level level, BlockPos world) {
  var c = SubLevelContainer.getContainer(level);
  if (c == null) return "no container";
  StringBuilder out = new StringBuilder();
  Vec3 centre = Vec3.atCenterOf(world);
  out.append("inBounds=").append(c.inBounds(world));
  for (var s : c.getAllSubLevels()) {
   if (s == null) continue;
   out.append(" | ").append(s.getUniqueId().toString(), 0, 8);
   try {
    var pose = s.logicalPose();
    var p = pose.position();
    var local = BlockPos.containing(pose.transformPositionInverse(centre));
    out.append(" pose=").append(String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", p.x(), p.y(), p.z()));
    out.append(" local=").append(local.getX()).append(',').append(local.getY()).append(',').append(local.getZ());
    out.append(" plot?").append(s.getPlot().contains(new ChunkPos(local)));
    var o = s.getPlot().toGlobal(new ChunkPos(0, 0));
    out.append(" origin=").append(o.getMinBlockX()).append(',').append(o.getMinBlockZ());
   } catch (RuntimeException noPose) {
    out.append(" pose=threw");
   }
  }
  return out.toString();
 }
 /**
  * Every body whose plot contains the clicked point once its pose is undone, nearest body first.
  *
  * <p>One point can be inside *several* plots at once -- each body is asked in its own frame, and the answer is
  * its own coordinate, so they all say yes. What separates them is where they are drawn: the ship the player
  * clicked is the one whose pose stands closest to the click.
  */
 static java.util.List<SourceAnchor> bodyAnchorsAt(Level level, BlockPos world) {
  var c = SubLevelContainer.getContainer(level);
  if (c == null) return java.util.List.of();
  var found = new java.util.ArrayList<SourceAnchor>();
  for (var s : c.getAllSubLevels()) {
   if (s == null || s.isRemoved()) continue;
   try {
    var local = BlockPos.containing(s.logicalPose().transformPositionInverse(Vec3.atCenterOf(world)));
    if (s.getPlot().contains(new ChunkPos(local))) {
     var o = s.getPlot().toGlobal(new ChunkPos(0, 0));
     found.add(new SourceAnchor(s.getUniqueId(), local.offset(-o.getMinBlockX(), 0, -o.getMinBlockZ())));
    }
   } catch (RuntimeException noPose) {
    // A body without a usable pose cannot answer.
   }
  }
  return found;
 }

 /** The distance from a body's own pose to a world point, or a huge number when the body is gone. */
 static double poseDistance(Level level, SourceAnchor a, Vec3 world) {
  var c = SubLevelContainer.getContainer(level);
  if (c == null || a.ship() == null) return Double.MAX_VALUE;
  var s = c.getSubLevel(a.ship());
  if (s == null || s.isRemoved()) return Double.MAX_VALUE;
  try {
   var p = s.logicalPose().position();
   return Math.sqrt((p.x() - world.x) * (p.x() - world.x) + (p.y() - world.y) * (p.y() - world.y) + (p.z() - world.z) * (p.z() - world.z));
  } catch (RuntimeException noPose) {
   return Double.MAX_VALUE;
  }
 }
 static SourceAnchor bodyAnchorAt(Level level,BlockPos world){
  var c=SubLevelContainer.getContainer(level);
  if(c==null)return null;
  for(var s:c.getAllSubLevels()){
   if(s==null||s.isRemoved())continue;
   try{
    var local=BlockPos.containing(s.logicalPose().transformPositionInverse(Vec3.atCenterOf(world)));
    if(s.getPlot().contains(new ChunkPos(local))){
     var o=s.getPlot().toGlobal(new ChunkPos(0,0));
     return new SourceAnchor(s.getUniqueId(),local.offset(-o.getMinBlockX(),0,-o.getMinBlockZ()));
    }
   }catch(RuntimeException noPose){
    // A body without a usable pose cannot answer; the next one might.
   }
  }
  return null;
 }

 static BlockPos resolve(Level level,SourceAnchor a) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());
  if(s==null || s.isRemoved() || s.getPlot().getLoadedChunks().isEmpty()) return null;
  var o=s.getPlot().toGlobal(new ChunkPos(0,0));var pos=a.pos().offset(o.getMinBlockX(),0,o.getMinBlockZ());
  return s.getPlot().getChunk(s.getPlot().toLocal(new ChunkPos(pos)))==null ? null:pos;
 }
 /**
  * A point on a body at this frame, as {x,y,z,qx,qy,qz,qw}, or null when the body is gone.
  *
  * <p>This interpolates the <em>pose</em> and then transforms the point, which is what Sable's renderer does.
  * The obvious alternative -- transform the point with each tick's pose and lerp the two world points -- is not
  * the same thing: a body rotates about its rotation point, so the point's path between two ticks is an arc, and
  * a straight line between its two ends cuts across that arc. The error grows with the point's distance from the
  * pivot and with how far the body turned, and it changes every frame, which on a deck reads as the light pool
  * sliding and flickering while the hull turns.
  */
 static float[] pointAt(Level level,SourceAnchor a,Vec3 local,double partialTick) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());
  if(s==null||s.isRemoved())return null;
  var blend=new dev.ryanhcode.sable.companion.math.Pose3d();
  s.lastPose().lerp(s.logicalPose(),partialTick,blend);
  var world=blend.transformPosition(local);
  if(world==null)return null;
  var q=blend.orientation();
  double length=Math.sqrt(q.x()*q.x()+q.y()*q.y()+q.z()*q.z()+q.w()*q.w());
  if(!(length>1.0E-6))return null;
  double sign=q.w()<0.0?-1.0:1.0;
  return new float[]{(float)world.x,(float)world.y,(float)world.z,
   (float)(sign*q.x()/length),(float)(sign*q.y()/length),(float)(sign*q.z()/length),(float)(sign*q.w()/length)};
 }
 static Vec3 world(Level level,SourceAnchor a,Vec3 point) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());if(s==null)return null;
  var v=s.logicalPose().bakeIntoMatrix(new org.joml.Matrix4d()).transformPosition(new Vector3d(point.x,point.y,point.z));
  return new Vec3(v.x,v.y,v.z);
 }
 /**
  * The body's rotation as x,y,z,w, or null when the body is gone.
  *
  * <p>Not normalised here: a pose is already a rotation, and the shader takes the conjugate for the
  * inverse, so any uniform scale in the matrix would cancel out of a rotation of a direction anyway.
  */
 static float[] rotation(Level level,SourceAnchor a) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());
  if(s==null||s.isRemoved())return null;
  var q=s.logicalPose().bakeIntoMatrix(new org.joml.Matrix4d())
    .getUnnormalizedRotation(new org.joml.Quaternionf());
  return new float[]{q.x,q.y,q.z,q.w};
 }
 /**
  * The same point under the pose the body had at the <em>previous</em> tick.
  *
  * <p>Two poses a tick apart are enough to place something on a hull that is drawn between ticks: a caller
  * that keeps both ends can interpolate by the frame's partial tick and stay glued to the hull. This is done
  * here, from state that is only ever read, instead of asking Sable for its own render pose -- that call can
  * fail halfway through a uniform upload, and losing a uniform that way once already took every light in the
  * frame down with it.
  */
 static Vec3 worldLast(Level level,SourceAnchor a,Vec3 point) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());
  if(s==null||s.isRemoved())return null;
  return s.lastPose().transformPosition(point);
 }
 /** The body's rotation at the previous tick, as x,y,z,w, or null when the body is gone. */
 static float[] rotationLast(Level level,SourceAnchor a) {
  var c=SubLevelContainer.getContainer(level);var s=c==null?null:c.getSubLevel(a.ship());
  if(s==null||s.isRemoved())return null;
  var q=s.lastPose().bakeIntoMatrix(new org.joml.Matrix4d()).getUnnormalizedRotation(new org.joml.Quaternionf());
  return new float[]{q.x,q.y,q.z,q.w};
 }
 /** Whether a body with this id still exists, which is the only thing that means a mounted cannon is gone. */
 public static boolean bodyExists(net.minecraft.world.level.Level level,java.util.UUID ship){
  if(ship==null||!net.neoforged.fml.ModList.get().isLoaded("sable"))return false;
  var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
  if(container==null)return false;
  var body=container.getSubLevel(ship);
  return body!=null&&!body.isRemoved();
 }
 /**
  * The plot's origin block, so a local position can become a real one without asking anything to be loaded.
  *
  * <p>The existing resolve() answers a different question: it returns null both when the body is gone and
  * when the chunks are not loaded, and a caller that treats the two the same would delete a player's settings
  * the moment their ship sailed out of loaded chunks.
  */
 public static net.minecraft.core.BlockPos plotOrigin(net.minecraft.world.level.Level level,java.util.UUID ship){
  if(ship==null||!net.neoforged.fml.ModList.get().isLoaded("sable"))return null;
  var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
  if(container==null)return null;
  var body=container.getSubLevel(ship);
  if(body==null)return null;
  var origin=body.getPlot().toGlobal(new net.minecraft.world.level.ChunkPos(0,0));
  return new net.minecraft.core.BlockPos(origin.getMinBlockX(),0,origin.getMinBlockZ());
 }
 /**
  * The block columns this body's plot owns, asked of the plot one column at a time.
  *
  * <p>The answer lets a light field tell the pocket from the level around it: outside these columns the
  * level's own ground is not the ship's world and must not stop its light.
  *
  * <p>The plot already knows which chunks it holds, so asking it has no frame to get wrong. The first attempt
  * computed a box from the plot's first and last chunk and could produce nothing usable; the flood then ran
  * with exactly the rule this was meant to replace, and nothing anywhere said so, because a null there looked
  * the same as a lamp standing on the ground.
  */
 public static Pocket pocket(net.minecraft.world.level.Level level,java.util.UUID ship){
  if(ship==null||!net.neoforged.fml.ModList.get().isLoaded("sable"))return null;
  var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
  if(container==null)return null;
  var body=container.getSubLevel(ship);
  if(body==null||body.isRemoved())return null;
  var plot=body.getPlot();
  return (x,z)->plot.contains(new net.minecraft.world.level.ChunkPos(x>>4,z>>4));
 }}
