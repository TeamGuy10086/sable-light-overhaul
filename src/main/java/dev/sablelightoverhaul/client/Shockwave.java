package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.ShockwavePayload;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.effects.DefaultSpecs;
import dev.sablelightoverhaul.effects.EffectBus;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * What an explosion throws, from the measurements the server sends: a detonation wave and a ring of dust.
 *
 * <p>Two things are drawn, and they are deliberately separate. The <b>mesh</b> ({@link ShockwaveMesh}) is a
 * spherical detonation wave, sized by the charge and timed by the reader. The <b>ring</b>
 * ({@link ShockwaveRing}) is the KubeJS particle burst ported over: a disc of smoke, cloud and sparks that
 * only belongs on the ground. The server says which of the three shapes this charge was (see
 * {@link ShockwavePayload.BlastClass}) and the ring decides for itself what that permits; the mesh does not
 * care, because a sphere is a sphere wherever it goes off.
 *
 * <p>Everything a reader tunes is in {@code sablelightoverhaul-client.toml} under {@code [shockwave]}.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class Shockwave {
 /** One explosion's detonation wave, as the mesh draws it: the three numbers it was born with and its age. */
 static final class Wave {
  final Vec3 center;
  final float meshSize;
  final float meshTicks;
  final float meshExpansion;
  int age;

  Wave(Vec3 center,float meshSize,float meshTicks,float meshExpansion){
   this.center=center;
   this.meshSize=meshSize;
   this.meshTicks=meshTicks;
   this.meshExpansion=meshExpansion;
  }

  boolean done(){
   return age>meshTicks;
  }
 }

 private static final List<Wave> ACTIVE=new ArrayList<>();
 private static ClientLevel owner;
 private static int wavesSeen,ringBursts;
 private static long lastLog=Long.MIN_VALUE;
 private Shockwave(){}

 /**
  * Registers this client's two impact listeners, once, from the client bootstrap.
  *
  * <p>They are listeners and not code inside {@link #receive} because the arrival of a payload is one event
  * with several children in it, and each child is somebody's separate business: a third party can cancel the
  * dust ring by id and leave the sphere alone, or add a third thing beside them. The server never has to know:
  * it sent one measurement, and this side decides child by child.
  */
 public static void install(){
  EffectApi.onImpact(EffectBus.DEFAULT_PRIORITY,Shockwave::dustRing);
  EffectApi.onImpact(EffectBus.DEFAULT_PRIORITY,Shockwave::detonationWave);
 }

 /** The dust ring: the shockwave's particle path. The sphere is the other listener, hence the child id. */
 private static void dustRing(EffectApi.ImpactEvent event){
  ClientLevel level=clientLevel(event);
  if(level==null)return;
  if(event.cancellation().blocks(EffectApi.Section.SPAWN_PARTICLES,DefaultSpecs.SPAWN_RING))return;
  // MESH means this player asked for the sphere alone: a rendering preference, not an event decision.
  if(ExtraConfig.SHOCKWAVE_MODE.get()==ExtraConfig.ShockwaveMode.MESH)return;
  ShockwaveRing.burst(level,event);
  ringBursts++;
 }

 /**
  * The detonation wave. Its three numbers are snapshotted here so one explosion's motion cannot change under
  * it, and so that a config edit is felt on the next charge rather than halfway through the current one. The
  * config's duration is in seconds and the model counts in ticks, hence the 20. The size comes from the
  * charge: the coefficient is calibrated so that power 24 gives 60 blocks, and every smaller charge scales
  * down.
  */
 private static void detonationWave(EffectApi.ImpactEvent event){
  if(clientLevel(event)==null)return;
  if(event.cancellation().blocks(EffectApi.Section.SPAWN_PARTICLES,DefaultSpecs.SPAWN_MESH))return;
  if(ExtraConfig.SHOCKWAVE_MODE.get()==ExtraConfig.ShockwaveMode.PARTICLES)return;
  float size=(float)ShockwaveMesh.meshRadius(event.yield(),ExtraConfig.SHOCKWAVE_MESH_SIZE.get());
        // The configured duration is the time the calibration charge (power 24, 60 blocks) takes; every other charge
        // gets the time its own radius implies at that same speed. A fixed duration made a small wave cross its shorter
        // distance at a quarter of the speed, which read as "the little ones crawl". The clamps keep it from becoming a
        // one-frame blink at the bottom, or a long crawl at the top.
        double configured=(float)(ExtraConfig.SHOCKWAVE_MESH_DURATION.get()*20.0);
        double share=size/60.0;
        float ticks=(float)Math.min(Math.max(configured*share,0.45*configured),2.0*configured);
  float expansion=ExtraConfig.SHOCKWAVE_MESH_EXPANSION.get().floatValue();
  ACTIVE.add(new Wave(event.center(),size,ticks,expansion));
 }

 /**
  * The level of an impact this side is allowed to draw, or null.
  *
  * <p>An impact is dispatched on the side the blast happened on, and in single player both dispatches reach
  * the same bus, so a listener that draws for the client has to say so. Anything that is not a client level
  * is the server's own event, which the server side has already handled.
  */
 private static ClientLevel clientLevel(EffectApi.ImpactEvent event){
  return event.level() instanceof ClientLevel level?level:null;
 }

 /** The receiver the mod's network hook calls; no client class is resolved on a dedicated server. */
 public static void receive(ShockwavePayload payload){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null)return;
  if(owner!=level){ACTIVE.clear();owner=level;ShockwaveRing.clear();}
  if(!level.dimension().location().equals(payload.dimension()))return;
  if(!ExtraConfig.SHOCKWAVE_ENABLED.get())return;
  ExtraConfig.ShockwaveMode mode=ExtraConfig.SHOCKWAVE_MODE.get();
  // Nothing is suppressed here on purpose: a blast is always spawned, and whether any given particle is
  // visible is decided per particle per frame (particles spread far vertically, so a blast centre says
  // nothing about where they end up).
  // Have this blast's directions ready before its particles are drawn: its own core first, then the ring it
  // will spread across. A barrage otherwise arrives with more new directions than one pass can cast, and
  // the particles that miss out fall back to visible for a few frames.
  VoxyAccess.prepare(payload.center(),
    Math.max(8.0,ShockwaveMesh.meshRadius(payload.yield(),ExtraConfig.SHOCKWAVE_MESH_SIZE.get())));
  // The artillery haze that used to be spawned here is gone: it was an experiment behind its own switch, its
  // own particle kind and its own quota, and it is not part of what an impact is. So it is neither dispatched
  // to a listener nor drawn by one.
  // One measurement, one event; the ring and the sphere are two listeners on it, registered above. The
  // entity that fired is a server side fact and is not resolvable here, so the event carries no source.
  EffectBus.impact(new EffectApi.ImpactEvent(level,payload.center(),null,payload.yield(),payload.solidity(),
    payload.reach(),payload.blocks(),ShockwavePayload.toHitClass(payload.blastClass()),payload.tier(),
    null,null,DefaultSpecs.forImpact(),EffectApi.Cancellation.none()));
  wavesSeen++;
  if(ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)){
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul shockwave recv: blocks={} yield={} solidity={} class={} mode={} | mesh radius {} over {} s",
     payload.blocks(),fmt(payload.yield()),fmt(payload.solidity()),payload.blastClass(),mode,
     ACTIVE.isEmpty()?"-":fmt(ACTIVE.get(ACTIVE.size()-1).meshSize),
     ACTIVE.isEmpty()?"-":fmt(ACTIVE.get(ACTIVE.size()-1).meshTicks/20.0));
  }
 }

 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null){ACTIVE.clear();owner=null;ShockwaveRing.clear();return;}
  if(owner!=level){ACTIVE.clear();owner=level;ShockwaveRing.clear();}
  // The ring spawns its own batches, one per client tick, exactly as the script did.
  // The ring spawns itself on arrival (see ShockwaveRing.burst), so there is nothing to drive here.
  for(Iterator<Wave> it=ACTIVE.iterator();it.hasNext();){
   Wave wave=it.next();
   wave.age++;
   if(wave.done())it.remove();
  }
  if(ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)&&GameClock.due(level.getGameTime(),lastLog,20)){
   if(wavesSeen>0||ringBursts>0){
    com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul shockwave: {} wave(s) received, {} ring burst(s) queued, {} still spawning",
      wavesSeen,ringBursts,ShockwaveRing.pending());
   }
   lastLog=level.getGameTime();wavesSeen=0;ringBursts=0;
  }
 }

 /** The live waves, for the mesh renderer. */
 static List<Wave> waves(){return ACTIVE;}

 @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event){
  ACTIVE.clear();owner=null;ShockwaveRing.clear();
 }

 /** Whether anything is being drawn, for a debug command to report. */
 public static int activeWaves(){return ACTIVE.size();}

 private static String fmt(double value){
  return String.format(java.util.Locale.ROOT,"%.2f",value);
 }
}
