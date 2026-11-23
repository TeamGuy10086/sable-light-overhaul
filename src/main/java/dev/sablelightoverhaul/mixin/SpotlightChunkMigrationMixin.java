package dev.sablelightoverhaul.mixin;
import dev.sablelightoverhaul.lighting.SpotlightRangeMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChunkSerializer.class)
abstract class SpotlightChunkMigrationMixin {
    @Inject(method="read",at=@At("HEAD"))
    private static void slo$migrateRange(ServerLevel level,PoiManager poi,RegionStorageInfo storage,ChunkPos pos,
        CompoundTag tag,CallbackInfoReturnable<ProtoChunk> cir) {
        if(SpotlightRangeMigration.chunk(tag))tag.putBoolean("slo:range_migrated",true);
    }
    @Inject(method="read",at=@At("RETURN"))
    private static void slo$saveMigratedRange(ServerLevel level,PoiManager poi,RegionStorageInfo storage,ChunkPos pos,
        CompoundTag tag,CallbackInfoReturnable<ProtoChunk> cir) {
        if(tag.getBoolean("slo:range_migrated")) {
            tag.remove("slo:range_migrated");
            // Normal save scheduling only: no forced save, flush, chunk ticket or synchronous disk I/O.
            if(cir.getReturnValue()!=null)cir.getReturnValue().setUnsaved(true);
        }
    }
}
