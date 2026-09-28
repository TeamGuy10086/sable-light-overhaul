package dev.cbcmuzzlelight;

import dev.cbcmuzzlelight.client.MediumCannonTrails;
import dev.cbcmuzzlelight.lighting.LightingCommon;
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
import net.neoforged.neoforge.network.PacketDistributor;

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
@EventBusSubscriber(modid=MuzzleLight.ID)
public final class TracerFeed {
 /** Shells in flight, keyed weakly so a despawned entity cannot leak. */
 private static final Set<Entity> shells=Collections.newSetFromMap(new WeakHashMap<>());
 private static long lastLog=Long.MIN_VALUE;
 private TracerFeed(){}
 @SubscribeEvent public static void join(EntityJoinLevelEvent event){
  if(event.getLevel().isClientSide)return;
  Entity entity=event.getEntity();
  if(entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile
    ||MediumCannonTrails.shellType(entity))shells.add(entity);
 }
 @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){
  // Deliberately not removed here. Sable re-parents a shell between the outer level and a sub-level, and
  // that fires this event while the shell is still very much in flight; treating it as a death is how a
  // shell ended up tracked for exactly one tick. `isRemoved()` in the tick is the real signal, and the
  // dimension check there lets go of anything left behind by a world change.
 }
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  if(!LightingCommon.TRAIL_FEED_ENABLED.get())return;
  if(shells.isEmpty())return;
  double range=LightingCommon.TRAIL_FEED_RANGE.get();
  double rangeSqr=range*range;
  int fed=0,untraced=0,told=0,dropped=0,tooFar=0;
  var players=level.players();
  List<ShellTrackPayload.Shell> track=new ArrayList<>();
  String refused=null;
  String sampled=null;
  String removed=null;
   for(ServerPlayer player:players){
   track.clear();
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
    if(entity.isRemoved()){
     it.remove(); dropped++;
     if(removed==null)removed=entity.getClass().getSimpleName()+" removed";
     continue;
    }
    // Landed shells leave immediately. CBC keeps a landed projectile for 400 ticks (twenty seconds) and
    // `hasTracer()` reports false for it, so without this test it would sit in the set being counted as
    // "without a tracer" for twenty seconds and, worse, be fed if the flag ever flickered back.
    boolean landed=entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile big
      ?big.isInGround():MediumCannonTrails.inGround(entity);
    if(landed){
     it.remove(); dropped++;
     if(removed==null)removed=entity.getClass().getSimpleName()+" landed";
     continue;
    }
    boolean tracer=entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile big
      ?big.hasTracer():MediumCannonTrails.tracer(entity);
    if(!tracer){
     if(player==players.getFirst()){
      untraced++;
      if(refused==null)refused=entity.getClass().getName()+(entity instanceof rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile big2
        ?" (big cannon: tracer item is "+big2.getTracer()+")":" (addon shell: isTracer() false)");
     }
     continue;
    }
    // The two endpoints, transformed into the space the player sees: a shell on a physics body reports
    // plot coordinates and the player's world needs the pose applied.
    Vec3 now=rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,entity.position());
    Vec3 previous=rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,
      new Vec3(entity.xOld,entity.yOld,entity.zOld));
    Vec3 motion=entity.getDeltaMovement();
    double dx=now.x-player.getX(),dy=now.y-player.getY(),dz=now.z-player.getZ();
    if(dx*dx+dy*dy+dz*dz>rangeSqr){tooFar++;continue;}
    if(sampled==null)sampled="previous "+vec(previous)+" now "+vec(now)+" motion "+vec(motion);
    track.add(new ShellTrackPayload.Shell(entity.getId(),
      now.x,now.y,now.z,previous.x,previous.y,previous.z,motion.x,motion.y,motion.z));
   }
   if(track.isEmpty())continue;
   fed+=track.size();
   told++;
   PacketDistributor.sendToPlayer(player,new ShellTrackPayload(level.dimension().location(),List.copyOf(track)));
  }
  if(LightingCommon.GEN_DEBUG.get()&&(fed>0||untraced>0)&&(lastLog==Long.MIN_VALUE||level.getGameTime()-lastLog>=10)){
   lastLog=level.getGameTime();
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light trail feed: {} tracer shell(s) fed, {} without a tracer, {} out of range, {} player(s) told, set={}, dropped={}, range {}{}{}{}",
     fed,untraced,tooFar,told,shells.size(),dropped,Math.round(range),
     refused==null?"":"; refused: "+refused,
     sampled==null?"":"; "+sampled,
     removed==null?"":"; "+removed);
  }
 }
 /** A position as three one-decimal numbers, for the diagnostic line. */
 private static String vec(Vec3 v){
  return String.format(java.util.Locale.ROOT,"(%.1f, %.1f, %.1f)",v.x,v.y,v.z);
 }
}
