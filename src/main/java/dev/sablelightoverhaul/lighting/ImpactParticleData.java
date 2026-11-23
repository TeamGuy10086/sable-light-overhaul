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
 * A spark, a streak or a chip thrown by an impact. One type, three looks, one physics.
 *
 * <p>This exists because Create: Big Cannons' own spark draws itself with a custom
 * {@code ParticleRenderType}, a solid colour texture and a hand-set entity shader, and Iris only
 * replaces the vanilla particle render types: under a shader pack those sparks are simply not drawn.
 * This one is drawn with a vanilla type and an atlas sprite, so any pack can draw it, and its motion
 * is the same in all three looks.
 *
 * <pre>sablelightoverhaul:impact{size:0.16,color:16755200,lifetime:20,bounce:0.45,mode:1,glow:1.0}</pre>
 *
 * @param size - how many blocks the quad spans.
 * @param color - 0xRRGGBB tint.
 * @param lifetime - ticks before it disappears.
 * @param bounce - how much speed survives hitting a surface; 0 means it dies on contact.
 * @param mode - {@link #SPARK} is a small bright square facing the camera, {@link #STREAK} is a
 *               scratch drawn along the direction of travel (the look Create: Big Cannons' sparks
 *               have), {@link #CHIP} is a longer, dimmer, tumbling fragment.
 * @param glow - how strongly the particle asks to be treated as emissive. It is already drawn
 *               self-lit; this value is carried so a shader adapter that offers an emissive pass can
 *               pick these particles up later without changing the effect.
 */
public record ImpactParticleData(float size,int color,int lifetime,float bounce,int mode,float glow) implements ParticleOptions {
 /** A small bright square facing the camera. */
 public static final int SPARK=0;
 /** A scratch along the direction of travel; the closest look to the original CBC spark. */
 public static final int STREAK=1;
 /** A longer, dimmer fragment that tumbles as it flies. */
 public static final int CHIP=2;
 public static final int FLAK_STREAK=3;
 public static final int SHELL_SPARK=4;
 public static final int SHELL_DEBRIS=5;
 public static final int AIR_DEBRIS=6;
 public static final MapCodec<ImpactParticleData> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
   Codec.FLOAT.fieldOf("size").forGetter(ImpactParticleData::size),
   Codec.INT.optionalFieldOf("color",0xFFAA33).forGetter(ImpactParticleData::color),
   Codec.INT.optionalFieldOf("lifetime",20).forGetter(ImpactParticleData::lifetime),
   Codec.FLOAT.optionalFieldOf("bounce",0.45F).forGetter(ImpactParticleData::bounce),
   Codec.INT.optionalFieldOf("mode",SPARK).forGetter(ImpactParticleData::mode),
   Codec.FLOAT.optionalFieldOf("glow",1.0F).forGetter(ImpactParticleData::glow)
 ).apply(instance,ImpactParticleData::new));
 public static final StreamCodec<RegistryFriendlyByteBuf,ImpactParticleData> STREAM_CODEC=StreamCodec.of(
   (buf,data)->{buf.writeFloat(data.size());buf.writeInt(data.color());buf.writeVarInt(data.lifetime());buf.writeFloat(data.bounce());buf.writeVarInt(data.mode());buf.writeFloat(data.glow());},
   buf->new ImpactParticleData(buf.readFloat(),buf.readInt(),buf.readVarInt(),buf.readFloat(),buf.readVarInt(),buf.readFloat()));
 @Override public ParticleType<?> getType(){return SableLightOverhaul.IMPACT.get();}
 public boolean isChip(){return mode==CHIP||isShellDebris();}
 public boolean isShellDebris(){return mode==SHELL_DEBRIS||mode==AIR_DEBRIS;}
 public boolean isStreak(){return mode==STREAK||mode==FLAK_STREAK||mode==SHELL_SPARK;}
 /** The same particle with its tint replaced, for a burst that takes a colour from its context. */
 public ImpactParticleData withColor(int rgb){return new ImpactParticleData(size,rgb,lifetime,bounce,mode,glow);}
}
