package dev.sablelightoverhaul.mixin;
import dev.sablelightoverhaul.lighting.SpotlightRangeMigration;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.simibubi.create.content.contraptions.Contraption",remap=false)
abstract class SpotlightContraptionMigrationMixin {
    @Inject(method="readBlocksCompound",at=@At("HEAD"))
    private void slo$migrateRange(Tag blocks,Level level,boolean paletted,CallbackInfo ci) {
        SpotlightRangeMigration.contraption(blocks,paletted);
    }
}
