package dev.cbcmuzzlelight.lighting;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
/** Ship positions are relative to plot origin, never persistent global plot allocations. */
public record SourceAnchor(UUID ship,BlockPos pos) {
 public static SourceAnchor capture(Level level,BlockPos pos) {
  return ModList.get().isLoaded("sable") ? SableAnchors.capture(level,pos) : new SourceAnchor(null,pos.immutable());
 }
 public BlockPos resolve(Level level) { return ship==null?pos:ModList.get().isLoaded("sable")?SableAnchors.resolve(level,this):null; }
 public Vec3 world(Level level,BlockPos resolved,Vec3 point) {return ship==null?point:SableAnchors.world(level,this,point);}
}
