package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One emitter sample, in blocks and simulation ticks. */
public record PhalanxBurstPayload(ResourceLocation dimension, Vec3 muzzle, Vec3 axis, long seed,
                                 int count, int firstPhaseFifths, double speed, double range) implements CustomPacketPayload {
    public static final Type<PhalanxBurstPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "phalanx_burst_v1"));
    public static final int MAX_TICKS = 48;
    public boolean valid() {
        return count >= 1 && count <= 3 && firstPhaseFifths >= 0 && firstPhaseFifths <= 4 && firstPhaseFifths + 2 * (count - 1) <= 4
            && Double.isFinite(muzzle.lengthSqr()) && Math.abs(axis.lengthSqr() - 1) < .001
            && speed > 0 && speed <= 128 && range > 0 && range <= 4096;
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, PhalanxBurstPayload> CODEC = StreamCodec.of(
        (buf,p) -> {
            buf.writeResourceLocation(p.dimension);
            buf.writeVec3(p.muzzle);
            buf.writeVec3(p.axis);
            buf.writeLong(p.seed);
            buf.writeByte(p.count);
            buf.writeByte(p.firstPhaseFifths);
            buf.writeDouble(p.speed);
            buf.writeDouble(p.range);
        }, buf -> new PhalanxBurstPayload(buf.readResourceLocation(), buf.readVec3(), buf.readVec3(), buf.readLong(),
            buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readDouble(), buf.readDouble()));
    @Override public Type<PhalanxBurstPayload> type() { return TYPE; }
}
