package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelightoverhaul.client.MuzzleVisualProbe;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.effects.particles.smoke.TrailSmokeParticleData;

/**
 * CBC autocannon tracers spawn two dense batches of big-smoke puffs every client tick along the flight
 * path. Seen from beside the muzzle, the first few are a row of white discs between the barrel and the
 * flash, which reads as a segmented jet even though the flash itself is continuous.
 *
 * <p>Every tick of the projectile is wrapped and all {@link TrailSmokeParticleData} puffs it spawns are
 * dropped along the whole trajectory, regardless of distance from the muzzle. The projectile entity,
 * its tracer item and every other particle option (muzzle smoke, explosion smoke, missile smoke) pass
 * through untouched. The visible tracer streak is a separate system with its own rendering; this mixin
 * does not draw anything. Client-side only and purely visual: no server hooks, no gameplay changes.
 */
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile", remap = false)
public abstract class AutocannonTrailMixin {
    @WrapOperation(method = "tick", require = 2,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private void slo$suppressTrail(Level level, ParticleOptions particle, boolean always,
            double x, double y, double z, double dx, double dy, double dz, Operation<Void> original) {
        if (particle instanceof TrailSmokeParticleData && (dev.sablelightoverhaul.client.ClientChannels.has(dev.sablelightoverhaul.AutocannonTrackPayload.TYPE) || dev.sablelightoverhaul.client.ClientChannels.has(dev.sablelightoverhaul.AutocannonTrackPayload.Phased.TYPE))) {
            MuzzleVisualProbe.autocannonTrailSuppressed();
            return;
        }
        original.call(level, particle, always, x, y, z, dx, dy, dz);
    }
}
