package dev.cbcmuzzlelight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbcmuzzlelight.lighting.LightingCommon;
import dev.cbcmuzzlelight.lighting.ParticleSpec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.CBCCompatTransformers;

/**
 * Corrects the medium cannon's own breech puff.
 *
 * <p>CBC Modern Warfare emits it in contraption space ({@code entity.toGlobalVector} on a mounted
 * cannon) and gives it a velocity of 0.075 blocks a tick along the bore axis. On a physics body that
 * places the cloud beside the breech rather than at it, and it barely moves once it is there. The
 * call is replaced with the same particle sent from the transformed world position, with its
 * velocity rotated the same way and scaled by a configurable factor.
 *
 * <p>Only the call site inside that one method is wrapped, so re-sending the particle here cannot
 * re-enter this hook.
 */
@Mixin(targets = "riftyboi.cbcmodernwarfare.cannons.medium_cannon.breech.MediumcannonBreechBlock", remap = false)
public abstract class MediumcannonBreechMixin {
    @WrapOperation(method = "onInteractWhileAssembled", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
                    remap = false))
    private int muzzle$breechSmoke(ServerLevel level, ParticleOptions options, double x, double y, double z,
                                   int count, double dx, double dy, double dz, double speed,
                                   Operation<Integer> original) {
        if (!LightingCommon.CANNON_BREECH_FIX.get()) {
            return original.call(level, options, x, y, z, count, dx, dy, dz, speed);
        }
        Vec3 at = CBCCompatTransformers.transformVec3(level, new Vec3(x, y, z));
        // An affine transform maps a direction as the difference of two transformed points.
        Vec3 velocity = CBCCompatTransformers.transformVec3(level, new Vec3(dx, dy, dz))
                .subtract(CBCCompatTransformers.transformVec3(level, Vec3.ZERO));
        double scale = LightingCommon.CANNON_BREECH_SPEED.get();
        String override = "";
        ParticleOptions use = options;
        if (override != null && !override.isBlank()) {
            ParticleOptions parsed = ParticleSpec.parse(level, override);
            if (parsed != null) use = parsed;
        }
        return dev.cbcmuzzlelight.lighting.ForcedParticles.broadcast(level, use, at.x, at.y, at.z, count,
                velocity.x * scale, velocity.y * scale, velocity.z * scale, speed);
    }
}
