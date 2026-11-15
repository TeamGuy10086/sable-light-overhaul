package dev.sablelightoverhaul.lighting;
import java.util.ArrayList;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
/**
 * Server-side probe for the far entity pass, behind {@code farEntities.debug}. It prints what the server
 * itself believes about the entities the client is missing, once a second:
 *
 * <ul>
 *   <li>{@code dist} -- how far the entity is from the nearest player <em>on the server</em>. If this is
 *       already far outside the view distance while the client sees nothing, the entity does exist where
 *       it is drawn and only delivery is in question. If instead it sits at plot coordinates (tens of
 *       thousands of blocks out), Sable has parked the whole sub-level and no tracking change can help:
 *       the thing to fix would be the parking, not the tracking.</li>
 *   <li>{@code chunkTracked} -- vanilla's second gate in {@code ChunkMap$TrackedEntity.updatePlayer}:
 *       the chunk has to be inside the player's tracking view, and no entity packet is sent otherwise.
 *       SSRD never touches this gate, so if it reads false here, lifting the range alone is not enough.</li>
 *   <li>{@code plot} -- whether Sable owns a plot at the entity's chunk, the same test SSRD gates its own
 *       bypass on.</li>
 * </ul>
 *
 * <p>Normal play keeps this off: it is one line per matching entity per second, and only on the server.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class FarEntityDebug{
 private FarEntityDebug(){}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  // Only its own flag, not the global debug.effects master: this probe writes a line per entity per second, which is fine while
  // someone is specifically looking at far-entity tracking and unreadable otherwise.
  if(!LightingCommon.SPEC.isLoaded()||!LightingCommon.FAR_ENTITY_DEBUG.get())return;
  if(level.getGameTime()%20L!=0L)return;
  var players=level.players();
  if(players.isEmpty())return;
  var names=LightingCommon.FAR_ENTITY_NAMES.get();
  var lines=new ArrayList<String>();
  for(Entity entity:level.getAllEntities()){
   ResourceLocation id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
   if(id==null||!FarEntities.matchesName(id.getPath(),names))continue;
   ServerPlayer near=null;double best=-1.0;boolean tracked=false;
   for(ServerPlayer player:players){
    double d=Math.sqrt(player.distanceToSqr(entity));
    if(near==null||d<best){near=player;best=d;tracked=player.getChunkTrackingView().contains(entity.chunkPosition().x,entity.chunkPosition().z);}
   }
   String vehicle=entity.getVehicle()==null?"-":String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getVehicle().getType()));
   lines.add(id+" pos=("+(int)entity.getX()+","+(int)entity.getY()+","+(int)entity.getZ()+") chunk="+entity.chunkPosition().x+","+entity.chunkPosition().z
    +" plot="+inPlot(level,entity)+" dist="+(int)best+" chunkTracked="+tracked+" vehicle="+vehicle+" passengers="+entity.getPassengers().size());
  }
  if(lines.isEmpty())return;
  var log=com.mojang.logging.LogUtils.getLogger();
  log.info("far entity probe: {} matching, range={} blocks, viewCap={} ->",lines.size(),FarEntities.range(),level.getServer().getPlayerList().getViewDistance()*16);
  for(String line:lines)log.info("far entity probe:   {}",line);
 }
 /** The same test SSRD uses: Sable owns a plot at the entity's chunk. Silent when Sable is absent. */
 private static boolean inPlot(ServerLevel level,Entity entity){
  try{
   var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
   return container!=null&&container.getPlot(entity.chunkPosition())!=null;
  }catch(Throwable absent){return false;}
 }
}
