package dev.cbcmuzzlelight.voxy;

import dev.cbcmuzzlelight.lighting.LightingCommon;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Makes a crater visible in the far-distance LOD of clients that run Voxy.
 *
 * <p>Voxy builds its LODs from the chunks the client itself loads, and once a section is ingested it is
 * effectively frozen: the only thing that ever re-ingests is a section-border removal. So a shell that
 * blows a hole in the ground leaves the far LOD showing the terrain as it was, on every client, until
 * the column is ingested again. LSS ("Voxy Server Side") is the thing that can re-serve a column, and
 * {@link LssBridge} tells it which ones changed.
 *
 * <p>Two details make the difference between "the client re-asks and sees the crater" and "the client
 * re-asks and sees the same terrain again":
 *
 * <ol>
 * <li><b>The write.</b> LSS answers from the live loaded chunk when it has one, and otherwise from the
 * region file. An impact can only change a loaded chunk, so the live path is already correct — but a
 * request that arrives after the chunk unloads and before the save would read the old bytes, so the
 * changed chunks are marked unsaved and written out (rate limited: it is a main-thread save).</li>
 * <li><b>The delay.</b> {@code ExplosionEvent.Detonate} fires with the list of blocks <i>about to</i> be
 * destroyed, so nudging straight from the event would advertise a column whose crater does not exist
 * yet. The columns are therefore queued and drained a scan later, by which time the blocks are gone.</li>
 * </ol>
 *
 * <p>Frequency is bounded in four places: a column is not marked again within the debounce window, one
 * event marks at most its column cap, the queue is drained on the existing five-tick scan, and LSS
 * itself batches its dirty pushes on its own broadcast interval.
 */
public final class VoxyLodNudger {
 /** Columns queued for the next scan, per level: packed chunk position -> the tick it was queued on. */
 private static final Map<ServerLevel,Map<Long,Long>> PENDING=new WeakHashMap<>();
 /** When each column was last handed to LSS, per level, for the debounce. */
 private static final Map<ServerLevel,Map<Long,Long>> MARKED=new WeakHashMap<>();
 /** The tick of the last save this mod asked for, per level. */
 private static final Map<ServerLevel,Long> LAST_SAVE=new WeakHashMap<>();
 private static final int PRUNE_AT=8192;
 private VoxyLodNudger(){}
 /**
  * Records the columns a blast is about to change. Called before the blocks are gone, so nothing is
  * marked or written here; {@link #drain} does that on the next scan.
  *
  * <p>A big shell covers far more chunks than the cap, and the affected-block list has no useful order,
  * so the columns kept are the ones nearest the blast: an arbitrary subset would refresh one edge of a
  * crater and leave the middle of it stale in the LOD.
  */
 public static void queue(ServerLevel level,Vec3 centre,Iterable<BlockPos> positions){
  if(!LightingCommon.VOXY_ENABLED.get())return;
  int cap=LightingCommon.VOXY_MAX_COLUMNS.get();
  Map<Long,Long> queue=PENDING.computeIfAbsent(level,key->new HashMap<>());
  long now=level.getGameTime();
  // Distance to the blast per column, over both this event and whatever is still waiting.
  Map<Long,Double> distance=new HashMap<>();
  for(long packed:queue.keySet())distance.put(packed,0.0);
  int seen=0;
  for(BlockPos pos:positions){
   long packed=ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);
   seen++;
   double dx=pos.getX()+0.5-centre.x,dz=pos.getZ()+0.5-centre.z;
   double squared=dx*dx+dz*dz;
   Double previous=distance.get(packed);
   if(previous==null||squared<previous)distance.put(packed,squared);
  }
  if(distance.isEmpty())return;
  var picked=new java.util.ArrayList<>(distance.entrySet());
  if(picked.size()>cap){
   picked.sort(Map.Entry.comparingByValue());
   picked.subList(cap,picked.size()).clear();
  }
  int added=0;
  for(var entry:picked)if(queue.putIfAbsent(entry.getKey(),now)==null)added++;
  if(added>0&&LightingCommon.GEN_DEBUG.get()){
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light voxy: queued {} column(s) of {} touched, nearest the blast at {}",
     added,distance.size(),centre);
  }
 }
 /** Marks and writes whatever the last scan queued. Called from the existing five-tick scan. */
 public static int drain(ServerLevel level){
  if(!LightingCommon.VOXY_ENABLED.get())return 0;
  Map<Long,Long> queue=PENDING.get(level);
  if(queue==null||queue.isEmpty())return 0;
  long now=level.getGameTime();
  int debounce=LightingCommon.VOXY_DEBOUNCE_TICKS.get();
  double immediate=LightingCommon.VOXY_IMMEDIATE_RANGE.get();
  double immediateSqr=immediate*immediate;
  Map<Long,Long> marked=MARKED.computeIfAbsent(level,key->new HashMap<>());
  var changed=new java.util.ArrayList<Long>();
  int columns=0,written=0;
  var iterator=queue.entrySet().iterator();
  while(iterator.hasNext()){
   Map.Entry<Long,Long> entry=iterator.next();
   iterator.remove();
   long packed=entry.getKey();
   Long last=marked.get(packed);
   if(last!=null&&now-last<debounce)continue;
   marked.put(packed,now);
   columns++;
   changed.add(packed);
   int cx=ChunkPos.getX(packed),cz=ChunkPos.getZ(packed);
   // The chunk is loaded whenever an impact could have changed it; a chunk that is not loaded cannot
   // have been cratered by this event, so there is nothing to write and only LSS needs telling.
   LevelChunk chunk=level.getChunkSource().getChunkNow(cx,cz);
   if(chunk!=null){chunk.setUnsaved(true);written++;}
   LssBridge.markDirty(level,cx,cz);
  }
  // Everyone gets LSS's own broadcast for these columns; the players who could actually see the blast
  // are told now instead of up to ten seconds from now. Nobody is excluded from the update.
  int told=0;
  if(columns>0&&immediate>0){
   long[] packed=new long[changed.size()];
   for(int i=0;i<packed.length;i++)packed[i]=changed.get(i);
   var nearby=watchers(level,changed,immediateSqr);
   if(!nearby.isEmpty()){
    // The re-ask has to be answerable before it happens: LSS serves from its timestamp cache while it
    // believes a column is unchanged, so the cache entry is dropped now rather than on its timer.
    LssBridge.invalidate(level,packed);
    told=LssBridge.sendDirtyNow(level,packed,nearby);
   }
  }
  boolean saved=false;
  if(written>0&&LightingCommon.VOXY_SAVE_CHUNKS.get()){
   long interval=LightingCommon.VOXY_SAVE_INTERVAL_TICKS.get();
   long previous=LAST_SAVE.getOrDefault(level,Long.MIN_VALUE);
   if(now-previous>=interval||previous==Long.MIN_VALUE){
    // saveAllChunks only writes the chunks that are unsaved, so in practice this writes the crater and
    // little else. flush=true waits for the write, which is what makes the disk path correct too.
    level.getChunkSource().save(true);
    LAST_SAVE.put(level,now);
    saved=true;
   }
  }
  if(marked.size()>PRUNE_AT)marked.entrySet().removeIf(e->now-e.getValue()>Math.max(1,debounce)*4);
  if((columns>0||saved||told>0)&&LightingCommon.GEN_DEBUG.get()){
   // "accepted" is what LSS actually took; without it a silent LSS is indistinguishable from success.
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light voxy: tried {} column(s), wrote {} chunk(s), saved={}, told {} player(s) now, LSS {} (total marks {}, pushes {})",
     columns,written,saved,told,LssBridge.statusLine(),LssBridge.marks(),LssBridge.pushes());
  }
  return columns;
 }
 /** The players close enough to the changed columns to have seen the blast that made them. */
 private static java.util.List<net.minecraft.server.level.ServerPlayer> watchers(ServerLevel level,java.util.List<Long> columns,double rangeSqr){
  var out=new java.util.ArrayList<net.minecraft.server.level.ServerPlayer>();
  for(var player:level.players()){
   for(long packed:columns){
    double x=(ChunkPos.getX(packed)<<4)+8.0,z=(ChunkPos.getZ(packed)<<4)+8.0;
    // Measured against the column's axis at the player's own height, so this is the horizontal
    // distance to the column: the crater is somewhere down it, not at y=0.
    if(player.distanceToSqr(x,player.getY(),z)<=rangeSqr){out.add(player);break;}
   }
  }
  return out;
 }
 /**
  * Queues a square of columns and drains them immediately, for the debug command.
  * @returns how many columns were handed over.
  */
 public static int nudgeAround(ServerLevel level,BlockPos center,int radius){
  var positions=new java.util.ArrayList<BlockPos>();
  for(BlockPos pos:BlockPos.betweenClosed(center.offset(-radius,0,-radius),center.offset(radius,0,radius)))positions.add(pos.immutable());
  queue(level,center.getCenter(),positions);
  return drain(level);
 }
 /** How many columns are waiting to be drained, for diagnostics. */
 public static int pending(ServerLevel level){
  Map<Long,Long> queue=PENDING.get(level);
  return queue==null?0:queue.size();
 }
}
