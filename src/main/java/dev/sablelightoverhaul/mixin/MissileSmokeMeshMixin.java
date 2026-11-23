package dev.sablelightoverhaul.mixin;
import dev.sablelightoverhaul.client.MissileSmokeMesh;
import dev.sablelightoverhaul.client.MuzzleParticleLayer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ParticleEngine.class)
abstract class MissileSmokeMeshMixin {
    @Inject(method="render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",at=@At("HEAD"))
    private void slo$smokeStart(LightTexture light,Camera camera,float partial,Frustum frustum,Predicate<ParticleRenderType> predicate,CallbackInfo ci){
        dev.sablelightoverhaul.client.SmokeResolution.begin();
    }
    @Inject(method="render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;depthMask(Z)V"))
    private void slo$mesh(LightTexture light,Camera camera,float partial,Frustum frustum,Predicate<ParticleRenderType> predicate,CallbackInfo ci){
        if(predicate.test(MuzzleParticleLayer.TYPE)){
            dev.sablelightoverhaul.client.SmokeResolution.flush(camera,partial);
            MissileSmokeMesh.render(camera,partial,frustum);
        }
    }
}
