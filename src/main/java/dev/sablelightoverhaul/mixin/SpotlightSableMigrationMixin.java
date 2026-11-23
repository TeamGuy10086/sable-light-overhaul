package dev.sablelightoverhaul.mixin;
import dev.sablelightoverhaul.lighting.SpotlightRangeMigration;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot",remap=false)
abstract class SpotlightSableMigrationMixin {
    @Inject(method="load",at=@At("HEAD"))
    private void slo$migrateRange(CompoundTag tag,CallbackInfo ci) {
        ServerLevelPlot plot=(ServerLevelPlot)(Object)this;
        CompoundTag chunks=tag.getCompound("chunks");
        int minSection=plot.getSubLevel().getLevel().getMinSection();
        for(String key:chunks.getAllKeys()) {
            ChunkPos global=plot.toGlobal(new ChunkPos(Long.parseLong(key)));
            SpotlightRangeMigration.plotChunk(chunks.getCompound(key),global.getMinBlockX(),minSection,global.getMinBlockZ());
        }
    }
}
