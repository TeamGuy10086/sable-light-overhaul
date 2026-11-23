package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelightoverhaul.client.Shockwave;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.effects.particles.explosions.ShellExplosionCloudParticleData;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;

@Mixin(value = ShellExplosion.class, remap = false)
abstract class ClientShellImpactMixin {
    // CBC submits one cloud per visible shell explosion, after its noEffects check.
    @WrapOperation(method = "spawnParticles", require = 2, allow = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private void slo$localImpact(Level level, ParticleOptions options, boolean force, double x, double y,
            double z, double dx, double dy, double dz, Operation<Void> original) {
        original.call(level, options, force, x, y, z, dx, dy, dz);
        if (level instanceof ClientLevel client && options instanceof ShellExplosionCloudParticleData cloud) {
            Shockwave.localShell(client, (Explosion) (Object) this, cloud.isPlume());
        }
    }
}
