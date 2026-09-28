package dev.cbcmuzzlelight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbcmuzzlelight.ServerFlashes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption", remap = false)
public abstract class AutocannonMixin {
    @WrapOperation(method = "fireShot", at = @At(value = "INVOKE", target = "Lrbasamoyai/createbigcannons/CBCCompatTransformers;transformVec3(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), require = 1, expect = 1, allow = 1)
    private Vec3 muzzle$auto(Level level, Vec3 pos, Vec3 root, Operation<Vec3> original, @Local(name = "vec1") Vec3 direction, @Local(argsOnly=true) rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity entity) {
        Vec3 transformed = original.call(level, pos, root);
        ServerFlashes.emitGrouped(level, pos, root, direction, 0.1, 1, ((net.minecraft.world.entity.Entity)(Object)entity).getId(), pos);
        return transformed;
    }
}
