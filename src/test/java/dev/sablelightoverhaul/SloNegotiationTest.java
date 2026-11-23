package dev.sablelightoverhaul;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SloNegotiationTest {
    private static NegotiableNetworkComponent channel(String id, String version, PacketFlow flow, boolean optional) {
        return new NegotiableNetworkComponent(ResourceLocation.parse(id), version, Optional.of(flow), optional);
    }

    @Test
    void filtersOnlyIncompatibleMutuallyOptionalSloChannels() {
        var a = channel("sablelightoverhaul:a", "1", PacketFlow.CLIENTBOUND, true);
        var b = channel("sablelightoverhaul:b", "1", PacketFlow.CLIENTBOUND, true);
        var c = channel("sablelightoverhaul:c", "1", PacketFlow.CLIENTBOUND, true);
        var other = channel("other:stream", "1", PacketFlow.CLIENTBOUND, true);
        var legacy = channel("sablelightoverhaul:legacy", "1", PacketFlow.CLIENTBOUND, false);
        var client = List.of(a,
                channel(b.id().toString(), "2", PacketFlow.CLIENTBOUND, true),
                channel(c.id().toString(), "1", PacketFlow.SERVERBOUND, true),
                channel(other.id().toString(), "2", PacketFlow.CLIENTBOUND, true),
                channel(legacy.id().toString(), "2", PacketFlow.CLIENTBOUND, true));
        var server = List.of(a, b, c, other, legacy);
        assertEquals(Set.of(b.id(), c.id()), SloNegotiation.incompatible(server, client));
        assertEquals(Set.of(b.id(), c.id()), SloNegotiation.incompatible(client, server));
    }

    @Test
    void neoForgeAcceptsOptionalChannelsMissingOnEitherSide() {
        var channels = List.of(channel("sablelightoverhaul:flash", "46", PacketFlow.CLIENTBOUND, true));
        assertTrue(NetworkComponentNegotiator.negotiate(channels, List.of()).success());
        assertTrue(NetworkComponentNegotiator.negotiate(List.of(), channels).success());
    }
}
