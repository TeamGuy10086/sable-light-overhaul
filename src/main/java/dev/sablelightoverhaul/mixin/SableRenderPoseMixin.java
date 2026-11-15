package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.SableRenderTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the partial tick Sable gives its own renderer, so this mod can place a lamp in exactly the same
 * fraction of a tick as the hull it stands on.
 *
 * <p>Targeted by name rather than by class reference: Sable is optional, and this config's plugin refuses to
 * apply the mixin when the class is not on the classpath.
 *
 * <p>{@code renderPose(float)} is the interpolation Sable itself uses -- its body is
 * {@code lastPose.set(lastPose).lerp(logicalPose, partialTick)}, which is why recording the argument is enough
 * and no pose has to be copied out.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.ClientSubLevel")
public abstract class SableRenderPoseMixin {
    @Inject(method = "renderPose(F)Ldev/ryanhcode/sable/companion/math/Pose3dc;",
            at = @At("HEAD"), require = 0, remap = false)
    private void slo_recordRenderTick(float partialTick, CallbackInfoReturnable<?> info) {
        SableRenderTick.record(partialTick);
    }
}
