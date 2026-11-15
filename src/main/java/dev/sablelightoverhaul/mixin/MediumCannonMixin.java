package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelightoverhaul.MediumCannonEffects;
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
 * untouched. The construction hook dispatches effects; the send hook suppresses only the replaced plume.
 *
 * <p>Targeted by name rather than by a compile dependency, and the whole mixin config is gated on the
 * mod being present, so CBC Modern Warfare stays optional.
 */
@Mixin(targets="riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedMediumcannonContraption",remap=false)
public abstract class MediumCannonMixin {

    /**
     * Replaces CBC's own muzzle plume with ours, at the moment it is sent to a player.
     *
     * <p>This is the only place the substitution can legally happen. A wrapper around the construction itself
     * has to hand back the type it wrapped, so it cannot return a different particle; wrapping the call that
     * sends the plume can, because what it returns is a plain boolean. It also removes a doubling: the plume
     * CBC builds for this cannon used to be sent as well as ours, and two plumes per shot is a fog bank no
     * matter how either of them is tuned.
     *
     * <p>Everything that is not a plume passes through untouched, so a shot's other effects are exactly CBC's.
     */
 @WrapOperation(method="fireShot",at=@At(value="NEW",target="(FFI)Lrbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticleData;"),require=1,expect=1,allow=1)
 private BigCannonPlumeParticleData slo$mediumCannon(float size,float power,int lifetime,Operation<BigCannonPlumeParticleData> original,
   @Local(argsOnly=true) ServerLevel level,@Local(argsOnly=true) PitchOrientedContraptionEntity entity,
   @Local(name="spawnPos") Vec3 muzzle,@Local(name="vec1") Vec3 direction) {
  MediumCannonEffects.fire(level,(net.minecraft.world.entity.Entity)(Object)entity,muzzle,direction,size,power);
  dev.sablelightoverhaul.TankAfterSmokeEvents.fire(level,entity,muzzle,direction);
  return original.call(size,power,lifetime);
 }
 @WrapOperation(method="fireShot",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z"),require=1,expect=1,allow=1)
 private boolean slo$singlePlume(ServerLevel level,net.minecraft.server.level.ServerPlayer player,
    net.minecraft.core.particles.ParticleOptions options,boolean force,double x,double y,double z,int count,
    double dx,double dy,double dz,double speed,Operation<Boolean> original) {
  if(options instanceof BigCannonPlumeParticleData && dev.sablelightoverhaul.lighting.LightingCommon.CANNON_ENABLED.get()) return false;
  return original.call(level,player,options,force,x,y,z,count,dx,dy,dz,speed);
 }

}
