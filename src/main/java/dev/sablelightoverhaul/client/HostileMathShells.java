package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.HostileMathFlightPayload;
import dev.sablelightoverhaul.HostileShellPayload;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Server-confirmed poses and tracer segments. The carrier UUID never needs a client entity. */
public final class HostileMathShells {
    record Track(HostileShellPayload.Shell shell, long streak, long receivedAt) {
        Vec3 position(long now, float partial) {
            return now == receivedAt ? shell.previous().lerp(shell.current(), partial) : shell.current();
        }
    }

    private static final Map<UUID, Track> tracks = new LinkedHashMap<>();
    private static final Map<UUID, Long> claimed = new LinkedHashMap<>();
    // The UUID remains barred from mathematical playback after native handoff, even on late/replayed rows.
    private static final LinkedHashSet<UUID> released = new LinkedHashSet<>();
    record PendingEnd(Vec3 position, Track previous) {
        boolean accepts(HostileShellPayload.Shell shell) {
            return shell.terminal() && !HostileMathFlightPayload.isHandoff(shell)
                && shell.current().equals(position);
        }
    }
    // Null means the terminal feed row was already consumed. The API END can arrive before that row.
    private static final LinkedHashMap<UUID, PendingEnd> ended = new LinkedHashMap<>();
    private static ClientLevel world;
    private static long serverTick = Long.MIN_VALUE;
    // Separate from entity ids, Healthbar's predicted ids, and confirmed autocannon ids.
    private static long nextStreak = Long.MIN_VALUE / 2;
    private static long lastPacket;

    private HostileMathShells() {}

    public static boolean negotiated() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(HostileMathFlightPayload.TYPE);
    }

    static void tick(ClientLevel level) {
        if (world != level) {
            for (var track : tracks.values()) ProjectileStreaks.retire(track.streak());
            tracks.clear();
            claimed.clear();
            released.clear();
            ended.clear();
            world = level;
            serverTick = Long.MIN_VALUE;
        }
        if (world != null && ProjectileStreaks.visualTick() - lastPacket > 4 && !tracks.isEmpty()) {
            for (var track : tracks.values()) ProjectileStreaks.finish(track.streak());
            tracks.clear();
        }
    }

    public static void reset(ClientLevel level, List<UUID> carriers, boolean keepSequence) {
        if (level == null || world != level) return;
        for (var track : tracks.values()) ProjectileStreaks.retire(track.streak());
        if (keepSequence) {
            for (var carrier : carriers) if (!released.contains(carrier)) rememberEnd(carrier, null);
            for (var carrier : tracks.keySet()) rememberEnd(carrier, null);
        } else {
            ended.clear();
            released.clear();
            serverTick = Long.MIN_VALUE;
        }
        tracks.clear();
        claimed.clear();
    }

    static void receive(HostileMathFlightPayload payload) {
        var level = Minecraft.getInstance().level;
        var snapshot = payload.snapshot();
        if (level == null || !level.dimension().location().equals(snapshot.dimension())) return;
        tick(level);
        if (snapshot.tick() <= serverTick) return;
        serverTick = snapshot.tick();
        lastPacket = ProjectileStreaks.visualTick();
        claimed.values().removeIf(tick -> lastPacket - tick > 6);
        var next = new LinkedHashMap<UUID, Track>();
        ProjectileStreaks.maintain(level);
        for (var shell : snapshot.shells()) {
            if (ended(level, shell.uuid())) {
                var pending = ended.get(shell.uuid());
                if (pending != null && pending.accepts(shell)) {
                    updateTrack(level, shell, pending.previous());
                    ended.put(shell.uuid(), null);
                }
                continue;
            }
            if (released(level, shell.uuid())) continue;
            if (HostileMathFlightPayload.isHandoff(shell)) {
                handoff(level, shell.uuid());
                continue;
            }
            claimed.put(shell.uuid(), lastPacket);
            var track = updateTrack(level, shell, tracks.remove(shell.uuid()));
            if (shell.terminal()) {
                rememberEnd(shell.uuid(), null);
                HealthbarFlightBridge.claim(shell.uuid());
                claimed.remove(shell.uuid());
            } else next.put(shell.uuid(), track);
        }
        for (var stale : tracks.values()) ProjectileStreaks.finish(stale.streak());
        tracks.clear();
        tracks.putAll(next);
    }

    private static Track updateTrack(ClientLevel level, HostileShellPayload.Shell shell, Track previous) {
        boolean changed = previous == null || !sameSegment(previous.shell(), shell);
        var track = changed ? new Track(shell, previous == null ? nextStreak++ : previous.streak(), lastPacket) : previous;
        if (shell.tracer() && changed && !shell.previous().equals(shell.current())) {
            ProjectileStreaks.acceptConfirmedNaval(level, track.streak(), shell.previous(), shell.current(), shell.terminal());
        }
        if (shell.terminal()) ProjectileStreaks.finish(track.streak());
        return track;
    }

    public static void handoff(ClientLevel level, UUID carrier) {
        if (level == null) return;
        tick(level);
        if (ended.containsKey(carrier) || !released.add(carrier)) return;
        if (released.size() > 8192) released.removeFirst();
        claimed.remove(carrier);
        var track = tracks.remove(carrier);
        if (track != null) ProjectileStreaks.retire(track.streak());
    }

    public static boolean released(ClientLevel level, UUID carrier) {
        return level != null && world == level && released.contains(carrier);
    }

    public static void end(ClientLevel level, UUID carrier, Vec3 position) {
        if (level == null) return;
        tick(level);
        if (ended.containsKey(carrier) || released.contains(carrier)) return;
        var track = tracks.remove(carrier);
        rememberEnd(carrier, new PendingEnd(position, track));
        HealthbarFlightBridge.claim(carrier);
        claimed.remove(carrier);
        if (track != null) ProjectileStreaks.finish(track.streak());
    }

    private static void rememberEnd(UUID carrier, PendingEnd pending) {
        ended.put(carrier, pending);
        if (ended.size() > 8192) ended.pollFirstEntry();
    }

    public static boolean ended(ClientLevel level, UUID carrier) {
        return level != null && world == level && ended.containsKey(carrier);
    }

    static void nativeSeen(ClientLevel level, UUID carrier, HostileShellPayload.Model model) {
        if (!model.proximityHe() && (claims(level, carrier) || HealthbarFlightBridge.owns(carrier))) handoff(level, carrier);
    }

    static boolean sameSegment(HostileShellPayload.Shell a, HostileShellPayload.Shell b) {
        return a.previous().equals(b.previous()) && a.current().equals(b.current()) && a.tracer() == b.tracer();
    }

    static boolean claims(ClientLevel level, UUID carrier) {
        Long seen = claimed.get(carrier);
        return world == level && seen != null && ProjectileStreaks.visualTick() - seen <= 6;
    }

    static Iterable<Track> tracks(ClientLevel level) {
        return world == level && ProjectileStreaks.visualTick() - lastPacket <= 4 ? tracks.values() : List.of();
    }

    public static Vec3 position(ClientLevel level, UUID carrier, float partial) {
        if (world != level || level == null || ProjectileStreaks.visualTick() - lastPacket > 4) return null;
        var track = tracks.get(carrier);
        return track == null ? null : track.position(ProjectileStreaks.visualTick(), partial);
    }
}
