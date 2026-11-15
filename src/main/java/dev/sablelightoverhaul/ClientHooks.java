package dev.sablelightoverhaul;

import net.minecraft.world.entity.Entity;

/**
 * The few things the server-side half of the mod needs from the client half, without naming a client class.
 *
 * <p>Common code may not reference a class that only exists on a client: the reference is resolved the moment the
 * method that contains it runs, and on a dedicated server that is a crash rather than an empty answer. The three call
 * sites this replaces all sit in common classes and all reach a client-only implementation, so they now go through
 * here -- and the default implementations below are what a dedicated server gets, which is the honest answer for a
 * question that has no client to ask.
 *
 * <p>The real implementations are installed once, from {@code ClientBootstrap}, which is client-only. Until then, and
 * forever on a server, the defaults answer.
 */
public final class ClientHooks {
    /** Answers a held light's value by index, or zero when there is no client. */
    @FunctionalInterface
    public interface HeldLight {
        double number(int index);
    }

    /** Answers whether a projectile is one of the medium cannon's shells, or carries a tracer. */
    @FunctionalInterface
    public interface TrailQuery {
        boolean test(Entity entity);
    }

    private static volatile HeldLight heldLight = index -> 0.0;
    private static volatile TrailQuery shellType = entity -> false;
    private static volatile TrailQuery inGround = entity -> false;
    private static volatile TrailQuery tracer = entity -> false;

    private ClientHooks() {}

    /** Installed by the client at startup. */
    public static void install(HeldLight lights, TrailQuery shells, TrailQuery ground, TrailQuery tracers) {
        heldLight = lights;
        shellType = shells;
        inGround = ground;
        tracer = tracers;
    }

    public static double heldLight(int index) {
        return heldLight.number(index);
    }

    public static boolean shellType(Entity entity) {
        return shellType.test(entity);
    }

    public static boolean inGround(Entity entity) {
        return inGround.test(entity);
    }

    public static boolean tracer(Entity entity) {
        return tracer.test(entity);
    }
}