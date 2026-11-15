package dev.sablelightoverhaul;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Separate optional channel so older clients never confuse a mathematical flight with an entity id. */
public record HostileMathFlightPayload(HostileShellPayload snapshot) implements CustomPacketPayload {
    public static final String NATIVE_HANDOFF = "native-handoff";
    // Normal mathematical rows use id 0. Reserve -1 on this channel only, without changing the wire layout.
    private static final int HANDOFF = -1;
    public static final Type<HostileMathFlightPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "hostile_confirmed_flight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HostileMathFlightPayload> CODEC =
        HostileShellPayload.CODEC.map(HostileMathFlightPayload::new, HostileMathFlightPayload::snapshot);

    public static HostileShellPayload.Shell handoff(UUID carrier, HostileShellPayload.Model model, Vec3 position) {
        return new HostileShellPayload.Shell(HANDOFF, carrier, model, position, position, true, false);
    }

    public static boolean isHandoff(HostileShellPayload.Shell shell) {
        return shell.terminal() && shell.id() == HANDOFF;
    }

    @Override public Type<HostileMathFlightPayload> type() { return TYPE; }
}
