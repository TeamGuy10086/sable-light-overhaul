package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record BlastPreviewPayload(ResourceLocation dimension, Kind kind, Vec3 origin, Vec3 direction,
                                  float power, int dustColor) implements CustomPacketPayload {
    public enum Kind { GROUND, BURIED, AIR, HULL, INTERCEPTION }

    // Dust RGB occupies the low 24 bits; older clients already read those channels separately.
    private static final int SHALLOW_VENT = 1 << 24;
    private static final int DUST_HEIGHT_SHIFT = 25;

    static int ventDustColor(int rgb, double outletDistance) {
        return (rgb & 0xFFFFFF) | (outletDistance <= 3.25 ? SHALLOW_VENT : 0);
    }

    public boolean shallowVent() {
        return kind == Kind.BURIED && (dustColor & SHALLOW_VENT) != 0;
    }

    static int withDustHeight(int dustColor, float power, int destroyed) {
        if (destroyed < 0) return dustColor;
        int encoded = 1 + (int)Math.round(DustHeightCalibration.heightFactor(power, destroyed) * 64);
        return (dustColor & 0x01FFFFFF) | encoded << DUST_HEIGHT_SHIFT;
    }

    public double dustHeightFactor() {
        int encoded = dustColor >>> DUST_HEIGHT_SHIFT;
        return encoded == 0 ? 1 : (encoded - 1) / 64.0;
    }

    public static final Type<BlastPreviewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "blast_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlastPreviewPayload> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeResourceLocation(value.dimension);
            buf.writeEnum(value.kind);
            writeVector(buf, value.origin);
            writeVector(buf, value.direction);
            buf.writeFloat(value.power);
            buf.writeInt(value.dustColor);
        },
        buf -> new BlastPreviewPayload(buf.readResourceLocation(), buf.readEnum(Kind.class),
            readVector(buf), readVector(buf), buf.readFloat(), buf.readInt()));

    private static double visualRangeSquared() {
        int range = LightingCommon.PARTICLE_FORCE.get() ? LightingCommon.EXPLOSION_CULL.get() : 256;
        return (double)range * range;
    }

    public static void broadcast(ServerLevel level, Kind kind, Vec3 origin, Vec3 direction, float power, int dustColor) {
        if (!Double.isFinite(origin.lengthSqr()) || !Double.isFinite(direction.lengthSqr())
            || !Float.isFinite(power) || power <= 0) return;
        var payload = new BlastPreviewPayload(level.dimension().location(), kind, origin, direction, power, dustColor);
        double rangeSquared = visualRangeSquared();
        for (var viewer : level.players()) {
            if (viewer.distanceToSqr(origin) <= rangeSquared && viewer.connection.hasChannel(TYPE)) {
                SloNetwork.sendToPlayer(viewer, payload);
            }
        }
    }

    public record Impact(BlastPreviewPayload visual) implements CustomPacketPayload {
        public static final float MIN_POWER = 6;
        public static final Type<Impact> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "blast_impact_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Impact> CODEC =
            BlastPreviewPayload.CODEC.map(Impact::new, Impact::visual);

        static void broadcast(ServerLevel level, Kind kind, Vec3 center, Vec3 origin, Vec3 direction, float power, int color) {
            var packet = new Impact(new BlastPreviewPayload(level.dimension().location(), kind, origin, direction, power, color));
            double rangeSquared = visualRangeSquared();
            for (var viewer : level.players()) {
                if (Math.min(viewer.distanceToSqr(center), viewer.distanceToSqr(origin)) <= rangeSquared && viewer.connection.hasChannel(TYPE)) {
                    SloNetwork.sendToPlayer(viewer, packet);
                }
            }
        }

        @Override public Type<Impact> type() { return TYPE; }
    }

    private static void writeVector(RegistryFriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
    }

    private static Vec3 readVector(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override public Type<BlastPreviewPayload> type() { return TYPE; }

    public record Approach(ResourceLocation dimension, Vec3 end, Vec3 motion) implements CustomPacketPayload {
        public static final int TICKS = 9;
        public static final Type<Approach> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "blast_preview_approach"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Approach> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeResourceLocation(value.dimension);
                writeVector(buf, value.end);
                writeVector(buf, value.motion);
            },
            buf -> new Approach(buf.readResourceLocation(), readVector(buf), readVector(buf)));

        public static void broadcast(ServerLevel level, Vec3 end, Vec3 motion) {
            var payload = new Approach(level.dimension().location(), end, motion);
            for (var viewer : level.players()) {
                if (viewer.distanceToSqr(end) <= 256 * 256 && viewer.connection.hasChannel(TYPE)) {
                    SloNetwork.sendToPlayer(viewer, payload);
                }
            }
        }

        @Override public Type<Approach> type() { return TYPE; }
    }

    public record Adjust(boolean power, boolean next) implements CustomPacketPayload {
        public static final Type<Adjust> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "blast_preview_adjust"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Adjust> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBoolean(value.power); buf.writeBoolean(value.next); },
            buf -> new Adjust(buf.readBoolean(), buf.readBoolean()));

        @Override public Type<Adjust> type() { return TYPE; }
    }
}
