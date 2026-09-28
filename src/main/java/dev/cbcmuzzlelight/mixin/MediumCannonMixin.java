package dev.cbcmuzzlelight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbcmuzzlelight.MediumCannonEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticleData;

/**
 * Fires the tank cannon's muzzle effects.
 *
 * <p>The target is CBC Modern Warfare's medium cannon, hooked at the one construction site where the
 * cannon builds its own muzzle plume: at that point the muzzle position, the muzzle direction and the
 * charge are all already computed as locals, so nothing has to be re-derived and the shot itself is
 * untouched. The hook is additive — the plume the cannon builds is still returned unchanged.
 *
 * <p>Targeted by name rather than by a compile dependency, and the whole mixin config is gated on the
 * mod being present, so CBC Modern Warfare stays optional.
 */
@Mixin(targets="riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption",remap=false)
public abstract class MediumCannonMixin {
 @WrapOperation(method="fireShot",at=@At(value="NEW",target="(FFI)Lrbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticleData;"),require=1,expect=1,allow=1)
 private BigCannonPlumeParticleData muzzle$mediumCannon(float size,float power,int lifetime,Operation<BigCannonPlumeParticleData> original,
   @Local(argsOnly=true) ServerLevel level,@Local(argsOnly=true) PitchOrientedContraptionEntity entity,
   @Local(name="plumePos") Vec3 plume,@Local(name="vec1") Vec3 direction) {
  MediumCannonEffects.fire(level,(net.minecraft.world.entity.Entity)(Object)entity,plume,direction,size,power);
  return original.call(size,power,lifetime);
 }
}
