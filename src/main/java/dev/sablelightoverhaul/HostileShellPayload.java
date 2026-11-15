package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A bounded snapshot of the naval shell models this viewer can supplement. */
public record HostileShellPayload(ResourceLocation dimension, long tick, List<Shell> shells) implements CustomPacketPayload {
    public static final int LIMIT = 256;
    public static final Type<HostileShellPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "hostile_shells"));

    public enum Model {
        HE_203("shell_203mm_he"), AP_203("shell_203mm_ap"),
        HE_127("shell_127mm_he"), AP_127("shell_127mm_ap"),
        HE_76("shell_76mm_he"), AP_76("shell_76mm_ap");

        private final ResourceLocation id;
        Model(String path) { id = ResourceLocation.fromNamespaceAndPath("sablehostile", path); }
        public ResourceLocation id() { return id; }
        public boolean proximityHe() { return this == HE_76 || this == HE_127; }

        private static final java.util.Map<String, Model> BY_TYPE = java.util.Arrays.stream(values())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(model -> model.id.toString(), model -> model));

        public static Model forType(String type) { return BY_TYPE.get(type); }
    }

    public record Shell(int id, UUID uuid, Model model, Vec3 previous, Vec3 current, boolean terminal, boolean tracer) {
        public Shell terminalAt(Vec3 position) {
            Vec3 from = position.equals(current) ? previous : current;
            if (!validStep(from, position)) from = position;
            return new Shell(id, uuid, model, from, position, true, tracer);
        }
    }

    public HostileShellPayload {
        shells = List.copyOf(shells);
        if (shells.size() > LIMIT) throw new IllegalArgumentException("Too many naval shells");
    }

    public static boolean validStep(Vec3 from, Vec3 to) {
        return validPosition(from) && validPosition(to)
            && from.distanceToSqr(to) <= 128 * 128;
    }

    private static boolean validPosition(Vec3 v) {
        return Math.abs(v.x) <= 6e7 && Math.abs(v.y) <= 6e7 && Math.abs(v.z) <= 6e7;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HostileShellPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeResourceLocation(payload.dimension);
            buf.writeLong(payload.tick);
            buf.writeVarInt(payload.shells.size());
            for (var shell : payload.shells) {
                buf.writeVarInt(shell.id);
                buf.writeUUID(shell.uuid);
                buf.writeByte(shell.model.ordinal() | (shell.terminal ? 128 : 0) | (shell.tracer ? 64 : 0));
                writeVec(buf, shell.previous);
                writeVec(buf, shell.current);
            }
        },
        buf -> {
            var dimension = buf.readResourceLocation();
            long tick = buf.readLong();
            int count = buf.readVarInt();
            if (count < 0 || count > LIMIT) throw new IllegalArgumentException("Invalid naval shell count");
            var shells = new ArrayList<Shell>(count);
            var models = Model.values();
            for (int i = 0; i < count; i++) {
                int id = buf.readVarInt();
                UUID uuid = buf.readUUID();
                int flags = buf.readUnsignedByte();
                int model = flags & 63;
                Vec3 from = readVec(buf), to = readVec(buf);
                if (model >= models.length || !validStep(from, to)) throw new IllegalArgumentException("Invalid naval shell sample");
                shells.add(new Shell(id, uuid, models[model], from, to, (flags & 128) != 0, (flags & 64) != 0));
            }
            return new HostileShellPayload(dimension, tick, shells);
        });

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z);
    }
    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    @Override public Type<HostileShellPayload> type() { return TYPE; }
}
