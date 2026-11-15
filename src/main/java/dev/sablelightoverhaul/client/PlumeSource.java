package dev.sablelightoverhaul.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Which cannon a CBC plume particle came from.
 *
 * <p>Both the big cannon and CBC Modern Warfare's medium (tank) cannon spawn the same particle,
 * {@code BigCannonPlumeParticle}, and its data carries only size, power and lifetime -- there is no weapon
 * kind on the wire, and the values overlap. What differs on this side is which contraption the plume was
 * born beside, so the nearest cannon contraption decides. Asked once per particle, which is once per shot.
 *
 * <p>The alternative -- reading the kind from our own flash payload -- was rejected: the flash can be
 * disabled, culled for a viewer who still sees the plume, or arrive a tick apart from the particle, and
 * then the plume would silently use the wrong set of keys.
 */
public final class PlumeSource {
    /** Muzzles are at the barrel end, so this has to cover a long barrel from the contraption's centre. */
    private static final double RANGE = 24.0;

    private PlumeSource() {}

    /** True when the nearest cannon contraption at this point is the medium (tank) cannon. */
    public static boolean tankCannon(ClientLevel level, double x, double y, double z) {
        var box = new AABB(x - RANGE, y - RANGE, z - RANGE, x + RANGE, y + RANGE, z + RANGE);
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box,
                e -> e.getClass().getName().contains("cannon_control.contraption"))) {
            double distance = entity.distanceToSqr(x, y, z);
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest != null && nearest.getClass().getName().contains("Mediumcannon");
    }
}
