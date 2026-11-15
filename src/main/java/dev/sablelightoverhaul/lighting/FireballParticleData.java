package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** One connected muzzle gas event with explicit axis length and cross-section controls. */
public record FireballParticleData(float size, int lifetime, float alpha, float length, float coneAngle, int detail) implements ParticleOptions {
    public FireballParticleData(float size,int lifetime,float alpha) { this(size,lifetime,alpha,2.5f,22,12); }
    public static final MapCodec<FireballParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.FLOAT.fieldOf("size").forGetter(FireballParticleData::size),
            com.mojang.serialization.Codec.INT.optionalFieldOf("lifetime", 3).forGetter(FireballParticleData::lifetime),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("alpha", 0.95F).forGetter(FireballParticleData::alpha),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("length", 2.5F).forGetter(FireballParticleData::length),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("cone_angle", 22F).forGetter(FireballParticleData::coneAngle),
            com.mojang.serialization.Codec.INT.optionalFieldOf("detail", 12).forGetter(FireballParticleData::detail)
    ).apply(instance, FireballParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FireballParticleData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeFloat(data.size());
                buf.writeVarInt(data.lifetime());
                buf.writeFloat(data.alpha());
                buf.writeFloat(data.length());
                buf.writeFloat(data.coneAngle());
                buf.writeVarInt(data.detail());
            },
            buf -> new FireballParticleData(buf.readFloat(), buf.readVarInt(), buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readVarInt()));

    @Override public ParticleType<?> getType() {
        return dev.sablelightoverhaul.SableLightOverhaul.FIREBALL.get();
    }
}
