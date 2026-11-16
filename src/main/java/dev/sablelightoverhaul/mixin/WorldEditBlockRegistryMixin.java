package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.SpotlightBlock;
import java.util.Collection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hide the spotlight's 1024-value range from WorldEdit's Cartesian state table. */
@Pseudo
@Mixin(targets = "com.sk89q.worldedit.neoforge.NeoForgeBlockRegistry", remap = false)
abstract class WorldEditBlockRegistryMixin {
    @Redirect(method = "getProperties", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/block/state/BlockState;getProperties()Ljava/util/Collection;"))
    private Collection<Property<?>> slo$compactSpotlightProperties(BlockState state) {
        Collection<Property<?>> properties = state.getProperties();
        if (!(state.getBlock() instanceof SpotlightBlock)) return properties;
        return properties.stream().filter(property -> property != SpotlightBlock.RANGE).toList();
    }
}
