package dev.cbcmuzzlelight.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbcmuzzlelight.client.FlywheelAreaContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Uses Flywheel's own writer; no raw memory layout assumptions or world light storage mutation. */
@Pseudo
@Mixin(targets="dev.engine_room.flywheel.backend.engine.LightDataCollector",remap=false)
public abstract class AdvancedFlywheelMixin {
    @WrapMethod(method="collectSection")
    private void muzzle$collect(long ptr,long section,Operation<Void> original) {
        var old=FlywheelAreaContext.enter(section);
        try { original.call(ptr,section); }
        finally { FlywheelAreaContext.restore(old); }
    }
    @WrapMethod(method="write")
    private static void muzzle$write(long ptr,int x,int y,int z,int block,int sky,Operation<Void> original) {
        original.call(ptr,x,y,z,FlywheelAreaContext.light(x,y,z,block),sky);
    }
}
