package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One puff of a shockwave, drawn by this mod instead of by a vanilla particle type.
 *
 * <p>Two reasons it exists, both of them the point of the request that produced it:
 *
 * <ul>
 *   <li><b>Its own render queue.</b> A particle's {@code ParticleRenderType} is the queue it lands in,
 *       and each queue holds 16384 particles and evicts from its head. Vanilla cloud puffs land in the
 *       same queue as Create: Big Cannons' muzzle smoke, so a wave made of them competes with the smoke
 *       for space; these land in their own, so a wave can never push smoke out again.</li>
 *   <li><b>A size that follows the wave.</b> A puff has to cover the shell it is part of, so with a
 *       front that grows to tens of blocks the useful size grows with it -- and the number needed falls
 *       with its square. The wave passes {@code size} in, and about sixty of these cover what five
 *       hundred vanilla puffs did.</li>
 * </ul>
 *
 * <pre>sablelightoverhaul:shockwave{size:4.0,color:16777215,lifetime:8}</pre>
 *
 * <p>{@code size} and {@code lifetime} written in the server config are replaced at spawn by the wave's
 * own front radius and remaining life; {@code color} is not, so a config can tint the wave.
 *
 * @param size     how many blocks the quad spans.
 * @param color    0xRRGGBB tint.
 * @param lifetime ticks before it disappears.
 */
public record ShockwaveParticleData(float size, int color, int lifetime) implements ParticleOptions {
    public static final int DEFAULT_COLOR = 0xEAF2FF;
    public static final MapCodec<ShockwaveParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("size", 4.0F).forGetter(ShockwaveParticleData::size),
            Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(ShockwaveParticleData::color),
            Codec.INT.optionalFieldOf("lifetime", 8).forGetter(ShockwaveParticleData::lifetime)
    ).apply(instance, ShockwaveParticleData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShockwaveParticleData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeFloat(data.size());
                buf.writeInt(data.color());
                buf.writeVarInt(data.lifetime());
            },
            buf -> new ShockwaveParticleData(buf.readFloat(), buf.readInt(), buf.readVarInt()));
    @Override public ParticleType<?> getType() { return SableLightOverhaul.SHOCKWAVE.get(); }
}
