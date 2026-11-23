package dev.sablelightoverhaul.mixin;
import dev.sablelightoverhaul.lighting.SpotlightRangeMigration;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(StructureTemplate.class)
abstract class SpotlightStructureMigrationMixin {
    @ModifyVariable(method="loadPalette",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private ListTag slo$migrateRange(ListTag blocks,HolderGetter<Block> lookup,ListTag palette,ListTag original) {
        return SpotlightRangeMigration.structureBlocks(palette,blocks);
    }
}
