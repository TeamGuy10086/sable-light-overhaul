package dev.cbcmuzzlelight;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One tick of one tracer shell's flight: the two positions CBC's own trail interpolates between, and the
 * shell's velocity for the frame's drift.
 *
 * <p>The server does not draw. It sends {@code px..pz} (the shell's position before this tick),
 * {@code x..z} (its position after), and {@code mx..mz} (its velocity, from {@code getDeltaMovement()}),
 * and the client lays CBC's own loop over them, with the frame velocity
 * {@code -motion * trail.drift + jitter} — exactly the format the {@code /mkTrail} debug command used in
 * the version the user verified as drawing correctly.
 *
 * <p>Nothing is predicted and nothing is remembered between messages: a lost packet costs one tick of
 * rope and nothing else. Coordinates are absolute {@code double}s — an earlier version quantised them
 * into 16 bits and silently clamped everything beyond ±8 blocks.
 */
public record ShellTrackPayload(ResourceLocation dimension, List<Shell> shells) implements CustomPacketPayload {
    /** One shell. {@code x,y,z} is where it is; {@code px,py,pz} where it was one tick ago; {@code m} its velocity. */
    public record Shell(int id,
                        double x, double y, double z,
                        double px, double py, double pz,
                        double mx, double my, double mz) {}

    public static final Type<ShellTrackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MuzzleLight.ID, "shell_track"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShellTrackPayload> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeResourceLocation(value.dimension);
            buf.writeVarInt(value.shells.size());
            for (Shell s : value.shells) {
                buf.writeVarInt(s.id());
                buf.writeDouble(s.x()); buf.writeDouble(s.y()); buf.writeDouble(s.z());
                buf.writeDouble(s.px()); buf.writeDouble(s.py()); buf.writeDouble(s.pz());
                buf.writeDouble(s.mx()); buf.writeDouble(s.my()); buf.writeDouble(s.mz());
            }
        },
        buf -> {
            ResourceLocation dimension = buf.readResourceLocation();
            int count = Math.min(buf.readVarInt(), 4096);
            List<Shell> shells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                shells.add(new Shell(buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
            }
            return new ShellTrackPayload(dimension, List.copyOf(shells));
        });
    @Override public Type<ShellTrackPayload> type() { return TYPE; }
}
