package dev.cbcmuzzlelight.lighting;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Where the dust that comes off a vehicle actually sits: on its blocks.
 *
 * <p>A bounding box is not a structure. An axis-aligned box around a turret is mostly air, so a column
 * sampled from the box lands on nothing and dust appears hanging in mid-air over empty space 闁?which
 * is exactly what the box sampler did, no matter how the box was chosen.
 *
 * <p>Sable stores a body's blocks in the same server level, at the plot coordinates it owns, so they
 * can simply be read: every loaded chunk of the body is walked, every non-air block is carried into
 * world space by the same transform the muzzle effects use, only the ones near the cannon are kept,
 * and the highest block of each column wins. A column with no block is skipped 闁?that is the "nothing
 * there" case 闁?and no inverse transform is needed at all.
 */
public final class SableSurfaces {
 private SableSurfaces(){}
 /**
  * The highest real block of every column of this body near the cannon, in world space.
  *
  * @param radius - how far from the cannon a surface may be, horizontally.
  * @param rise - how far above the cannon a surface may be; nothing higher is what it rides on.
  * @returns points just above those surfaces, at most {@code limit} of them.
  */
 public static List<Vec3> blockTops(ServerLevel level,ServerSubLevel body,Vec3 near ,double rise,int limit){
  Map<Long,Vec3> tops=new HashMap<>();
  for(var holder:body.getPlot().getLoadedChunks()){
   var chunk=holder.getChunk();
   if(chunk==null)continue;
   int baseX=chunk.getPos().getMinBlockX();
   int baseZ=chunk.getPos().getMinBlockZ();
   var sections=chunk.getSections();
   for(int index=0;index<sections.length;index++){
    var section=sections[index];
    if(section==null||section.hasOnlyAir())continue;
    int baseY=chunk.getSectionYFromSectionIndex(index)<<4;
    for(int y=0;y<16;y++){
     for(int z=0;z<16;z++){
      for(int x=0;x<16;x++){
       if(section.getBlockState(x,y,z).isAir())continue;
       Vec3 world=rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,new Vec3(baseX+x,baseY+y,baseZ+z));
       if(world==null)continue;
       if(world.y>near.y+rise||world.y<near.y-rise-4.0)continue;
       long key=(((long)Math.floor(world.x))<<32)^((long)Math.floor(world.z)&0xFFFFFFFFL);
       Vec3 previous=tops.get(key);
       if(previous==null||world.y>previous.y)tops.put(key,world);
      }
     }
    }
   }
  }
  // Random columns, not the first ones in x/z order: taking them in order piled every point into one
  // corner of the deck. Dust is scattered over the surfaces the body actually has.
  List<Vec3> candidates=new ArrayList<>(tops.values());
  List<Vec3> points=new ArrayList<>();
  int want=Math.min(limit,candidates.size());
  for(int i=0;i<want;i++){
   Vec3 top=candidates.remove(level.random.nextInt(candidates.size()));
   points.add(new Vec3(top.x,top.y+0.35,top.z));
  }
  return points;
 }
 /**
  * The surfaces a shot's dust may sit on: the real blocks of the body the cannon rides on.
  *
  * <p>Nothing else is tried first, because nothing else describes the vehicle: the dust belongs on the
  * deck, the turret roof or the barrel near the muzzle, and every one of those is a block. The box
  * sampler is kept only as a fallback for the case where no block was found at all (an unloaded plot,
  * say), where some dust beats none.
  */
 public static List<Vec3> dustSurfaces(ServerLevel level,Vec3 near,double radius,int limit){
  if(!net.neoforged.fml.ModList.get().isLoaded("sable"))return List.of();
  ServerSubLevel body=SableMarkers.markedBody(level,near);
  boolean marked=body!=null;
  if(body==null)body=SableMarkers.nearestBody(level,near);
  if(body==null){
   if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light dust surfaces: no body near {}, nothing to sit on",near);
   return List.of();
  }
  List<Vec3> real=blockTops(level,body,near,8.0,limit);
  if(LightingCommon.GEN_DEBUG.get()){
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light dust surfaces: body={} anchor={} radius={} blocks={} first={}",
     body.getUniqueId(),marked?"hull-marker":"cannon-body",String.format(java.util.Locale.ROOT,"%.1f",radius),real.size(),real.isEmpty()?"none":real.getFirst());
  }
  if(!real.isEmpty())return real;
  return SableMarkers.dustSurfaces(level,near,limit);
 }
}