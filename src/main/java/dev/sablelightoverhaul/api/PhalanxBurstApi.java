package dev.sablelightoverhaul.api;

import dev.sablelightoverhaul.SloNetwork;
import dev.sablelightoverhaul.PhalanxBurstPayload;
import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PhalanxBurstApi {
    private PhalanxBurstApi() {}

    /** Called on the server thread after this tick's launches succeed. */
    public static void emit(ServerLevel level, Vec3 muzzle, Vec3 axis, long seed, int count,
                            int firstPhaseFifths, double speed, double range) {
        if (level == null || muzzle == null || axis == null || !LightingCommon.TRAIL_FEED_ENABLED.get()) return;
        var burst = new PhalanxBurstPayload(level.dimension().location(), muzzle, axis.normalize(), seed,
            count, firstPhaseFifths, speed, range);
        if (!burst.valid()) return;
        double reach = Math.min(range, speed * PhalanxBurstPayload.MAX_TICKS);
        double view = LightingCommon.TRAIL_FEED_RANGE.get();
        for (var player : level.players()) {
            if (!player.connection.hasChannel(PhalanxBurstPayload.TYPE)) continue;
            double along = Math.clamp(player.position().subtract(muzzle).dot(burst.axis()), 0, reach);
            if (player.distanceToSqr(muzzle.add(burst.axis().scale(along))) <= view * view)
                SloNetwork.sendToPlayer(player, burst);
        }
    }
}
