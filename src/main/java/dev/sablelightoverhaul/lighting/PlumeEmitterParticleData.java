package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** A short smoke release. Legacy field power now carries the finite per-shot puff quantity (same wire format). */
public record PlumeEmitterParticleData(float size, float power, int lifetime, float alpha, float fadeFrom,
                                       float fadeRate, float travel, int childLifetime, float expansion) implements ParticleOptions {
    public PlumeEmitterParticleData(float size,float power,int lifetime,float alpha,float fadeFrom,float fadeRate) {
        this(size,power,lifetime,alpha,fadeFrom,fadeRate,4,18,1.8f);
    }
    public static final MapCodec<PlumeEmitterParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(PlumeEmitterParticleData::size),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("power", 1.0F).forGetter(PlumeEmitterParticleData::power),
            com.mojang.serialization.Codec.INT.optionalFieldOf("lifetime", 10).forGetter(PlumeEmitterParticleData::lifetime),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("alpha", 0.6F).forGetter(PlumeEmitterParticleData::alpha),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("fadeFrom", 0.35F).forGetter(PlumeEmitterParticleData::fadeFrom),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("fadeRate", 1.5F).forGetter(PlumeEmitterParticleData::fadeRate),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("travel", 4F).forGetter(PlumeEmitterParticleData::travel),
            com.mojang.serialization.Codec.INT.optionalFieldOf("childLifetime", 18).forGetter(PlumeEmitterParticleData::childLifetime),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("expansion", 1.8F).forGetter(PlumeEmitterParticleData::expansion)
    ).apply(instance, PlumeEmitterParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlumeEmitterParticleData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeFloat(data.size());
                buf.writeFloat(data.power());
                buf.writeVarInt(data.lifetime());
                buf.writeFloat(data.alpha());
                buf.writeFloat(data.fadeFrom());
                buf.writeFloat(data.fadeRate());
                buf.writeFloat(data.travel());
                buf.writeVarInt(data.childLifetime());
                buf.writeFloat(data.expansion());
            },
            buf -> new PlumeEmitterParticleData(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(),buf.readFloat(),buf.readVarInt(),buf.readFloat()));

    /** The opacity a plume at this point in its life should still have. */
    public float alphaAt(float progress) {
        if (progress <= fadeFrom()) return Math.max(0.0F, Math.min(1.0F, alpha()));
        float faded = alpha() * (1.0F - (progress - fadeFrom()) * fadeRate());
        return Math.max(0.0F, Math.min(1.0F, faded));
    }

    @Override public ParticleType<?> getType() {
        return dev.sablelightoverhaul.SableLightOverhaul.PLUME.get();
    }
}
