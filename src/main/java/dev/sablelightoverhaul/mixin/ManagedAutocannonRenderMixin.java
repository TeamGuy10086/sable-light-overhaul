package dev.sablelightoverhaul.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
/** A carrier can briefly remain registered during takeover. Do not draw its native tracer a second time. */
@Mixin(targets="rbasamoyai.createbigcannons.munitions.autocannon.AutocannonProjectileRenderer",remap=false)
public abstract class ManagedAutocannonRenderMixin {
 @Inject(method="render(Lrbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"),cancellable=true,require=1)
 private void slo$managed(AbstractAutocannonProjectile projectile,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci){
  if(dev.sablelightoverhaul.client.HealthbarFlightBridge.owns(projectile.getUUID())
     ||dev.sablelightoverhaul.client.ProjectileStreaks.active(projectile.getId()))ci.cancel();
  else if(projectile.isTracer()&&!(projectile.getOrientation().lengthSqr()>=1e-4))ci.cancel();
 }
}
