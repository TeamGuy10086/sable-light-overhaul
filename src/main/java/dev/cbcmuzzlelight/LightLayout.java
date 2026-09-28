package dev.cbcmuzzlelight;

import java.util.ArrayList;
import java.util.List;
import dev.cbcmuzzlelight.FlameShape.Vector;

/** Bounded sparse cross: center, front/back, right/left, up/down. Distances are in blocks. */
public record LightLayout(int count, double length, double width, int muzzleHoldTicks, double fallbackSpeed) {
    public static final LightLayout BIG = new LightLayout(5,6,4,2,0.9);
    public static final LightLayout AUTO = new LightLayout(3,3,0,0,0);
    public LightLayout {
        if (count < 1 || count > 7 || !Double.isFinite(length) || length < 0 || length > 24
                || !Double.isFinite(width) || width < 0 || width > 24 || muzzleHoldTicks < 0 || muzzleHoldTicks > 600
                || !Double.isFinite(fallbackSpeed) || fallbackSpeed < 0 || fallbackSpeed > 4)
            throw new IllegalArgumentException("Invalid light layout");
    }
    public List<Vector> offsets(double dx, double dy, double dz) {
        var b = FlameShape.basis(dx,dy,dz);
        var offsets = List.of(new Vector(0,0,0),b.forward().scale(length/2),b.forward().scale(-length/2),
                b.right().scale(width/2),b.right().scale(-width/2),b.up().scale(width/2),b.up().scale(-width/2));
        return new ArrayList<>(offsets.subList(0,count));
    }
    public double fallbackTravel(long age) {
        return fallbackSpeed * (1-Math.pow(0.95,Math.max(0,age-muzzleHoldTicks))) / 0.05;
    }
}
