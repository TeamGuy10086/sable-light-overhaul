package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.LightFields;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tell the light fields that real terrain has arrived, because until it does the level says "air" and means
 * "not loaded".
 *
 * <p>This is the same class of fault as a broken block, and it is the harder one to notice. A chunk that has
 * not been received yet is not unknown to {@code level.getBlockState} -- the client's chunk cache answers with
 * an empty chunk, so every cell in it reads as air with no light cost. A field flooded at that moment spreads
 * through the missing terrain as though nothing were there, caches that as fact, and is never rebuilt when the
 * chunk lands a moment later. The visible result is a lamp whose light passes through a hillside, permanent
 * until the field's lamp moves.
 *
 * <p><b>Why this method.</b> {@code ClientChunkCache.replaceWithPacketData} is the only path that delivers
 * real block data for a chunk, and it is client-only. {@code ClientLevel.onChunkLoaded} would have been the
 * friendlier target, but the biome-only packet calls it too, and a biome change cannot alter a flood -- hooking
 * that would rebuild every field near the player every time they crossed a biome border, for nothing.
 *
 * <p><b>Every parameter is declared even though only the first two are read.</b> Mixin does allow a handler to
 * stop after the parameters it needs, but nothing else in this mod relies on that, and the cost of being
 * explicit is two unused arguments rather than one assumed rule. The three that are not read are the ones that
 * make this a compile-time error if the target's signature ever moves.
 */
@Mixin(ClientChunkCache.class)
public abstract class FieldChunkDataMixin {
    @Inject(method = "replaceWithPacketData", at = @At("TAIL"))
    private void slo$chunkData(
            int chunkX,
            int chunkZ,
            FriendlyByteBuf buffer,
            CompoundTag heightmaps,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
            CallbackInfoReturnable<LevelChunk> info) {
        LightFields.chunkData(chunkX, chunkZ);
    }
}
