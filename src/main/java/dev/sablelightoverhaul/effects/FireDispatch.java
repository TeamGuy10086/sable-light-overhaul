package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.api.EffectApi;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.CBCCompatTransformers;

/**
 * Builds a {@link EffectApi.FireEvent} at each of the four points where a CBC weapon builds its muzzle plume,
 * and hands it to the bus. These four calls replace four direct calls to {@code ServerFlashes}, so this class
 * is the whole of "what a firing event is"; the drawing side is a listener like any other.
 *
 * <p>Two shapes arrive here and both are preserved exactly:
 * <ul>
 *   <li><b>Already world space</b> -- the big cannon's plume is world space and only its direction needs the
 *       contraption transform; the tank cannon transforms its own muzzle before it gets here.</li>
 *   <li><b>Contraption space</b> -- the autocannon and the twin autocannon report a local point plus the root
 *       it is relative to, so the transform happens here. It is the same two calls, in the same order, that
 *       {@code ServerFlashes.emitGrouped} used to make, because a rotated axis derived differently would move
 *       the light.</li>
 * </ul>
 *
 * <p>The light centre used to be passed separately from the muzzle position; at all four call sites the two
 * were the same value, so the event carries one point and the listener uses it for both.
 *
 * <p>Nothing here is gated on a config. Each weapon's own switch stays where it has always been -- in
 * {@code MediumCannonEffects.fire} for the tank cannon, on the client for the rest -- so this slice cannot
 * change when a shot produces effects. Normalising those gates into one place is a later slice.
 */
public final class FireDispatch {
    private FireDispatch() {}

    /** The muzzle offset the autocannon family lights its flash at, in contraption space. */
    private static final double AUTO_OFFSET = 0.1;
    /** The strength figures the autocannon family has always sent: both are constants, not measurements. */
    private static final float AUTO_SIZE = 4.0F;
    private static final float AUTO_POWER = 4.0F;
    /** The big cannon's plume sits this far up the bore from where CBC reports it. */
    private static final double BIG_OFFSET = 1.6;

    /**
     * The big cannon. Everything is world space already except the direction; the plume is offset up the bore
     * the same 1.6 blocks it always was.
     */
    public static void bigCannon(Level level, Entity owner, Vec3 plume, Vec3 direction, float size, float power) {
        Vec3 axis = CBCCompatTransformers.transformLocationNormal(level, owner.blockPosition(), direction).normalize();
        Vec3 origin = plume.add(axis.scale(BIG_OFFSET));
        dispatch(level, owner, origin, axis, EffectApi.WeaponKind.CANNON,
                Math.max(1, size), Math.max(0, power));
    }

    /**
     * The autocannon and the twin autocannon, which differ only in which barrel was picked -- and that pick has
     * already been applied by whoever reported the position. {@code root} is the contraption origin the local
     * point is relative to; for both weapons today it is the reported position itself.
     */
    public static void autocannon(Level level, Entity owner, Vec3 plume, Vec3 root, Vec3 direction,
                                  EffectApi.WeaponKind kind) {
        Vec3 point = plume.add(direction.scale(AUTO_OFFSET));
        Vec3 origin = CBCCompatTransformers.transformVec3(level, point, root);
        Vec3 axis = CBCCompatTransformers.transformVec3(level, point.add(direction), root).subtract(origin).normalize();
        dispatch(level, owner, origin, axis, kind, AUTO_SIZE, AUTO_POWER);
    }

    /** The tank cannon, which has already transformed its own muzzle and normalised its own axis. */
    public static void tankCannon(Level level, Entity owner, Vec3 origin, Vec3 axis, float size, float power) {
        dispatch(level, owner, origin, axis, EffectApi.WeaponKind.TANK_CANNON, size, power);
    }

    private static void dispatch(Level level, Entity owner, Vec3 origin, Vec3 direction,
                                 EffectApi.WeaponKind kind, float size, float power) {
        // No SourceKey yet: it needs the ship's identity and a position in that ship's own frame, which is what
        // the per cannon profile work resolves. No flash token either: the client mints it when the payload
        // arrives, so it cannot travel outwards from here.
        EffectBus.fire(new EffectApi.FireEvent(level, owner, sourceKey(level, owner, origin), owner, origin, direction, kind, size, power,
                0L, DefaultSpecs.forFire(kind), EffectApi.Cancellation.none()));
    }
    /**
     * Who fired, where from, and what kind of cannon it is.
     *
     * <p>The body comes from the muzzle position, so a cannon mounted on a turret keys on the turret rather
     * than on the hull it is attached to. SourceAnchor answers that question and returns a null body when
     * there is none, which is the case that means plain world coordinates. Nothing here may throw: an
     * unregistered or oddly placed cannon still has to fire, so anything that cannot be resolved degrades to
     * the world-space key rather than to an exception.
     */
    private static EffectApi.SourceKey sourceKey(Level level, Entity owner, Vec3 muzzle) {
        try {
            var anchor = dev.sablelightoverhaul.lighting.SourceAnchor.capture(level, net.minecraft.core.BlockPos.containing(muzzle));
            var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType());
            return new EffectApi.SourceKey(level.dimension(), anchor.ship(), anchor.pos(), typeId,
                    owner.hasCustomName() ? owner.getName().getString() : null);
        } catch (Throwable failure) {
            var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType());
            return new EffectApi.SourceKey(level.dimension(), null, net.minecraft.core.BlockPos.containing(muzzle), typeId, null);
        }
    }}
