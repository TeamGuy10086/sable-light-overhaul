package dev.cbcmuzzlelight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbcmuzzlelight.ServerFlashes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticleData;

@Mixin(targets="rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption",remap=false)
public abstract class BigCannonMixin {
    @WrapOperation(method="fireShot",at=@At(value="NEW",target="(FFI)Lrbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticleData;"),require=1,expect=1,allow=1)
    private BigCannonPlumeParticleData muzzle$big(float size,float power,int lifetime,Operation<BigCannonPlumeParticleData> original,
            @Local(argsOnly=true) ServerLevel level,@Local(argsOnly=true) PitchOrientedContraptionEntity entity,
            @Local(name="plumePos") Vec3 plume,@Local(name="vec") Vec3 direction) {
        var data=original.call(size,power,lifetime);
        // Native plume is already transformed to world space; transform only its direction.
        var owner=(Entity)(Object)entity;
        var axis=CBCCompatTransformers.transformLocationNormal(level,owner.blockPosition(),direction).normalize();
        var pos=plume.add(axis.scale(1.6));
        ServerFlashes.emitSized(level,pos,axis,0,owner.getId(),pos,Math.max(0,power),Math.max(1,size));
        return data;
    }
}
