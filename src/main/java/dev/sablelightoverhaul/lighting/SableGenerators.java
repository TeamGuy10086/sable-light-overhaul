package dev.sablelightoverhaul.lighting;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Finds our generators inside Sable sub-levels.
 *
 * <p>Loaded separately, and only reached when Sable is present.
 *
 * <p>The plot's own chunks are walked, exactly the way Sable's own code reaches a plot's block
 * entities, instead of asking the host level for the chunk at a mapped position: a sub-level owns
 * its storage, so the host level is not guaranteed to answer for it. Each block entity is then
 * turned into an anchor, and if the position a chunk reports is not the space anchors use, the same
 * position is mapped through the plot's origin and tried again — so this works whether a plot stores
 * global chunk coordinates or plot-local ones.
 */
public final class SableGenerators {
 private SableGenerators(){}
 /** Whether this world position belongs to a physics body rather than to the world itself. */
 public static boolean owned(ServerLevel level,BlockPos pos){return SourceAnchor.capture(level,pos).ship()!=null;}
 static List<GeneratorServer.Found> scan(ServerLevel level){
  var container=SubLevelContainer.getContainer(level);
  if(container==null)return List.of();
  List<GeneratorServer.Found> found=new ArrayList<>();
  Set<Long> seen=new HashSet<>();
  for(var subLevel:container.getAllSubLevels()){
   if(subLevel.isRemoved())continue;
   var plot=subLevel.getPlot();
   for(var holder:plot.getLoadedChunks()){
    var chunk=holder.getChunk();
    if(chunk==null)continue;
    for(var entry:chunk.getBlockEntities().entrySet()){
     if(!(entry.getValue() instanceof GeneratorBlockEntity generator))continue;
     var anchor=anchorFor(level,plot,chunk,entry.getKey());
     if(anchor==null)continue;
     if(!seen.add(anchor.ship().getMostSignificantBits()^anchor.pos().asLong()))continue;
     found.add(new GeneratorServer.Found(anchor,generator));
    }
   }
  }
  return found;
 }
 /** The anchor for one block entity, trying both coordinate spaces a plot chunk may report. */
 private static SourceAnchor anchorFor(ServerLevel level,dev.ryanhcode.sable.sublevel.plot.LevelPlot plot,
                                       net.minecraft.world.level.chunk.LevelChunk chunk,BlockPos raw){
  var anchor=SourceAnchor.capture(level,raw);
  if(anchor.ship()!=null)return anchor;
  var global=plot.toGlobal(chunk.getPos());
  BlockPos mapped=new BlockPos((global.x<<4)+(raw.getX()&15),raw.getY(),(global.z<<4)+(raw.getZ()&15));
  anchor=SourceAnchor.capture(level,mapped);
  return anchor.ship()==null?null:anchor;
 }
}
