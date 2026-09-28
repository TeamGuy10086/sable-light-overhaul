package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.MuzzleLight;
import dev.cbcmuzzlelight.ShockwavePayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The shockwave an explosion throws, drawn by the client from the server's measurements.
 *
 * <p>Two things make this a wave rather than a spray of sparks. The first is that the particles are
 * spawned on an <b>expanding front</b> and not all at once at the centre: the front's radius follows
 * {@code r(t) = R·(1-(1-t/T)^p)}, so it is thin and fast at the start and slows as it goes, and each frame
 * is left where the front passed it. The second is that the front is <b>not a sphere</b>: the server
 * measured how far the blast got in each of eight octants ({@code reach}), so a charge that opened a hole
 * in a cliff face pushes most of its wave that way and a charge on open ground spreads evenly.
 *
 * <p>How many particles a tick gets is the area the front added that tick, not a constant, so a large
 * charge spreads its budget over a wider shell instead of producing a denser one. Everything a reader
 * would want to tune is in {@code cbcmuzzlelight-client.toml} under {@code explosions.shockwave} and takes
 * effect on the next world load.
 */
@EventBusSubscriber(modid=MuzzleLight.ID,value=Dist.CLIENT)
public final class Shockwave {
 /** One explosion's front, as it expands. */
 private static final class Wave {
  final Vec3 center;
  final float yield;
  final float solidity;
  final int tier;
  /** Reach per octant, in {@link ShockwavePayload#octant} order. */
  final float[] reach;
  final float range;
  final float ticks;
  final double totalParticles;
  double emitted;
  int age;
  int spawned;

  Wave(ShockwavePayload payload, float range, float ticks, double totalParticles) {
   this.center = payload.center();
   this.yield = payload.yield();
   this.solidity = payload.solidity();
   this.tier = payload.tier();
   this.reach = payload.reach();
   this.range = range;
   this.ticks = ticks;
   this.totalParticles = totalParticles;
  }

  /** The front's radius at one tick, from the configured expansion law. */
  double radius(double tick) {
   double t = Math.max(0.0, Math.min(1.0, tick / Math.max(1.0F, ticks)));
   double p = ExtraConfig.SHOCKWAVE_EXPANSION.get();
   return range * (1.0 - Math.pow(1.0 - t, p));
  }

  /** The front's outward speed at one tick, the derivative of {@link #radius}. */
  double speed(double tick) {
   double step = 0.5;
   return (radius(tick + step) - radius(Math.max(0.0, tick - step))) / (2.0 * step);
  }

  boolean done() {
   return age > ticks;
  }
 }

 private static final List<Wave> ACTIVE = new ArrayList<>();
 private static ClientLevel owner;
 private static int wavesSeen, particlesThisSecond;
 private static long lastLog = Long.MIN_VALUE;
 private Shockwave(){}
 /** The receiver the mod's network hook calls; no client class is resolved on a dedicated server. */
 public static void receive(ShockwavePayload payload){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null)return;
  if(owner!=level){ACTIVE.clear();owner=level;}
  if(!level.dimension().location().equals(payload.dimension()))return;
  if(!ExtraConfig.SHOCKWAVE_ENABLED.get())return;
  // The front sweeps the whole measured reach, and its lifetime is set by how long a wave takes to cross
  // that at a plausible speed. The relation is fixed rather than configurable because it is geometry:
  // what a reader tunes is the shape (expansion), the weight (density) and the look (the particle list).
  float range=payload.reachMax();
  float mean=ShockwavePayload.reachMean(payload.reach());
  float ticks=Math.max(6.0F,range*0.55F);
  double area=4.0*Math.PI*mean*mean;
  double total=Math.max(1.0,area*ExtraConfig.SHOCKWAVE_DENSITY.get()*(0.4+0.6*(1.0-payload.solidity())));
  // The density floor is how a small explosion is measured and lit but not drawn: below it there is not
  // enough of a wave to be worth the particles, which is what keeps a firefight from strobing.
  if(total<ExtraConfig.SHOCKWAVE_MINDENSITY.get()*40.0)return;
  ACTIVE.add(new Wave(payload,range,ticks,total));
  wavesSeen++;
  if(ExtraConfig.SHOCKWAVE_DEBUG.get()){
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light shockwave recv: yield={} solidity={} tier={} reachMean={} reachMax={} front={} ticks={} particles~{}",
     fmt(payload.yield()),fmt(payload.solidity()),payload.tier(),fmt(mean),fmt(range),fmt(range),fmt(ticks),Math.round(total));
  }
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null){ACTIVE.clear();owner=null;return;}
  if(owner!=level){ACTIVE.clear();owner=level;}
  if(ACTIVE.isEmpty())return;
  for(Iterator<Wave> it=ACTIVE.iterator();it.hasNext();){
   Wave wave=it.next();
   expand(level,wave);
   wave.age++;
   if(wave.done())it.remove();
  }
  if(ExtraConfig.SHOCKWAVE_DEBUG.get()&&(lastLog==Long.MIN_VALUE||level.getGameTime()-lastLog>=20)){
   if(wavesSeen>0||particlesThisSecond>0){
    com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light shockwave: {} wave(s) received, {} particle(s) spawned in the last second",
      wavesSeen,particlesThisSecond);
   }
   lastLog=level.getGameTime();wavesSeen=0;particlesThisSecond=0;
  }
 }
 /** Spawns this tick's share of the wave, on the ring the front swept since the last tick. */
 private static void expand(ClientLevel level,Wave wave){
  double previous=wave.radius(wave.age);
  double now=wave.radius(wave.age+1);
  if(now<=previous)return;
  // The share is the fraction of the front's total area added this tick, applied to the wave's budget:
  // a large charge covers more area and so spends more per tick on the same density.
  double share=(now*now-previous*previous)/Math.max(1.0E-6,Math.pow(wave.range,2.0));
  int count=(int)Math.max(1,Math.min(ExtraConfig.SHOCKWAVE_MAX_PER_TICK.get(),
    Math.round(wave.totalParticles*share)));
  double speed=wave.speed(wave.age);
  ParticleOptions options=dev.cbcmuzzlelight.lighting.ParticleSpec.parse(level,
    dev.cbcmuzzlelight.lighting.LightingCommon.SHOCKWAVE_PARTICLE.get());
  if(options==null)return;
  for(int i=0;i<count;i++){
   Vec3 direction=sampleDirection(level,wave);
   double radius=previous+(now-previous)*level.random.nextDouble();
   Vec3 at=wave.center.add(direction.scale(radius));
   at=ground(level,at,wave.center);
   double jitter=1.0+(level.random.nextDouble()-0.5)*2.0*ExtraConfig.SHOCKWAVE_SPEED_JITTER.get();
   double up=ExtraConfig.SHOCKWAVE_UP_BIAS.get()*Math.max(0.0,direction.y);
   // The client's own spawn call, with force set: it is the client that draws these, so there is no
   // packet and nothing for a server to cull. This is the same path the trail uses.
   level.addParticle(options,true,at.x,at.y,at.z,
     direction.x*speed*jitter,direction.y*speed*jitter+up,direction.z*speed*jitter);
   wave.spawned++;particlesThisSecond++;
  }
  // A few embers over and above the wave, only while the blast is young.
  int embers=ExtraConfig.SHOCKWAVE_EMBER_COUNT.get();
  if(embers>0&&wave.age<(int)Math.max(1.0F,wave.ticks*0.3F)){
   ParticleOptions ember=dev.cbcmuzzlelight.lighting.ParticleSpec.parse(level,
     dev.cbcmuzzlelight.lighting.LightingCommon.SHOCKWAVE_EMBER_PARTICLE.get());
   if(ember!=null){
    int perTick=Math.max(1,embers/8);
    for(int i=0;i<perTick;i++){
     Vec3 direction=sampleDirection(level,wave);
     Vec3 at=wave.center.add(direction.scale(now*0.5));
     level.addParticle(ember,true,at.x,at.y,at.z,
       direction.x*speed*0.6,direction.y*speed*0.6+ExtraConfig.SHOCKWAVE_UP_BIAS.get(),direction.z*speed*0.6);
     particlesThisSecond++;
    }
   }
  }
 }
 /**
  * A random direction from the front, weighted by the octant it belongs to.
  *
  * <p>The weight is the octant's reach squared, which is its share of the front's area: an octant the
  * blast barely entered gets almost no particles, and one it opened a hole through gets most of them. The
  * direction is drawn inside the octant rather than on its diagonal, so the shell reads as a surface.
  */
 private static Vec3 sampleDirection(ClientLevel level,Wave wave){
  double total=0.0;
  for(float r:wave.reach)total+=r*r;
  double pick=level.random.nextDouble()*total;
  int index=0;
  for(int i=0;i<wave.reach.length;i++){
   pick-=wave.reach[i]*wave.reach[i];
   if(pick<=0.0){index=i;break;}
  }
  double x=level.random.nextDouble();
  double y=level.random.nextDouble();
  double z=level.random.nextDouble();
  Vec3 direction=new Vec3(
    (index&1)!=0?x:-x,
    (index&2)!=0?y:-y,
    (index&4)!=0?z:-z);
  double length=direction.length();
  return length<1.0E-6?new Vec3(0,1,0):direction.scale(1.0/length);
 }
 /** Pulls the lower half of the shell onto a floor, so a wave over ground rolls outward along it. */
 private static Vec3 ground(ClientLevel level,Vec3 at,Vec3 center){
  double slack=ExtraConfig.SHOCKWAVE_GROUND_SLACK.get();
  if(slack<=0.0||at.y>=center.y)return at;
  var below=net.minecraft.core.BlockPos.containing(at.x,at.y-slack,at.z);
  for(int i=0;i<=slack;i++){
   var pos=below.above(i);
   if(!level.hasChunkAt(pos))return at;
   if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()){
    return new Vec3(at.x,pos.getY()+1.0+ExtraConfig.SHOCKWAVE_GROUND_OFFSET.get(),at.z);
   }
  }
  return at;
 }
 private static String fmt(double value){
  return String.format(java.util.Locale.ROOT,"%.2f",value);
 }
 @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event){ACTIVE.clear();owner=null;}
 /** Whether anything is being drawn, for a debug command to report. */
 public static int activeWaves(){return ACTIVE.size();}
}
