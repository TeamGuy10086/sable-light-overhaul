package dev.sablelightoverhaul.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelightoverhaul.lighting.FarEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
/**
 * Vanilla only delivers an entity within {@code min(its effective range, the player's view distance in
 * blocks)} -- {@code ChunkMap$TrackedEntity.updatePlayer}. That is why an assembled cannon is absent
 * from a client standing past 48 blocks at view distance 3 (64 at 4) even though CBC registers the type
 * with a 16 chunk range and the ship it sits on renders far beyond that: the client was never sent the
 * entity, and nothing drawn client side can invent it. Measured, see HANDOFF.zh-CN.md 8.27.
 *
 * <p>Both arguments of that min() are lifted here, and only for the entity types named in
 * {@code farEntities.names}: the entity's own effective range, and the view distance it is capped with.
 *
 * <p>SSRD does the same job by redirecting the min() call itself, but gates on the registry namespace
 * ({@code create:} / {@code aeronautics:} / {@code offroad:}) plus contraption/carriage/propeller. CBC
 * registers its cannon as {@code createbigcannons:pitch_contraption}, which starts with none of those
 * prefixes, so SSRD's bypass never applied to it -- and its "Overriding tracking range" line, which the
 * gate also guards, never appeared in the log. These two hooks touch the two arguments instead of that
 * call, so a future SSRD update cannot collide with them on the same instruction.
 *
 * <p>Server side by nature: tracking is the server's decision. In singleplayer the integrated server
 * reads the same config file; on a dedicated server this mod has to be installed there as well.
 */
@Mixin(targets="net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class FarEntityTrackingMixin {
 @Shadow @Final Entity entity;
 /** The entity's own range (its type's, plus any passengers'): lift it so the type cannot bind below. */
 @ModifyExpressionValue(method="updatePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ChunkMap$TrackedEntity;getEffectiveRange()I"))
 private int slo$farEntityRange(int original){
  if(!FarEntities.matches(this.entity))return original;
  int far=FarEntities.range();
  return far>0?Math.max(original,far):original;
 }
 /** And lift the view distance that range is capped with, rounded up to whole chunks. */
 @ModifyExpressionValue(method="updatePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ChunkMap;getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I"))
 private int slo$farEntityViewDistance(int original){
  if(!FarEntities.matches(this.entity))return original;
  int far=FarEntities.range();
  if(far<=0)return original;
  return Math.max(original,(far+15)/16);
 }
 /**
  * The third gate in the same expression: the entity's chunk has to be inside the player's tracking
  * view, which the chunk a far sub-level's entity occupies is not. SSRD leaves this one alone -- it
  * extends the tracking view for the plots it knows about instead -- but a contraption is rendered from
  * its own entity data, so for a matching entity the packet is what matters and the chunk is not.
  */
 @ModifyExpressionValue(method="updatePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
 private boolean slo$farEntityChunkTracked(boolean original){
  return FarEntities.matches(this.entity)||original;
 }
}
