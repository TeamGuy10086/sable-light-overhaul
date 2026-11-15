package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.HostileShellPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileRenderer;

/** Leave the tracer flag available to the flight feed and suppress only CBC's naval billboard. */
@Mixin(value = BigCannonProjectileRenderer.class, remap = false)
public abstract class NavalTracerGlowMixin {
    @Redirect(method = "render(Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;hasTracer()Z"),
        require = 1)
    private boolean slo$drawNativeTracer(AbstractBigCannonProjectile shell) {
        return shell.hasTracer() && HostileShellPayload.Model.forType(
            BuiltInRegistries.ENTITY_TYPE.getKey(shell.getType()).toString()) == null;
    }
}
