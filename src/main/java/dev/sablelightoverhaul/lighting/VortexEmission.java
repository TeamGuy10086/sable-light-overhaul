package dev.sablelightoverhaul.lighting;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The mist a registered wing throws while it moves.
 *
 * <p><b>The whole wing, not its outline.</b> Every registered block can take part, and they are taken
 * in a stride that walks the whole set in one pass, so what appears is the surface fogging over
 * rather than a ribbon trailing off one edge. Which block a given particle comes from is only a
 * distribution detail; the set is sampled evenly so a big wing is not covered more slowly than a
 * small one.
 *
 * <p><b>Towards the generator.</b> Each puff is placed a little way from its block, along the line to
 * the generator, and drifts the same way. That is what gives the mist a direction instead of leaving
 * it as unconnected puffs sitting on separate blocks -- the flow reads as running across the wing to
 * the generator's side.
 *
 * <p><b>No speed threshold.</b> Any movement at all mists; the speed only decides how much. The
 * numbers on the block's page change the amount, the spread, the pull and the lift -- never whether
 * anything happens.
 *
 * <p><b>Positions are carried into the world through the body's own pose</b>, the same path the lights
 * use, and never through Create: Big Cannons' transformer: that one answers for CBC's bodies, and a
 * wing is not necessarily one of those. A mark that lands absurdly far from the generator is dropped
 * instead of drawn, so a wrong transform shows up as missing puffs rather than as puffs in the sky.
 */
final class VortexEmission {
 /** Below this the body counts as standing still; the value only rejects float noise. */
 private static final double STILL=1.0E-3;
 /** A mark this far from the generator means the pose is wrong; see the class note. */
 private static final double SANITY=64.0;
 private VortexEmission(){}
 static int emit(ServerLevel level,SourceAnchor anchor,GeneratorBlockEntity generator,Vec3 point,
                 Vec3 motion,List<ServerPlayer> viewers,int budget){
  VortexMarks marks=generator.marks();
  if(marks.empty())return 0;
  // The whole motion, not the ground track: a wing mists because of the air moving over it, and a
  // body climbing has just as much of that as one driving.
  double speed=motion.length();
  if(speed<STILL)return 0;
  int count=Math.min(budget,Math.max(1,(int)Math.round(speed*generator.number("vortexRate"))));
  if(count<=0)return 0;
  ParticleOptions options=GeneratorServer.options(level,generator);
  if(options==null)return 0;
  List<BlockPos> blocks=marks.offsets();
  if(blocks.isEmpty())return 0;
  BlockPos base=anchor.resolve(level);
  if(base==null)return 0;
  double spread=generator.number("vortexSpread");
  double flow=generator.number("vortexFlow");
  double lift=generator.number("vortexUp");
  // Stride through the set so one pass covers the whole wing instead of one corner of it.
  int stride=Math.max(1,blocks.size()/Math.max(1,count));
  int start=(int)(level.getGameTime()%blocks.size());
  int made=0;
  for(int i=0;i<count;i++){
   BlockPos offset=blocks.get((start+i*stride)%blocks.size());
   Vec3 where=anchor.world(level,base,Vec3.atCenterOf(base.offset(offset)));
   if(where==null)continue;
   if(where.distanceToSqr(point)>SANITY*SANITY)continue;
   Vec3 toGenerator=point.subtract(where);
   double distance=toGenerator.length();
   Vec3 inward=distance<1.0E-4?Vec3.ZERO:toGenerator.scale(1.0/distance);
   // Already on its way: the puff is born part of the way across, so the wing looks covered rather
   // than ringed.
   Vec3 at=where.add(inward.scale(Math.min(distance,1.0+level.random.nextDouble())));
   ParticleSpec.spawn(level,viewers,options,
     at.x+(level.random.nextDouble()-0.5)*2.0*spread,
     at.y+(level.random.nextDouble()-0.5)*2.0*spread,
     at.z+(level.random.nextDouble()-0.5)*2.0*spread,0,
     inward.x*flow*speed,lift,inward.z*flow*speed,1.0);
   made++;
  }
  return made;
 }
}
