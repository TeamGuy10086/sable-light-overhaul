package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.api.EffectApi;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a {@link EffectApi.BlockHitEvent} where a projectile meets a block.
 *
 * <p>This one is <b>client side only</b>, and deliberately so: CBC plays the per-block hit effect on
 * whichever side ran the hit, and its own comment says only the client draws particles. So the event is
 * dispatched where the drawing happens rather than being measured on the server and sent -- there is no
 * measurement in it, and nothing a server could add.
 *
 * <p>It is also the smallest of the events: everything it carries already arrived in CBC's packet, which
 * is why the hook is one line and why the event has fields ({@code blockState}, {@code projectileType})
 * that this mod's own sparks do not use. They are there because they are free and because a third party
 * skinning its own sparks by material or by shell type would otherwise have to re-hook CBC itself.
 *
 * <p>{@code direction} is CBC's own spray direction: the reverse of the incoming velocity on a hit, the
 * reflected one on a ricochet. It arrives in the hit body's frame when the block belongs to a physics
 * body; bringing it back to world space is the first thing the listener does, because that transform
 * needs the client level and a body lookup.
 */
public final class BlockHitDispatch {
    private BlockHitDispatch() {}

    public static void blockHit(Level level, Vec3 pos, Vec3 direction, boolean ricochet,
                                BlockState blockState, EntityType<?> projectileType) {
        // No SourceKey: CBC's block hit effect carries the projectile's type but not the entity, so there
        // is no shell to trace back to a cannon. Adding one means hooking the projectile entity itself,
        // which is the same registration the tracer work needs.
        EffectBus.blockHit(new EffectApi.BlockHitEvent(level, pos, direction, ricochet, blockState,
                projectileType, null, DefaultSpecs.forBlockHit(), EffectApi.Cancellation.none()));
    }
}
