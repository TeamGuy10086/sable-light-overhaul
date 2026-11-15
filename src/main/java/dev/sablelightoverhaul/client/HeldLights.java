package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AreaLight;
import dev.sablelightoverhaul.FlashlightBeam;
import dev.sablelightoverhaul.FlashlightPeersPayload;
import dev.sablelightoverhaul.FlashlightStatePayload;
import dev.sablelightoverhaul.lighting.LightingBlocks;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The light a player carries: a small point light around them and a cone down their gaze.
 *
 * <p>Built on the client, every tick, from the state the server already sent — there is no per-tick
 * traffic and nothing to keep in sync. The values are the server's, because the page that edits them is
 * operator-only, so a client cannot simply decide it is carrying a searchlight.
 *
 * <p><b>The lamp is at the waist, not at the eye.</b> That is what lets it cast a shadow at all: a ray
 * that starts inside the camera reads the depth buffer in front of the lens and the whole scene's
 * lighting crawls, which is why this light used to be marked "never shadow". Off the eye, it is an
 * ordinary light and behaves like one.
 *
 * <p><b>Other players' lamps are drawn here too</b>, from the list the server sends
 * ({@link FlashlightPeersPayload}). Only the numbers travel: where a peer is and where they are looking
 * arrive with their entity, so the only per-tick work is building the same two shapes for each of them.
 *
 * <p>The two lights do different jobs and fall off differently, which is the point: the point light
 * uses the plain radial falloff (bright at the player, gone at the rim), while the beam is a cone whose
 * brightness also fades along its length, so a long beam reads as a beam rather than a flat disc. How
 * fast it fades is {@code decay}; how wide it is is a plain angle in degrees. Both are
 * {@link FlashlightBeam}'s arithmetic, shared with the page so the two cannot disagree.
 */
public final class HeldLights {
 /**
  * Where the lamp sits, as a fraction of the player's own eye height.
  *
  * <p>Anchored to the eye height rather than to a fixed 0.9 blocks so it follows the pose: standing it
  * lands at 0.89 blocks, and a sneaking or swimming player carries it proportionally lower instead of
  * wearing it round their knees. The point light and the beam share the origin, so the cone's apex is
  * exactly where the glow is.
  */
 private static final double WAIST_FRACTION=0.55;
 /** The state the server sent. Defaults are what a player has before their first sync. */
 private static boolean on=true;
 private static int color;
 private static int rangeSteps=3;
 private static int rangeBlocks=80;
 private static int width=22;
 private static int decay=FlashlightBeam.DEFAULT_DECAY_PERCENT;
 private static int falloff=FlashlightBeam.DEFAULT_PROFILE;
 private static int intensity=10;
 private static int pointRadius=3;
 private static int pointIntensity=10;
 private static int volumeMode=1;
 private static boolean synced;
 /** The lamps of nearby players, replaced wholesale by every message. */
 private static volatile Map<UUID,FlashlightPeersPayload.Peer> peers=Map.of();
 private HeldLights(){}
 /** The server's answer for this player: what to draw when the flashlight is in hand. */
 public static void receive(FlashlightStatePayload payload){
  on=payload.on();
  color=payload.color();
  rangeSteps=payload.rangeSteps();
  rangeBlocks=payload.rangeBlocks();
  width=payload.width();
  decay=payload.decay();
  falloff=payload.falloff();
  intensity=payload.intensity();
  pointRadius=payload.pointRadius();
  pointIntensity=payload.pointIntensity();
  volumeMode=payload.volumeMode();
  synced=true;
 }
 /**
  * The neighbouring players' lamps. The whole set replaces the old one, so a peer who switched off,
  * dropped the lamp or walked out of range simply stops arriving.
  */
 public static void receivePeers(FlashlightPeersPayload payload){
  var next=new java.util.LinkedHashMap<UUID,FlashlightPeersPayload.Peer>(payload.peers().size());
  for(var peer:payload.peers())next.put(peer.id(),peer);
  peers=Map.copyOf(next);
 }
 public static boolean synced(){return synced;}
 /** How many peers are being drawn, for the debug line. */
 public static int peerCount(){return peers.size();}
 /**
  * One field for the page, by the index {@code FlashlightSettings.FIELDS} uses.
  *
  * <p>The page edits a distance step, so that is what it must see here, even though the light is drawn
  * from the length in blocks.
  */
 public static double number(int index){
  return switch(index){
   case 0->color;
   case 1->rangeSteps;
   case 2->width;
   case 3->decay;
   case 4->falloff;
   case 5->intensity;
   case 6->pointRadius;
   case 7->pointIntensity;
   case 8->volumeMode;
   default->0;
  };
 }
 /** One call per tick, from the light pass. Nothing is added unless a lamp is actually held and on. */
 public static void append(ClientLevel level,Map<String,AreaSnapshot> world){
  var player=Minecraft.getInstance().player;
  if(player==null||!level.isClientSide)return;
  if(on&&holding(player)){
   lay(world,"held/"+player.getUUID(),player,color,rangeBlocks,width,decay,falloff,intensity,pointRadius,
     pointIntensity,volumeMode,
     // The light the player is looking down is the one worth a shadow slot, and the mod reserves one for
     // it. Its own small halo is not: a second depth march per frame for a 3-block glow is not a trade
     // worth making, and the halo's own block is washed out by its light anyway.
     !ExtraConfig.GPU_FLASHLIGHT_SHADOW.get(),true);
  }
  for(var entry:peers.entrySet()){
   var other=byId(level,entry.getKey());
   if(other==null)continue;
   var peer=entry.getValue();
   lay(world,"peer/"+peer.id(),other,peer.color(),peer.rangeBlocks(),peer.width(),peer.decay(),peer.falloff(),
     peer.intensity(),peer.pointRadius(),peer.pointIntensity(),peer.volumeMode(),
     !ExtraConfig.GPU_FLASHLIGHT_PEER_SHADOW.get(),true);
  }
 }
 /**
  * One lamp: the halo at the waist and the cone down the gaze, both starting at the same point.
  *
  * <p>{@code pointSelfCull} and {@code beamSelfCull} are the pack's single per-light flag. It means two
  * things at once — "do not light your own block" and "do not cast a shadow" — because the patched shader
  * has one bit for both. That is why the two callers above pass the shadow decision straight in.
  */
 private static void lay(Map<String,AreaSnapshot> world,String key,Player player,int color,
     int rangeBlocks,int width,int decay,int falloff,int intensity,int pointRadius,int pointIntensity,int volumeMode,
     boolean beamSelfCull,boolean pointSelfCull){
  Vec3 waist=waist(player);
  if(pointRadius>0&&pointIntensity>0){
   // A plain radial light: brightness falls off with distance in every direction, which is what makes
   // it read as a lamp the player is carrying rather than a second beam. Its profile is its own and does
   // not follow the beam's decay: it is a glow, not a cone.
   double core=0.15,middle=0.575,tail=0.5;
   world.put(key+"/point",snapshot(new AreaLight(waist.x,waist.y,waist.z,1,0,0,pointRadius,pointRadius,
     core,pointIntensity,false,middle,tail),color,pointSelfCull));
  }
  if(rangeBlocks>0&&intensity>0){
   Vec3 look=player.getLookAngle();
   if(look.lengthSqr()>1.0E-8){
    // A beam, for one reason: its origin is its apex. The lamp the player carries has to start exactly at
    // the waist, and a cone's x/y/z is the midpoint of its axis, so every cone is a guess about where the
    // player's body is relative to a box. `axial` for a beam is the range and `lateral` is the tangent of
    // the half angle, which is the spotlight's own profile -- and this one dims along its length as well
    // as across it, so it reads as a lamp rather than as a stripe of constant brightness.
    // Two curves, two knobs: the cross-section is one of the five profiles the spotlights offer, and the
    // length fades on its own ramp whose speed is "decay". They used to be one profile driven by one number,
    // so shortening the beam's reach also narrowed its cone.
    var profile=dev.sablelightoverhaul.BeamLight.profile(falloff);
    var light=AreaLight.beam(waist.x,waist.y,waist.z,look.x,look.y,look.z,
      rangeBlocks,FlashlightBeam.halfAngleTangent(width),
      profile.core(),profile.middle(),profile.tail(),intensity*profile.brightness(),
      // The one thing a hand torch asks for that a searchlight does not: it fades along its own length,
      // so it reads as a pool of light instead of a stripe of constant brightness.
      true,FlashlightBeam.knee(decay));
    world.put(key+"/beam",snapshot(light,color,beamSelfCull));
    float[] tint=new float[3];
    dev.sablelightoverhaul.lighting.SourceColor.rgb(color,(int)player.level().getGameTime(),tint,
      ExtraConfig.GPU_RED.get().floatValue(),ExtraConfig.GPU_GREEN.get().floatValue(),ExtraConfig.GPU_BLUE.get().floatValue());
    double cosHalf=1.0/Math.sqrt(1+Math.pow(FlashlightBeam.halfAngleTangent(width),2));
    int beam=IrisMuzzleBridge.addBeam(waist,look.normalize(),cosHalf,.12F,intensity/15F,tint[0],tint[1],tint[2],rangeBlocks,falloff,volumeMode);
    if(beam>=0) SpotShadows.attach(beam,key.hashCode(),(ClientLevel)player.level(),waist,look.normalize(),cosHalf,rangeBlocks,volumeMode,.12);
   }
  }
 }
 /** The point both of this player's lights start from: the middle of their body, not their eyes. */
 private static Vec3 waist(Player player){
  return new Vec3(player.getX(),player.getY()+player.getEyeHeight()*WAIST_FRACTION,player.getZ());
 }
 private static boolean holding(Player player){
  return player.getMainHandItem().is(LightingBlocks.FLASHLIGHT.get())
    ||player.getOffhandItem().is(LightingBlocks.FLASHLIGHT.get());
 }
 /** The client's own view of a peer. Absent means the client is not tracking them, so there is nowhere
  * to put the light: the server's own range default is the vanilla tracking range for exactly this
  * reason. */
 private static Player byId(ClientLevel level,UUID id){
  for(AbstractClientPlayer other:level.players())if(other.getUUID().equals(id))return other;
  return null;
 }
 private static AreaSnapshot snapshot(AreaLight light,int color,boolean selfCull){
  return new AreaSnapshot(light,AreaSnapshot.bounds(light),null,color,selfCull);
 }
}
