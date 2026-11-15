package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;

/** Shared geometry helpers, kept independent of rendering and world state. */
public final class CannonDustMotion {
    private CannonDustMotion() {}
    public static Vec3 tangent(Vec3 away, Vec3 normal, Vec3 fallback) {
        Vec3 n = normal.normalize();
        Vec3 v = away.subtract(n.scale(away.dot(n)));
        if (v.lengthSqr() < 1e-8) v = fallback.subtract(n.scale(fallback.dot(n)));
        if (v.lengthSqr() < 1e-8) v = n.cross(Math.abs(n.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        return v.normalize();
    }
    /** Artistic baseline of loosened dust plus face exposure; not a measured vibration/air split. */
    public static double surfaceExposure(Vec3 point,Vec3 normal,Vec3 muzzle){
        Vec3 incoming=muzzle.subtract(point).normalize();
        return .4+.6*Math.max(0,Math.min(1,normal.normalize().dot(incoming)));
    }
    /**
     * The fraction of a shot's push that reaches the smoke hanging <em>behind</em> the muzzle.
     *
     * <p>The smoke a shot leaves sits all around the barrel's mouth, so a push that treated every puff alike
     * streamed the rear half off the back of the gun as a second plume. Most of the blast goes forward, and
     * this is the share the other half gets: enough to keep it from hanging on the barrel, little enough that
     * it stays where it was made.
     */
    public static final double REAR_PUSH = 0.3;
    /**
     * The same push with no direction known, which is what a caller that only has a point and a radius gets:
     * every puff is pushed alike. The haze path always passes the barrel, so the rear half can be spared.
     */
    public static Vec3 gustHorizontal(Vec3 point, Vec3 muzzle, double radius, double impulse) {
        return gustHorizontal(point, muzzle, null, radius, impulse);
    }
    /**
     * The impulse one firing event gives a puff of muzzle haze that is already in the air: horizontal,
     * radially away from the muzzle, falling off with distance and with nothing vertical in it. A shot
     * does not blow its own smoke down the barrel -- it shoves the smoke hanging around the muzzle
     * outwards, which is what makes a burst read as clearing a hole in front of the gun.
     *
     * <p>The puff's side of the muzzle decides how much of the push it gets: {@link #REAR_PUSH} of it for
     * everything in the rear half, the whole of it in front.
     */
    public static Vec3 gustHorizontal(Vec3 point, Vec3 muzzle, Vec3 axis, double radius, double impulse) {
        if (radius <= 0 || impulse <= 0) return Vec3.ZERO;
        double dx = point.x - muzzle.x, dz = point.z - muzzle.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance >= radius) return Vec3.ZERO;
        // Straight above the muzzle there is no horizontal direction to speak of; any of them will do,
        // and a puff that happens to sit exactly over the barrel must still be pushed off it.
        Vec3 direction = distance < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(dx / distance, 0, dz / distance);
        double weight = 1 - distance / radius;
        // Which side of the gun the puff is on. Horizontal only, because the push is horizontal: a barrel
        // pointed nearly straight up has no rear half to speak of and everything is pushed alike.
        double forward = axis == null ? 1.0 : dx * axis.x + dz * axis.z;
        double share = forward < 0 ? REAR_PUSH : 1.0;
        return direction.scale(impulse * weight * weight * share);
    }
    public static Vec3 cap(Vec3 velocity, double max) {
        return velocity.lengthSqr() > max * max ? velocity.normalize().scale(max) : velocity;
    }
}
