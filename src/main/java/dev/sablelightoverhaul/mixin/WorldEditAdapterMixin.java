package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.SpotlightBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Native spotlight states still contain range. Project them onto the smaller WorldEdit table. */
@Pseudo
@Mixin(targets = "com.sk89q.worldedit.neoforge.internal.NeoForgeTransmogrifier", remap = false)
abstract class WorldEditAdapterMixin {
    @ModifyVariable(method = "transmogToWorldEditProperties", at = @At("HEAD"), argsOnly = true)
    private static Map<Property<?>, Comparable<?>> slo$omitSpotlightRange(Map<Property<?>, Comparable<?>> properties) {
        if (!properties.keySet().stream().anyMatch(property -> property == SpotlightBlock.RANGE)) return properties;
        Map<Property<?>, Comparable<?>> projected = new LinkedHashMap<>(properties);
        projected.remove(SpotlightBlock.RANGE);
        return projected;
    }
}
