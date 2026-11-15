package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;

/** Small motion-driven strain, with no spin, periodic wobble, shear or footprint expansion. */
public final class HullDustDeformation {
    private HullDustDeformation(){}
    public record Size(float width,float height){}
    public static Size apply(float width,float height,Vec3 velocity,Vec3 right,Vec3 up){
        double x=Math.abs(velocity.dot(right)),y=Math.abs(velocity.dot(up));
        double scale=Math.max(1,x+y);
        return new Size((float)(width*(1-.08*Math.min(1,y/scale))),
            (float)(height*(1-.08*Math.min(1,x/scale))));
    }
}
