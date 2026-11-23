package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Confirmed segments of Healthbar-managed autocannon rounds, including the final segment to contact. */
public record AutocannonFlightPayload(ResourceLocation dimension, UUID session, List<Segment> segments)
        implements CustomPacketPayload {
    public static final int LIMIT = 256;
    public static final Type<AutocannonFlightPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "autocannon_confirmed_flight"));

    public AutocannonFlightPayload {
        if (segments.size() > LIMIT) throw new IllegalArgumentException("Too many autocannon flight segments");
        segments = List.copyOf(segments);
    }

    public record Segment(long number, Vec3 from, Vec3 to, boolean flak, int phaseFifths, boolean terminal) {
        public Segment(long number, Vec3 from, Vec3 to, boolean flak) {
            this(number, from, to, flak, 0);
        }
        public Segment(long number, Vec3 from, Vec3 to, boolean flak, int phaseFifths) {
            this(number, from, to, flak, phaseFifths, false);
        }
        public Segment {
            if (number <= 0 || number >= Long.MAX_VALUE - Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid autocannon flight id");
            }
            if (phaseFifths < 0 || phaseFifths > 4) throw new IllegalArgumentException("Invalid launch phase");
        }

        /** Separate from CBC entity ids (int) and the predicted Healthbar visuals (negative long). */
        public long visualId() { return Long.MAX_VALUE - number; }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AutocannonFlightPayload> CODEC = codec(false);

    private static StreamCodec<RegistryFriendlyByteBuf, AutocannonFlightPayload> codec(boolean phased) {
      return StreamCodec.of(
        (buf, payload) -> {
            buf.writeResourceLocation(payload.dimension);
            buf.writeUUID(payload.session);
            buf.writeVarInt(payload.segments.size());
            for (var segment : payload.segments) {
                buf.writeVarLong(segment.number);
                vector(buf, segment.from);
                vector(buf, segment.to);
                buf.writeBoolean(segment.flak);
                if (phased) buf.writeByte(segment.phaseFifths | (segment.terminal ? 8 : 0));
            }
        }, buf -> {
            var dimension = buf.readResourceLocation();
            var session = buf.readUUID();
            int size = buf.readVarInt();
            if (size < 0 || size > LIMIT) throw new IllegalArgumentException("Invalid autocannon flight batch size");
            var segments = new ArrayList<Segment>(size);
            for (int i = 0; i < size; i++) {
                long number = buf.readVarLong();
                Vec3 from = vector(buf), to = vector(buf);
                boolean flak = buf.readBoolean();
                int flags = phased ? buf.readUnsignedByte() : 0;
                if ((flags & ~15) != 0) throw new IllegalArgumentException("Invalid autocannon segment flags");
                segments.add(new Segment(number, from, to, flak, flags & 7, (flags & 8) != 0));
            }
            return new AutocannonFlightPayload(dimension, session, segments);
        });
    }

    public record Phased(AutocannonFlightPayload data) implements CustomPacketPayload {
        public static final Type<Phased> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "autocannon_confirmed_flight_phased"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Phased> CODEC =
            codec(true).map(Phased::new, Phased::data);
        @Override public Type<Phased> type() { return TYPE; }
    }

    /** Negotiates render sampling for direct autocannons. Other autocannons retain the confirmed feed. */
    public record Sampled(AutocannonFlightPayload data) implements CustomPacketPayload {
        public static final Type<Sampled> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "autocannon_sampled_flight"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sampled> CODEC =
            codec(true).map(Sampled::new, Sampled::data);
        @Override public Type<Sampled> type() { return TYPE; }
    }

    private static void vector(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
    }

    private static Vec3 vector(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override public Type<AutocannonFlightPayload> type() { return TYPE; }
}
