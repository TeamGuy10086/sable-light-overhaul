package dev.sablelightoverhaul.lighting;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Which server-side positions are one of this mod's lamps.
 *
 * <p>Exists so the server's light engine can be told that a lamp gives up its own emission, which is what makes
 * the light data sent to clients dark. The client-side take-over cannot do that on its own: a client does not
 * recompute light the network handed it, so the pool of vanilla light came back after every chunk load, every
 * block update, and on every freshly placed lamp.
 *
 * <p>Before the first server tick, the light engine uses normal block emission. Each level publishes its first
 * snapshot on that tick and rechecks the registered lamps in loaded chunks. Subsequent registry edits are
 * coalesced, and only added or removed lamp positions are rechecked.
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

    private static final ConcurrentHashMap<ServerLevel, LevelState> LEVELS = new ConcurrentHashMap<>();

    private static final class LevelState {
        // Only the server thread accesses the registry and rebuild bookkeeping.
        private final RegisteredSources sources;
        private long publishedRevision;
        private long builtAt;
        // A null value means initial publication is pending. Published sets are never mutated.
        private volatile LongSet positions;

        private LevelState(ServerLevel level) {
            sources = RegisteredSources.get(level);
        }
    }

    private ServerTakeOver() {}

    /** Called by the light engine, including on worker threads during startup. */
    public static boolean isLamp(ServerLevel level, long pos) {
        LevelState state = LEVELS.get(level);
        LongSet positions = state == null ? null : state.positions;
        return positions != null && positions.contains(pos);
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelState state = LEVELS.get(level);
        if (state == null) {
            state = new LevelState(level);
            LEVELS.put(level, state);
        }
        LongSet previous = state.positions;
        long revision = state.sources.revision();
        if (previous != null && state.publishedRevision == revision) return;
        long now = System.nanoTime();
        if (previous != null && now - state.builtAt < DEBOUNCE) return;

        LongSet positions = new LongOpenHashSet();
        for (RegisteredSources.Entry entry : state.sources.entries.values()) {
            BlockPos resolved = entry.anchor().resolve(level);
            if (resolved != null) positions.add(resolved.asLong());
        }

        // Publish before scheduling rechecks, so even an immediately running light task sees the new set.
        state.positions = LongSets.unmodifiable(positions);
        state.publishedRevision = revision;
        state.builtAt = now;
        relightChanges(level, previous, positions);
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
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
            // A section without a holder has nothing to send.
        }
    }

    private static void relightChanges(ServerLevel level, LongSet previous, LongSet positions) {
        for (long pos : positions) {
            if (previous == null || !previous.contains(pos)) relight(level, pos);
        }
        if (previous != null) {
            for (long pos : previous) {
                if (!positions.contains(pos)) relight(level, pos);
            }
        }
        if (previous == null || !previous.equals(positions)) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: server take-over -- {} lamp block(s){} in {}", positions.size(),
                    previous == null ? ", first pass" : ", changed", level.dimension().location());
        }
    }
}
