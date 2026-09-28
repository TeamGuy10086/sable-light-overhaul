package dev.cbcmuzzlelight.client;
import java.util.*;
import dev.cbcmuzzlelight.*;
import dev.cbcmuzzlelight.lighting.*;
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
@EventBusSubscriber(modid=MuzzleLight.ID,value=Dist.CLIENT)
public final class ExtraLights {
 private static final Set<Entity> projectiles=Collections.newSetFromMap(new WeakHashMap<>());
 private record Candidate(Entity entity,boolean auto,Vec3 position) {}
 private static ClientLevel sourceLevel;
 private static List<SourcePayload.State> sources=List.of();
 @SubscribeEvent public static void join(EntityJoinLevelEvent e){if(e.getLevel().isClientSide&&(e.getEntity() instanceof AbstractAutocannonProjectile || e.getEntity() instanceof AbstractBigCannonProjectile))projectiles.add(e.getEntity());}
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){projectiles.remove(e.getEntity());}
 public static void worldChanged(ClientLevel level){projectiles.removeIf(e->e.level()!=level);if(sourceLevel!=level){sources=List.of();sourceLevel=null;}}
 public static void receive(SourcePayload p){var l=Minecraft.getInstance().level;if(l!=null&&l.dimension().location().equals(p.dimension())){sourceLevel=l;sources=p.states();}}
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
  projectiles.removeIf(e->e.isRemoved()||e.level()!=level);
  var sorted=new ArrayList<Candidate>();
  for(var e:projectiles){boolean isAuto=e instanceof AbstractAutocannonProjectile;
   if(isAuto){if(!ExtraConfig.AUTO.get()||!((AbstractAutocannonProjectile)e).isTracer()||((AbstractAutocannonProjectile)e).isInGround())continue;}
   else if(!ExtraConfig.BIG.get()||!((AbstractBigCannonProjectile)e).hasTracer())continue;
   sorted.add(new Candidate(e,isAuto,rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,e.position())));
  }
  sorted.sort(Comparator.comparingDouble(c->player.distanceToSqr(c.position())));int auto=0,big=0;
  for(var candidate:sorted){var e=candidate.entity();boolean isAuto=candidate.auto();
   if(isAuto){if(auto>=ExtraConfig.AUTO_MAX.get())continue;auto++;}else{if(big>=ExtraConfig.BIG_MAX.get())continue;big++;}
   double range=isAuto?ExtraConfig.AUTO_RANGE.get():ExtraConfig.BIG_RANGE.get(),brightness=isAuto?ExtraConfig.AUTO_BRIGHT.get():ExtraConfig.BIG_BRIGHT.get();
   var worldPos=candidate.position();
   var light=sphere(worldPos,range,brightness,ExtraConfig.TRACER_CORE.get());put(world,"tracer/"+e.getId(),light,0);
  }
  // Registered sources and spotlights are consumed by every backend; the GPU ones feed the
  // shader light buffer where the CPU ones feed Lamb.
  if(!includeSources)return;
  if(sourceLevel!=level){sources=List.of();sourceLevel=null;return;}
  for(var s:sources){
   var a=s.anchor();var pos=a.resolve(level);if(pos==null)continue;
   var local=Vec3.atCenterOf(pos);var center=a.world(level,pos,local);if(center==null)continue;
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
    var aimed=local.add(dev.cbcmuzzlelight.lighting.SpotlightBlockEntity.aim(s.tilt(),s.azimuth()));
    var tip=a.world(level,pos,aimed);if(tip==null)continue;
    var axis=tip.subtract(center);if(axis.lengthSqr()<1.0E-8)continue;
    int range=beamRange(s.distance());
    put(world,"spot/"+s.id(),BeamLight.create(center,axis.normalize(),range,s.widthSignal(),s.intensity(),s.beamMode()),s.colorSignal());
   }else if(s.emission()>0&&s.emission()<=15){
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
    put(world,(a.ship()==null?"staticGround/":"staticShip/")+s.id(),sphere(center,radius,s.emission(),0),s.colorSignal(),true,s.shadowMode(),s.shadowSteps());
   }
  }
 }
 // Trails are no longer drawn here. They are spawned by the server (see dev.cbcmuzzlelight.TracerFeed),
 // because a client only knows the shells vanilla is still tracking, so its trail died at the tracking
 // range while CBC's own shell trail stays visible for hundreds of blocks. The particle is still this
 // mod's, so the look is unchanged — only the side that spawns it moved.
 /**
  * Beam range for this tier. The CPU tier (Lamb) relights every section the beam's bounding box
  * covers, so a long beam is clipped hard there: 64 blocks is a 5x5x9 box of sections, while the
  * 512 an airship searchlight wants would be thousands. The GPU tiers keep the full range.
  */
 private static int beamRange(int requested){
  if(!ClientFlashes.cpuTier()||requested<=CPU_BEAM_RANGE)return requested;
  if(!clampedOnce){clampedOnce=true;com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light: spotlight range clamped to {} in the Compatible tier (requested {})",CPU_BEAM_RANGE,requested);}
  return CPU_BEAM_RANGE;
 }
 private static final int CPU_BEAM_RANGE=64;
 private static boolean clampedOnce;
 private static AreaLight sphere(Vec3 p,double r,double brightness,double core){double mid=core+(1-core)*.5;return new AreaLight(p.x,p.y,p.z,1,0,0,r,r,core,brightness,false,mid,.5);}
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color){put(world,key,light,color,false);}
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull));}
 /** Same, with the light's own shadow wish; see the registered-lamp branch for what the two mean. */
 private static void put(Map<String,AreaSnapshot> world,String key,AreaLight light,int color,boolean selfCull,int shadowMode,int shadowSteps){world.put(key,new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull,shadowMode,shadowSteps));}
}
