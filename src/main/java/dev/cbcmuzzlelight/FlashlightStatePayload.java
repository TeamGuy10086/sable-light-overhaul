package dev.cbcmuzzlelight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's flashlight settings, resolved by the server and sent to that player alone.
 *
 * <p>The settings are authoritative on the server (they are operator-gated), so the client that draws
 * the light cannot simply read a config file: it is told what to draw, and told again whenever the
 * value changes, the switch is flipped, or the player rejoins.
 *
 * <p>The beam length travels twice on purpose. The page edits a <b>distance step</b>, which is what the
 * server stores and what every other light in this mod is configured by; the cone is drawn in
 * <b>blocks</b>, and that number has to be the server's, because a client's copy of the server's config
 * file can say something different. Sending both keeps the page showing the right row and the beam the
 * right length without either side guessing.
 *
 * <p>{@code width} is a full angle in degrees and {@code decay} a percentage of the reference profile —
 * see {@link FlashlightBeam}. Both are the numbers the page shows verbatim, so no unit conversion is
 * left for the client to get wrong.
 */
public record FlashlightStatePayload(boolean on,int color,int rangeSteps,int rangeBlocks,int width,int decay,
                                     int intensity,int pointRadius,int pointIntensity) implements CustomPacketPayload {
 public static final Type<FlashlightStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MuzzleLight.ID,"flashlight_state"));
 public static final StreamCodec<RegistryFriendlyByteBuf,FlashlightStatePayload> CODEC=StreamCodec.of((b,p)->{
  b.writeBoolean(p.on);
  b.writeByte(p.color);b.writeByte(p.rangeSteps);b.writeShort(p.rangeBlocks);b.writeByte(p.width);
  // A short, not a byte: the decay speed runs to 300 percent.
  b.writeShort(p.decay);b.writeByte(p.intensity);b.writeByte(p.pointRadius);b.writeByte(p.pointIntensity);
 },b->new FlashlightStatePayload(b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedShort(),
   b.readUnsignedByte(),b.readUnsignedShort(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte()));
 @Override public Type<FlashlightStatePayload> type(){return TYPE;}
}
