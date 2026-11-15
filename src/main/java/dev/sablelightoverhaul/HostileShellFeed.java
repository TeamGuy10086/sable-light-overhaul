package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;

/** Reuses CBC's measured projectile loop. It never loads chunks or advances ballistics. */
public final class HostileShellFeed {
    private static final Map<ServerLevel, Set<UUID>> RECIPIENTS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<UUID>> MATH_RECIPIENTS = new WeakHashMap<>();
    private static final Map<Entity, Sample> PREVIOUS = new WeakHashMap<>();
    private static Map<EntityType<?>, HostileShellPayload.Model> types;
    private static MethodHandle ended, detonationPosition;
    private record Sample(int tick, Vec3 position) {}

    private HostileShellFeed() {}

    static HostileShellPayload.Model model(Entity entity) {
        if (types == null) {
            types = new IdentityHashMap<>();
            for (var model : HostileShellPayload.Model.values()) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(model.id()).ifPresent(type -> types.put(type, model));
            }
            if (!types.isEmpty()) {
                try {
                    var api = Class.forName("dev.sablehostile.NavalShells");
                    ended = MethodHandles.publicLookup().findStatic(api,
                        "hasEnded", MethodType.methodType(boolean.class, Entity.class));
                    detonationPosition = MethodHandles.publicLookup().findStatic(api,
                        "detonationPosition", MethodType.methodType(Vec3.class, Entity.class));
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Older Sable Hostile builds only expose CBC's landed state.
                }
            }
        }
        return types.get(entity.getType());
    }

    private static boolean ended(Entity entity) {
        if (ended == null) return false;
        try {
            return (boolean) ended.invokeExact(entity);
        } catch (Throwable error) {
            com.mojang.logging.LogUtils.getLogger().warn("Sable Hostile shell end-state hook failed", error);
            ended = null;
            return false;
        }
    }

    static void join(Entity entity) {
        if (model(entity) != null) PREVIOUS.putIfAbsent(entity, new Sample(entity.tickCount, entity.position()));
    }

    static void measure(ServerLevel level, Entity entity, HostileShellPayload.Model model,
                        List<HostileShellPayload.Shell> shells, List<dev.sablelightoverhaul.api.EffectApi.Host> hosts) {
        if (HealthbarServerBridge.owns(level, entity.getUUID())) {
            PREVIOUS.remove(entity);
            return;
        }
        if (!(entity instanceof AbstractBigCannonProjectile shell)) return;
        boolean terminal = shell.isRemoved() || shell.isInGround() || ended(shell);
        var next = new Sample(entity.tickCount, entity.position());
        var old = terminal ? PREVIOUS.remove(entity) : PREVIOUS.put(entity, next);
        if (old == null || next.tick != old.tick + 1) return;
        Vec3 to;
        if (terminal) {
            // CBC may advance its position once more after it schedules removal. Only an actual hit is safe to use.
            var hit = impact(entity);
            if (hit == null) return;
            to = CBCCompatTransformers.transformVec3(level, hit);
        } else {
            to = CBCCompatTransformers.transformVec3(level, next.position, old.position);
        }
        Vec3 from = CBCCompatTransformers.transformVec3(level, old.position, old.position);
        if (!HostileShellPayload.validStep(from, to)) return;
        shells.add(new HostileShellPayload.Shell(entity.getId(), entity.getUUID(), model, from, to, terminal, shell.hasTracer()));
        if (shell.hasTracer() && from.distanceToSqr(to) > 1e-8) {
            hosts.add(new dev.sablelightoverhaul.api.EffectApi.Host(entity.getId(), from, to, to.subtract(from), 0, 0, true));
        }
    }

    private static Vec3 impact(Entity entity) {
        if (detonationPosition == null) return null;
        try {
            return (Vec3) detonationPosition.invokeExact(entity);
        } catch (Throwable error) {
            com.mojang.logging.LogUtils.getLogger().warn("Sable Hostile shell detonation hook failed", error);
            detonationPosition = null;
            return null;
        }
    }

    static void deliver(ServerLevel level, List<HostileShellPayload.Shell> shells) {
        deliver(level, shells, RECIPIENTS, HostileShellPayload.TYPE, payload -> payload);
    }

    public static void deliverMath(ServerLevel level, List<HostileShellPayload.Shell> shells) {
        deliver(level, shells, MATH_RECIPIENTS, HostileMathFlightPayload.TYPE, HostileMathFlightPayload::new);
    }

    public static boolean hasMathRecipients(ServerLevel level) {
        return MATH_RECIPIENTS.containsKey(level);
    }

    private static void deliver(ServerLevel level, List<HostileShellPayload.Shell> shells,
                                Map<ServerLevel, Set<UUID>> history,
                                net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> channel,
                                java.util.function.Function<HostileShellPayload, net.minecraft.network.protocol.common.custom.CustomPacketPayload> wrap) {
        var previous = history.getOrDefault(level, Set.of());
        if (shells.isEmpty() && previous.isEmpty()) return;
        var recipients = new HashSet<UUID>();
        double range = LightingCommon.TRAIL_FEED_RANGE.get();
        for (var player : level.players()) {
            if (!player.connection.hasChannel(channel)) continue;
            var nearby = new ArrayList<HostileShellPayload.Shell>();
            for (var shell : shells) {
                if (player.distanceToSqr(shell.current()) <= range * range) nearby.add(shell);
            }
            if (nearby.size() > HostileShellPayload.LIMIT) {
                nearby.sort(Comparator.comparingDouble((HostileShellPayload.Shell s) -> player.distanceToSqr(s.current()))
                    .thenComparing(HostileShellPayload.Shell::uuid));
                nearby.subList(HostileShellPayload.LIMIT, nearby.size()).clear();
            }
            if (!nearby.isEmpty()) recipients.add(player.getUUID());
            if (!nearby.isEmpty() || previous.contains(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, wrap.apply(new HostileShellPayload(
                    level.dimension().location(), level.getGameTime(), nearby)));
            }
        }
        if (recipients.isEmpty()) history.remove(level);
        else history.put(level, recipients);
    }
}
