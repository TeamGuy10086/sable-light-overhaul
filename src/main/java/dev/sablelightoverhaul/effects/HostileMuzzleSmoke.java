package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.SloNetwork;
import dev.sablelightoverhaul.CannonDustEvents;
import dev.sablelightoverhaul.FlashStore;
import dev.sablelightoverhaul.HostileSmokePayload;
import dev.sablelightoverhaul.HostileSmokeWeapon;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.server.level.ServerLevel;

final class HostileMuzzleSmoke {
    private HostileMuzzleSmoke() {}

    static boolean requested(EffectApi.FireEvent event) {
        return event.key() != null && event.key().typeId().getNamespace().equals("sablehostile")
            && event.cancellation().muzzleSmokeTakeoverRequested();
    }

    static void send(EffectApi.FireEvent event) {
        if (!requested(event) || event.cancellation().blocks(EffectApi.Section.SPAWN_PARTICLES)
                || !(event.level() instanceof ServerLevel level)) return;
        var weapon = event.key().typeId();
        var muzzle = event.origin();
        var axis = event.direction().normalize();
        if (!FlashStore.valid(muzzle.x, muzzle.y, muzzle.z, axis.x, axis.y, axis.z, 0)) return;

        boolean automatic = event.kind() == EffectApi.WeaponKind.AUTOCANNON
            || event.kind() == EffectApi.WeaponKind.TWIN_AUTOCANNON;
        if (HostileSmokeWeapon.resolve(weapon.getPath(), automatic).thin()) {
            if (LightingCommon.HAZE_ENABLED.get()) {
                int source = event.key().stableId().hashCode() | Integer.MIN_VALUE;
                CannonDustEvents.emitHostileAutocannon(level, muzzle, axis, source, weapon,
                    player -> event.cancellation().acknowledgeMuzzleSmoke(player.getUUID()));
            }
            return;
        }
        var payload = new HostileSmokePayload(weapon, level.dimension().location(), muzzle, axis, null);
        double range = LightingCommon.CANNON_CULL.get();
        for (var player : level.players()) {
            if (player.distanceToSqr(muzzle) > range * range || !player.connection.hasChannel(HostileSmokePayload.TYPE)) continue;
            SloNetwork.sendToPlayer(player, payload);
            event.cancellation().acknowledgeMuzzleSmoke(player.getUUID());
        }
    }
}
