package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One muzzle flash, as it crosses to the client.
 *
 * <p>{@code profile} is the per-cannon form for this shot, or null to use the client's own configuration. It
 * is a fixed set of {@link #PROFILE_SLOTS} numbers whose names are listed in {@link #PROFILE_SLOTS_ARE},
 * because a form is a known shape rather than a map: sending names with every shot would cost more than the
 * numbers do.
 *
 * <p>The form travels with the shot instead of being looked up on the client. Which cannon fired is decided on
 * the server -- that is where its identity and its type are known -- and a client has no way to work out which
 * cannon a flash came from, so it cannot look the same thing up even in principle.
 *
 * <p>{@code mountPos} names the mount that fired, packed as one long, which carries that idea one step further:
 * a mount is a definite coordinate whether it stands in the world or inside a body, so one number identifies it
 * and the client can turn it into a cannon. Anything else that wants to be per-cannon as well as per-kind can
 * then use the same number instead of needing a field of its own.
 */
public record FlashPayload(ResourceLocation dimension, double x, double y, double z,
                           float dx, float dy, float dz, int kind, int emitterId, double lightX, double lightY, double lightZ,
                           float charge, float plumeSize, float[] profile, boolean hasMount, long mountPos)
        implements CustomPacketPayload {
    // The mount slot is unused when hasMount is false. Reuse it without changing the packet layout.
    static final long HALF_SIZE_FIREBALL = 1L;

    public float fireballSizeScale() {
        return !hasMount && mountPos == HALF_SIZE_FIREBALL ? 0.5f : 1.0f;
    }

    /** How many numbers a form is. */
    public static final int PROFILE_SLOTS = 14;
    /**
     * What those numbers are, in order, named exactly as an exported form names them, so a value read here and
     * a value written to the clipboard mean the same thing.
     */
    public static final String PROFILE_SLOTS_ARE =
            "shape.forwardRange, shape.rearRange, shape.sideRange, shape.verticalRange, shape.rearClipDistance, "
                    + "spatial.coreFraction, spatial.middleFraction, spatial.tailBrightness, "
                    + "time.durationTicks, time.peakHoldTicks, time.minimumBrightness, time.decayPerTick, "
                    + "scaling.referenceSize, scaling.maximumRange";

    /** True when this shot carries a form at all. */
    public boolean hasProfile() {
        return profile != null && profile.length == PROFILE_SLOTS;
    }

    /** A shot with neither a form nor a mount to name. */
    public FlashPayload(ResourceLocation dimension, double x, double y, double z,
                        float dx, float dy, float dz, int kind, int emitterId,
                        double lightX, double lightY, double lightZ, float charge, float plumeSize) {
        this(dimension, x, y, z, dx, dy, dz, kind, emitterId, lightX, lightY, lightZ, charge, plumeSize, null,
                false, 0L);
    }

    /** A shot with a form but no mount: an explosion is not fired by a cannon. */
    public FlashPayload(ResourceLocation dimension, double x, double y, double z,
                        float dx, float dy, float dz, int kind, int emitterId,
                        double lightX, double lightY, double lightZ, float charge, float plumeSize,
                        float[] profile) {
        this(dimension, x, y, z, dx, dy, dz, kind, emitterId, lightX, lightY, lightZ, charge, plumeSize, profile,
                false, 0L);
    }

    public static final Type<FlashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "flash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeResourceLocation(value.dimension);
            buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
            buf.writeFloat(value.dx); buf.writeFloat(value.dy); buf.writeFloat(value.dz);
            buf.writeByte(value.kind);
            buf.writeVarInt(value.emitterId); buf.writeDouble(value.lightX); buf.writeDouble(value.lightY); buf.writeDouble(value.lightZ);
            buf.writeFloat(value.charge); buf.writeFloat(value.plumeSize);
            // A form is either there in full or not at all, so one flag is enough and a client can never read
            // half of one.
            boolean carrying = value.hasProfile();
            buf.writeBoolean(carrying);
            if (carrying) for (float one : value.profile) buf.writeFloat(one);
            // A flag rather than a sentinel position: a cannon could stand on the position a sentinel would use,
            // and then a blast would look exactly like a shot.
            buf.writeBoolean(value.hasMount());
            buf.writeLong(value.mountPos());
        },
        buf -> {
            ResourceLocation dimension = buf.readResourceLocation();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float dx = buf.readFloat(), dy = buf.readFloat(), dz = buf.readFloat();
            int kind = buf.readUnsignedByte();
            int emitterId = buf.readVarInt();
            double lightX = buf.readDouble(), lightY = buf.readDouble(), lightZ = buf.readDouble();
            float charge = buf.readFloat(), plumeSize = buf.readFloat();
            float[] profile = null;
            if (buf.readBoolean()) {
                profile = new float[PROFILE_SLOTS];
                for (int slot = 0; slot < PROFILE_SLOTS; slot++) profile[slot] = buf.readFloat();
            }
            boolean hasMount = buf.readBoolean();
            long mountPos = buf.readLong();
            return new FlashPayload(dimension, x, y, z, dx, dy, dz, kind, emitterId,
                    lightX, lightY, lightZ, charge, plumeSize, profile, hasMount, mountPos);
        });

    @Override public Type<FlashPayload> type() { return TYPE; }
}
