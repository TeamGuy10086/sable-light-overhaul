package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;

/** Deterministic round-robin neighborhood seeds. No per-particle network positions. */
public final class HullDustEmission {
    private HullDustEmission(){}
    public record Sample(int source,Vec3 offset,double delay){}
    public static int count(int base,double density){return Math.clamp((int)Math.round(base*density),0,512);}
    public static Sample sample(int index,int origins,long seed,Vec3 normal,double spread,double stagger){
        if(origins<=0||index<0)throw new IllegalArgumentException("No hull source");
        int source=index%origins,sibling=index/origins;
        if(sibling==0)return new Sample(source,Vec3.ZERO,0);
        Vec3 n=normal.normalize(),u=n.cross(Math.abs(n.y)>.8?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=n.cross(u);
        long key=seed+source*0x9E3779B97F4A7C15L+sibling*0xD1B54A32D192ED03L;
        double angle=hash(key)*Math.PI*2,radius=spread*Math.sqrt(.10+.90*hash(key+1));
        Vec3 offset=u.scale(Math.cos(angle)*radius).add(v.scale(Math.sin(angle)*radius));
        return new Sample(source,offset,stagger*(.15+.85*hash(key+2)));
    }
    private static double hash(long x){x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;x=(x^(x>>>27))*0x94D049BB133111EBL;return ((x^(x>>>31))>>>11)*0x1.0p-53;}
}
