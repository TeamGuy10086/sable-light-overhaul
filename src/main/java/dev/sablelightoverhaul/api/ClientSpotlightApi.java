package dev.sablelightoverhaul.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client-only lights supplied by another mod during SLO's light collection. */
public final class ClientSpotlightApi {
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<PointProvider> POINT_PROVIDERS = new CopyOnWriteArrayList<>();

    private ClientSpotlightApi() {}

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    public static void collect(ClientLevel level, Consumer<Spotlight> output) {
        for (var provider : PROVIDERS) provider.collect(level, output);
    }

    public static void registerPoints(PointProvider provider) {
        POINT_PROVIDERS.add(provider);
    }

    public static void collectPoints(ClientLevel level, Consumer<PointLight> output) {
        for (var provider : POINT_PROVIDERS) provider.collect(level, output);
    }

    @FunctionalInterface
    public interface PointProvider {
        void collect(ClientLevel level, Consumer<PointLight> output);
    }

    /** Omnidirectional light with a radius in blocks and sRGB as 0xRRGGBB. */
    public record PointLight(ResourceLocation id, Vec3 origin, int radius, int intensity, int rgb) {}

    @FunctionalInterface
    public interface Provider {
        void collect(ClientLevel level, Consumer<Spotlight> output);
    }

    /** World-space origin and direction, full cone in degrees, and sRGB as 0xRRGGBB. */
    public record Spotlight(ResourceLocation id, Vec3 origin, Vec3 axis, int range, double cone,
                            int intensity, int rgb, double aperture, int falloff, int volumeMode) {}
}
