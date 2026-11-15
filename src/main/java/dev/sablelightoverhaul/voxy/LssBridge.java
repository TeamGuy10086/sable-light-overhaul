package dev.sablelightoverhaul.voxy;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * The optional hop into LSS ("Voxy Server Side"), the server mod that feeds distant LODs to Voxy
 * clients and knows how to tell them a column changed.
 *
 * <p>LSS is not on this mod's compile classpath and never will be: it is an optional third-party mod
 * with a protocol of its own, so everything here is looked up by name once and called through
 * reflection afterwards. Missing mod, renamed internals, a server that has not started yet — every one
 * of them ends in "no bridge" and a silent no-op, never in an exception during a shot.
 *
 * <p>The call itself is {@code DirtyColumnTracker.markDirty(dimension, chunkX, chunkZ)}: it puts the
 * column in the set LSS drains on its own broadcast interval, which then invalidates every cached
 * timestamp for it and pushes a dirty-column message to the players within LSS's LOD distance. Those
 * players re-ask for the column and LSS re-serves it, which is what makes a crater appear in the LOD.
 *
 /** Deliberately not called: {@code drainDirty}. Draining by hand would break LSS's own reference
  * counting for the in-flight invalidations it is tracking.
  *
  * <p>There is a second, faster path for the players who could actually see the blast: LSS's own
  * broadcast runs on a timer (ten seconds by default), so a nearby player would wait that long to be
  * told, and by then the dust has settled. {@link #sendDirtyNow} pushes the same message to those
  * players straight away, and {@link #invalidate} makes sure their re-ask is answered with the new
  * column rather than out of LSS's timestamp cache.
  */
public final class LssBridge {
 /** The class holding LSS's server-side request service. */
 private static final String NETWORKING="dev.vox.lss.networking.server.LSSServerNetworking";
 /** The service that owns the dirty set. */
 private static final String SERVICE="dev.vox.lss.networking.server.RequestProcessingService";
 /** The tracker whose markDirty is the entry point. */
 private static final String TRACKER="dev.vox.lss.common.tracking.DirtyColumnTracker";
 /** The off-thread processor whose timestamp cache has to be invalidated for a re-ask to be re-read. */
 private static final String PROCESSOR="dev.vox.lss.common.processing.OffThreadProcessor";
 /** The state a player has once LSS has accepted them; only those clients understand the message. */
 private static final String STATE="dev.vox.lss.common.processing.AbstractPlayerRequestState";
 /** LSS's "these columns changed" message, which its client half turns into a re-ask. */
 private static final String DIRTY_PAYLOAD="dev.vox.lss.networking.payloads.DirtyColumnsS2CPayload";
 private static final String MOD_ID="lss";
 private static boolean looked;
 private static Method requestService;
 private static Method dirtyTracker;
 private static Method markDirty;
 private static Method offThreadProcessor;
 private static Method invalidateTimestamps;
 private static Method getPlayers;
 private static Method handshakeDone;
 private static java.lang.reflect.Constructor<?> dirtyPayload;
 /** How many marks have been handed over, for the log line. */
 private static long marks;
 private static long pushes;
 /** Why marks are not landing, counted so a single log line can say it out loud. */
 private static long serviceNull,unresolved,errors;
 private static boolean toldAboutServiceNull;
 private LssBridge(){}
 /** True when LSS's service object exists at all. It does not in a single-player world until LAN is opened. */
 public static boolean serviceLive(){
  if(!present()||!resolve())return false;
  try{return requestService.invoke(null)!=null;}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){return false;}
 }
 /** How many players LSS has accepted, and how many of those finished the handshake. */
 public static int[] clients(){
  if(!present()||!resolve()||getPlayers==null)return new int[]{0,0};
  try{
   Object service=requestService.invoke(null);
   if(service==null)return new int[]{0,0};
   Object players=getPlayers.invoke(service);
   if(!(players instanceof java.util.Map<?,?> map))return new int[]{0,0};
   int total=map.size(),done=0;
   for(Object state:map.values()){
    try{if(Boolean.TRUE.equals(handshakeDone.invoke(state)))done++;}catch(ReflectiveOperationException|RuntimeException ignored){}
   }
   return new int[]{total,done};
  }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){
   return new int[]{0,0};
  }
 }
 /** One line for the debug command: whether marks are landing, and why not when they are not. */
 public static String statusLine(){
  if(!present())return "LSS absent";
  if(!resolve())return "LSS present but unresolved ("+unresolved+" attempt(s))";
  int[] clients=clients();
  return "accepted="+marks+", serviceNull="+serviceNull+", unresolved="+unresolved+", errors="+errors
    +", clients="+clients[0]+" (handshaked "+clients[1]+")";
 }
 /** True when the LSS mod is loaded at all; the bridge may still fail to resolve inside it. */
 public static boolean present(){return ModList.get().isLoaded(MOD_ID);}
 /** True when the reflective path into LSS resolved, so marks actually go somewhere. */
 public static boolean ready(){return present()&&resolve();}
 public static long marks(){return marks;}
 /** How many immediate pushes have been delivered, for the log line. */
 public static long pushes(){return pushes;}
 /**
  * Invalidates LSS's cached timestamps for these columns right now.
  *
  * <p>Without this an immediate nudge would be pointless: LSS answers a request from its timestamp
  * cache when it believes the column is unchanged, so the client would be told "up to date" and keep
  * the old terrain. LSS's own broadcast does this on its timer, which is exactly the delay being
  * skipped here. The two-argument form is used on purpose: it passes no completion callback, and since
  * these columns were never drained out of the tracker there is no reference count to settle.
  */
 public static void invalidate(ServerLevel level,long[] packedColumns){
  if(packedColumns.length==0||!present()||!resolve())return;
  try{
   Object service=requestService.invoke(null);
   if(service==null)return;
   Object processor=offThreadProcessor.invoke(service);
   if(processor==null)return;
   invalidateTimestamps.invoke(processor,level.dimension().location().toString(),packedColumns);
  }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){
   // Same rule as everywhere else here: an optional mod must never break a shot.
  }
 }
 /**
  * Hands the changed columns straight to the players near them, instead of waiting for LSS's timer.
  *
  * <p>Only players LSS has already accepted are sent anything. That is not politeness: a client
  * without LSS has no handler for this message, and NeoForge drops a connection that receives an
  * unregistered payload, so sending it to everyone would kick vanilla players off the server.
  * @returns how many players were told.
  */
 public static int sendDirtyNow(ServerLevel level,long[] packedColumns,java.util.List<net.minecraft.server.level.ServerPlayer> nearby){
  if(packedColumns.length==0||nearby.isEmpty()||!present()||!resolve())return 0;
  Object service;
  Object players;
  try{
   service=requestService.invoke(null);
   if(service==null)return 0;
   players=getPlayers.invoke(service);
  }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){
   return 0;
  }
  if(!(players instanceof java.util.Map<?,?> map))return 0;
  int sent=0;
  for(var player:nearby){
   Object state=map.get(player.getUUID());
   if(state==null)continue;
   try{
    if(!Boolean.TRUE.equals(handshakeDone.invoke(state)))continue;
    Object payload=dirtyPayload.newInstance((Object)packedColumns);
    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,(net.minecraft.network.protocol.common.custom.CustomPacketPayload)payload);
    sent++;
   }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){
    // A player whose state cannot be read is simply left to LSS's own broadcast.
   }
  }
  pushes+=sent;
  return sent;
 }
 /**
  * Tells LSS that one column's contents changed, so it re-serves it to the clients that want it.
  * @returns true when LSS took the mark.
  */
 public static boolean markDirty(ServerLevel level,int chunkX,int chunkZ){
  if(!present())return false;
  if(!resolve()){unresolved++;return false;}
  try{
   // The service is rebuilt when a server starts, so it is read again on every call rather than cached.
   Object service=requestService.invoke(null);
   if(service==null){
    // This is the single-player case, and it is LSS's own decision rather than a failure:
    // onServerStarted refuses to start the service unless the server is dedicated, so an integrated
    // server has none until the world is opened to LAN. Said once, loudly, because otherwise a silent
    // zero looks like our bug.
    serviceNull++;
    if(!toldAboutServiceNull){
     toldAboutServiceNull=true;
     com.mojang.logging.LogUtils.getLogger().warn("Sable Light Overhaul voxy: LSS is installed but its LOD service is not running, so nothing can be refreshed. LSS defers it in a single-player world until the game is opened to LAN (or the server is dedicated); open to LAN, or start with -Dlss.test.integratedServer=true.");
    }
    return false;
   }
   Object tracker=dirtyTracker.invoke(service);
   if(tracker==null)return false;
   markDirty.invoke(tracker,level.dimension().location().toString(),chunkX,chunkZ);
   marks++;
   return true;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError problem){
   // A shot must never fail because an optional mod changed its internals.
   errors++;
   if(errors==1)com.mojang.logging.LogUtils.getLogger().warn("Sable Light Overhaul voxy: LSS markDirty failed, LOD refresh disabled for this session ({})",problem.toString());
   return false;
  }
 }
 /** Resolved once; a failed lookup is remembered as "no bridge" instead of retried every shot. */
 private static boolean resolve(){
  if(looked)return markDirty!=null;
  looked=true;
  try{
   ClassLoader loader=LssBridge.class.getClassLoader();
   Class<?> networking=Class.forName(NETWORKING,false,loader);
   Class<?> service=Class.forName(SERVICE,false,loader);
   Class<?> tracker=Class.forName(TRACKER,false,loader);
   requestService=networking.getMethod("getRequestService");
   dirtyTracker=service.getMethod("getDirtyTracker");
   markDirty=tracker.getMethod("markDirty",String.class,int.class,int.class);
   // The fast path is optional: when only these lookups fail, marks still work and everything simply
   // falls back to LSS's own broadcast timer.
   try{
    Class<?> processor=Class.forName(PROCESSOR,false,loader);
    Class<?> state=Class.forName(STATE,false,loader);
    Class<?> payload=Class.forName(DIRTY_PAYLOAD,false,loader);
    offThreadProcessor=service.getMethod("getOffThreadProcessor");
    invalidateTimestamps=processor.getMethod("invalidateTimestamps",String.class,long[].class);
    getPlayers=service.getMethod("getPlayers");
    handshakeDone=state.getMethod("hasCompletedHandshake");
    dirtyPayload=payload.getConstructor(long[].class);
   }catch(ReflectiveOperationException|LinkageError|RuntimeException fastPath){
    offThreadProcessor=null;
    invalidateTimestamps=null;
    getPlayers=null;
    handshakeDone=null;
    dirtyPayload=null;
    com.mojang.logging.LogUtils.getLogger().warn("Sable Light Overhaul voxy: LSS marked but the immediate push is unavailable, falling back to its broadcast timer ({})",fastPath.toString());
   }
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul voxy: LSS bridge resolved ({}#markDirty, immediate push {})",TRACKER,dirtyPayload!=null?"available":"unavailable");
   return true;
  }catch(ReflectiveOperationException|LinkageError|RuntimeException problem){
   com.mojang.logging.LogUtils.getLogger().warn("Sable Light Overhaul voxy: LSS present but its internals did not resolve, LOD refresh disabled ({})",problem.toString());
   markDirty=null;
   return false;
  }
 }
}
