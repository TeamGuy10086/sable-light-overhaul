package dev.sablelightoverhaul.mixin;

import java.util.Map;
import dev.sablelightoverhaul.lighting.WorldEditSpotlightRange;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.world.block.BlockState;
import org.enginehub.linbus.tree.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Sponge schematic v1/v2/v3 can contain the original range property in their string palette. */
@Pseudo
@Mixin(targets="com.sk89q.worldedit.extent.clipboard.io.sponge.ReaderUtil",remap=false)
abstract class WorldEditSchematicRangeMixin {
    @ModifyArg(method="decodePalette",at=@At(value="INVOKE",target="Lcom/sk89q/worldedit/extension/factory/BlockFactory;parseFromInput(Ljava/lang/String;Lcom/sk89q/worldedit/extension/input/ParserContext;)Ljava/lang/Object;"),index=0)
    private static String slo$stripLegacyRange(String state){return WorldEditSpotlightRange.withoutRange(state);}

    @Inject(method="decodePalette",at=@At("RETURN"),cancellable=true)
    private static void slo$rememberRanges(LinCompoundTag raw,@Coerce Object fixer,CallbackInfoReturnable<Map<Integer,BlockState>> cir) {
        cir.setReturnValue(WorldEditSpotlightRange.remember(raw,cir.getReturnValue()));
    }
    @Inject(method="initializeClipboardFromBlocks",at=@At("RETURN"))
    private static void slo$restoreRanges(Clipboard clipboard,Map<Integer,BlockState> palette,byte[] data,
        LinListTag<LinCompoundTag> entities,@Coerce Object fixer,boolean nested,CallbackInfo ci) throws WorldEditException {
        WorldEditSpotlightRange.restore(clipboard,palette,data);
    }
}
