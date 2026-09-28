package dev.cbcmuzzlelight.lighting;

import dev.cbcmuzzlelight.FlashlightBeam;
import dev.cbcmuzzlelight.FlashlightPeersPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One player's flashlight settings, kept on the player.
 *
 * <p>They live on the server because they are permission-gated: only an operator may change them, and
 * a client cannot be trusted to enforce that. The values are stored in the player's own persistent data
 * so they survive a relog and a death, and every write is pushed to that one client through
 * {@link dev.cbcmuzzlelight.FlashlightStatePayload}.
 *
 * <p>The on/off switch is stored here too, and for a second reason: other players have to be able to see
 * that your lamp is off. It is deliberately <em>not</em> operator-gated — switching your own torch is not
 * a privilege — while every field that decides what the torch <em>is</em> still is.
 *
 * <p>This class also runs the peer feed: every few ticks it hands each player the lamps of the players
 * near them (see {@link dev.cbcmuzzlelight.FlashlightPeersPayload}). Only numbers the client cannot work
 * out for itself are sent, and only when they change, so a quiet server sends nothing at all.
 *
 * <p>Storage and the page both go through the same clamps, so a page can never store a value the light
 * would refuse to read.
 */
@EventBusSubscriber(modid=dev.cbcmuzzlelight.MuzzleLight.ID)
public final class FlashlightSettings {
 /** Everything the page edits, in the order the page shows it. */
 public static final String[] FIELDS={"color","range","width","decay","intensity","pointRadius","pointIntensity"};
 /** Most peers one client is told about; the client's own light budget trims further. */
 private static final int PEER_LIMIT=8;
 /** Ticks between two peer feeds. Movement within the range is picked up by the receiver, so this only
  * has to be fast enough that a lamp being switched on does not feel delayed. */
 private static final int PEER_INTERVAL_TICKS=5;
 private static final String ROOT="cbcmuzzlelight_flashlight";
 /** What each player was last told, so an unchanged list costs nothing. */
 private static final Map<UUID,List<FlashlightPeersPayload.Peer>> LAST_SENT=new HashMap<>();
 private FlashlightSettings(){}
 /** The whole state, as the payload carries it. */
 public static int[] read(Player player){
  CompoundTag tag=player.getPersistentData().getCompound(ROOT);
  return new int[]{
    value(tag,"color",LightingCommon.FL_COLOR.get()),
    value(tag,"range",LightingCommon.FL_RANGE.get()),
    value(tag,"width",LightingCommon.FL_WIDTH.get()),
    value(tag,"decay",LightingCommon.FL_DECAY.get()),
    value(tag,"intensity",LightingCommon.FL_INTENSITY.get()),
    value(tag,"pointRadius",LightingCommon.FL_POINT_RADIUS.get()),
    value(tag,"pointIntensity",LightingCommon.FL_POINT_INTENSITY.get())};
 }
 /** Whether this player's torch is switched on. A torch you are holding starts on. */
 public static boolean isOn(Player player){
  return on(player.getPersistentData().getCompound(ROOT));
 }
 /**
  * Flips the switch and tells that player. Returns the new state, so the item can say which way it went.
  *
  * <p>Anyone may do this: it is their own lamp and nothing about it is a privilege.
  */
 public static boolean toggle(Player player){
  CompoundTag tag=player.getPersistentData().getCompound(ROOT);
  boolean next=!on(tag);
  tag.putBoolean("on",next);
  player.getPersistentData().put(ROOT,tag);
  sync(player);
  return next;
 }
 /**
  * One field by id, for the page.
  *
  * <p>On the client this answers from the state the server sent, not from the local persistent data:
  * the values are the server's, and a client reading its own config would show the wrong numbers.
  */
 public static double number(Player player,String field){
  int index=indexOf(field);
  if(index<0)return 0.0;
  if(!(player instanceof ServerPlayer))
   return dev.cbcmuzzlelight.client.HeldLights.number(index);
  return read(player)[index];
 }
 /** A write from the page: operators only, clamped, stored, and pushed to that player alone. */
 public static boolean setNumber(Player player,String field,double value){
  int index=indexOf(field);
  if(index<0||!player.hasPermissions(2))return false;
  var tag=player.getPersistentData().getCompound(ROOT);
  tag.putInt(field,clamp(field,(int)Math.round(value)));
  player.getPersistentData().put(ROOT,tag);
  sync(player);
  return true;
 }
 /** Sends this player their own settings; also called when they join, so the client is never guessing. */
 public static void sync(Player player){
  if(player instanceof ServerPlayer server){
   int[] state=read(server);
   // Both the step the page edits and the blocks the beam is drawn with: the client's copy of a server
   // config can differ, and a beam whose length disagreed with the lamp it is aimed at would be a
   // strange bug to chase.
   PacketDistributor.sendToPlayer(server,
     new dev.cbcmuzzlelight.FlashlightStatePayload(isOn(server),state[0],state[1],
       LightingCommon.distance(state[1]),state[2],state[3],state[4],state[5],state[6]));
  }
 }
 /**
  * Tells the players near you about your lamp, and you about theirs.
  *
  * <p>Clearing the remembered list on login matters: the client starts with nothing, so a remembered
  * list that happens to match would leave it permanently in the dark about its neighbours.
  */
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  if(level.getGameTime()%PEER_INTERVAL_TICKS!=0)return;
  syncPeers(level);
 }
 @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event){
  LAST_SENT.remove(event.getEntity().getUUID());
  sync(event.getEntity());
 }
 @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent event){
  LAST_SENT.remove(event.getEntity().getUUID());
 }
 private static void syncPeers(ServerLevel level){
  List<ServerPlayer> players=level.players();
  if(players.size()<2)return;
  double range=LightingCommon.FL_SYNC_RANGE.get();
  for(ServerPlayer viewer:players){
   List<FlashlightPeersPayload.Peer> peers=range>0.0 ? near(level,viewer,range) : List.of();
   if(peers.equals(LAST_SENT.get(viewer.getUUID())))continue;
   LAST_SENT.put(viewer.getUUID(),peers);
   PacketDistributor.sendToPlayer(viewer,new FlashlightPeersPayload(peers));
  }
 }
 /** The lamps of the players within {@code range} of this one, nearest first and capped. */
 private static List<FlashlightPeersPayload.Peer> near(ServerLevel level,ServerPlayer viewer,double range){
  var candidates=new ArrayList<ServerPlayer>();
  double rangeSqr=range*range;
  for(ServerPlayer other:level.players()){
   if(other==viewer||!isOn(other)||!holding(other))continue;
   if(other.distanceToSqr(viewer)>rangeSqr)continue;
   candidates.add(other);
  }
  if(candidates.size()>PEER_LIMIT){
   candidates.sort(Comparator.comparingDouble(other -> other.distanceToSqr(viewer)));
   candidates.subList(PEER_LIMIT,candidates.size()).clear();
  }
  var peers=new ArrayList<FlashlightPeersPayload.Peer>(candidates.size());
  for(ServerPlayer other:candidates){
   int[] state=read(other);
   peers.add(new FlashlightPeersPayload.Peer(other.getUUID(),state[0],LightingCommon.distance(state[1]),
     state[2],state[3],state[4],state[5],state[6]));
  }
  return List.copyOf(peers);
 }
 /** Whether the lamp is in either hand. The client can see this for anyone; the server only lists them. */
 private static boolean holding(Player player){
  return player.getMainHandItem().is(LightingBlocks.FLASHLIGHT.get())
    ||player.getOffhandItem().is(LightingBlocks.FLASHLIGHT.get());
 }
 private static boolean on(CompoundTag tag){return !tag.contains("on")||tag.getBoolean("on");}
 private static int indexOf(String field){
  for(int i=0;i<FIELDS.length;i++)if(FIELDS[i].equals(field))return i;
  return -1;
 }
 private static int value(CompoundTag tag,String field,int fallback){return tag.contains(field)?clamp(field,tag.getInt(field)):fallback;}
 /** The same clamps the page's own inputs use, plus the ones the point light needs. */
 private static int clamp(String field,int value){
  return switch(field){
   case "color","intensity","pointIntensity"->Math.max(0,Math.min(15,value));
   case "range"->Math.max(1,Math.min(LightingCommon.count(),value));
   case "width"->Math.max(FlashlightBeam.MIN_WIDTH_DEGREES,Math.min(FlashlightBeam.MAX_WIDTH_DEGREES,value));
   case "decay"->Math.max(FlashlightBeam.MIN_DECAY_PERCENT,Math.min(FlashlightBeam.MAX_DECAY_PERCENT,value));
   case "pointRadius"->Math.max(0,Math.min(24,value));
   default->value;
  };
 }
}
