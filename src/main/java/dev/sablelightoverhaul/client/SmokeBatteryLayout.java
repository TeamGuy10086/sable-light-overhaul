package dev.sablelightoverhaul.client;

import net.minecraft.world.phys.Vec3;

/** The viewer stands two blocks in front of eight muzzles, looking along the firing axis. */
final class SmokeBatteryLayout {
    private SmokeBatteryLayout(){}
    static Vec3 muzzle(Vec3 eye,Vec3 forward,int gun){
        Vec3 horizontal=new Vec3(forward.x,0,forward.z).normalize();
        if(horizontal.lengthSqr()<1e-8)horizontal=new Vec3(0,0,1);
        Vec3 side=horizontal.cross(new Vec3(0,1,0));
        return eye.subtract(horizontal.scale(2)).add(side.scale((gun%4-1.5)*1.5))
            .add(0,gun/4*.8,0);
    }
    static double born(int volley,int batch){return 3+volley*10+batch/20.0;}
}
