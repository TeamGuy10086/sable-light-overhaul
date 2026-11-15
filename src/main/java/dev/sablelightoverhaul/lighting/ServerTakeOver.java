package dev.sablelightoverhaul.lighting;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Which server-side positions are one of this mod's lamps.
 *
 * <p>Exists so the server's light engine can be told that a lamp gives up its own emission, which is what makes
 * the light data sent to clients dark. The client-side take-over cannot do that on its own: a client does not
 * recompute light the network handed it, so the pool of vanilla light came back after every chunk load, every
 * block update, and on every freshly placed lamp.
 *
 * <p>The snapshot is rebuilt at most twice a second, which is the only knob here. It is rebuilt from the
 * registration the server already keeps, and any lamp that appears in it has the block it occupies re-lit once,
 * so the change reaches the light data instead of waiting for the next unrelated update.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid=dev.sablelightoverhaul.SableLightOverhaul.ID)
public final class ServerTakeOver {
    /**
     * How long to wait after the registry changed before rebuilding, in nanoseconds.
     *
     * <p>Editing a lamp writes one value per drag and each write marks the registry dirty. Rebuilding on every one of
     * them would walk the registry and re-light sections for every pixel of a slider; this keeps the work to at most one
     * pass per half second while it is being dragged, and nothing at all while nothing changes.
     */
    private static final long DEBOUNCE = 500_000_000L;

    /** Set when the registry changed, so the next tick rebuilds. Starts set: the first pass must always run. */
    private static volatile boolean DIRTY = true;

    private static final WeakHashMap<ServerLevel, Snapshot> CACHES = new WeakHashMap<>();

    private record Snapshot(LongSet positions, long builtAt) {}

    private ServerTakeOver() {}

    /**
     * Whether a position is a registered lamp's own block.
     *
     * <p>Read only, on purpose. Called from the light engine's emission query, which is hot, so the answer comes
     * from a set of longs rather than from the registry -- and, more importantly, nothing here may call back into
     * the engine: a {@code checkBlock} issued from inside {@code getEmission} is a mutation during the engine's own
     * query, and an engine mid-query ignores it. That is why the light level came back unchanged on every single
     * relight in the log, no matter how many passes ran.
     */
    public static boolean isLamp(ServerLevel level, long pos) {
        Snapshot snapshot = CACHES.get(level);
        // Built on first use, not only from the tick. A chunk's light sources are collected the moment its chunk
        // arrives, which is usually before the first tick pass -- and an empty snapshot then means every lamp is
        // collected as a light source, gets its level baked into the light data, and never loses it afterwards.
        // This build reads positions only and asks the engine nothing, so it is safe here.
        if (snapshot == null) snapshot = buildPositions(level);
        return snapshot.positions().contains(pos);
    }

    /** Positions only, no re-lighting: safe to call from anywhere, including a light source scan. */
    private static Snapshot buildPositions(ServerLevel level) {
        LongSet positions = new LongOpenHashSet();
        for (RegisteredSources.Entry entry : RegisteredSources.get(level).entries.values()) {
            BlockPos resolved = entry.anchor().resolve(level);
            if (resolved == null) continue;
            positions.add(resolved.asLong());
            // Remembered for the emission hook, which has no position to work with.

        }
        // builtAt 0 makes the next tick pass treat this as a first pass: it re-lights everything, which is what
        // makes the light data agree with these positions.
        Snapshot snapshot = new Snapshot(positions, 0L);
        CACHES.put(level, snapshot);
        return snapshot;
    }

    /**
     * Rebuilds the snapshot after the registry changed, and re-lights only what moved. Called from the server tick,
     * which is the only place allowed to ask the engine to look again.
     */
    public static void tick(ServerLevel level) {
        if (!DIRTY) return;
        long now = System.nanoTime();
        Snapshot snapshot = CACHES.get(level);
        if (snapshot != null && snapshot.builtAt() != 0L && now - snapshot.builtAt() < DEBOUNCE) return;
        DIRTY = false;
        rebuild(level, now, snapshot);
    }

    /** Called whenever the registry changed: the cached positions are stale and the next tick rebuilds them. */
    public static void invalidate() {
        DIRTY = true;
    }

    /** Drives the rebuild from the server tick, never from inside the light engine. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    /**
     * Asks the engine to re-evaluate one block, which is what applies the zero to the light data it sends.
     *
     * <p>Reads the light level either side of the call and reports it when it changes. That is the measurement the
     * whole server-side attempt stands on: whether a lamp that reports emission zero still has a stored level
     * afterwards, and whether asking the engine to look again is what clears it.
     */
    private static void relight(ServerLevel level, long pos) {
        BlockPos block = BlockPos.of(pos);
        if (!level.hasChunkAt(block)) return;
        level.getLightEngine().checkBlock(block);
        // Tell the server that this section's light changed, so it is marked and its data is sent to clients.
        // Without this the zero only exists in the server's own copy: a client keeps the level it was given, a
        // section mesh interpolates that, and no rebuild -- placing a lamp, toggling one, reloading a chunk -- can
        // ever produce a different picture. This is the missing link between "the server cleared it" and "the
        // screen shows it cleared".
        try {
            level.getChunkSource().onLightUpdate(LightLayer.BLOCK, net.minecraft.core.SectionPos.of(block));
        } catch (RuntimeException notSent) {
            // A section without a holder has nothing to send; the next pass tries again.
        }
    }

    private static Snapshot rebuild(ServerLevel level, long now, Snapshot previous) {
        LongSet positions = new LongOpenHashSet();
        for (RegisteredSources.Entry entry : RegisteredSources.get(level).entries.values()) {
            BlockPos resolved = entry.anchor().resolve(level);
            if (resolved == null) continue;
            positions.add(resolved.asLong());
            // Remembered for the emission hook, which has no position to work with.

        }
        // Only what changed is re-lit.
        //
        // A full sweep every half second cost one light query plus one section notification per lamp, forever, and --
        // measured -- never changed what a client draws. The zero that matters is the query hook; this only repairs
        // the positions whose lamp state actually changed, plus everything on the first pass so a world that is
        // already built gets cleared.
        if (previous == null) {
            for (long pos : positions) relight(level, pos);
        } else {
            for (long pos : positions) {
                if (!previous.positions().contains(pos)) relight(level, pos);
            }
            for (long pos : previous.positions()) {
                if (!positions.contains(pos)) relight(level, pos);
            }
        }
        if (previous == null || !previous.positions().equals(positions)) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: server take-over -- {} lamp block(s){}", positions.size(),
                    previous == null ? ", first pass" : ", changed");
        }
        Snapshot snapshot = new Snapshot(positions, now);
        CACHES.put(level, snapshot);
        return snapshot;
    }
}
