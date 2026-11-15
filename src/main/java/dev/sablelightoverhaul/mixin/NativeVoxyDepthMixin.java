package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.NativeVoxyDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.NormalRenderPipeline", remap = false)
abstract class NativeVoxyDepthMixin {
    @Inject(method = "finish", at = @At("RETURN"), require = 0)
    private void slo$captureDepth(@Coerce Object viewport, int framebuffer, int width, int height, CallbackInfo ci) {
        NativeVoxyDepth.capture(this, viewport, framebuffer);
    }
}
