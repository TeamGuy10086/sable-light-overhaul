package dev.sablelightoverhaul.api;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import dev.sablelightoverhaul.client.MissileSmokeMesh;

/** Client-thread-only optional visual API. No particle objects, gameplay, networking or trajectory authority. */
public final class ClientMissileTrailApi {
    public static final int VERSION=2;
    /** sequence and displayTimeTicks use the original authoritative Snapshot.stateTick domain.
     * nozzle is the current rendered WORLD-space nozzle, not a camera-relative or wind-adjusted position.
     */
    public record FrameEndpoint(long sequence,double displayTimeTicks,Vec3 nozzle,boolean emitting) {}
    @FunctionalInterface public interface EndpointProvider {
        /** Called on the render thread before smoke geometry is built, including offscreen missiles.
         * Return null for unknown/missing IDs. Do not call sample/stop or mutate SLO from this callback.
         * Reuse the same pause-aware interpolated pose as the model and glow; never advance simulation here.
         */
        FrameEndpoint endpoint(ClientLevel level,Object id,float partialTick);
    }
    private static final java.util.List<EndpointProvider> ENDPOINTS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static void registerEndpointProvider(EndpointProvider provider){
        java.util.Objects.requireNonNull(provider);
        if(!ENDPOINTS.contains(provider))ENDPOINTS.add(provider);
    }
    public static void unregisterEndpointProvider(EndpointProvider provider){ENDPOINTS.remove(provider);}
    /** Read-only pull; results are not cached by SLO, so missing/end events cannot leave a stale nozzle. */
    public static FrameEndpoint frameEndpoint(ClientLevel level,Object id,float partialTick){
        for(var provider:ENDPOINTS){var endpoint=provider.endpoint(level,id,partialTick);if(endpoint!=null)return endpoint;}
        return null;
    }
    private ClientMissileTrailApi(){}
    /** True only for a supported renderer and the current world. Consumers suppress fallback smoke only then. */
    public static boolean available(ClientLevel level){return MissileSmokeMesh.available(level);}
    /** id must be stable and namespaced by the provider. sequence is a monotonically increasing authoritative tick.
     * Feed nozzle positions only. emitting=false breaks continuity, keeping already emitted smoke alive.
     * Returns true when SLO takes ownership (including budget rejection), to prevent fallback floods.
     */
    public static boolean sample(ClientLevel level,Object id,long sequence,Vec3 nozzle,boolean emitting){
        return MissileSmokeMesh.sample(level,id,sequence,nozzle,emitting);
    }
    /** End/interest loss stops emission; it never removes the existing trail immediately. */
    public static void stop(ClientLevel level,Object id){MissileSmokeMesh.stop(level,id);}
}
