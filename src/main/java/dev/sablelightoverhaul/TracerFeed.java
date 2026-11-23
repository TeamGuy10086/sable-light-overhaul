package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Feeds the client the two endpoints of every tracer shell's tick, so the client can run CBC's trail
 * loop over them.
 *
 * <p>This is the ShockwavePayload pattern applied to shells: the server measures, the client draws. What
 * is measured is the same pair CBC's own projectile trail interpolates between — the position before this
 * tick ({@code entity.xOld}) and the one after ({@code entity.position()}) — plus the shell's velocity and
 * id. Nothing is predicted, and a shell is kept for as long as it exists.
 *
 * <p><b>Only two things end a shell here: it being removed, or it landing.</b> A previous version also
 * dropped any shell whose {@code level()} was not the very Level object the tick event carried. That is a
 * comparison of object identity, and a shell that Sable has re-parented into a sub-level fails it while
 * still flying, so every shell was dropped after the single tick it had been fed: one segment of rope near
 * the muzzle and nothing after it.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class TracerFeed {
 /** Shells in flight, keyed weakly so a despawned entity cannot leak. */
 private static final Set<Entity> shells=Collections.newSetFromMap(new WeakHashMap<>());
 private static long lastLog=Long.MIN_VALUE;
 /** What the delivery half did on the last offer, so one diagnostic line can report both halves. */
 private static int lastFed,lastTooFar,lastTold;
 /** Registers this mod's own delivery as the default listener for the offer it produces. */
 public static void install(){
  dev.sablelightoverhaul.api.EffectApi.onTrackedHost(dev.sablelightoverhaul.effects.EffectBus.DEFAULT_PRIORITY,
    TracerFeed::deliver);
 }
 private TracerFeed(){}
 @SubscribeEvent public static void join(EntityJoinLevelEvent event){
  if(event.getLevel().isClientSide)return;
  Entity entity=event.getEntity();
  if(entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile
    ||ClientHooks.shellType(entity)){shells.add(entity);HostileShellFeed.join(entity);}
 }
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
  // Deliberately not removed here. Sable re-parents a shell between the outer level and a sub-level, and
  // that fires this event while the shell is still very much in flight; treating it as a death is how a
  // shell ended up tracked for exactly one tick. `isRemoved()` in the tick is the real signal, and the
  // dimension check there lets go of anything left behind by a world change.
 }
 /**
  * Measures every tracer shell in this level and offers them, once per tick.
  *
  * <p>The measurement is what this class knows: which shells are still in flight, which of them carry a
  * tracer, and where they are in the space a player sees. What is done with the offer -- who is told, and
  * within what range -- is the delivery half, {@link #deliver}, which is a listener like anybody else's and
  * can be cancelled or preceded.
  */
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  if(!LightingCommon.TRAIL_FEED_ENABLED.get()||shells.isEmpty()){HostileShellFeed.deliver(level,List.of());return;}
  var players=level.players();
  // With nobody to tell there is nobody to measure for, and counting shells as "traced" for an empty
  // audience would put lines in the log that used to require a player.
  if(players.isEmpty())return;
  int untraced=0,dropped=0;
  String refused=null;
  String sampled=null;
  String removed=null;
  List<dev.sablelightoverhaul.api.EffectApi.Host> hosts=new ArrayList<>();
  var naval = new ArrayList<HostileShellPayload.Shell>();
  for(Iterator<Entity> it=shells.iterator();it.hasNext();){
   Entity entity=it.next();
   // ---- the pass that matters ---------------------------------------------------------------
   //
   // `LevelTickEvent.Post` fires once per level every tick, so this handler runs three times a tick on a
   // normal save: the overworld, the nether, then the end. The set of shells is shared across all of
   // them, so during the nether's pass an overworld shell is being looked at by a handler that has no
   // business with it. A shell that does not belong to this level is SKIPPED -- never removed, because
   // removal is what a shell earns by dying, not by being seen from the wrong dimension.
   //
   // Getting this wrong is what cost the entire trail: the cleanup that dropped shells whose level did
   // not equal the event's level deleted every shell on the same tick it had been fed, so each shot laid
   // exactly one segment of rope near the muzzle -- on a Sable body and on plain ground alike, which is
   // what ruled out the sub-level theory.
   if(!entity.level().dimension().equals(level.dimension()))continue;
   var model = HostileShellFeed.model(entity);
   if(model!=null){
    HostileShellFeed.measure(level,entity,model,naval,hosts);
    if(entity.isRemoved())it.remove();
    continue;
   }
   if(entity.isRemoved()){
    it.remove(); dropped++;
    if(removed==null)removed=entity.getClass().getSimpleName()+" removed";
    continue;
   }
   // Landed shells leave immediately. CBC keeps a landed projectile for 400 ticks (twenty seconds) and
   // `hasTracer()` reports false for it, so without this test it would sit in the set being counted as
   // "without a tracer" for twenty seconds and, worse, be fed if the flag ever flickered back.
   boolean landed=entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile big
     ?big.isInGround():ClientHooks.inGround(entity);
   if(landed){
    it.remove(); dropped++;
    if(removed==null)removed=entity.getClass().getSimpleName()+" landed";
    continue;
   }
   if(HealthbarServerBridge.owns(level,entity.getUUID()))continue;
   boolean tracer=entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile big
     ?big.hasTracer():ClientHooks.tracer(entity);
   if(!tracer){
    untraced++;
    if(refused==null)refused=entity.getClass().getName()+(entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile big2
      ?" (big cannon: tracer item is "+big2.getTracer()+")":" (addon shell: isTracer() false)");
    continue;
   }
   // The two endpoints, transformed into the space the player sees: a shell on a physics body reports
   // plot coordinates and the player's world needs the pose applied. The transform does not depend on the
   // player, so it is done once here rather than once per player as it used to be -- the same numbers.
   Vec3 now=rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,entity.position());
   Vec3 previous=rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,
     new Vec3(entity.xOld,entity.yOld,entity.zOld));
   Vec3 motion=now.subtract(previous); // measured world displacement, never mixed with a plot-space vector
   if(sampled==null)sampled="previous "+vec(previous)+" now "+vec(now)+" motion "+vec(motion);
   hosts.add(new dev.sablelightoverhaul.api.EffectApi.Host(entity.getId(),previous,now,motion,0,0,true));
  }
  HostileShellFeed.deliver(level,naval);
  lastFed=0;lastTooFar=0;lastTold=0;
  if(!hosts.isEmpty()){
   dev.sablelightoverhaul.effects.EffectBus.trackedHost(new dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent(
     level,dev.sablelightoverhaul.api.EffectApi.HostKind.PROJECTILE,List.copyOf(hosts),List.of(),
     dev.sablelightoverhaul.api.EffectApi.Side.SERVER,
     // A shell is its own host: its id identifies it, so the offer needs no group.
     0,
     dev.sablelightoverhaul.effects.DefaultSpecs.forTrackedHosts(),
     dev.sablelightoverhaul.api.EffectApi.Cancellation.none()));
  }
  int fed=lastFed,tooFar=lastTooFar,told=lastTold;
  // Its own switch only: a line per tracer per second is a diagnostic, not something the global master should drag along.
   if(LightingCommon.TRAIL_FEED_DEBUG.get()&&(fed>0||untraced>0)&&(lastLog==Long.MIN_VALUE||level.getGameTime()-lastLog>=10)){
   lastLog=level.getGameTime();
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul trail feed: {} tracer shell(s) fed, {} without a tracer, {} out of range, {} player(s) told, set={}, dropped={}, range {}{}{}{}",
     fed,untraced,tooFar,told,shells.size(),dropped,Math.round(LightingCommon.TRAIL_FEED_RANGE.get()),
     refused==null?"":"; refused: "+refused,
     sampled==null?"":"; "+sampled,
     removed==null?"":"; "+removed);
  }
 }
 /**
  * The default delivery: one message per player, carrying only the shells inside that player's feed range.
  *
  * <p>A listener, so a third party can run before it -- to cancel it and send its own richer message, or to
  * read the same offer and add something of its own.
  */
 private static void deliver(dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent event){
  if(event.side()!=dev.sablelightoverhaul.api.EffectApi.Side.SERVER)return;
  if(event.hostKind()!=dev.sablelightoverhaul.api.EffectApi.HostKind.PROJECTILE)return;
  if(!(event.level() instanceof ServerLevel level))return;
  double range=LightingCommon.TRAIL_FEED_RANGE.get();
  double rangeSqr=range*range;
  var carriers=new java.util.HashMap<Integer,java.util.UUID>();
  for(var entity:shells)carriers.put(entity.getId(),entity.getUUID());
  List<ShellTrackPayload.Shell> track=new ArrayList<>();
  for(ServerPlayer player:level.players()){
   track.clear();
   for(var host:event.hosts()){
    var carrier=carriers.get((int)host.id());if(HealthbarServerBridge.owns(level,carrier))continue;
    // The host carries world space positions, so the range test is the same one the feed always made:
    // measured from this player to where the shell is now.
    double dx=host.current().x-player.getX(),dy=host.current().y-player.getY(),dz=host.current().z-player.getZ();
    if(dx*dx+dy*dy+dz*dz>rangeSqr){lastTooFar++;continue;}
    track.add(new ShellTrackPayload.Shell((int)host.id(),
      host.current().x,host.current().y,host.current().z,
      host.previous().x,host.previous().y,host.previous().z,
      host.motion().x,host.motion().y,host.motion().z,carrier));
   }
   if(track.isEmpty())continue;
   lastFed+=track.size();
   lastTold++;
   SloNetwork.sendToPlayer(player,new ShellTrackPayload(level.dimension().location(),List.copyOf(track)));
  }
 }
 /** A position as three one-decimal numbers, for the diagnostic line. */
 private static String vec(Vec3 v){
  return String.format(java.util.Locale.ROOT,"(%.1f, %.1f, %.1f)",v.x,v.y,v.z);
 }
}
