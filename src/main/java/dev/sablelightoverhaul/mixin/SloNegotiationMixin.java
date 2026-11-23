package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sablelightoverhaul.SloNegotiation;
import java.util.List;
import net.neoforged.neoforge.network.negotiation.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = NetworkComponentNegotiator.class, remap = false)
abstract class SloNegotiationMixin {
    @WrapMethod(method = "negotiate")
    private static NegotiationResult slo$optionalVersions(List<NegotiableNetworkComponent> server,
            List<NegotiableNetworkComponent> client, Operation<NegotiationResult> original) {
        var skipped = SloNegotiation.incompatible(server, client);
        if (skipped.isEmpty()) return original.call(server, client);
        return original.call(server.stream().filter(c -> !skipped.contains(c.id())).toList(),
                client.stream().filter(c -> !skipped.contains(c.id())).toList());
    }
}
