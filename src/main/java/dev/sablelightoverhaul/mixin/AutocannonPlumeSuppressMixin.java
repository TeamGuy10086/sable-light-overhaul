package dev.sablelightoverhaul.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.effects.particles.plumes.AutocannonPlumeParticleData;

/** This CBC-only emitter makes the unwanted vanilla flame/cloud children for both autocannon families. */
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.plumes.AutocannonPlumeParticle$Provider",remap=false)
public abstract class AutocannonPlumeSuppressMixin {
    @Inject(method="createParticle(Lrbasamoyai/createbigcannons/effects/particles/plumes/AutocannonPlumeParticleData;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at=@At("HEAD"),cancellable=true,require=1,expect=1,allow=1)
    private void slo$removeNativeAutocannonPlume(AutocannonPlumeParticleData data,ClientLevel level,
            double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> ci) {
        dev.sablelightoverhaul.client.MuzzleVisualProbe.suppressedAutoPlume();
        ci.setReturnValue(null);
    }
}
