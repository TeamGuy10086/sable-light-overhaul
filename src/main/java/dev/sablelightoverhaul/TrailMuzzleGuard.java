package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;

/** A fixed launch plane, with conservative room for every corner of a growing trail billboard. */
public record TrailMuzzleGuard(Vec3 origin,Vec3 axis,double margin) {
    public double ahead(Vec3 p) { return p.subtract(origin).dot(axis)-margin; }
    public Vec3 clipStart(Vec3 from,Vec3 to) {
        double a=ahead(from),b=ahead(to);
        if(b<0)return null;
        if(a>=0)return from;
        double t=-a/(b-a);
        return from.add(to.subtract(from).scale(t));
    }
    /** Total travel is bounded by v/(1-.9); include the random jitter in that budget. */
    public Vec3 drift(Vec3 position,Vec3 velocity) {
        double axial=velocity.dot(axis),allowed=Math.max(0,ahead(position))/10.0;
        return axial< -allowed?velocity.add(axis.scale(-allowed-axial)):velocity;
    }
}
