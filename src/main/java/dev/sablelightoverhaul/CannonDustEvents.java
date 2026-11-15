package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CannonDustEvents {
    private static final Map<ServerLevel, Map<String, Long>> LAST = new WeakHashMap<>();
    private CannonDustEvents() {}
    public static void emitAutocannon(ServerLevel level, Vec3 muzzle, Vec3 axis, int emitterId) {
        emit(level, muzzle, axis, false, emitterId, true, null, null);
    }
    public static void emitHostileAutocannon(ServerLevel level, Vec3 muzzle, Vec3 axis, int emitterId,
                                            ResourceLocation weapon, Consumer<ServerPlayer> handled) {
        emit(level, muzzle, axis, false, emitterId, true, weapon, handled);
    }
    public static void emit(ServerLevel level, Vec3 muzzle, Vec3 axis, boolean tank, int emitterId) {
        emit(level, muzzle, axis, tank, emitterId, false, null, null);
    }
    private static void emit(ServerLevel level, Vec3 muzzle, Vec3 axis, boolean tank, int emitterId, boolean autocannon,
                             ResourceLocation weapon, Consumer<ServerPlayer> handled) {
        var viewers = level.players().stream().filter(p -> p.distanceToSqr(muzzle) <= Math.pow(LightingCommon.CANNON_CULL.get(), 2)).toList();
        if (viewers.isEmpty()) return;
        long now = level.getGameTime();
        var times = LAST.computeIfAbsent(level, unused -> new java.util.HashMap<>());
        times.entrySet().removeIf(e -> now < e.getValue() || now - e.getValue() > 100);
        int interval = LightingCommon.VEHICLE_HAZE_INTERVAL.get();
        String key = emitterId != 0 ? "entity:" + emitterId : "cell:" + BlockPos.containing(muzzle).asLong();
        Long last = times.get(key);
        // 0 means every shot gets its smoke: the haze is the thing that makes a burst read as fire, and
        // a rate limit here showed up in game as smoke appearing at random. The interval stays as the
        // performance knob for whoever needs one.
        boolean spawn = interval <= 0 || last == null || now - last >= interval;
        if (interval > 0 && spawn && (times.size() < 1024 || last != null)) times.put(key, now);
        List<CannonDustPayload.Surface> surfaces = tank && net.neoforged.fml.ModList.get().isLoaded("sable")
            ? dev.sablelightoverhaul.lighting.ExperimentalHullDust.collect(level, muzzle, axis) : List.of();
        var burst = new GroundDustParticleData((float)((LightingCommon.CANNON_HULL_SIZE_MIN.get() + Math.max(LightingCommon.CANNON_HULL_SIZE_MIN.get(), LightingCommon.CANNON_HULL_SIZE_MAX.get())) * .5),
            (tank&&LightingCommon.HULL_FLOW_ENABLED.get()?LightingCommon.HULL_FLOW_COLOR.get():LightingCommon.CANNON_HULL_COLOR.get()), LightingCommon.CANNON_HULL_LIFETIME.get(), 1.2F, false,
            LightingCommon.CANNON_HULL_DAMPING.get().floatValue(), LightingCommon.CANNON_HULL_GRAVITY.get().floatValue(), LightingCommon.CANNON_HULL_ALPHA.get().floatValue(),LightingCommon.CANNON_HULL_FADE.get().floatValue(),false);
        var haze = new GroundDustParticleData(LightingCommon.VEHICLE_HAZE_SIZE.get().floatValue(), LightingCommon.CANNON_HULL_COLOR.get(),
            LightingCommon.VEHICLE_HAZE_LIFE.get(), 1.5F, false, .97F, 0F, LightingCommon.VEHICLE_HAZE_ALPHA.get().floatValue(), true);
        var event = new CannonDustPayload(level.dimension().location(), muzzle, axis.normalize(), level.random.nextLong(), surfaces,
            surfaces.isEmpty() ? 0 : Math.min(LightingCommon.CANNON_HULL_COUNT.get(), LightingCommon.CANNON_BUDGET.get()),
            spawn ? LightingCommon.VEHICLE_HAZE_COUNT.get() : 0, LightingCommon.CANNON_HULL_SPEED.get().floatValue(), burst, haze,
            // The haze motion travels with the shot: how it moves is behaviour, and behaviour is the
            // server's. The client keeps budgets and switches only.
            LightingCommon.HAZE_SPREAD.get().floatValue(), LightingCommon.HAZE_DRIFT.get().floatValue(),
            LightingCommon.HAZE_SINK.get().floatValue(), LightingCommon.HAZE_CLEARANCE.get().floatValue(),
            LightingCommon.HAZE_PUSH.get().floatValue(), LightingCommon.HAZE_PUSH_RADIUS.get().floatValue(),
            LightingCommon.HAZE_LIGHT_GAIN.get().floatValue(), emitterId,
            tank&&LightingCommon.HULL_FLOW_ENABLED.get()?new HullDustModel.Settings(LightingCommon.HULL_FLOW_LIFT.get(),LightingCommon.HULL_FLOW_TURBULENCE.get(),LightingCommon.HULL_FLOW_RELEASE.get(),LightingCommon.HULL_FLOW_MOTION.get(),LightingCommon.HULL_ATTACH_SECONDS.get(),LightingCommon.HULL_SURFACE_SPEED.get(),LightingCommon.HULL_CLEARANCE.get(),LightingCommon.HULL_INHERIT_MOTION.get(),LightingCommon.HULL_CLUSTER_DENSITY.get(),LightingCommon.HULL_CLUSTER_SPREAD.get(),LightingCommon.HULL_CLUSTER_STAGGER.get(),LightingCommon.HULL_DETACH_FADE.get(),LightingCommon.HULL_DETACH_EXPANSION.get()):null);
        var softHaze = autocannon ? new AutocannonHazePayload(event) : null;
        var typedHaze = weapon == null ? null : HostileSmokePayload.thin(weapon, event);
        for (var player : viewers) {
            if (typedHaze != null && player.connection.hasChannel(HostileSmokePayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, typedHaze);
                handled.accept(player);
                continue;
            }
            if (typedHaze != null && !LightingCommon.AUTO_HAZE_ENABLED.get()) continue;
            PacketDistributor.sendToPlayer(player,
                softHaze != null && player.connection.hasChannel(AutocannonHazePayload.TYPE) ? softHaze : event);
        }
    }
}
