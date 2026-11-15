package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelightoverhaul.FlakConeDamage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakAutocannonProjectile;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;

/** Keep the original explosion/packet/sound; replace only its following fragment burst call. */
@Mixin(value=FlakAutocannonProjectile.class,remap=false)
public abstract class FlakConeMixin {
    @WrapOperation(method="detonate",require=1,allow=1,at=@At(value="INVOKE",target="Lrbasamoyai/createbigcannons/munitions/fragment_burst/CBCProjectileBurst;spawnConeBurst(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;ID)Lrbasamoyai/ritchiesprojectilelib/projectile_burst/ProjectileBurst;"))
    private ProjectileBurst slo$cone(Level level,EntityType<?> type,Vec3 origin,Vec3 velocity,int count,double spread,Operation<ProjectileBurst> original){
        FlakConeDamage.apply((Entity)(Object)this,origin,velocity,count,spread);
        return null; // CBC discards this return value; checked against the installed bytecode.
    }
}
