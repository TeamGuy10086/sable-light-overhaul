package dev.sablelightoverhaul.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;

@Mixin(value = BlockArmorPropertiesHandler.class, remap = false)
public interface CbcArmorRegistryAccess {
    @Accessor("FALLBACK_PROVIDER")
    static BlockArmorPropertiesProvider slo$fallbackProvider() {
        throw new AssertionError("CBC armor registry accessor was not applied");
    }
}
