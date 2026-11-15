package dev.sablelightoverhaul.voxy;

import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Makes a crater visible in the far-distance LOD of clients that run Voxy.
 *
 * <p>Voxy builds its LODs from the chunks the client itself loads, and once a section is ingested it is
 * effectively frozen: the only thing that ever re-ingests is a section-border removal. So a shell that
 * blows a hole in the ground leaves the far LOD showing the terrain as it was, on every client, until the
 * column is ingested again. LSS ("Voxy Server Side") is the thing that can re-serve a column, and
 * {@link LssBridge} tells it which ones changed.
 *
 * <p>Columns are queued until after the blast removes the reported blocks. LSS handles
 * serving the changed terrain through its normal storage path.
 *
 * <p><b>Scheduling.</b> Every column waits {@code VOXY_NUDGE_DELAY} ticks before it is offered to LSS. A
 * second blast in the same column inside that window is neither dropped nor sent twice: its deadline is
 * pushed back by the same delay and the two hits are added, so a place that is being shelled is re-sent
 * once when the shelling pauses. The wait is bounded at {@link #MAX_DEFERS} postponements, after which the
 * column goes out regardless — otherwise a sustained barrage in one spot would postpone its own LOD update
 * forever.
 *
 * <p>Every due column is sent, ordered by how much of it was destroyed, so if the cap ever binds it is the
 * shallowly damaged columns that wait rather than the crater's edge.
 */
public final class VoxyLodNudger {
    /** How many times a column may be postponed by a repeat hit before it is sent anyway. */
    private static final int MAX_DEFERS = 6;

    /** A column waiting to be offered to LSS. */
    private static final class Pending {
        long dueAt;
        int damage;
        int defers;

        Pending(long dueAt, int damage, int defers) {
            this.dueAt = dueAt;
            this.damage = damage;
            this.defers = defers;
        }
    }

    /** Columns queued for the next scan, per level: packed chunk position -> its deadline and damage. */
    private static final Map<ServerLevel, Map<Long, Pending>> QUEUE = new WeakHashMap<>();
    private VoxyLodNudger() {}

    /**
     * Records the columns a blast is about to change, with how many blocks it will take out of each.
     *
     * <p>Called before the blocks are gone. {@link #drain} notifies LSS after the queued delay.
     * Counting per column costs one map increment per block — the same
     * pass the old distance filter needed — and it is what makes "send the worst first" possible.
     */
    public static void queue(ServerLevel level, Vec3 centre, Iterable<BlockPos> positions) {
        if (!LightingCommon.VOXY_ENABLED.get() || !LssBridge.ready()) return;
        Map<Long, Integer> hits = new HashMap<>();
        for (BlockPos pos : positions) {
            hits.merge(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), 1, Integer::sum);
        }
        if (hits.isEmpty()) return;
        int cap = LightingCommon.VOXY_MAX_COLUMNS.get();
        if (hits.size() > cap) {
            // Only the worst columns are kept when an event exceeds the cap: an arbitrary subset would refresh
            // one edge of a crater and leave the middle of it stale in the LOD.
            var picked = new ArrayList<>(hits.entrySet());
            picked.sort(Map.Entry.<Long, Integer>comparingByValue().reversed());
            hits.clear();
            for (var entry : picked.subList(0, cap)) hits.put(entry.getKey(), entry.getValue());
        }
        long now = level.getGameTime();
        long delay = LightingCommon.VOXY_NUDGE_DELAY.get();
        Map<Long, Pending> queue = QUEUE.computeIfAbsent(level, key -> new HashMap<>());
        int added = 0;
        for (var entry : hits.entrySet()) {
            Pending existing = queue.get(entry.getKey());
            if (existing == null) {
                queue.put(entry.getKey(), new Pending(now + delay, entry.getValue(), 0));
                added++;
            } else {
                // Hit again while waiting: push the deadline back and add the damage. It is not dropped --
                // that was the old debounce, and it lost a second crater in the same street.
                existing.dueAt = Math.max(existing.dueAt, now) + delay;
                existing.damage += entry.getValue();
                existing.defers++;
            }
        }
        if (added > 0 && LightingCommon.on(LightingCommon.GEN_DEBUG)) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul voxy: queued {} column(s) near {}, {}t wait, {} block(s) touched",
                    added, centre, delay, hits.values().stream().mapToInt(Integer::intValue).sum());
        }
    }

    /** Notifies LSS about columns that are due. Called from the existing five-tick scan. */
    public static int drain(ServerLevel level) {
        if (!LightingCommon.VOXY_ENABLED.get() || !LssBridge.ready()) {
            QUEUE.remove(level);
            return 0;
        }
        Map<Long, Pending> queue = QUEUE.get(level);
        if (queue == null || queue.isEmpty()) return 0;
        long now = level.getGameTime();
        var due = new ArrayList<Map.Entry<Long, Pending>>();
        var iterator = queue.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Pending pending = entry.getValue();
            // Sent when its wait is over, or when it has been postponed too often to be worth waiting for.
            if (pending.dueAt > now && pending.defers < MAX_DEFERS) continue;
            due.add(entry);
            iterator.remove();
        }
        if (due.isEmpty()) return 0;
        // Heaviest first: if the cap ever binds, the crater's middle goes out and the fringe waits.
        due.sort((a, b) -> Integer.compare(b.getValue().damage, a.getValue().damage));
        int cap = LightingCommon.VOXY_MAX_COLUMNS.get();
        if (due.size() > cap) due.subList(cap, due.size()).clear();
        double immediate = LightingCommon.VOXY_IMMEDIATE_RANGE.get();
        double immediateSqr = immediate * immediate;
        var changed = new ArrayList<Long>();
        int columns = 0, heaviest = 0;
        for (var entry : due) {
            long packed = entry.getKey();
            int cx = ChunkPos.getX(packed), cz = ChunkPos.getZ(packed);
            if (!LssBridge.markDirty(level, cx, cz)) continue;
            columns++;
            heaviest = Math.max(heaviest, entry.getValue().damage);
            changed.add(packed);
        }
        // Everyone gets LSS's own broadcast for these columns; the players who could actually see the blast
        // are told now instead of up to ten seconds from now. Nobody is excluded from the update.
        int told = 0;
        if (columns > 0 && immediate > 0) {
            long[] packed = new long[changed.size()];
            for (int i = 0; i < packed.length; i++) packed[i] = changed.get(i);
            var nearby = watchers(level, changed, immediateSqr);
            if (!nearby.isEmpty()) {
                // The re-ask has to be answerable before it happens: LSS serves from its timestamp cache while
                // it believes a column is unchanged, so the cache entry is dropped now rather than on its timer.
                LssBridge.invalidate(level, packed);
                told = LssBridge.sendDirtyNow(level, packed, nearby);
            }
        }
        if (columns > 0 && LightingCommon.on(LightingCommon.GEN_DEBUG)) {
            // "accepted" is what LSS actually took; without it a silent LSS is indistinguishable from success.
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul voxy: sent {} column(s), heaviest {} block(s), "
                            + "told {} player(s); {} | {} | {}",
                    columns, heaviest, told,
                    LssBridge.statusLine(), LssBridge.marks(), LssBridge.pushes());
        }
        return columns;
    }

    /** The players close enough to the changed columns to have seen the blast that made them. */
    private static List<net.minecraft.server.level.ServerPlayer> watchers(
            ServerLevel level, List<Long> columns, double rangeSqr) {
        var out = new ArrayList<net.minecraft.server.level.ServerPlayer>();
        for (var player : level.players()) {
            for (long packed : columns) {
                double x = (ChunkPos.getX(packed) << 4) + 8.0, z = (ChunkPos.getZ(packed) << 4) + 8.0;
                // Measured against the column's axis at the player's own height, so this is the horizontal
                // distance to the column: the crater is somewhere down it, not at y=0.
                if (player.distanceToSqr(x, player.getY(), z) <= rangeSqr) {
                    out.add(player);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Queues a square of columns and drains them immediately, for the debug command.
     *
     * @returns how many columns were handed over.
     */
    public static int nudgeAround(ServerLevel level, BlockPos center, int radius) {
        var positions = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            positions.add(pos.immutable());
        }
        queue(level, center.getCenter(), positions);
        // The nudge command exists to see the effect now, so it skips the wait instead of asking for one.
        Map<Long, Pending> map = QUEUE.get(level);
        if (map != null) for (Pending pending : map.values()) pending.dueAt = 0L;
        return drain(level);
    }

    /** How many columns are waiting to be drained, for diagnostics. */
    public static int pending(ServerLevel level) {
        Map<Long, Pending> queue = QUEUE.get(level);
        return queue == null ? 0 : queue.size();
    }
}
