package dev.sablelightoverhaul.mixin;

import dev.sablehealthbar.api.flight.ClientFlightFrame;
import dev.sablelightoverhaul.client.HostileShellClient;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Optional hook for legacy Healthbar. 0.8.215 and later have no star renderer to intercept. */
@Pseudo
@Mixin(targets = "dev.sablehealthbar.client.ClientFlights", remap = false)
public abstract class HealthbarNavalPlaceholderMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Ldev/sablehealthbar/flight/FlightClientState;visitVisible(Ljava/util/function/Consumer;)V"),
        index = 0, require = 0, expect = 0)
    private static Consumer<ClientFlightFrame> slo$skipNavalPlaceholder(Consumer<ClientFlightFrame> draw) {
        return frame -> {
            if (!HostileShellClient.replacesMathPlaceholder(frame.current().profile().type())) draw.accept(frame);
        };
    }
}
