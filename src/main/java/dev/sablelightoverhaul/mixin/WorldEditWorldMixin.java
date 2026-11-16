package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.SpotlightBlock;
import dev.sablelightoverhaul.lighting.SpotlightBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Carry range in clipboard NBT and export the default orientation. */
@Pseudo
@Mixin(targets = "com.sk89q.worldedit.neoforge.NeoForgeWorld", remap = false)
abstract class WorldEditWorldMixin {
    @Redirect(method = "getFullBlock", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithId(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag slo$copyDefaultSpotlightAim(BlockEntity entity, HolderLookup.Provider registries) {
        CompoundTag tag = entity.saveWithId(registries);
        if (entity instanceof SpotlightBlockEntity lamp) {
            tag.putInt("slo_we_range", lamp.getBlockState().getValue(SpotlightBlock.RANGE));
            tag.putDouble("angle", 0.0);
            tag.putDouble("azimuth", 0.0);
            tag.putDouble("sweepBase", 0.0);
        }
        return tag;
    }
}
