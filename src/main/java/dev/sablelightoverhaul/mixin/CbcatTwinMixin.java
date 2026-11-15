package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.effects.FireDispatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture the already selected barrel AFTER CBCAT applies its alternating lateral offset. */
@Pseudo
@Mixin(targets="com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption",remap=false)
public abstract class CbcatTwinMixin {
    @Inject(method="fireShot(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;"),
            require=1,expect=1,allow=1)
    private void slo$twin(CallbackInfo ci, @Local(argsOnly=true) ServerLevel level,
                            @Local(name="particlePos") Vec3 position, @Local(name="vec1") Vec3 direction,
                            @Local(argsOnly=true) rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity entity) {
        // Use the selected native barrel for both visible flame and environmental light.
        FireDispatch.autocannon(level,(net.minecraft.world.entity.Entity)(Object)entity,position,position,direction,
                EffectApi.WeaponKind.TWIN_AUTOCANNON);
    }
}
