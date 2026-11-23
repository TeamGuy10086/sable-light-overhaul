package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One confirmed visual/audio event. Neither medium uses the other's distance cutoff. */
public record InterceptionPayload(ResourceLocation dimension, Vec3 center, Vec3 incomingMotion,
                                  ResourceLocation missileType, ResourceLocation warheadKind, float power,
                                  int cbcRange) implements CustomPacketPayload {
    public static final Type<InterceptionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "missile_interception"));
    public static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "unknown");
    public static final int VISUAL_RANGE = 1536;
    public static final int LEGACY_RANGE = 256;

    public static final StreamCodec<FriendlyByteBuf, InterceptionPayload> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeResourceLocation(value.dimension);
            vector(buf, value.center);
            vector(buf, value.incomingMotion);
            buf.writeResourceLocation(value.missileType);
            buf.writeResourceLocation(value.warheadKind);
            buf.writeFloat(value.power);
            buf.writeVarInt(value.cbcRange);
        }, buf -> new InterceptionPayload(buf.readResourceLocation(), vector(buf), vector(buf),
            buf.readResourceLocation(), buf.readResourceLocation(), buf.readFloat(), buf.readVarInt()));

    public static void broadcast(ServerLevel level, Vec3 center, Vec3 incomingMotion,
                                 ResourceLocation missileType, ResourceLocation warheadKind, float power,
                                 float fragmentDiameter) {
        if (center == null || incomingMotion == null || !NavalSoundPayload.validPosition(center)
                || !Double.isFinite(incomingMotion.lengthSqr()) || !Float.isFinite(power) || power < 0) return;
        int range = LightingCommon.EXPLOSION_CULL.get();
        var payload = new InterceptionPayload(level.dimension().location(), center, incomingMotion,
            missileType, warheadKind, power, range);
        var sized = Float.isFinite(fragmentDiameter) && fragmentDiameter > 0
            ? new Sized(payload, Math.clamp(fragmentDiameter, .05f, 4)) : null;
        var legacy = new BlastPreviewPayload(payload.dimension, BlastPreviewPayload.Kind.INTERCEPTION,
            center, incomingMotion, power, 0);
        for (var viewer : level.players()) {
            double distance = viewer.distanceToSqr(center);
            if (viewer.connection.hasChannel(TYPE)) {
                if (receives(distance, range)) {
                    if (sized != null && viewer.connection.hasChannel(Sized.TYPE)) PacketDistributor.sendToPlayer(viewer, sized);
                    else PacketDistributor.sendToPlayer(viewer, payload);
                }
            } else if (power > 0 && distance <= LEGACY_RANGE * LEGACY_RANGE
                    && viewer.connection.hasChannel(BlastPreviewPayload.TYPE)) {
                PacketDistributor.sendToPlayer(viewer, legacy);
            }
        }
    }

    static boolean receives(double distanceSquared, int cbcRange) {
        return distanceSquared <= VISUAL_RANGE * VISUAL_RANGE || distanceSquared < (double)cbcRange * cbcRange;
    }

    public static float cbcVolume(float power) { return Math.max(16, 2 * power); }

    public static double audibleRange(float power, int cbcRange) {
        return Math.min(cbcRange, 16.0 * cbcVolume(power));
    }

    private static void vector(FriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
    }

    private static Vec3 vector(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override public Type<InterceptionPayload> type() { return TYPE; }

    public record Sized(InterceptionPayload data, float fragmentDiameter) implements CustomPacketPayload {
        public static final Type<Sized> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "missile_interception_sized"));
        public static final StreamCodec<FriendlyByteBuf, Sized> CODEC = StreamCodec.of(
            (buf, value) -> { InterceptionPayload.CODEC.encode(buf, value.data); buf.writeFloat(value.fragmentDiameter); },
            buf -> new Sized(InterceptionPayload.CODEC.decode(buf), buf.readFloat()));

        @Override public Type<Sized> type() { return TYPE; }
    }
}
