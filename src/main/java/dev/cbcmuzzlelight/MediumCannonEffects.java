package dev.cbcmuzzlelight;

import dev.cbcmuzzlelight.lighting.LightingCommon;
import dev.cbcmuzzlelight.lighting.ParticleSpec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.particles.ParticleTypes;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticleData;

/**
 * The tank cannon's muzzle effects, fired from the one place that knows where the muzzle is.
 *
 * <p>Everything here is server side: the shot already runs on the server, so the effects are spawned
 * once for the players that can see them instead of once per client, and the ground search uses the
 * authoritative level. The four groups of parameters all live in the {@code [tankCannon]} config so
 * they can be tuned by firing the gun.
 *
 * <p>The three groups map to the three effects that the mod itself does not already provide: a bright
 * flash, dim sparks with unlit smoke, and dust kicked off the ground and the hull. The mod's own
 * breech smoke is left alone.
 */
public final class MediumCannonEffects {
 /** Flash kind of a tank cannon, kept apart from the big cannon (0) and autocannon (1). */
 public static final int KIND=2;
 // Particles are built here rather than read from the config: a spec string in a config file breaks the config screens other mods provide.
 private static final String FLAME_PARTICLE="createbigcannons:cannon_smoke{power:8.0,size:1.6,lifetime:5,friction:0.9}";
 private static final String SPARK_PARTICLE="createbigcannons:spark_burst{color:[1.0,0.45,0.12],deflect:true,count:24}";
 private MediumCannonEffects(){}
 /**
  * Spawn one shot's worth of effects.
  * @param level - the level the shot happened in.
  * @param owner - the cannon contraption, used for the coordinate transform and as the emitter id.
  * @param plume - muzzle position as the cannon reports it.
  * @param direction - muzzle direction as the cannon reports it.
  * @param size - the cannon's own plume size, which scales with how many barrels the round passed.
  * @param power - the cannon's own plume power, equal to its size.
  */
 public static void fire(ServerLevel level,Entity owner,Vec3 plume,Vec3 direction,float size,float power){
  if(!LightingCommon.CANNON_ENABLED.get()||level==null||owner==null)return;
  // The cannon reports contraption space; the transform turns it into true world space and rotates
  // the direction, the same two calls the tracer lights already rely on.
  Vec3 world=CBCCompatTransformers.transformVec3(level,plume);
  Vec3 axis=CBCCompatTransformers.transformLocationNormal(level,owner.blockPosition(),direction);
  if(world==null||axis==null||axis.lengthSqr()<1.0E-8)return;
  axis=axis.normalize();
  double charge=Math.max(1.0,power);
  List<ServerPlayer> viewers=viewers(level,world,LightingCommon.CANNON_CULL.get());
  if(viewers.isEmpty())return;
  int budget=LightingCommon.CANNON_BUDGET.get();
  budget-=plume(level,viewers,world,axis,size,power);
  budget-=flame(level,viewers,world,axis,charge,budget);
  budget-=sparks(level,viewers,world,axis,budget);
  budget-=groundDust(level,viewers,world,axis,charge,budget);
  // Hull dust gets its own allowance. Sharing one pool meant a ground count sitting at the cap
  // consumed the whole budget first and left hull dust with zero, without saying anything.
  hullDust(level,viewers,world,axis,LightingCommon.CANNON_BUDGET.get());
  // The gun pushes back. Without this the dust flies and the vehicle stands still, which is the one
  // part of a shot the eye always notices.
  SableRecoil.apply(level,world,axis);
  // The light is what this mod is for: one short bright pulse where the fireball is.
  ServerFlashes.emitSized(level,world,axis,KIND,owner.getId(),world,(float)charge,size);
 }
 /** Amplify the cannon's own plume, which already carries its white puff, flame phase and smoke. */
 private static int plume(ServerLevel level,List<ServerPlayer> viewers,Vec3 world,Vec3 axis,float size,float power){
  double scale=LightingCommon.CANNON_PLUME_SCALE.get();
  if(scale<=0)return 0;
  var data=new BigCannonPlumeParticleData((float)(size*scale),(float)(power*scale),10);
  ParticleSpec.spawn(level,viewers,data,world.x,world.y,world.z,0,axis.x,axis.y,axis.z,1.0);
  return 0;
 }
 /** Bright fireball: fast, short lived, aimed down the barrel. */
 private static int flame(ServerLevel level,List<ServerPlayer> viewers,Vec3 world,Vec3 axis,double charge,int budget){
  int count=Math.min(LightingCommon.CANNON_FLAME_COUNT.get(),budget);
  if(count<=0)return 0;
  ParticleOptions options=ParticleSpec.parse(level,FLAME_PARTICLE);
  if(options==null)return 0;
  double speed=LightingCommon.CANNON_FLAME_SPEED.get();
  for(int i=0;i<count;i++){
   double push=speed*(1.0+0.4*charge)*(0.6+level.random.nextDouble()*0.8);
   ParticleSpec.spawn(level,viewers,options,world.x,world.y,world.z,0,
     axis.x*push+spread(level)*0.3,axis.y*push+spread(level)*0.3,axis.z*push+spread(level)*0.3,1.0);
  }
  return count;
 }
 /** Dim sparks, and one puff of unlit smoke that spreads rather than hangs at the muzzle. */
 private static int sparks(ServerLevel level,List<ServerPlayer> viewers,Vec3 world,Vec3 axis,int budget){
  int bursts=Math.min(LightingCommon.CANNON_SPARK_BURSTS.get(),budget);
  if(bursts<=0)return 0;
  ParticleOptions options=new dev.cbcmuzzlelight.lighting.ImpactParticleData(LightingCommon.CANNON_SPARK_SIZE.get().floatValue(),LightingCommon.CANNON_SPARK_COLOR.get(),LightingCommon.CANNON_SPARK_LIFETIME.get(),LightingCommon.CANNON_SPARK_BOUNCE.get().floatValue(),dev.cbcmuzzlelight.lighting.ImpactParticleData.STREAK,1.0F);
  double speed=LightingCommon.CANNON_SPARK_SPEED.get();
  // A burst particle reads its own velocity as the direction it throws sparks along.
  for(int i=0;i<bursts;i++){
   ParticleSpec.spawn(level,viewers,options,world.x,world.y,world.z,0,axis.x,axis.y,axis.z,speed);
  }
  return bursts;
 }
 /** Ground dust: where the blast axis meets the ground, coloured by whatever it hit. */
 private static int groundDust(ServerLevel level,List<ServerPlayer> viewers,Vec3 world,Vec3 axis,double charge,int budget){
  int count=Math.min(LightingCommon.CANNON_GROUND_COUNT.get(),budget);
  if(count<=0)return 0;
  double distance=LightingCommon.CANNON_GROUND_DISTANCE.get();
  double ring=LightingCommon.CANNON_GROUND_RADIUS.get();
  boolean tint=LightingCommon.CANNON_GROUND_TINT.get();
  // A shot throws dust up all around the tank, not only where the bore happens to point: the ground
  // is looked for in a ring around the muzzle, and the bore line is one more sample so that firing
  // into the ground still throws a proper plume under the barrel.
  int sites=Math.max(3,Math.min(8,count));
  List<Vec3> probes=new ArrayList<>();
  for(int i=0;i<sites;i++){
   double angle=(i+level.random.nextDouble()*0.5)*Math.PI*2/sites;
   double reach=ring*(0.6+0.8*level.random.nextDouble());
   probes.add(world.add(Math.cos(angle)*reach,0,Math.sin(angle)*reach));
  }
  probes.add(world);
  List<Vec3> points=new ArrayList<>();
  List<Integer> tints=new ArrayList<>();
  for(Vec3 probe:probes){
   BlockHitResult hit=dev.cbcmuzzlelight.lighting.GeneratorServer.groundBelow(level,probe,distance);
   if(hit==null)continue;
   points.add(hit.getLocation().add(0,0.05,0));
   tints.add(tint?mapColor(level,hit.getBlockPos()):0);
  }
  if(points.isEmpty()){
   if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light cannon dust: {} probes, no ground within {} blocks of {}",probes.size(),distance,world);
   return 0;
  }
  double speed=LightingCommon.CANNON_GROUND_SPEED.get()*(0.6+0.4*Math.min(1,charge/6.0));
  double scatter=LightingCommon.CANNON_GROUND_SCATTER.get();
  // Dust is stirred hardest where the blast lands and barely at all at the edge of the ring, so the
  // push a cloud gets follows how close to the muzzle its patch of ground is. Nothing is thrown
  // upward: the blast spreads along the ground and the cloud sinks onto it.
  for(int i=0;i<count;i++){
   int site=Math.min(points.size()-1,(int)(level.random.nextDouble()*points.size()));
   Vec3 ground=points.get(site).add(spread(level)*scatter,0.0,spread(level)*scatter);
   Vec3 away=ground.subtract(world);
   double reach=Math.hypot(away.x,away.z);
   double falloff=Math.pow(Math.max(0.0,1.0-reach/Math.max(1.0,ring)),1.5);
   Vec3 push=reach<1.0E-4?new Vec3(1.0,0.0,0.0):new Vec3(away.x/reach,0.0,away.z/reach);
   ParticleSpec.spawn(level,viewers,cloud(level,true,tint?tints.get(site):null),
     ground.x,ground.y+0.3,ground.z,0,
     push.x*speed*falloff,-0.02-axis.y*0.05,push.z*speed*falloff,1.0);
  }
  if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light cannon dust: ground probes={} hits={} spawned={}",sites+1,points.size(),count);
  return count;
 }
 /** Hull dust: the blast washing backwards over the tank, blown off surfaces near the muzzle. */
 private static int hullDust(ServerLevel level,List<ServerPlayer> viewers,Vec3 world,Vec3 axis,int budget){
  int count=Math.min(LightingCommon.CANNON_HULL_COUNT.get(),budget);
  if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light cannon dust: hull allowance={} bodies-near={}",count,world);
  if(count<=0)return 0;
  double radius=LightingCommon.CANNON_HULL_RADIUS.get(),speed=LightingCommon.CANNON_HULL_SPEED.get();
  // A dust marker puts the cloud on the surfaces of the marked vehicle. Columns the body does not
  // cover are skipped, so dust never appears over the ground or inside the hull.
  if(net.neoforged.fml.ModList.get().isLoaded("sable")){
   java.util.List<Vec3> surfaces=dev.cbcmuzzlelight.lighting.SableSurfaces.dustSurfaces(level,world,LightingCommon.CANNON_HULL_RADIUS.get(),Math.min(count,32));
   if(!surfaces.isEmpty()){
    for(int i=0;i<count;i++){
     Vec3 at=surfaces.get(level.random.nextInt(surfaces.size()));
     double angle=level.random.nextDouble()*Math.PI*2;
     ParticleSpec.spawn(level,viewers,cloud(level,false,null),at.x,at.y,at.z,0,Math.cos(angle)*speed,-0.02,Math.sin(angle)*speed,1.0);
    }
    if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light cannon dust: hull surfaces={} spawned={}",surfaces.size(),count);
    return count;
   }
  }
  double scatter=LightingCommon.CANNON_HULL_SCATTER.get();
  for(int i=0;i<count;i++){
   double angle=level.random.nextDouble()*Math.PI*2;
   double reach=radius*(0.2+level.random.nextDouble()*0.8);
   // Biased behind and below the muzzle, which is where a hull actually sits relative to the blast.
   Vec3 at=world.add(Math.cos(angle)*reach,reach*(level.random.nextDouble()-0.7),Math.sin(angle)*reach)
     .subtract(axis.scale(reach*0.5)).add(spread(level)*scatter,spread(level)*scatter*0.5,spread(level)*scatter);
   // Same falloff as the ground dust, and still nothing pushing it up: once a cloud has left the hull
   // only gravity acts on it.
   double falloff=Math.pow(Math.max(0.0,1.0-reach/Math.max(0.5,radius)),1.5);
   ParticleSpec.spawn(level,viewers,cloud(level,false,null),at.x,at.y,at.z,0,
     Math.cos(angle)*speed*falloff,-0.02,Math.sin(angle)*speed*falloff,1.0);
  }
  return count;
 }
 /**
  * One cloud with its size and horizontal stretch rolled inside the range its own group asks for.
  *
  * <p>The ground and the hull have separate ranges on purpose: dust kicked off the ground is wide,
  * flat and long lived, while what washes over a hull is tighter and short lived, and reusing one
  * range for both is what made the two effects look like the same thing.
  */
 private static ParticleOptions cannonParticle(ServerLevel level,ParticleOptions base,boolean ground){
  return base;
 }
 /**
  * The cloud the cannon throws: always this mod's own particle, never a config string, so the ground
  * and hull variants can never drift apart into two different looking effects. They differ in numbers
  * only, and those numbers are the config: the ground cloud is wider, flatter, longer lived and takes
  * the colour of whatever it was kicked off, while the hull cloud is tighter and short lived.
  */
 private static ParticleOptions cloud(ServerLevel level,boolean ground,Integer tint){
  double sizeMin=ground?LightingCommon.CANNON_GROUND_SIZE_MIN.get():LightingCommon.CANNON_HULL_SIZE_MIN.get();
  double sizeMax=ground?LightingCommon.CANNON_GROUND_SIZE_MAX.get():LightingCommon.CANNON_HULL_SIZE_MAX.get();
  double stretchMin=ground?LightingCommon.CANNON_GROUND_STRETCH_MIN.get():LightingCommon.CANNON_HULL_STRETCH_MIN.get();
  double stretchMax=ground?LightingCommon.CANNON_GROUND_STRETCH_MAX.get():LightingCommon.CANNON_HULL_STRETCH_MAX.get();
  int lifetime=ground?LightingCommon.CANNON_GROUND_LIFETIME.get():LightingCommon.CANNON_HULL_LIFETIME.get();
  int color=tint!=null?tint:(ground?LightingCommon.CANNON_GROUND_COLOR.get():LightingCommon.CANNON_HULL_COLOR.get());
  double size=sizeMin+level.random.nextDouble()*Math.max(0,sizeMax-sizeMin);
  double stretch=stretchMin+level.random.nextDouble()*Math.max(0,stretchMax-stretchMin);
  // The motion lives with the cloud: a heavy horizontal damping so it stays where it was stirred up,
  // and gravity as the only vertical term.
  float damping=(ground?LightingCommon.CANNON_GROUND_DAMPING.get():LightingCommon.CANNON_HULL_DAMPING.get()).floatValue();
  float gravity=(ground?LightingCommon.CANNON_GROUND_GRAVITY.get():LightingCommon.CANNON_HULL_GRAVITY.get()).floatValue();
  float alpha=(ground?LightingCommon.CANNON_GROUND_ALPHA.get():LightingCommon.CANNON_HULL_ALPHA.get()).floatValue();
  return new dev.cbcmuzzlelight.lighting.GroundDustParticleData((float)size,color,lifetime,(float)stretch,false,damping,gravity,alpha);
 }
 private static List<ServerPlayer> viewers(ServerLevel level,Vec3 point,double cull){
  double limit=cull*cull;
  List<ServerPlayer> result=new ArrayList<>();
  for(ServerPlayer player:level.players())if(player.distanceToSqr(point)<=limit)result.add(player);
  return result;
 }
 private static double spread(ServerLevel level){return level.random.nextDouble()-level.random.nextDouble();}
 private static int mapColor(ServerLevel level,BlockPos pos){
  BlockState state=level.getBlockState(pos);
  return state.getMapColor(level,pos).col;
 }
}
