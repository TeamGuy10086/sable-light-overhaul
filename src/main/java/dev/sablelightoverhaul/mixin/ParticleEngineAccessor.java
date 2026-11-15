package dev.sablelightoverhaul.mixin;

import java.util.Map;
import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The engine's live particle table, so a burst can size itself against the load that is actually there.
 *
 * <p>Read once per explosion, on the client thread, and only summed -- this is not a per frame cost. It is
 * deliberately not taken from the depth sort's own bookkeeping: turning the sort off would then silently
 * turn the throttle off too, which is the kind of coupling that has bitten this mod before.
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("particles") Map<ParticleRenderType, Queue<Particle>> slo$particles();
}