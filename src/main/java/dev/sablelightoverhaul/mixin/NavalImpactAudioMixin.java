package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelightoverhaul.HealthbarServerBridge;
import dev.sablelightoverhaul.HostileShellPayload.Model;
import dev.sablelightoverhaul.effects.NavalImpactAudio;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.ImpactResult;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

@Mixin(value = AbstractCannonProjectile.class, remap = false)
abstract class NavalImpactAudioMixin {
    @WrapOperation(method = "clipAndDamage", require = 1,
        at = @At(value = "INVOKE", target = "Lrbasamoyai/createbigcannons/munitions/AbstractCannonProjectile;calculateBlockPenetration(Lrbasamoyai/createbigcannons/munitions/ProjectileContext;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/BlockHitResult;)Lrbasamoyai/createbigcannons/munitions/AbstractCannonProjectile$ImpactResult;"))
    private ImpactResult slo$navalImpact(AbstractCannonProjectile projectile, ProjectileContext context,
            BlockState state, BlockHitResult hit, Operation<ImpactResult> original) {
        if (!(projectile.level() instanceof ServerLevel level)) return original.call(projectile, context, state, hit);
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (!type.getNamespace().equals("sablehostile")) return original.call(projectile, context, state, hit);
        var model = Model.forType(type.toString());
        if (model == null || HealthbarServerBridge.owns(level, projectile.getUUID())) {
            return original.call(projectile, context, state, hit);
        }

        var surface = NavalImpactAudio.surface(level, hit.getBlockPos(), state);
        var point = CBCCompatTransformers.transformVec3(level, hit.getLocation());
        var result = original.call(projectile, context, state, hit);
        NavalImpactAudio.play(level, point, model, surface, result.kinematics() == ImpactResult.KinematicOutcome.BOUNCE);
        return result;
    }
}
