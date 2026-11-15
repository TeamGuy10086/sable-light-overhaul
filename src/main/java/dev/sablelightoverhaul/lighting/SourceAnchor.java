package dev.sablelightoverhaul.lighting;
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
 /** The plot-frame anchor this world position belongs to when it is a rendered body position, or null. */
 /** Diagnostic: every body's view of one clicked position. */
 public static String probe(Level level,BlockPos world){return ModList.get().isLoaded("sable")?SableAnchors.probe(level,world):"no sable";}
 public BlockPos resolve(Level level) { return ship==null?pos:ModList.get().isLoaded("sable")?SableAnchors.resolve(level,this):null; }
 public Vec3 world(Level level,BlockPos resolved,Vec3 point) {return ship==null?point:SableAnchors.world(level,this,point);}
 /**
  * The rotation of the body this anchor is on, as x,y,z,w, or null when it is on none.
  *
  * <p>This is what lets a lamp's shadow cones be painted on the hull instead of on the world: the angles
  * are the ones a player measures by eye against the deck, and a ship that turns has to take them along.
  * Only the rotation is wanted, because the shader only has to turn a direction into the body's frame.
  */
 public float[] rotation(Level level) {return ship==null||!ModList.get().isLoaded("sable")?null:SableAnchors.rotation(level,this);}
 /**
  * The block columns this body's plot owns, or null when this anchor is on the ground.
  *
  * <p>A light field flooded in a plot has to know where the pocket ends: outside it the level's own ground
  * sits at those coordinates, and a flood that respected it would stop at the hull's edge.
  */
 public Pocket pocket(Level level) {return ship==null||!ModList.get().isLoaded("sable")?null:SableAnchors.pocket(level,ship);}
 /**
  * The same point under the pose this body had at the previous tick, or null when the body is gone.
  *
  * <p>With {@link #world} this gives the two ends of one tick of a hull's motion: what a caller needs to put
  * something on the hull <em>as it is drawn</em> rather than where it was at the last tick.
  */
 public Vec3 worldLast(Level level,Vec3 point) {return ship==null?point:SableAnchors.worldLast(level,this,point);}
 /** The body's rotation at the previous tick, as x,y,z,w, or null when this anchor is on the ground. */
 public float[] rotationLast(Level level) {return ship==null||!ModList.get().isLoaded("sable")?null:SableAnchors.rotationLast(level,this);}
 /**
  * A point on this body at this frame, as {x,y,z,qx,qy,qz,qw}, or null when the body is gone.
  *
  * <p>The pose is interpolated first and the point is transformed through it, because that is what the renderer
  * does. Interpolating two already-transformed points instead cuts the arc a rotating body's point travels, and
  * the difference is a light that slides and flickers on a deck that is turning.
  */
 public float[] pointAt(Level level,Vec3 local,double partialTick) {
  return ship==null||!ModList.get().isLoaded("sable")?null:SableAnchors.pointAt(level,this,local,partialTick);
 }
}
