package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.effects.BlockHitDispatch;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.block_hit_effects.ProjectileHitEffect;

/**
 * Turns a projectile's block hit into an event.
 *
 * <p>Create: Big Cannons plays a per-block hit effect, and that is where its own spark is spawned -
 * a particle that draws itself through a custom render type and therefore never shows up under a
 * shader pack. This hook reads the same call: the position, the block that was hit, whether the shot
 * bounced, and the direction CBC already chose for its spray (reverse of the incoming velocity on a
 * hit, the reflected one on a ricochet). CBC's own particles are left alone; this mod's sparks are a
 * listener on the event now, so what happens here is only the fact.
 */
@Mixin(targets = "rbasamoyai.createbigcannons.block_hit_effects.BlockHitEffect", remap = false)
public abstract class BlockHitEffectMixin {
    @Inject(method = "playEffect", at = @At("HEAD"), remap = false)
    private void slo$blockHit(Level level, boolean deflect, boolean forceDisplay,
                                 double x, double y, double z, double dx, double dy, double dz,
                                 EntityType<?> entityType, BlockState blockState, ProjectileHitEffect effect,
                                 CallbackInfo callback) {
        // The effect is played on whichever side ran the hit; only the client draws particles.
        if (level == null || !level.isClientSide) return;
        BlockHitDispatch.blockHit(level, new Vec3(x, y, z), new Vec3(dx, dy, dz), deflect, blockState, entityType);
    }
}
