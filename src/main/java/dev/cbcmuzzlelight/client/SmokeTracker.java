package dev.cbcmuzzlelight.client;
import java.util.ArrayList;
import java.util.List;
/** Direct references captured during synchronous plume emission, including fallback smoke. */
public final class SmokeTracker {
    private static final ThreadLocal<Group> CONTEXT=new ThreadLocal<>();
    private static final List<Group> GROUPS=new ArrayList<>();
    public static final class Group {
        public long token;
        final long born;
        final int limit;
        public final double hotFraction;
        final List<TrackedSmoke> particles=new ArrayList<>();
        boolean observed;
        boolean retired;
        boolean live=true;
        Group(long born) { this.born=born; limit=MuzzleClientConfig.TRACKED_PARTICLES.get(); hotFraction=MuzzleClientConfig.HOT_FRACTION.get(); }
        // Cached per group per tick, not a store search for every smoke vertex or child particle.
        public boolean active() { return !retired && token!=0 && live; }
        public void capture(TrackedSmoke particle) {
            if(retired) return;
            particles.removeIf(p -> !p.muzzle$alive() || p.muzzle$heat()<=0);
            observed=true;
            // Retain recent hot children, not only the first burst: later orange smoke must remain represented.
            if(particles.size()>=limit) particles.removeFirst();
            particles.add(particle);
        }
    }
    public static Group create(long now) {
        var group=new Group(now);
        if(GROUPS.size()>=64) { var old=GROUPS.removeFirst(); old.retired=true; old.particles.clear(); }
        GROUPS.add(group); return group;
    }
    public static Group current() { return CONTEXT.get(); }
    public static Group enter(Group group) { var old=CONTEXT.get(); if(group==null) CONTEXT.remove(); else CONTEXT.set(group); return old; }
    public static void clear() { GROUPS.forEach(g -> {g.retired=true; g.particles.clear();}); GROUPS.clear(); CONTEXT.remove(); }
    public static void tick(long now) {
        for(var g:GROUPS) if(g.token!=0) g.live=ClientFlashes.STORE.find(g.token)!=null;
        GROUPS.removeIf(g -> { boolean remove=g.token==0 ? now-g.born>5 : !g.active(); if(remove) {g.retired=true; g.particles.clear();} return remove; });
        for(var g:GROUPS) {
            if(!g.active()) continue;
            double x=0,y=0,z=0,weight=0,size=0;
            g.particles.removeIf(p -> !p.muzzle$alive() || p.muzzle$heat()<=0);
            for(var p:g.particles) {
                double h=p.muzzle$heat(); x+=p.muzzle$x()*h; y+=p.muzzle$y()*h; z+=p.muzzle$z()*h; weight+=h; size+=p.muzzle$size()*h;
            }
            if(weight>0) { ClientFlashes.STORE.followHotCloud(g.token,x/weight,y/weight,z/weight,now,true); ClientFlashes.STORE.smokeSize(g.token,size/weight); }
            else if(g.observed) ClientFlashes.STORE.followHotCloud(g.token,0,0,0,now,false);
        }
    }
}
