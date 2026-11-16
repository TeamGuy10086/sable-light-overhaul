package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.SpotlightBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Restore the range carried in a WorldEdit clipboard after the destination entity loads. */
@Pseudo
@Mixin(targets = "com.sk89q.worldedit.neoforge.internal.NeoForgeWorldNativeAccess", remap = false)
abstract class WorldEditNativeAccessMixin {
    @Redirect(method = "updateTileEntity(Lnet/minecraft/core/BlockPos;Lorg/enginehub/linbus/tree/LinCompoundTag;)Z",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void slo$loadSpotlightRange(BlockEntity entity, CompoundTag tag, HolderLookup.Provider registries) {
        entity.loadWithComponents(tag, registries);
        if (entity instanceof SpotlightBlockEntity lamp && tag.contains("slo_we_range")) {
            lamp.setNumber("range", tag.getInt("slo_we_range"));
        }
    }
}
