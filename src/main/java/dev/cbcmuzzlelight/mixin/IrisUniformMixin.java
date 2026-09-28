package dev.cbcmuzzlelight.mixin;

import dev.cbcmuzzlelight.client.IrisMuzzleBridge;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers CBC muzzle-light uniforms into Iris' fixed-input set. Applied only when Iris is present
 * (see {@link IrisMixinPlugin}); the target classes are compile-only and are never touched on a
 * dedicated server.
 */
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisUniformMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"))
    private static void cbcmuzzlelight$uniforms(UniformHolder holder, IdMap ids, PackDirectives directives,
                                               FrameUpdateNotifier notifier, CallbackInfo ci) {
        try {
            IrisMuzzleBridge.register(holder, notifier);
        } catch (RuntimeException | LinkageError ex) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "CBC Muzzle Light: Iris muzzle-light bridge disabled for this session", ex);
        }
    }
}
