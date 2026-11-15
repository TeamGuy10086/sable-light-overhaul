package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.effects.FireDispatch;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption", remap = false)
public abstract class AutocannonMixin {
    @WrapOperation(method = "fireShot", at = @At(value = "INVOKE", target = "Lrbasamoyai/createbigcannons/CBCCompatTransformers;transformVec3(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), require = 1, expect = 1, allow = 1)
    private Vec3 slo$auto(Level level, Vec3 pos, Vec3 root, Operation<Vec3> original, @Local(name = "vec1") Vec3 direction, @Local(argsOnly=true) rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity entity) {
        Vec3 transformed = original.call(level, pos, root);
        FireDispatch.autocannon(level, (net.minecraft.world.entity.Entity)(Object)entity, pos, root, direction,
                EffectApi.WeaponKind.AUTOCANNON);
        return transformed;
    }
}
