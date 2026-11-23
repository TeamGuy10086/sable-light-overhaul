package dev.sablelightoverhaul;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;

/** Remove only incompatible SLO channels whose two registrations both allow their omission. */
public final class SloNegotiation {
    private SloNegotiation() {}
    public static Set<ResourceLocation> incompatible(List<NegotiableNetworkComponent> server,
                                                     List<NegotiableNetworkComponent> client) {
        var skipped = new HashSet<ResourceLocation>();
        for (var local : server) {
            if (!local.optional() || !SableLightOverhaul.ID.equals(local.id().getNamespace())) continue;
            for (var peer : client) {
                if (peer.optional() && local.id().equals(peer.id())
                    && (!local.version().equals(peer.version()) || !local.flow().equals(peer.flow()))) {
                    skipped.add(local.id());
                }
            }
        }
        return skipped;
    }
}
