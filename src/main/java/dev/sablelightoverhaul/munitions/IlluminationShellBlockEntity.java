package dev.sablelightoverhaul.munitions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;

/** Preserves the payload through placement, Create contraptions and CBC extraction. */
public final class IlluminationShellBlockEntity extends FuzedBlockEntity {
    private FlareSettings settings = FlareSettings.DEFAULT;

    public IlluminationShellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public FlareSettings settings() { return settings; }

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        settings.write(tag);
    }

    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = FlareSettings.read(tag);
    }

    @Override public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        settings.write(tag);
    }

    @Override protected void writeSafeComponents(PatchedDataComponentMap components) {
        super.writeSafeComponents(components);
        components.set(FlareMunitions.SETTINGS.get(), settings);
    }

    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        settings = input.getOrDefault(FlareMunitions.SETTINGS.get(), FlareSettings.DEFAULT);
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(FlareMunitions.SETTINGS.get(), settings);
    }
}
