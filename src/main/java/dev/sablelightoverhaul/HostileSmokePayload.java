package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** An opt-in, typed muzzle-smoke delivery. The existing flash and dust codecs remain independent. */
public record HostileSmokePayload(ResourceLocation weapon, ResourceLocation dimension, Vec3 muzzle, Vec3 axis,
                                 CannonDustPayload haze) implements CustomPacketPayload {
    public static final Type<HostileSmokePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "hostile_muzzle_smoke"));

    public static HostileSmokePayload thin(ResourceLocation weapon, CannonDustPayload haze) {
        return new HostileSmokePayload(weapon, haze.dimension(), haze.muzzle(), haze.axis(), haze);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HostileSmokePayload> CODEC = StreamCodec.of((buf, value) -> {
        buf.writeResourceLocation(value.weapon);
        buf.writeBoolean(value.haze != null);
        if (value.haze != null) {
            CannonDustPayload.CODEC.encode(buf, value.haze);
        } else {
            buf.writeResourceLocation(value.dimension);
            writeVector(buf, value.muzzle);
            writeVector(buf, value.axis);
        }
    }, buf -> {
        var weapon = buf.readResourceLocation();
        return buf.readBoolean() ? thin(weapon, CannonDustPayload.CODEC.decode(buf))
            : new HostileSmokePayload(weapon, buf.readResourceLocation(), readVector(buf), readVector(buf), null);
    });

    private static void writeVector(RegistryFriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
    }

    private static Vec3 readVector(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override public Type<HostileSmokePayload> type() { return TYPE; }
}
