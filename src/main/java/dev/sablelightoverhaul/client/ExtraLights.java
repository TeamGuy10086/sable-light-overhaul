package dev.sablelightoverhaul.client;
import java.util.*;
import dev.sablelightoverhaul.*;
import dev.sablelightoverhaul.lighting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class ExtraLights {
 private static final Set<Entity> projectiles=Collections.newSetFromMap(new WeakHashMap<>());
 private record Candidate(long id,boolean auto,Vec3 position) {}
 /**
  * What the server last said about one tracer shell: where it is, and the tick it said so.
  *
  * <p>This exists because a light built from a tracked entity dies with the entity, and the entity dies at
  * the client's tracking range -- while the shell itself flies on for hundreds of blocks and the server
  * keeps feeding its trail the whole way. So the trail was there and its light was not: a shell crossing
  * the sky stopped lighting the clouds exactly when it left the view, which is the one scene cloud
  * lighting exists for.
  *
  * <p>Every shell in this map is a <em>big</em> shell as far as the config is concerned, and that is the
  * server's own classification rather than a guess: the feed carries CBC's big cannon shells and the medium
  * cannon addon's, and neither is an autocannon tracer (an autocannon round never leaves the client's
  * tracking range before it lands, so it needs no feed).
  */
 private record Fed(double x,double y,double z,long tick,boolean auto) {}
 private static final Map<Integer,Fed> fed=new HashMap<>();
 /** A fed shell older than this many ticks is gone: the feed repeats every tick, so silence is death. */
 private static final long FED_TTL_TICKS=5;
 private static ClientLevel fedLevel;
 private static ClientLevel sourceLevel;
 private static List<SourcePayload.State> sources=List.of();
 private static final Map<net.minecraft.core.BlockPos,SourcePayload.State> spotlightModels=new HashMap<>();
 private static long spotlightIndexTick=Long.MIN_VALUE;
 /** Effective server pose, including unlit lamps. Late-loading ship anchors get one retry per tick. */
 public static SourcePayload.State spotlightModel(ClientLevel level,net.minecraft.core.BlockPos pos){
  if(sourceLevel!=level)return null;
  var found=spotlightModels.get(pos);
  if(found==null&&spotlightIndexTick!=level.getGameTime())indexSpotlights(level);
  return spotlightModels.get(pos);
 }
 private static void indexSpotlights(ClientLevel level){
  spotlightModels.clear();spotlightIndexTick=level.getGameTime();
  for(var s:sources)if(s.spotlight()){var pos=s.anchor().resolve(level);if(pos!=null)spotlightModels.put(pos.immutable(),s);}
 }
 /**
  * The block a flooding lamp's light field lives on, for this tick, by light key.
  *
  * <p>On the ground that is the lamp's own world block. On a physics body it is the lamp's block
  * <em>in that body's plot</em>, because that is the only place the hull's blocks exist: a field flooded at
  * the world position of a ship lamp spreads through air where the deck should be. Only this class knows
  * that, because only this class has the anchor it was built from; the light list has a key and a position.
  */
 private static final Map<String,net.minecraft.core.BlockPos> FIELD_BLOCKS=new HashMap<>();
 /** The lattice a flooding lamp's field lives on, or null for a lamp on the ground. */
 public static net.minecraft.core.BlockPos fieldBlock(String key){return key==null?null:FIELD_BLOCKS.get(key);}
 /**
  * The block columns the plot of each flooding lamp owns, for this tick.
  *
  * <p>The field is flooded in the plot, and a plot is a pocket inside the same level: outside these columns
  * the level's own ground sits there, and a flood that respected it stopped at the hull's edge -- the report
  * "it does not light the surrounding area". The flood treats everything outside as air instead.
  */
 private static final Map<String,dev.sablelightoverhaul.lighting.Pocket> FIELD_PLOTS=new HashMap<>();
 public static dev.sablelightoverhaul.lighting.Pocket pocket(String key){return key==null?null:FIELD_PLOTS.get(key);}
 /**
  * The anchor of every lamp standing on a hull, so it can be placed where the hull is <em>drawn</em>.
  *
  * <p>The pose is re-evaluated every frame from the hull's own two ticks, through the partial tick Sable drew
  * the hull with. The first version of this kept two already-transformed world points and lerped between them,
  * which is not the same curve: a body turns about its rotation point, so a point on it travels an arc, and a
  * straight line between the arc's ends cuts the corner. The size of that cut grows with the point's distance
  * from the pivot and with how far the body turned, and it changes every frame -- on a deck it reads as the
  * light pool sliding and flickering while the ship is turning.
  */
 private static final Map<String,SourceAnchor> FIELD_ANCHORS=new HashMap<>();
 private static final Map<String,PointLightAppearance> APPEARANCES=new HashMap<>();
 private static final Map<String,net.minecraft.core.BlockPos> SOURCE_BLOCKS=new HashMap<>();
 public static PointLightAppearance appearance(String key){return APPEARANCES.getOrDefault(key,PointLightAppearance.DEFAULT);}
 public static net.minecraft.core.BlockPos sourceBlock(String key){return SOURCE_BLOCKS.get(key);}
 private static Vec3 offset(Vec3 local,PointLightAppearance p){return local.add(p.x(),p.y(),p.z());}
 /**
  * A ship lamp at this frame: {{x,y,z},{qx,qy,qz,qw}} in world space, or null to keep the per-tick values.
  *
  * <p>Null is the answer for every lamp that is not on a hull, and also for a value that could not be trusted --
  * a non-finite number here would become a NaN light and take the whole frame's lighting with it, which is a
  * failure this mod has already had once.
  */
 public static float[][] livePose(String key,double partialTick){
  if(key==null)return null;
  SourceAnchor anchor=FIELD_ANCHORS.get(key);
  var level=Minecraft.getInstance().level;
  if(anchor==null||level==null)return null;
  // A tick fraction offset, for the last of the phase that can remain: 0 is "the same fraction of a tick Sable
  // drew the hull with", which is what this asks for by default.
  double t=partialTick+ExtraConfig.SHIP_POSE_PHASE.get();
  if(t<0.0)t=0.0;else if(t>1.0)t=1.0;
  var resolved=anchor.resolve(level);
  if(resolved==null)return null;
  var pose=anchor.pointAt(level,offset(Vec3.atCenterOf(resolved),appearance(key)),t);
  if(pose==null)return null;
  for(float value:pose) if(!Float.isFinite(value)) return null;
  return new float[][]{{pose[0],pose[1],pose[2]},{pose[3],pose[4],pose[5],pose[6]}};
 } @SubscribeEvent public static void join(EntityJoinLevelEvent e){if(e.getLevel().isClientSide&&(e.getEntity() instanceof AbstractAutocannonProjectile || e.getEntity() instanceof AbstractBigCannonProjectile))projectiles.add(e.getEntity());}
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){projectiles.remove(e.getEntity());}
 public static void worldChanged(ClientLevel level){projectiles.removeIf(e->e.level()!=level);if(fedLevel!=level){fed.clear();fedLevel=null;}if(sourceLevel!=level){sources=List.of();spotlightModels.clear();sourceLevel=null;}}
 public static void receive(SourcePayload p){var l=Minecraft.getInstance().level;if(l!=null&&l.dimension().location().equals(p.dimension())){sourceLevel=l;sources=p.states();indexSpotlights(l);}}
 /**
  * The registered sources this client was last told about, for the level it is standing in.
  *
  * <p>For the axis lines: they have to draw where a lamp is and in whose frame its angles are measured,
  * so they need the anchor rather than only the block position.
  */
 public static List<SourcePayload.State> sourcesFor(ClientLevel level){return sourceLevel==level?sources:List.of();}
 /**
  * The server's own account of where the tracer shells are, taken from the trail feed's payload.
  *
  * <p>It is kept even when the trail itself is switched off: a shell's light is not its rope, and the
  * client's own switches decide which of the two is drawn.
  */
 public static void retireFed(int id){fed.remove(id);}
 public static void receiveAutocannons(ClientLevel level,dev.sablelightoverhaul.ShellTrackPayload payload){
  if(fedLevel!=level){fedLevel=level;fed.clear();}
  long tick=level.getGameTime();for(var s:payload.shells())fed.put(s.id(),new Fed(s.x(),s.y(),s.z(),tick,true));
 }
 public static void receiveShells(ClientLevel level,dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent event){
  if(fedLevel!=level){fedLevel=level;fed.clear();}
  long tick=level.getGameTime();
  for(var host:event.hosts())fed.put((int)host.id(),new Fed(host.current().x,host.current().y,host.current().z,tick,false));
 }
 /**
  * The world block of every registered source, one value per source.
  *
  * <p>Read once per client tick by the in-pack path so it can take a lamp's vanilla light over: the
  * block there stops emitting, and the colour the shader adds is all that is left of it.
  *
  * <p>The <em>resolved block</em> is used, never the sub-block center the light itself is drawn from.
  * A lamp mounted on a body has its center transformed by the body's pose, so a pitching or turning
  * ship moved that center by a fraction of a block every tick and it crossed a block boundary back and
  * forth: the block was taken over, released and taken over again, and each flip rebuilt the section
  * and its neighbours. The resolved block steps only when the ship really moves a whole block.
  */
 public static void forEachSourceBlock(ClientLevel level,java.util.function.LongConsumer out){
  if(sourceLevel!=level)return;
  for(var s:sources){
   var pos=s.anchor().resolve(level);if(pos==null)continue;
   out.accept(pos.asLong());
  }
 }
 public static void append(ClientLevel level,Map<String,AreaSnapshot> world,boolean includeSources){
  var player=Minecraft.getInstance().player;if(player==null)return;
  // This tick's body frames, from scratch: a lamp that left or stopped flooding must not keep last
  // tick's pose, which would remap its field with a stale hull.
  if(includeSources){FIELD_BLOCKS.clear();FIELD_PLOTS.clear();FIELD_ANCHORS.clear();APPEARANCES.clear();SOURCE_BLOCKS.clear();}
  IrisMuzzleBridge.beginBeams();
  SpotShadows.beginFrame();
  if(includeSources) HeldLights.append(level,world);
  DebugFlares.append(level,world);
  projectiles.removeIf(e->e.isRemoved()||e.level()!=level);
  var sorted=new ArrayList<Candidate>();
  var tracked=new HashSet<Integer>();
  for(var e:projectiles){if(HealthbarFlightBridge.owns(e.getUUID()))continue;boolean isAuto=e instanceof AbstractAutocannonProjectile;
   if(isAuto){if(!ExtraConfig.AUTO.get()||!((AbstractAutocannonProjectile)e).isTracer()||((AbstractAutocannonProjectile)e).isInGround())continue;}
   else if(!ExtraConfig.BIG.get()||!((AbstractBigCannonProjectile)e).hasTracer())continue;
   sorted.add(new Candidate(e.getId(),isAuto,rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,e.position())));
   tracked.add(e.getId());
  }
  // The shells this client can no longer see, from the positions the server is still feeding. They are
  // added after the tracked ones and keyed the same way, so a shell is never lit twice: while it is in
  // view its own entity (and its interpolated position) is the better answer, and the moment it is not,
  // the feed takes over without the light ever flickering out.
  long now=level.getGameTime();
  fed.entrySet().removeIf(f->now-f.getValue().tick()>FED_TTL_TICKS);
  for(var f:fed.entrySet()){
   if(tracked.contains(f.getKey())||HealthbarFlightBridge.suppress(f.getKey()))continue;
   var at=f.getValue();
   if(at.auto()?!ExtraConfig.AUTO.get():!ExtraConfig.BIG.get())continue;
   sorted.add(new Candidate(f.getKey(),at.auto(),new Vec3(at.x(),at.y(),at.z())));
  }
  for(var light:HealthbarFlightBridge.lights(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))){
   if(light.auto()?!ExtraConfig.AUTO.get():!ExtraConfig.BIG.get())continue;
   sorted.add(new Candidate(light.id(),light.auto(),light.position()));
  }
  sorted.sort(Comparator.comparingDouble(c->player.distanceToSqr(c.position())));int auto=0,big=0;
  for(var candidate:sorted){boolean isAuto=candidate.auto();
   if(isAuto){if(auto>=ExtraConfig.AUTO_MAX.get())continue;auto++;}else{if(big>=ExtraConfig.BIG_MAX.get())continue;big++;}
   double range=isAuto?ExtraConfig.AUTO_RANGE.get():ExtraConfig.BIG_RANGE.get(),brightness=isAuto?ExtraConfig.AUTO_BRIGHT.get():ExtraConfig.BIG_BRIGHT.get();
   var worldPos=candidate.position();
   var light=sphere(worldPos,range,brightness,ExtraConfig.TRACER_CORE.get());put(world,"tracer/"+candidate.id(),light,0);
  }
  // Registered sources and spotlights are consumed by every backend; the GPU ones feed the
  if(!includeSources)return;
  dev.sablelightoverhaul.api.ClientSpotlightApi.collect(level, s -> appendExternalSpotlight(level, world, player.position(), s));
  dev.sablelightoverhaul.api.ClientSpotlightApi.collectPoints(level, light -> {
   if(light.intensity()<1 || light.radius()<1 || !finite(light.origin()))return;
   double reach=ExtraConfig.ACTIVATION.get();
   if(player.distanceToSqr(light.origin())>reach*reach)return;
   var shape=sphere(light.origin(),Math.clamp(light.radius(),1,32),Math.clamp(light.intensity(),1,15),0);
   put(world,"entityPoint/"+light.id(),shape,SourceColor.CUSTOM | (light.rgb() & 0xffffff));
  });
  if(sourceLevel!=level){sources=List.of();spotlightModels.clear();sourceLevel=null;return;}
  for(var s:sources){
   var a=s.anchor();var pos=a.resolve(level);if(pos==null)continue;
   var local=Vec3.atCenterOf(pos);var center=a.world(level,pos,s.spotlight()?local:offset(local,s.appearance()));if(center==null)continue;
   // A registered lamp is only lit while the player is near it, unless it was force-enabled. Spotlights
   // used to be exempt from this entirely, which made every lamp in the level a light in the buffer on
   // every tick; they now have a reach of their own, measured to the lamp minus the beam's own length so
   // a long beam that genuinely reaches the player is kept even when the fixture is far off.
   if(s.spotlight()){
    double reach=ExtraConfig.SPOT_ACTIVATION.get();
    if(reach>0.0){
     double limit=Math.max(0.0,reach)+s.distance();
     if(player.distanceToSqr(center)>limit*limit)continue;
    }
   }else if(s.mode()!=2&&player.distanceToSqr(center)>ExtraConfig.ACTIVATION.get()*ExtraConfig.ACTIVATION.get())continue;
   if(s.spotlight()){
    if(s.intensity()<1||s.distance()<1)continue;
    // The beam leaves the block's own up, tilted by whatever aim the lamp is set to. The tilt is
    // built in the block's own space and only then transformed, so a lamp on a moving body keeps
    // aiming with the body instead of pointing at a fixed patch of sky.
    // The beam can leave its own block so it sits inside a housing instead of at the block centre; the aim point
   // moves with it, so the direction is unchanged and only the origin shifts.
   // The offset is in the body's own frame, not in world axes: a lamp bolted to a hull has to keep its
   // offset as the hull turns, and a world-space nudge would slide out of the housing on the first roll.
   var mouth=SpotlightPose.mouthOffset(s.tilt(),s.azimuth());
   var localOrigin=local.add(s.offX()+mouth[0],s.offY()+mouth[1],s.offZ()+mouth[2]);
   var origin=a.world(level,pos,localOrigin);if(origin==null)continue;
   var aimed=localOrigin.add(dev.sablelightoverhaul.lighting.SpotlightBlockEntity.aim(s.tilt(),s.azimuth()));
    var tip=a.world(level,pos,aimed);if(tip==null)continue;
    var axis=tip.subtract(origin);if(axis.lengthSqr()<1.0E-8)continue;
    int range=s.distance();
   float[] tint=dev.sablelightoverhaul.lighting.SourceColor.rgb(Math.max(0,s.colorSignal()),0,new float[3],1.0F,1.0F,1.0F);
   double cosHalf=Math.cos(Math.toRadians(s.coneTenths()/20.0));
   int beam=IrisMuzzleBridge.addBeam(origin,axis.normalize(),cosHalf,(float)SpotlightPose.APERTURE,(float)(s.intensity()/15.0),tint[0],tint[1],tint[2],(float)range,s.beamMode(),s.volumeMode());
   // The shadow map is cast here, on this thread, because the composite cannot render geometry. It is per
   // beam and dirty driven, so a lamp that stands still costs nothing after its first map.
   if(beam>=0)SpotShadows.attach(beam,s.id(),level,origin,axis.normalize(),cosHalf,range,s.volumeMode(),SpotlightPose.APERTURE);
    put(world,"spot/"+s.id(),BeamLight.create(origin,axis.normalize(),range,s.coneTenths()/10.0,s.intensity(),s.beamMode()),s.colorSignal());
   }else if(s.emission()>0&&s.emission()<=15){
   // A blink pattern is run here, on the client: the server only carries the text, so two players may be a
   // tick apart and nothing in the light list has to be resent when the lamp changes state.
   if(!dev.sablelightoverhaul.lighting.BlinkPattern.of(s.blink()).litAt(level.getGameTime()))continue;
    // The source's own radius when it has one, in blocks; 0 is the old rule, where the radius follows
    // the brightness. The literal is RegisteredSources.RADIUS_AUTO, which is a server class this client
    // one must not reach for: the payload carries the number, not the type.
    double radius=s.radius()>0?s.radius():ExtraConfig.STATIC_RANGE.get()*s.emission()/15.0;
    // A registered lamp keeps its own texture: culling its own block stops the additive light from
    // washing it out to white, while the coloured glow on everything around it stays.
    // Its shadow behaviour is its own: shadowMode 0 means this lamp does not cast at all, 1 a hard edge
    // and 2 a soft one, and shadowSteps caps the taps it may ask for. Both are only wishes -- the client
    // that receives them applies its own ceiling on top, which is what keeps a lamp on somebody else's
    // vehicle from deciding this player's frame time.
    // A lamp with cones on a hull needs that hull's rotation, so the cone a player measured against the
    // deck turns with it. A lamp on the vanilla light model needs the hull's rotation for the same kind of
    // reason: its field was flooded in the hull's plot, so the shader has to read it in that frame. The cones
    // use the shared body table; the field carries its own copy of the rotation, so it cannot be lost to a
    // table that is full or to a lookup that falls back to the world.
    boolean onBody=a.ship()!=null;
    // Every lamp on a hull asks for a body slot now, not only the ones with cones or a field. The shader needs
    // the hull's rotation for two things beyond shadows: reading a field in that frame, and drawing the lamp's
    // own block there. A hull's blocks are not axis-aligned in the world, so a world-space cell test picks
    // whichever neighbour the rotation favours and lights it instead.
    boolean needsBody=(s.coneA()!=0||s.coneB()!=0||s.coneC()!=0||s.coneD()!=0)||onBody;
    int body=0;
    if(needsBody)body=BodyFrames.slot(a.ship(),a.rotation(level));
    String key=(a.ship()==null?"staticGround/":"staticShip/")+s.id();
    APPEARANCES.put(key,s.appearance());SOURCE_BLOCKS.put(key,pos);
    if(s.appearance().shifted())FIELD_BLOCKS.put(key,net.minecraft.core.BlockPos.containing(offset(local,s.appearance())));
    float[] fieldQuat=null;
    // Every lamp on a hull gets the two ends of this tick's motion, so the shader pass can place it where the
    // hull is *drawn* rather than where it was at the last tick. That is not a field-model idea: a ray lamp on
    // a deck slides off it exactly the same way. What is field-only is the *rotation*, which overwrites the ray
    // model's falloff profile when it is written into the shape vector.
    if(onBody){
     // The lamp's own block, in the plot where it actually lives. It is the field's lattice when the lamp is on
     // the field model, and it is the block whose emission tier the lamp stands in for on *either* model -- a
     // world query at a hull lamp's position answers about air, and would put every ship lamp on the default
     // tier while the same lamp on the ground got its own.
     FIELD_BLOCKS.put(key,net.minecraft.core.BlockPos.containing(offset(local,s.appearance())));
     // Where the lamp stands, kept as the anchor rather than as a position: the position has to be re-evaluated
     // every frame from the hull's own two ticks. See FIELD_ANCHORS.
     FIELD_ANCHORS.put(key,a);
     var q4=unitQuat4(a.rotation(level));
     if(s.flood()&&q4!=null){
      // The shader reads the field in the lamp's own frame, so it needs the very same lattice the flood ran
      // on: the lamp's block in the plot, which is what `pos` already is. The plot's own column extent goes
      // with it, so the flood can treat the ground outside the pocket as air instead of stopping there.
      var pocket=a.pocket(level);
      if(pocket!=null)FIELD_PLOTS.put(key,pocket);
      fieldQuat=new float[]{q4[0],q4[1],q4[2]};
     }
    }
    put(world,key,sphere(center,radius,s.emission(),0),s.colorSignal(),!s.appearance().shifted(),s.shadowMode(),s.shadowSteps(),s.cloudReach(),s.flood(),s.faces(),s.coneA(),s.coneB(),s.coneC(),s.coneD(),body,fieldQuat);
   }
  }
 }
 // Trails are no longer drawn here. They are spawned by the server (see dev.sablelightoverhaul.TracerFeed),
 // because a client only knows the shells vanilla is still tracking, so its trail died at the tracking
 // range while CBC's own shell trail stays visible for hundreds of blocks. The particle is still this
 // mod's, so the look is unchanged — only the side that spawns it moved.
 private static void appendExternalSpotlight(ClientLevel level, Map<String,AreaSnapshot> world, Vec3 viewer,
         dev.sablelightoverhaul.api.ClientSpotlightApi.Spotlight spot){
  if(spot.intensity()<=0 || spot.range()<1 || !Double.isFinite(spot.cone()) || !Double.isFinite(spot.aperture())
      || !finite(spot.origin()) || !finite(spot.axis()) || spot.axis().lengthSqr()<1e-8)return;
  var origin=spot.origin();
  var axis=spot.axis().normalize();
  int range=Math.clamp(spot.range(),1,512);
  double reach=ExtraConfig.SPOT_ACTIVATION.get();
  if(reach>0 && viewer.distanceToSqr(origin)>Math.pow(reach+range,2))return;
  double half=Math.toRadians(Math.clamp(spot.cone(),1,90)/2);
  double cos=Math.cos(half);
  int intensity=Math.clamp(spot.intensity(),1,15), mode=Math.clamp(spot.falloff(),0,4);
  int volume=Math.clamp(spot.volumeMode(),0,2);
  float aperture=(float)Math.clamp(spot.aperture(),.05,4);
  int color=SourceColor.CUSTOM | (spot.rgb() & 0xffffff);
  var profile=BeamLight.profile(mode);
  if(volume>0){
   float[] tint=SourceColor.rgb(color,0,new float[3],1,1,1);
   int beam=IrisMuzzleBridge.addBeam(origin,axis,cos,aperture,intensity/15f,tint[0],tint[1],tint[2],range,mode,volume);
   if(beam>=0)SpotShadows.attach(beam,spot.id().hashCode(),level,origin,axis,cos,range,volume,aperture);
  }
  var light=AreaLight.beam(origin.x,origin.y,origin.z,axis.x,axis.y,axis.z,range,Math.tan(half),
          profile.core(),profile.middle(),profile.tail(),intensity*profile.brightness());
  put(world,"spot/"+spot.id(),light,color);
 }
 private static boolean finite(Vec3 v){
  return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
 }
 private static boolean clampedOnce;
 private static AreaLight sphere(Vec3 p,double r,double brightness,double core){double mid=core+(1-core)*.5;return new AreaLight(p.x,p.y,p.z,1,0,0,r,r,core,brightness,false,mid,.5);}
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color){put(world,key,light,color,false);}
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull));}
 /** Same, with the light's own shadow wish; see the registered-lamp branch for what the two mean. */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps));}
 /** Same, with the lamp's own cloud reach: 0 means "leave it to the client's global setting". */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps,int cloudReach){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps,cloudReach));}
 /** Same, with the lamp's own light style: flood instead of shaping, and which faces it emits through. */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps,int cloudReach,boolean flood,int faces){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces));}
 /** Same, with the lamp's own shadow cones: the directions it is allowed to cast in. */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps,int cloudReach,boolean flood,int faces,int coneA,int coneB,int coneC,int coneD){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,coneA,coneB,coneC,coneD));}
 /** Same, with the body frame the cones are measured in (0 = the world). */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps,int cloudReach,boolean flood,int faces,int coneA,int coneB,int coneC,int coneD,int bodySlot){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,coneA,coneB,coneC,coneD,bodySlot));}
 /** Same, with the rotation the lamp's light field must be read in: the hull's, or null for the world. */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps,int cloudReach,boolean flood,int faces,int coneA,int coneB,int coneC,int coneD,int bodySlot,float[] bodyQuat){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,coneA,coneB,coneC,coneD,bodySlot,bodyQuat));}
 /**
  * One body rotation, normalised and with w &gt;= 0, or null when there is no usable rotation.
  *
  * <p>w is forced non-negative so that the shader can recover it from the other three components: a unit
  * quaternion and its negation are the same rotation, so choosing the representative with w &gt;= 0 loses
  * nothing.
  */
 /**
  * One line about every registered source the client was told about, for the debug log.
  *
  * <p>It exists to answer questions the light buffer cannot: a lamp that is in the buffer but does not move
  * with its hull, a lamp whose page asks for a model it is not getting, or a lamp that lost its body and is
  * being placed at a frozen world position. The position printed is the one the pose gives the lamp right
  * now, so watching the line while a ship moves says at once whether the failure is in the data or later.
  */
 public static String describeSources(){
  var level=Minecraft.getInstance().level;
  if(level==null||sources.isEmpty())return "none";
  var text=new StringBuilder();
  int shown=0;
  for(var s:sources){
   if(shown++>=6){text.append(" ...");break;}
   var a=s.anchor();
   var pos=a.resolve(level);
   var center=pos==null?null:a.world(level,pos,Vec3.atCenterOf(pos));
   text.append(' ').append(s.id())
       .append(s.spotlight()?"/spot":"/lamp")
       .append(" mode=").append(s.mode())
       .append(" flood=").append(s.flood()?1:0)
       .append(" em=").append(s.emission())
       // The shadow wish travels with the page, so a missing shadow has two very different causes: the lamp never
       // asked for one, or the client refused it. This prints the wish; the casting line prints the decision.
       .append(" shadow=").append(s.shadowMode())
       .append("/").append(s.shadowSteps())
       .append(" ship?").append(a.ship()!=null?1:0)
       .append(" plot=").append(a.pos().getX()).append(',').append(a.pos().getZ());
   if(center!=null)text.append(" world=").append(Math.round(center.x)).append(',').append(Math.round(center.y)).append(',').append(Math.round(center.z));
   else text.append(" world=unresolved");
   // What the world still says this block emits, and whether this mod has taken it over. A bright patch that
   // survives a chunk reload and a zeroed brightness is either the vanilla light engine still emitting here or
   // the pack calling the block emissive by its material, and these two numbers separate those.
   if(pos!=null){
    var state=level.getBlockState(pos);
    text.append(" taken?").append(ClientFlashes.takenOver(level,pos.asLong())?1:0)
        .append(" van=").append(state.getLightEmission(level,pos))
        .append(" ours=").append(state.getBlock() instanceof dev.sablelightoverhaul.lighting.LightSourceBlock?1:0);
   }
  }
  return text.toString();
 }

 /**
  * One rotation, normalised, as x,y,z,w with w &gt;= 0, or null when there is nothing usable.
  *
  * <p>w is forced non-negative because a unit quaternion and its negation are the same rotation: choosing the
  * representative with w &gt;= 0 loses nothing, and it is what lets the shader recover w from the other three.
  */
 private static float[] unitQuat4(float[] q){
  if(q==null||q.length<4)return null;
  double length=Math.sqrt((double)q[0]*q[0]+(double)q[1]*q[1]+(double)q[2]*q[2]+(double)q[3]*q[3]);
  if(!(length>1.0E-6)||!Double.isFinite(length))return null;
  double x=q[0]/length,y=q[1]/length,z=q[2]/length,w=q[3]/length;
  if(w<0){x=-x;y=-y;z=-z;w=-w;}
  return new float[]{(float)x,(float)y,(float)z,(float)w};
 }
}
