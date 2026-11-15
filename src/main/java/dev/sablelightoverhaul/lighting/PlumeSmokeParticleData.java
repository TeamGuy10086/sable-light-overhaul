package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One puff of a muzzle plume: what our copy of CBC's smoke is, plus an opacity of our own.
 *
 * @param size     how large the puff is drawn
 * @param lifetime how many ticks it lasts
 * @param alpha    the opacity it starts at, before the fade
 * @param fadeFrom the fraction of its life after which the fade begins
 * @param fadeRate how quickly the opacity falls, per tick, once it has begun
 */
public record PlumeSmokeParticleData(float size, int lifetime, float alpha, float fadeFrom, float fadeRate, float expansion)
        implements ParticleOptions {
    public PlumeSmokeParticleData(float size,int lifetime,float alpha,float fadeFrom,float fadeRate) {
        this(size,lifetime,alpha,fadeFrom,fadeRate,1.8f);
    }
    public static final MapCodec<PlumeSmokeParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(PlumeSmokeParticleData::size),
            com.mojang.serialization.Codec.INT.optionalFieldOf("lifetime", 30).forGetter(PlumeSmokeParticleData::lifetime),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("alpha", 0.6F).forGetter(PlumeSmokeParticleData::alpha),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("fadeFrom", 0.35F).forGetter(PlumeSmokeParticleData::fadeFrom),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("fadeRate", 1.5F).forGetter(PlumeSmokeParticleData::fadeRate),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("expansion", 1.8F).forGetter(PlumeSmokeParticleData::expansion)
    ).apply(instance, PlumeSmokeParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlumeSmokeParticleData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeFloat(data.size());
                buf.writeVarInt(data.lifetime());
                buf.writeFloat(data.alpha());
                buf.writeFloat(data.fadeFrom());
                buf.writeFloat(data.fadeRate());
                buf.writeFloat(data.expansion());
            },
            buf -> new PlumeSmokeParticleData(buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(),buf.readFloat()));

    /** The opacity this puff should have at this point in its life. */
    public float alphaAt(float progress) {
        if (progress <= fadeFrom()) return clamp(alpha());
        return clamp(alpha() * (1.0F - (progress - fadeFrom()) * fadeRate()));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    @Override public ParticleType<?> getType() {
        return dev.sablelightoverhaul.SableLightOverhaul.PLUME_SMOKE.get();
    }
}