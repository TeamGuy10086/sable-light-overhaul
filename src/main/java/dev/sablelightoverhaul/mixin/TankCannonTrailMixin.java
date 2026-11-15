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
 * Tank cannon rounds leave the barrel with the same dense smoke trail as the autocannon, so from beside
 * the muzzle the first puffs read as a segmented jet rather than a continuous flash.
 *
 * <p>Every tick of the projectile is wrapped and all {@link TrailSmokeParticleData} puffs it spawns are
 * dropped along the whole trajectory, regardless of distance from the muzzle. The projectile entity,
 * its tracer and every other particle option (muzzle smoke, explosion smoke, missile smoke) pass
 * through untouched. The visible tracer streak is a separate system with its own rendering; this mixin
 * does not draw anything. Client-side only and purely visual: no server hooks, no gameplay changes.
 */
@Mixin(targets = "riftyboi.cbcmodernwarfare.munitions.medium_cannon.AbstractMediumcannonProjectile", remap = false)
public abstract class TankCannonTrailMixin {
    @WrapOperation(method = "tick", require = 1, expect = 1, allow = 1,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private void slo$suppressTrail(Level level, ParticleOptions particle, boolean always,
            double x, double y, double z, double dx, double dy, double dz, Operation<Void> original) {
        if (particle instanceof TrailSmokeParticleData) {
            MuzzleVisualProbe.tankTrailSuppressed();
            return;
        }
        original.call(level, particle, always, x, y, z, dx, dy, dz);
    }
}
