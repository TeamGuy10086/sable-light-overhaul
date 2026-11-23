package dev.sablelightoverhaul;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import dev.sablelightoverhaul.lighting.LightingCommon;
/** One bounded batch per player/tick. Does not expand full entity/chunk tracking. */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class AutocannonFeed {
 private static final Map<AbstractAutocannonProjectile, Sample> tracked=new WeakHashMap<>();
 private AutocannonFeed(){}
 @SubscribeEvent public static void join(EntityJoinLevelEvent e){
  if(!e.getLevel().isClientSide&&e.getEntity() instanceof AbstractAutocannonProjectile p)tracked.put(p,new Sample(p));
 }
 @SubscribeEvent public static void tick(LevelTickEvent.Post e){
  if(!(e.getLevel() instanceof ServerLevel level)||!LightingCommon.TRAIL_FEED_ENABLED.get())return;
  tracked.keySet().removeIf(p->p.isRemoved()||p.isInGround());
  if(level.players().isEmpty()||tracked.isEmpty())return;
  var frames=new ArrayList<ShellTrackPayload.Shell>();
  var flakIds=new HashSet<Integer>();
  var phases=new HashMap<Integer,Integer>();
  for(var entry:tracked.entrySet()){
   var p=entry.getKey();
   if(p.level()!=level||!p.isTracer())continue;
   var previous=entry.getValue();
   if(p.tickCount==previous.tick)continue;
   var rawFrom=previous.position;
   var rawTo=p.position();
   int elapsed=p.tickCount-previous.tick;
   int phase=previous.tick==0?Math.clamp(p.getPersistentData().getInt("sablehostile_launch_phase_fifths"),0,4):0;
   double distance=p.getTotalDisplacement()-previous.displacement;
   previous.update(p);
   if(!measuredStep(rawFrom,rawTo,elapsed,distance)||HealthbarServerBridge.owns(level,p.getUUID()))continue;
   // Apply one reference frame to both endpoints, including when the shot starts on a moving hull.
   Vec3 from=CBCCompatTransformers.transformVec3(level,rawFrom,rawFrom);
   Vec3 to=CBCCompatTransformers.transformVec3(level,rawTo,rawFrom);
   Vec3 motion=to.subtract(from);
   if(!(motion.lengthSqr()>1e-8&&motion.lengthSqr()<=4096*4096))continue;
   if(p instanceof rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakAutocannonProjectile)flakIds.add(p.getId());
   if(phase!=0)phases.put(p.getId(),phase);
   frames.add(new ShellTrackPayload.Shell(p.getId(),to.x,to.y,to.z,from.x,from.y,from.z,motion.x,motion.y,motion.z,p.getUUID()));
  }
  double range=LightingCommon.TRAIL_FEED_RANGE.get(),range2=range*range;
  for(var player:level.players()){
   var nearby=new ArrayList<ShellTrackPayload.Shell>();
   for(var s:frames)if(player.distanceToSqr(s.x(),s.y(),s.z())<=range2)nearby.add(s);
   nearby.sort(Comparator.comparingDouble(s->player.distanceToSqr(s.x(),s.y(),s.z())));
   if(nearby.size()>256)nearby.subList(256,nearby.size()).clear();
   if(!nearby.isEmpty()){
    var payload=new AutocannonTrackPayload(new ShellTrackPayload(level.dimension().location(),List.copyOf(nearby)),flakIds);
    if(!phases.isEmpty()&&player.connection.hasChannel(AutocannonTrackPayload.Phased.TYPE))
     PacketDistributor.sendToPlayer(player,new AutocannonTrackPayload.Phased(payload,phases));
    else PacketDistributor.sendToPlayer(player,payload);
   }
  }
 }

 // CBC increments displacement from the position at the start of its actual projectile tick.
 // An unticked spawn, teleport, or skipped simulation frame must not become a drawn segment.
 static boolean measuredStep(Vec3 from,Vec3 to,int elapsed,double displacement){
  if(elapsed!=1||!(displacement>1e-4&&displacement<=4096))return false;
  return Math.abs(from.distanceTo(to)-displacement)<=1e-4+displacement*1e-6;
 }

 private static final class Sample {
  private int tick;
  private Vec3 position;
  private double displacement;
  private Sample(AbstractAutocannonProjectile projectile){update(projectile);}
  private void update(AbstractAutocannonProjectile projectile){
   tick=projectile.tickCount;
   position=projectile.position();
   displacement=projectile.getTotalDisplacement();
  }
 }
}
