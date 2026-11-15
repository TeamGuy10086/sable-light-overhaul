package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.MuzzleParticleLayer;
import dev.sablelightoverhaul.client.TrackedSmoke;
import dev.sablelightoverhaul.client.MuzzleSmokeMesh;
import dev.sablelightoverhaul.client.MuzzleVisualProbe;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only captured muzzle smoke joins the shared sorted no-depth-write layer; shell/explosion smoke stays native. */
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.smoke.FallbackCannonSmokeParticle",remap=false)
public abstract class MuzzleFallbackSmokeMixin extends TextureSheetParticle {
    protected MuzzleFallbackSmokeMixin(ClientLevel level,double x,double y,double z) { super(level,x,y,z); }
    @Override public void render(VertexConsumer out,Camera camera,float partialTick) {
        if(!((TrackedSmoke)this).slo$tankControlled()) { super.render(out,camera,partialTick); return; }
        float size=getQuadSize(partialTick);
        MuzzleSmokeMesh.draw(out,camera,x,y,z,xo,yo,zo,partialTick,size,Mth.lerp(partialTick,oRoll,roll),
            getU0(),getU1(),getV0(),getV1(),rCol,gCol,bCol,alpha,getLightColor(partialTick));
        MuzzleVisualProbe.smokeDraw(alpha,size);
    }
    @Inject(method="getRenderType",at=@At("HEAD"),cancellable=true)
    private void slo$muzzleLayer(CallbackInfoReturnable<ParticleRenderType> ci) {
        // Only the tank-controlled fallback is drawn by MuzzleSmokeMesh. Other CBC smoke groups
        // must keep CBC's own render type: putting their atlas quads into our custom smoke batch
        // made the native square smoke show up as semi-transparent slabs around the soft cloud.
        if(((TrackedSmoke)this).slo$tankControlled())ci.setReturnValue(MuzzleParticleLayer.TYPE);
    }
}
