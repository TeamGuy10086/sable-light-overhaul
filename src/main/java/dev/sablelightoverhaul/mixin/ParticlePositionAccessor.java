package dev.sablelightoverhaul.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a particle's position from outside its package.
 *
 * <p>{@code x}, {@code y} and {@code z} are {@code protected}, and the depth sort in
 * {@link ParticleDepthSortMixin} needs them for every particle in a layer. The alternative --
 * {@code getRenderBoundingBox} -- is public but builds a box per particle per frame, which is exactly
 * the kind of per-frame allocation a sort of up to 16384 particles must not add.
 */
@Mixin(Particle.class)
public interface ParticlePositionAccessor {
    @Accessor("x") double slo$x();
    @Accessor("y") double slo$y();
    @Accessor("z") double slo$z();
    @Accessor("xo") double slo$previousX();
    @Accessor("yo") double slo$previousY();
    @Accessor("zo") double slo$previousZ();
}
