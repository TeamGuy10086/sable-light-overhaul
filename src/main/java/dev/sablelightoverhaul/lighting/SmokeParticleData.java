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
 * One puff of a smoke vent.
 *
 * <p>The engine smoke generator used to emit a vanilla particle ({@code minecraft:campfire_cosy_smoke}),
 * and a vanilla particle decides its own opacity and how it fades -- there is nowhere to put either of
 * the two things a vent needs to be tunable. This is that particle with the two knobs, drawn with the
 * <b>same frames</b> the vanilla one uses ({@code minecraft:big_smoke_0..11}, see
 * {@code particles/smoke.json}), so a vent that switches to it keeps its look and gains the controls.
 *
 * <pre>sablelightoverhaul:smoke{size:0.9,color:16777215,lifetime:90,opacity:0.5,fade:0.4}</pre>
 *
 * @param size     how many blocks the puff spans.
 * @param color    0xRRGGBB tint multiplied into the grey frames.
 * @param lifetime ticks before it disappears.
 * @param opacity  opacity at birth, {@code 0.02..1}: how thick the plume leaves the pipe.
 * @param fade     the fraction of the life spent fading out, {@code 0.02..1}: how fast it thins.
 */
public record SmokeParticleData(float size, int color, int lifetime, float opacity, float fade)
        implements ParticleOptions {
    /** The frames are already grey, so the default tint is no tint at all. */
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    public static final MapCodec<SmokeParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("size", 0.9F).forGetter(SmokeParticleData::size),
            Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(SmokeParticleData::color),
            Codec.INT.optionalFieldOf("lifetime", 90).forGetter(SmokeParticleData::lifetime),
            Codec.FLOAT.optionalFieldOf("opacity", 0.5F).forGetter(SmokeParticleData::opacity),
            Codec.FLOAT.optionalFieldOf("fade", 0.4F).forGetter(SmokeParticleData::fade)
    ).apply(instance, SmokeParticleData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmokeParticleData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeFloat(data.size());
                buf.writeInt(data.color());
                buf.writeVarInt(data.lifetime());
                buf.writeFloat(data.opacity());
                buf.writeFloat(data.fade());
            },
            buf -> new SmokeParticleData(buf.readFloat(), buf.readInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat()));
    private static final int MAX_LIFETIME = 1200;
    @Override public ParticleType<?> getType() { return SableLightOverhaul.SMOKE.get(); }
    /** The same puff with the values the emitter owns; clamped here, so a page can never send the
     * client a particle it cannot draw. */
    public SmokeParticleData with(float newSize, int newLifetime, float newOpacity, float newFade) {
        return new SmokeParticleData(Math.min(Math.max(newSize, 0.05F), 16.0F), color,
                Math.min(Math.max(newLifetime, 2), MAX_LIFETIME),
                Math.min(Math.max(newOpacity, 0.02F), 1.0F),
                Math.min(Math.max(newFade, 0.02F), 1.0F));
    }
}
