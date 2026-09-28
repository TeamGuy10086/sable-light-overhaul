package dev.cbcmuzzlelight.lighting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cbcmuzzlelight.MuzzleLight;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Ground dust, reusing the frames Create: Big Cannons uses for {@code debris_smoke}
 * ({@code minecraft:big_smoke_0..11}) with this mod's own control program.
 *
 * <p>Everything the effect is made of lives here, because the emitter is the only side that knows
 * how strong the dust was stirred: how wide one cloud is, how long it lasts, how much it is
 * stretched sideways, and the two numbers the motion is integrated with (a high horizontal damping
 * and a gravity that only ever pulls down).
 *
 * <pre>cbcmuzzlelight:ground_dust{size:2.4,color:16777215,lifetime:40,stretch:1.6,track:true,damping:0.75,gravity:0.08,alpha:0.35}</pre>
 *
 * @param size - how many blocks the cloud spans; it barely grows from there.
 * @param color - 0xRRGGBB tint multiplied into the grey smoke frames.
 * @param lifetime - ticks before it disappears; the last part of that fades it out.
 * @param stretch - horizontal widening, 1 is round.
 * @param track - true for track dust, which is thrown up and lands; false for dust a shot stirs up,
 *                which is pushed along the ground and sinks onto it.
 * @param damping - per-tick multiplier on horizontal motion. Dust does not glide: a high value stops
 *                  a cloud within a handful of ticks, so a trail is made of many clouds left behind.
 * @param gravity - downward acceleration, in the units vanilla uses (0.04 per tick squared each).
 * @param alpha - opacity at birth; the cloud fades out over its last stretch.
 */
public record GroundDustParticleData(float size,int color,int lifetime,float stretch,boolean track,
                                     float damping,float gravity,float alpha) implements ParticleOptions {
 public static final MapCodec<GroundDustParticleData> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
   Codec.FLOAT.fieldOf("size").forGetter(GroundDustParticleData::size),
   Codec.INT.optionalFieldOf("color",0xFFFFFF).forGetter(GroundDustParticleData::color),
   Codec.INT.optionalFieldOf("lifetime",60).forGetter(GroundDustParticleData::lifetime),
   Codec.FLOAT.optionalFieldOf("stretch",1.0F).forGetter(GroundDustParticleData::stretch),
   Codec.BOOL.optionalFieldOf("track",false).forGetter(GroundDustParticleData::track),
   Codec.FLOAT.optionalFieldOf("damping",0.75F).forGetter(GroundDustParticleData::damping),
   Codec.FLOAT.optionalFieldOf("gravity",0.08F).forGetter(GroundDustParticleData::gravity),
   Codec.FLOAT.optionalFieldOf("alpha",0.35F).forGetter(GroundDustParticleData::alpha)
 ).apply(instance,GroundDustParticleData::new));
 public static final StreamCodec<RegistryFriendlyByteBuf,GroundDustParticleData> STREAM_CODEC=StreamCodec.of(
   (buf,data)->{buf.writeFloat(data.size());buf.writeInt(data.color());buf.writeVarInt(data.lifetime());buf.writeFloat(data.stretch());buf.writeBoolean(data.track());buf.writeFloat(data.damping());buf.writeFloat(data.gravity());buf.writeFloat(data.alpha());},
   buf->new GroundDustParticleData(buf.readFloat(),buf.readInt(),buf.readVarInt(),buf.readFloat(),buf.readBoolean(),buf.readFloat(),buf.readFloat(),buf.readFloat()));
 private static final int MAX_LIFETIME=1200;
 @Override public ParticleType<?> getType(){return MuzzleLight.GROUND_DUST.get();}
 /** The same cloud with the tint replaced, used when dust takes the colour of the block under it. */
 public GroundDustParticleData withColor(int rgb){return new GroundDustParticleData(size,rgb,lifetime,stretch,track,damping,gravity,alpha);}
 /** The same cloud with every value the emitter randomises or owns; all of them are clamped here so
  * a configuration page can never send the client a particle it cannot draw. */
 public GroundDustParticleData with(float newSize,int newLifetime,float newStretch,float newDamping,float newGravity,float newAlpha){
  return new GroundDustParticleData(Math.min(Math.max(newSize,0.1F),16.0F),color,
    Math.min(Math.max(newLifetime,2),MAX_LIFETIME),Math.min(Math.max(newStretch,1.0F),6.0F),track,
    Math.min(Math.max(newDamping,0.1F),1.0F),Math.min(Math.max(newGravity,0.0F),4.0F),
    Math.min(Math.max(newAlpha,0.02F),1.0F));
 }
}
