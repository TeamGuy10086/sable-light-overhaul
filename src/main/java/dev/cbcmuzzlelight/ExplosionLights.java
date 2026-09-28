package dev.cbcmuzzlelight;

import dev.cbcmuzzlelight.lighting.LightingCommon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * The light of a big explosion.
 *
 * <p>An explosion destroys blocks, throws a shockwave of particles and is over in a few ticks: what it
 * looks like it is doing is lighting the place up, and that is the one part a particle ring cannot do.
 * So every explosion above a size is given a flash of its own.
 *
 * <p>The size is the number of blocks the blast reports, which is the same number the shockwave script
 * uses, and the tiers are the same three (550 / 2000 / 18000 by default): the light and the shockwave
 * then always agree about how big an event this was. On a big shell that count is not the damage: CBC's
 * list is the blocks around the blast, and it grows with the solid material the shell is buried in, so
 * it reads as "how much stuff was in the way" rather than "how much was destroyed".
 *
 * <p>Everything is configurable, and small blasts (a creeper, a grenade) are below the threshold and
 * stay dark, so a firefight does not turn into a strobe.
 */
@EventBusSubscriber(modid=MuzzleLight.ID)
public final class ExplosionLights {
 private static final Vec3 UP=new Vec3(0.0,1.0,0.0);
 private ExplosionLights(){}
 @SubscribeEvent
 public static void detonate(ExplosionEvent.Detonate event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  int blocks=event.getAffectedBlocks().size();
  // Refreshing the far LOD is its own feature and has its own switch: it has to work with the
  // explosion light turned off, and it is what makes the crater show up beyond the render distance.
  if(blocks>=LightingCommon.VOXY_MIN_BLOCKS.get())dev.cbcmuzzlelight.voxy.VoxyLodNudger.queue(level,event.getExplosion().center(),event.getAffectedBlocks());
  if(!LightingCommon.EXPL_ENABLED.get())return;
  if(blocks<LightingCommon.EXPL_MIN_BLOCKS.get())return;
  // The charge itself, when the explosion has one. CBC's CustomExplosion passes its
  // block_damaging_explosive_power straight into the vanilla constructor, so this is the shell's own
  // number and does not change with what it hit; a plain vanilla blast has none and takes the floor.
  float yield=event.getExplosion().radius();
  if(!(yield>ShockwavePayload.FALLBACK_BELOW))yield=ShockwavePayload.DEFAULT_YIELD;
  Vec3 center=event.getExplosion().center();
  // How far the blast actually got, per octant: measured from the blocks vanilla's own rays walked
  // through, so it already carries the resistance of everything that was in the way.
  float[] reach=ShockwavePayload.measure(center,yield,event.getAffectedBlocks());
  float solidity=ShockwavePayload.solidity(yield,reach);
  int tier=tierOf(yield);
  var source=event.getExplosion().getDirectSourceEntity();
  int emitter=source==null?-1:source.getId();
  // The light follows the measurement instead of a constant per tier: an airburst is bright over the
  // radius it actually filled, a buried shot is dimmer over the reach it actually managed.
  double span=Math.max(1.0,ShockwavePayload.reachMean(reach));
  double exposure=1.0-solidity;
  double charge=LightingCommon.EXPL_CHARGE.get()*(yield/ShockwavePayload.DEFAULT_YIELD)*(0.35+0.65*exposure);
  double size=span*LightingCommon.EXPL_SIZE_SCALE.get();
  // Two lights stacked up the column: one at the centre reads as a point, while a blast also lights
  // the ground it goes off on and the smoke rising above it.
  ServerFlashes.emitSized(level,center,UP,0,emitter,center,(float)charge,(float)size);
  ServerFlashes.emitSized(level,center.add(0.0,2.5,0.0),UP,0,emitter,center.add(0.0,2.5,0.0),(float)(charge*0.6),(float)(size*0.6));
  // The client draws the shockwave, so it is told what was measured rather than what to spawn.
  double range=LightingCommon.SHOCKWAVE_RANGE.get();
  for(var player:level.players()){
   if(player.distanceToSqr(center)>range*range)continue;
   net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
     new ShockwavePayload(level.dimension().location(),center.x,center.y,center.z,yield,solidity,tier,reach,emitter));
  }
  if(LightingCommon.GEN_DEBUG.get()){
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light explosion: blocks={} yield={} solidity={} tier={} reach[min/mean/max]={}/{}/{} at {}",
     blocks,fmt(yield),fmt(solidity),tier,fmt(minOf(reach)),fmt(ShockwavePayload.reachMean(reach)),fmt(maxOf(reach)),center);
  }
 }
 /** The shell's own charge decides the tier, whatever it happened to hit. */
 private static int tierOf(float yield){
  java.util.List<? extends Integer> thresholds=LightingCommon.EXPL_YIELD_TIERS.get();
  int tier=1;
  for(int i=0;i<thresholds.size();i++)if(yield>=thresholds.get(i))tier=i+2;
  return Math.min(tier,6);
 }
 private static float minOf(float[] values){
  float min=Float.MAX_VALUE;
  for(float v:values)min=Math.min(min,v);
  return min;
 }
 private static float maxOf(float[] values){
  float max=0.0F;
  for(float v:values)max=Math.max(max,v);
  return max;
 }
 private static String fmt(double value){
  return String.format(java.util.Locale.ROOT,"%.2f",value);
 }
}
