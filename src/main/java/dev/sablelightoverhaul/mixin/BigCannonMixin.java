package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelightoverhaul.effects.FireDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticleData;

@Mixin(targets="rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption",remap=false)
public abstract class BigCannonMixin {

    @WrapOperation(method="fireShot",at=@At(value="NEW",target="(FFI)Lrbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticleData;"),require=1,expect=1,allow=1)
    private BigCannonPlumeParticleData slo$big(float size,float power,int lifetime,Operation<BigCannonPlumeParticleData> original,
            @Local(argsOnly=true) ServerLevel level,@Local(argsOnly=true) PitchOrientedContraptionEntity entity,
            @Local(name="plumePos") Vec3 plume,@Local(name="vec") Vec3 direction) {
        var data=original.call(size,power,lifetime);
        // The plume is already world space; only its direction needs the contraption transform, and that
        // transform now happens in one place for every weapon that needs it.
        FireDispatch.bigCannon(level,(Entity)(Object)entity,plume,direction,size,power);
        return data;
    }
}
