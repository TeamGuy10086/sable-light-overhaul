package dev.sablelightoverhaul.api;

import dev.sablelightoverhaul.client.PhalanxBurstClient;
import java.util.function.BiConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Client/render thread only. Emits world-space tail/head pairs for the supplied current level.
 * Consumers may transform or clip segments for display. No replay, entity or contact is created. */
public final class ClientPhalanxBurstApi {
    private ClientPhalanxBurstApi() {}

    public static void forEachSegment(ClientLevel level, float partialTick, BiConsumer<Vec3, Vec3> sink) {
        PhalanxBurstClient.forEachSegment(level, partialTick, sink);
    }
}
