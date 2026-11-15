package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The event bus behind {@link EffectApi}: ordered listeners, and the registries for presets and profile hosts.
 *
 * <p>Every event is wired: {@code FireEvent} is built at the four points where a CBC weapon builds its muzzle
 * plume, and {@code ImpactEvent}, {@code TerrainChangedEvent}, {@code BlockHitEvent} and
 * {@code TrackedHostEvent} each have a live dispatch point (see the {@code effects/} package). The three
 * registries that would let a third party add presets or profile hosts are still unwired and throw from
 * {@link EffectApi}; nothing calls them yet, so they cannot change any behaviour.
 *
 * <p>Two things are deliberate here:
 * <ul>
 *   <li><b>Priority is an int, lower runs first.</b> Our own defaults register last (a high number) so any
 *       other mod may run before them, and may cancel a section to replace it.</li>
 *   <li><b>A throwing listener is isolated.</b> One broken third party listener must not take the effect
 *       system down with it, and it must be visible in the log rather than silent. Note the consequence: an
 *       exception inside a listener no longer reaches the weapon that fired, so the log line is the signal.</li>
 * </ul>
 */
public final class EffectBus {
    private EffectBus() {}

    /** Our own defaults run at this priority: after everybody else, and cancellable. */
    public static final int DEFAULT_PRIORITY = 1000;

    private static final List<Entry<EffectApi.FireEvent>> FIRE = new ArrayList<>();
    private static final List<Entry<EffectApi.ImpactEvent>> IMPACT = new ArrayList<>();
    private static final List<Entry<EffectApi.TrackedHostEvent>> TRACKED = new ArrayList<>();
    private static final List<Entry<EffectApi.BlockHitEvent>> BLOCK_HIT = new ArrayList<>();
    private static final List<Entry<EffectApi.TerrainEvent>> TERRAIN = new ArrayList<>();

    private static final Map<ResourceLocation, EffectApi.LightPreset> LIGHT_PRESETS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, EffectApi.RingPreset> RING_PRESETS = new ConcurrentHashMap<>();
    private static final List<Predicate<BlockState>> PROFILE_HOSTS = new ArrayList<>();

    private record Entry<T>(int priority, EffectApi.Listener<T> listener) {}

    private static <T> void add(List<Entry<T>> list, int priority, EffectApi.Listener<T> listener) {
        if (listener == null) throw new IllegalArgumentException("null listener");
        synchronized (list) {
            list.add(new Entry<>(priority, listener));
            list.sort(Comparator.comparingInt(Entry::priority));
        }
    }

    public static void onFire(int priority, EffectApi.Listener<EffectApi.FireEvent> listener) {
        add(FIRE, priority, listener);
    }

    public static void onImpact(int priority, EffectApi.Listener<EffectApi.ImpactEvent> listener) {
        add(IMPACT, priority, listener);
    }

    public static void onTrackedHost(int priority, EffectApi.Listener<EffectApi.TrackedHostEvent> listener) {
        add(TRACKED, priority, listener);
    }

    public static void onBlockHit(int priority, EffectApi.Listener<EffectApi.BlockHitEvent> listener) {
        add(BLOCK_HIT, priority, listener);
    }

    public static void onTerrainChanged(int priority, EffectApi.Listener<EffectApi.TerrainEvent> listener) {
        add(TERRAIN, priority, listener);
    }

    public static void registerLightPreset(ResourceLocation id, EffectApi.LightPreset preset) {
        LIGHT_PRESETS.put(id, preset);
    }

    public static void registerRingPreset(ResourceLocation id, EffectApi.RingPreset preset) {
        RING_PRESETS.put(id, preset);
    }

    public static EffectApi.LightPreset lightPreset(ResourceLocation id) {
        return LIGHT_PRESETS.get(id);
    }

    public static EffectApi.RingPreset ringPreset(ResourceLocation id) {
        return RING_PRESETS.get(id);
    }

    public static void registerProfileHost(Predicate<BlockState> host) {
        if (host == null) throw new IllegalArgumentException("null host predicate");
        synchronized (PROFILE_HOSTS) {
            PROFILE_HOSTS.add(host);
        }
    }

    /** Used by the profile editor: is this a block that may carry its own settings? */
    public static boolean isProfileHost(BlockState state) {
        synchronized (PROFILE_HOSTS) {
            for (Predicate<BlockState> host : PROFILE_HOSTS) {
                if (host.test(state)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // Dispatch. Called once per event, never per particle per frame.
    // ------------------------------------------------------------------------------------------------

    public static void fire(EffectApi.FireEvent event) {
        dispatch(FIRE, event);
    }

    public static void impact(EffectApi.ImpactEvent event) {
        dispatch(IMPACT, event);
    }

    public static void trackedHost(EffectApi.TrackedHostEvent event) {
        dispatch(TRACKED, event);
    }

    public static void blockHit(EffectApi.BlockHitEvent event) {
        dispatch(BLOCK_HIT, event);
    }

    public static void terrainChanged(EffectApi.TerrainEvent event) {
        dispatch(TERRAIN, event);
    }

    private static <T> void dispatch(List<Entry<T>> list, T event) {
        List<Entry<T>> snapshot;
        synchronized (list) {
            snapshot = List.copyOf(list);
        }
        for (Entry<T> entry : snapshot) {
            // A whole event cancellation stops the chain here; a section cancellation is the listener's own
            // business, so it is left to ask Cancellation.blocks(section) about the section it owns.
            var cancellation = cancellationOf(event);
            if (cancellation != null && cancellation.blocks(null)) return;
            try {
                entry.listener().accept(event);
            } catch (Throwable failure) {
                com.mojang.logging.LogUtils.getLogger().error(
                        "sablelightoverhaul effect listener failed (priority {}), continuing with the rest",
                        entry.priority(), failure);
            }
        }
    }

    private static EffectApi.Cancellation cancellationOf(Object event) {
        if (event instanceof EffectApi.FireEvent e) return e.cancellation();
        if (event instanceof EffectApi.ImpactEvent e) return e.cancellation();
        if (event instanceof EffectApi.TrackedHostEvent e) return e.cancellation();
        if (event instanceof EffectApi.BlockHitEvent e) return e.cancellation();
        return null;
    }
}
