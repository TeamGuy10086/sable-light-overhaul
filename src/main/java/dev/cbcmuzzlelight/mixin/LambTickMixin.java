package dev.cbcmuzzlelight.mixin;

import dev.cbcmuzzlelight.client.LambAreaBackend;
import dev.lambdaurora.lambdynlights.engine.source.DeferredDynamicLightSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.lambdaurora.lambdynlights.LambDynLights",remap=false)
public abstract class LambTickMixin {
    @Inject(method="shouldTick(Ldev/lambdaurora/lambdynlights/engine/source/DeferredDynamicLightSource;)Z",at=@At("HEAD"),cancellable=true)
    private void muzzle$shortPulse(DeferredDynamicLightSource source,CallbackInfoReturnable<Boolean> cir) {
        if(source.behavior() instanceof LambAreaBackend.Behavior) cir.setReturnValue(true);
    }
}
