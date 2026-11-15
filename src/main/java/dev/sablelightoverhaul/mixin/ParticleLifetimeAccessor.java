package dev.sablelightoverhaul.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads and writes a particle's lifetime, which is {@code protected} and not final.
 *
 * <p>The ring shortens a cloud to {@code CLOUD_LIFETIME_TICKS} ticks: past about half a second a cloud is too
 * faint to read as part of the shockwave, so it is only spending the engine's budget. Kept in its own
 * interface on purpose -- {@code ParticlePositionAccessor} holds only double accessors, and the bytecode test
 * enforces exactly that, because those are the ones the depth sort reads per particle per frame.
 */
@Mixin(Particle.class)
public interface ParticleLifetimeAccessor {
    @Accessor("lifetime") int slo$getLifetime();
    @Accessor("lifetime") void slo$setLifetime(int value);
}