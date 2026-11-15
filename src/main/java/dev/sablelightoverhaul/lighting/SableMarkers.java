package dev.sablelightoverhaul.lighting;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Reaches the markers inside Sable physics bodies, and answers where a body's box is in the world.
 *
 * <p>Loaded separately and only called when Sable is present, the same way the generator search is.
 *
 * <p><b>The space a body reports its bounds in is decided, not assumed.</b> Sable's own bounds may be
 * in the space its storage lives in or in the space the body is drawn in, and getting that wrong
 * silently puts dust somewhere nobody can see. So both readings are taken and the one
 * that puts a body next to the muzzle wins: the muzzle is world space by definition, and the body
 * firing it is at most a few blocks away, so the closest reading is the right one. The decision is
 * logged while diagnosing, because this is exactly the kind of thing that used to be invisible.
 */
public final class SableMarkers {
 private SableMarkers(){}
 /** The body a dust marker names, or null when none is placed. */
 public static ServerSubLevel markedDustBody(ServerLevel level,Vec3 near){
  return marked(level,near,MarkerBlock.Dust.class);
 }
 /** The body nearest the point, which for a muzzle is the one carrying the cannon. */
 public static ServerSubLevel nearestBody(ServerLevel level,Vec3 near){
  var container=SubLevelContainer.getContainer(level);
  if(container==null)return null;
  ServerSubLevel best=null;
  double bestDistance=Double.MAX_VALUE;
  for(SubLevel candidate:container.getAllSubLevels()){
   if(!(candidate instanceof ServerSubLevel server)||server.isRemoved())continue;
   double distance=distanceSquared(boxNear(level,server,near),near);
   if(distance<bestDistance){bestDistance=distance;best=server;}
  }
  return best;
 }
 private static ServerSubLevel marked(ServerLevel level,Vec3 near,Class<?> kind){
  // The biggest marked body wins, not the nearest one: a turret is always smaller than the vehicle
  // it sits on. Every body seen and every marker found is reported, because a marker that is never
  // found is indistinguishable from a body that was never enumerated, and this tells them apart.
  var container=SubLevelContainer.getContainer(level);
  if(container==null)return null;
  ServerSubLevel best=null;
  double bestVolume=0.0;
  int seen=0,markedCount=0;
  var report=new StringBuilder();
  for(SubLevel candidate:container.getAllSubLevels()){
   if(!(candidate instanceof ServerSubLevel server)||server.isRemoved())continue;
   seen++;
   int markers=markers(server,kind);
   if(markers<=0)continue;
   markedCount++;
   double volume=volume(boxNear(level,server,near));
   report.append((char)32).append(server.getUniqueId().toString(),0,8).append(":vol").append(Math.round(volume)).append(":markers").append(markers);
   if(best==null||volume>bestVolume){bestVolume=volume;best=server;}
  }
  long now=System.currentTimeMillis();
  if(LightingCommon.on(LightingCommon.GEN_DEBUG)&&seen>0&&now-lastMarkerLog>2000L){
   lastMarkerLog=now;
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul markers: seen={} marked={} chosen={} detail={}",
     seen,markedCount,best==null?"none":best.getUniqueId(),report);
  }
  return best;
 }
 private static long lastMarkerLog;
 /** How many marker blocks of this kind a body holds; markers are plain blocks, so palettes are asked. */
 private static int markers(ServerSubLevel body,Class<?> kind){
  int count=0;
  for(var holder:body.getPlot().getLoadedChunks()){
   var chunk=holder.getChunk();
   if(chunk==null)continue;
   for(var section:chunk.getSections()){
    if(section==null||section.hasOnlyAir())continue;
    if(!section.maybeHas(state->kind.isInstance(state.getBlock())))continue;
    // A palette keeps listing a block that is no longer placed, so the section is asked for the block
    // itself: without this a broken marker still counted, and the cluster still refused a new one.
    boolean found=false;
    for(int y=0;y<16&&!found;y++){
     for(int z=0;z<16&&!found;z++){
      for(int x=0;x<16&&!found;x++){
       if(kind.isInstance(section.getBlockState(x,y,z).getBlock()))found=true;
      }
     }
    }
    if(found)count++;
   }
  }
  return count;
 }
 private static long lastSpaceLog;
 /** One space line every two seconds is enough: it used to print once per call. */
 private static boolean spaceLogDue(){
  long now=System.currentTimeMillis();
  if(now-lastSpaceLog<2000L)return false;
  lastSpaceLog=now;
  return true;
 }
 /** The box exactly as Sable reports it. */
 public static double[] rawBox(ServerSubLevel body){
  var box=body.boundingBox();
  return new double[]{box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()};
 }
 /** The box with its eight corners carried through the muzzle effects' transform. */
 public static double[] turnedBox(ServerLevel level,ServerSubLevel body){
  var box=body.boundingBox();
  double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,minZ=Double.MAX_VALUE;
  double maxX=-Double.MAX_VALUE,maxY=-Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;
  for(int corner=0;corner<8;corner++){
   Vec3 plot=new Vec3((corner&1)==0?box.minX():box.maxX(),(corner&2)==0?box.minY():box.maxY(),(corner&4)==0?box.minZ():box.maxZ());
   Vec3 world=rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,plot);
   if(world==null)continue;
   minX=Math.min(minX,world.x);minY=Math.min(minY,world.y);minZ=Math.min(minZ,world.z);
   maxX=Math.max(maxX,world.x);maxY=Math.max(maxY,world.y);maxZ=Math.max(maxZ,world.z);
  }
  return new double[]{minX,minY,minZ,maxX,maxY,maxZ};
 }
 /** A body's box in world space, whichever reading of it is the world one. */
 public static double[] boxNear(ServerLevel level,ServerSubLevel body,Vec3 near){
  return useTransformed(level,near)?turnedBox(level,body):rawBox(body);
 }
 /** True when the bodies' own bounds are the ones next to {@code near}; logged while diagnosing. */
 public static boolean useTransformed(ServerLevel level,Vec3 near){
  var container=SubLevelContainer.getContainer(level);
  if(container==null)return true;
  double raw=Double.MAX_VALUE,turned=Double.MAX_VALUE;
  int bodies=0;
  for(SubLevel candidate:container.getAllSubLevels()){
   if(!(candidate instanceof ServerSubLevel server)||server.isRemoved())continue;
   bodies++;
   raw=Math.min(raw,distanceSquared(rawBox(server),near));
   turned=Math.min(turned,distanceSquared(turnedBox(level,server),near));
  }
  boolean transform=turned<=raw;
  if(LightingCommon.on(LightingCommon.GEN_DEBUG)&&bodies>0&&spaceLogDue()){
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul sable space: bodies={} nearestRaw={} nearestTurned={} using={} near={}",
     bodies,String.format(java.util.Locale.ROOT,"%.1f",Math.sqrt(raw)),String.format(java.util.Locale.ROOT,"%.1f",Math.sqrt(turned)),
     transform?"transformed":"raw",near);
  }
  return transform;
 }
 public static boolean holds(double[] box,Vec3 point){
  return box[0]<=box[3]&&box[1]<=box[4]&&box[2]<=box[5]
    &&point.x>=box[0]&&point.x<=box[3]&&point.y>=box[1]&&point.y<=box[4]&&point.z>=box[2]&&point.z<=box[5];
 }
 public static double distanceSquared(double[] box,Vec3 point){
  if(box[0]>box[3])return Double.MAX_VALUE;
  if(holds(box,point))return 0.0;
  double dx=Math.max(Math.max(box[0]-point.x,0.0),point.x-box[3]);
  double dy=Math.max(Math.max(box[1]-point.y,0.0),point.y-box[4]);
  double dz=Math.max(Math.max(box[2]-point.z,0.0),point.z-box[5]);
  return dx*dx+dy*dy+dz*dz;
 }
 public static double volume(double[] box){
  if(box[0]>box[3])return 0.0;
  return Math.max(0.0,box[3]-box[0])*Math.max(0.0,box[4]-box[1])*Math.max(0.0,box[5]-box[2]);
 }
 /** Every body in the cluster the point falls in, or the nearest one's cluster when it falls in none. */
 public static List<ServerSubLevel> cluster(ServerLevel level,Vec3 near){
  var container=SubLevelContainer.getContainer(level);
  if(container==null)return List.of();
  ServerSubLevel self=null;
  double bestDistance=Double.MAX_VALUE;
  for(SubLevel candidate:container.getAllSubLevels()){
   if(!(candidate instanceof ServerSubLevel server)||server.isRemoved())continue;
   double distance=distanceSquared(boxNear(level,server,near),near);
   if(distance<bestDistance){bestDistance=distance;self=server;}
  }
  if(self==null)return List.of();
  List<ServerSubLevel> result=new ArrayList<>();
  for(SubLevel linked:SubLevelHelper.getConnectedChain(self)){
   if(linked instanceof ServerSubLevel server&&!server.isRemoved())result.add(server);
  }
  return result;
 }
 /**
  * Surfaces on the largest body of the cluster for dust to sit on.
  *
  * <p>Columns are taken across that body's world footprint, and each one is resolved to the highest
  * body box covering it, so overlapping parts do not stack dust. A column no box covers is skipped:
  * nothing is there to hold dust, which is how dust ends up on the vehicle and never on the ground.
  *
  * <p>Both outcomes are logged while diagnosing: an empty answer used to fall back to the old spawn
  * inside the hull with nothing said, which is indistinguishable from a wrong coordinate space.
  */
 public static List<Vec3> dustSurfaces(ServerLevel level,Vec3 near,int limit){
  List<ServerSubLevel> bodies=cluster(level,near);
  if(bodies.isEmpty()){
   if(LightingCommon.on(LightingCommon.GEN_DEBUG))com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul dust surfaces: no body cluster near {}, nothing to sit on",near);
   return List.of();
  }
  // The dust belongs on the body the cannon sits on, or on the body a dust marker names. Taking the
  // largest box of the cluster put it on whichever neighbouring structure happened to be bigger, which
  // is how it ended up beside and above the vehicle instead of on it.
  ServerSubLevel target=markedDustBody(level,near);
  if(target==null)target=nearestBody(level,near);
  double[] largestBox=target==null?boxNear(level,bodies.getFirst(),near):boxNear(level,target,near);
  List<Vec3> points=new ArrayList<>();
  for(double x=Math.floor(largestBox[0])+0.5;x<largestBox[3]&&points.size()<limit;x+=1.0){
   for(double z=Math.floor(largestBox[2])+0.5;z<largestBox[5]&&points.size()<limit;z+=1.0){
     // Only columns near the cannon: a bounding box can cover a lot of empty space, and dust
     // hanging in mid-air over nothing is what "the box is not the structure" looks like.
     if(Math.hypot(x-near.x,z-near.z)>LightingCommon.CANNON_HULL_RADIUS.get())continue;
    double top=Double.NEGATIVE_INFINITY;
    for(ServerSubLevel body:bodies){
     double[] box=boxNear(level,body,near);
     if(x<box[0]||x>box[3]||z<box[2]||z>box[5])continue;
     if(box[4]>top)top=box[4];
    }
    if(top==Double.NEGATIVE_INFINITY)continue;
    // Nothing may sit far above the cannon: that is a tall neighbouring structure, not the vehicle.
    if(top>near.y+8.0)continue;
    points.add(new Vec3(x,top+0.1,z));
   }
  }
  if(LightingCommon.on(LightingCommon.GEN_DEBUG)){
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul dust surfaces: bodies={} base=[{},{}]..[{},{}] points={} first={}",
     bodies.size(),
     String.format(java.util.Locale.ROOT,"%.1f",largestBox[0]),String.format(java.util.Locale.ROOT,"%.1f",largestBox[2]),
     String.format(java.util.Locale.ROOT,"%.1f",largestBox[3]),String.format(java.util.Locale.ROOT,"%.1f",largestBox[5]),
     points.size(),points.isEmpty()?"none":points.getFirst());
  }
  return points;
 }
 /** Whether one body carries a marker of this kind. */
 private static boolean carries(ServerSubLevel body,Class<?> kind){
  // The markers are plain blocks with no block entity, so the sections' palettes are what has to be
  // asked. Looking them up as block entities is why a placed marker was never found and the dust fell
  // back to guessing a body instead of using the one it was told to use.
  for(var holder:body.getPlot().getLoadedChunks()){
   var chunk=holder.getChunk();
   if(chunk==null)continue;
   for(var section:chunk.getSections()){
    if(section==null||section.hasOnlyAir())continue;
    if(section.maybeHas(state->kind.isInstance(state.getBlock())))return true;
   }
  }
  return false;
 }
}