package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AutocannonFlightPayload;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.ShellTrackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Trail frames from the server's two authoritative world-space endpoints, with no local prediction.
 * Shell hosts feed bounded persistent streaks. The old ten-puff sampler remains a missile/debug API.
 * Near a recently observed muzzle, the first segment is
 * clipped outside the barrel plane and backward particle drift gets a bounded travel allowance.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class TrailTrack {
 private static ClientLevel owner;
 private static java.util.UUID confirmedSession;
 private static long lastLog=Long.MIN_VALUE;
 private static int laid,shellsDrawn;
 private TrailTrack(){}
 /**
  * Registers this client's two listeners for the hosts the server offers.
  *
  * <p>They are two, and separately cancellable, because they have always been two decisions: a shell's
  * light is not its rope, and this mod has kept them behind different switches since before there was an
  * event. Now a third party can cancel one of them by name -- {@code TRACER_LIGHT} or {@code TRACER_TRAIL}
  * -- or run before them and replace either.
  */
 public static void install(){
  dev.sablelightoverhaul.api.EffectApi.onTrackedHost(dev.sablelightoverhaul.effects.EffectBus.DEFAULT_PRIORITY,
    TrailTrack::shellLights);
  dev.sablelightoverhaul.api.EffectApi.onTrackedHost(dev.sablelightoverhaul.effects.EffectBus.DEFAULT_PRIORITY,
    TrailTrack::tracerTrail);
 }
 /**
  * The lights first, and outside the trail switch: a shell's light is not its rope. The same feed that
  * draws the trail is what lets a shell that has left the client's tracking range keep lighting the
  * clouds, which is the whole reason the light side reads it too.
  */
 private static void shellLights(dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent event){
  if(event.side()!=dev.sablelightoverhaul.api.EffectApi.Side.CLIENT)return;
  // The client also offers its smoke as PARTICLE hosts, through the same event; this listener is about the
  // shells the server tracks.
  if(event.hostKind()!=dev.sablelightoverhaul.api.EffectApi.HostKind.PROJECTILE)return;
  if(!(event.level() instanceof ClientLevel level))return;
  if(event.cancellation().blocks(dev.sablelightoverhaul.api.EffectApi.Section.REFERENCED_ENTITIES,
    dev.sablelightoverhaul.effects.DefaultSpecs.TRACER_LIGHT))return;
  ExtraLights.receiveShells(level,event);
 }
 private static void tracerTrail(dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent event){
  if(event.side()!=dev.sablelightoverhaul.api.EffectApi.Side.CLIENT)return;
  if(event.hostKind()!=dev.sablelightoverhaul.api.EffectApi.HostKind.PROJECTILE)return;
  if(!(event.level() instanceof ClientLevel level))return;
  if(event.cancellation().blocks(dev.sablelightoverhaul.api.EffectApi.Section.REFERENCED_ENTITIES,
    dev.sablelightoverhaul.effects.DefaultSpecs.PROJECTILE_STREAK))return;
  for(var host:event.hosts())ProjectileStreaks.accept(level,host);
 }
 public static void receiveAutocannon(dev.sablelightoverhaul.AutocannonTrackPayload payload){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null||!level.dimension().location().equals(payload.data().dimension()))return;
  var filtered=payload.data().shells().stream().filter(s->!HealthbarFlightBridge.suppress(level,s.id(),s.carrier())).toList();
  ExtraLights.receiveAutocannons(level,new ShellTrackPayload(payload.data().dimension(),filtered));
  for(var s:filtered){
   // Entity existence is not render visibility: CBC stops drawing before it is untracked.
   // One SLO streak owns all distances while available; the native renderer remains a fallback.
   ProjectileStreaks.accept(level,new dev.sablelightoverhaul.api.EffectApi.Host(s.id(),
    new Vec3(s.px(),s.py(),s.pz()),new Vec3(s.x(),s.y(),s.z()),new Vec3(s.mx(),s.my(),s.mz()),0,0,true),true,payload.flakIds().contains(s.id()));
  }
 }
 public static boolean hasConfirmedAutocannonFeed(){
  var connection=Minecraft.getInstance().getConnection();
  return connection!=null&&connection.hasChannel(AutocannonFlightPayload.TYPE);
 }
 public static void receiveConfirmedAutocannon(AutocannonFlightPayload payload){
  var level=Minecraft.getInstance().level;
  if(level==null||!level.dimension().location().equals(payload.dimension()))return;
  ProjectileStreaks.maintain(level);
  if(!payload.session().equals(confirmedSession)){
   ProjectileStreaks.retireConfirmedAutocannons();
   confirmedSession=payload.session();
  }
  for(var segment:payload.segments()){
   ProjectileStreaks.accept(level,new dev.sablelightoverhaul.api.EffectApi.Host(segment.visualId(),
       segment.from(),segment.to(),segment.to().subtract(segment.from()),0,0,true),true,segment.flak());
  }
 }
 /** The receiver the mod's network hook calls; no client class is resolved on a dedicated server. */
 public static void receive(ShellTrackPayload payload){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null)return;
  if(owner!=level){owner=level;laid=0;shellsDrawn=0;}
  if(!level.dimension().location().equals(payload.dimension()))return;
  // One message, one event, and the client handles its children itself: the lights and the rope are the
  // two listeners registered above, and either can be cancelled by id without touching the other. The
  // server never has to know which of them a given client wants.
  var hosts=new java.util.ArrayList<dev.sablelightoverhaul.api.EffectApi.Host>(payload.shells().size());
  for(ShellTrackPayload.Shell shell:payload.shells()){
   if(HealthbarFlightBridge.suppress(level,shell.id(),shell.carrier()))continue;
   hosts.add(new dev.sablelightoverhaul.api.EffectApi.Host(shell.id(),
     new Vec3(shell.px(),shell.py(),shell.pz()),
     new Vec3(shell.x(),shell.y(),shell.z()),
     new Vec3(shell.mx(),shell.my(),shell.mz()),
     // A shell takes no part in an aggregate: no size, no heat. Only tracer shells are sent, so
     // everything that arrives here is one.
     0,0,true));
  }
  dev.sablelightoverhaul.effects.EffectBus.trackedHost(
    new dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent(level,
      dev.sablelightoverhaul.api.EffectApi.HostKind.PROJECTILE,java.util.List.copyOf(hosts),java.util.List.of(),
      dev.sablelightoverhaul.api.EffectApi.Side.CLIENT,
      // Every shell is its own host and carries its own id, so the offer needs no group id.
      0,
      dev.sablelightoverhaul.effects.DefaultSpecs.forTrackedHosts(),
      dev.sablelightoverhaul.api.EffectApi.Cancellation.none()));
 }
 /**
  * Ten interpolated samples, except for launch clipping or a very short flight step.
  *
  * <p>The frame count is ten, as CBC uses it, except when the tick covered less than a frame's width:
  * a shell that has almost stopped would otherwise stack ten frames on one spot, which is a bright dot
  * rather than a rope.
  */
 public static void missileTrail(ClientLevel level,dev.sablelightoverhaul.api.EffectApi.Host shell){
  if(!ExtraConfig.TRAIL_ENABLED.get())return;
  Vec3 from=shell.previous();
  Vec3 to=shell.current();
  Vec3 motion=shell.motion();
  double drift=ExtraConfig.TRAIL_DRIFT.get();
  double radius=ExtraConfig.TRAIL_SIZE.get()*1.25*1.35*Math.sqrt(2);
  var guard=shell.id()<0?null:ClientFlashes.STORE.trailGuard(from,to,level.getGameTime(),radius,drift);
  if(guard!=null) {
   from=guard.clipStart(from,to);
   if(from==null)return;
  }
  int frames=from.distanceToSqr(to)>0.5625?10:1;
  for(int i=0;i<frames;i++){
   double partial=frames==1?1.0:i*0.1;
   Vec3 at=new Vec3(
     Mth.lerp(partial,from.x,to.x),
     Mth.lerp(partial,from.y,to.y),
     Mth.lerp(partial,from.z,to.z));
   // The frame's own velocity is the shell's, backwards and damped, plus a whisper of jitter -- the exact
   // form of the /mkTrail command in the version the user verified. The force flag is set: without it
   // vanilla culls a frame beyond 32 blocks, which is exactly the range this feed exists to cover.
   Vec3 velocity=new Vec3(-motion.x*drift+(level.random.nextDouble()-0.5)*0.03,
     -motion.y*drift+(level.random.nextDouble()-0.5)*0.03,
     -motion.z*drift+(level.random.nextDouble()-0.5)*0.03);
   if(guard!=null)velocity=guard.drift(at,velocity);
   level.addParticle(SableLightOverhaul.TRAIL.get(),true,at.x,at.y,at.z,velocity.x,velocity.y,velocity.z);
   laid++;
  }
  shellsDrawn++;
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();
  ClientLevel level=mc.level;
  ProjectileStreaks.clientTick(level,!mc.isPaused());
  if(level==null){owner=null;return;}
  if(owner!=level){owner=level;laid=0;shellsDrawn=0;}
  if(ExtraConfig.on(ExtraConfig.TRAIL_DEBUG)&&GameClock.due(level.getGameTime(),lastLog,20)){
   if(laid>0||shellsDrawn>0){
    com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul trail: {} shell(s) drawn from the feed, {} frame(s) laid in the last second",
      shellsDrawn,laid);
   }
   lastLog=level.getGameTime();laid=0;shellsDrawn=0;
  }
 }
 /** What the debug command feeds: one straight step laid through the same code the feed uses. */
 public static void debugTrail(Vec3 from,Vec3 to){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null)return;
  Vec3 motion=to.subtract(from);
  missileTrail(level,new dev.sablelightoverhaul.api.EffectApi.Host(-1,from,to,motion,0,0,true));
 }
}
