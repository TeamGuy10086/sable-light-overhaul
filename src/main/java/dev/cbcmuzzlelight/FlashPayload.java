package dev.cbcmuzzlelight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FlashPayload(ResourceLocation dimension, double x, double y, double z,
                           float dx, float dy, float dz, int kind, int emitterId, double lightX, double lightY, double lightZ, float charge, float plumeSize)
        implements CustomPacketPayload {
    public FlashPayload(ResourceLocation dimension,double x,double y,double z,float dx,float dy,float dz,int kind,int emitterId,double lightX,double lightY,double lightZ) {
        this(dimension,x,y,z,dx,dy,dz,kind,emitterId,lightX,lightY,lightZ,4,4);
    }
    public static final Type<FlashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MuzzleLight.ID, "flash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeResourceLocation(value.dimension);
            buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
            buf.writeFloat(value.dx); buf.writeFloat(value.dy); buf.writeFloat(value.dz);
            buf.writeByte(value.kind);
            buf.writeVarInt(value.emitterId); buf.writeDouble(value.lightX); buf.writeDouble(value.lightY); buf.writeDouble(value.lightZ); buf.writeFloat(value.charge); buf.writeFloat(value.plumeSize);
        },
        buf -> new FlashPayload(buf.readResourceLocation(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUnsignedByte(),
            buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat()));
    @Override public Type<FlashPayload> type() { return TYPE; }
}
