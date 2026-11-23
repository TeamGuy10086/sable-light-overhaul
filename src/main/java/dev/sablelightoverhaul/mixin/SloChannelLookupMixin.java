package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkRegistry.class, remap = false)
abstract class SloChannelLookupMixin {
    // c:register and minecraft:register advertise IDs without a codec version. They cannot
    // re-enable a skipped PLAY codec, including when another mod queries our capability.
    @Inject(method = "hasChannel(Lnet/minecraft/network/Connection;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/resources/ResourceLocation;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void slo$negotiated(Connection connection, ConnectionProtocol protocol, ResourceLocation id,
                                       CallbackInfoReturnable<Boolean> result) {
        if (!SableLightOverhaul.ID.equals(id.getNamespace()) || protocol != null && protocol != ConnectionProtocol.PLAY) return;
        var setup = ChannelAttributes.getPayloadSetup(connection);
        result.setReturnValue(setup != null && setup.getChannels(ConnectionProtocol.PLAY).containsKey(id));
    }
}
