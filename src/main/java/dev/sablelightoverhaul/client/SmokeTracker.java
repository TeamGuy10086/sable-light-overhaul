package dev.sablelightoverhaul.client;
import java.util.ArrayList;
import java.util.List;
/** Direct references captured during synchronous plume emission, including fallback smoke. */
public final class SmokeTracker {
    private static final ThreadLocal<Group> CONTEXT=new ThreadLocal<>();
    private static final List<Group> GROUPS=new ArrayList<>();
    /** Hands out the group ids. A counter, because a group's identity is ours to define and nothing else. */
    private static long SERIAL;
    /**
     * Registers this mod's own aggregate as the default listener for the particle offers it produces.
     *
     * <p>The offer and the answer live in the same class on purpose: what this knows is which particles
     * belong to which burst and how hot each one is, and the answer -- one light following their weighted
     * centre at their mean size -- is the only thing this mod has ever done with that. A third party can now
     * run before it, cancel it by name ({@code DefaultSpecs.SMOKE_LIGHT}) and put something else on the same
     * smoke.
     */
    public static void install() {
        dev.sablelightoverhaul.api.EffectApi.onTrackedHost(
            dev.sablelightoverhaul.effects.EffectBus.DEFAULT_PRIORITY,SmokeTracker::follow);
    }
    /**
     * The aggregate: one point, the heat-weighted centre of the offered particles, and the mean size that
     * goes with it. Identical arithmetic to what this class did inline, in the same order, so the same
     * floating-point sums come out -- and a cold group switches the light off rather than freezing it where
     * the last hot puff died.
     *
     * <p>The flash this moves is looked up in this class's own bookkeeping, not carried by the offer: a
     * group of somebody's smoke must be offerable whether or not this mod happens to have a light on it,
     * and the ring's controller smoke has no flash at all. See the note on {@code OFFER_TOKEN}.
     */
    private static void follow(dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent event) {
        if(event.side()!=dev.sablelightoverhaul.api.EffectApi.Side.CLIENT) return;
        if(event.hostKind()!=dev.sablelightoverhaul.api.EffectApi.HostKind.PARTICLE) return;
        long token=OFFER_TOKEN;
        if(token==0) return;
        if(event.cancellation().blocks(dev.sablelightoverhaul.api.EffectApi.Section.REFERENCED_PARTICLES,
            dev.sablelightoverhaul.effects.DefaultSpecs.SMOKE_LIGHT)) return;
        if(!(event.level() instanceof net.minecraft.client.multiplayer.ClientLevel level)) return;
        double x=0,y=0,z=0,weight=0,size=0;
        for(var host:event.hosts()) {
            double heat=host.heat();
            if(heat<=0) continue;
            x+=host.current().x*heat; y+=host.current().y*heat; z+=host.current().z*heat;
            weight+=heat; size+=host.size()*heat;
        }
        if(weight>0) {
            ClientFlashes.STORE.followHotCloud(token,x/weight,y/weight,z/weight,level.getGameTime(),true);
            ClientFlashes.STORE.smokeSize(token,size/weight);
        } else {
            ClientFlashes.STORE.followHotCloud(token,0,0,0,level.getGameTime(),false);
        }
    }
    /**
     * The flash the offer being dispatched belongs to, or 0.
     *
     * <p>A side channel on purpose. The offer is about somebody else's particles, and the public event says
     * nothing about our lights -- a consumer must be able to react to a plume whether or not we have a light
     * on it. This class is both the producer and the default consumer, so the one thing only this class
     * knows -- which of its groups is being offered right now -- travels here. It stays correct because
     * dispatch is synchronous and single-threaded, exactly like {@link #CONTEXT}.
     */
    private static volatile long OFFER_TOKEN;
    public static final class Group {
        /** This group's own id, for correlating one tick's offer with the next. Not a flash token. */
        final long id=++SERIAL;
        /**
         * The flash this group's smoke feeds, if any. Zero is a legitimate state: the ring's controller
         * smoke belongs to no light of ours, and it must still be offerable.
         */
        public long token;
        final long born;
        final int limit;
        public final double hotFraction;
        /**
         * The opacity a child born right now should have. CBC's plume has no channel for this, so the plume
         * leaves the value here before it spawns its children and each child spends it as it is constructed.
         * Sampling per child is what fades the plume as a whole -- children are born throughout its life --
         * while a single puff keeps the opacity it was born with.
         */
        public float birthAlpha = 1.0F;
        /** Positive only for our controlled tank release; ordinary CBC plumes retain native growth/fade. */
        public float controlledExpansion;
        public boolean controlledTank;
        public float controlledFadeFrom,controlledFadeRate;
        final List<TrackedSmoke> particles=new ArrayList<>();
        boolean observed;
        boolean retired;
        boolean live=true;
        Group(long born) { this.born=born; limit=MuzzleClientConfig.TRACKED_PARTICLES.get(); hotFraction=MuzzleClientConfig.HOT_FRACTION.get(); }
        // Cached per group per tick, not a store search for every smoke vertex or child particle. A group
        // that feeds no flash is alive while it has smoke: its life is its own, not a light's.
        public boolean active() { return !retired && (token==0 ? observed && !particles.isEmpty() : live); }
        public void capture(TrackedSmoke particle) {
            if(retired) return;
            particles.removeIf(p -> !p.slo$alive() || p.slo$heat()<=0);
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
    public static void tick(net.minecraft.client.multiplayer.ClientLevel level) {
        long now=level.getGameTime();
        for(var g:GROUPS) if(g.token!=0) g.live=ClientFlashes.STORE.find(g.token)!=null;
        GROUPS.removeIf(g -> { boolean remove=g.retired||!g.active()||(g.token==0&&!g.observed&&now-g.born>5); if(remove) {g.retired=true; g.particles.clear();} return remove; });
        for(var g:GROUPS) {
            if(!g.active()||!g.observed) continue;
            // One offer per group per tick: a group is the smoke of one burst, and the burst's flash token is
            // what a light following it is tied to. A group that has never captured a particle is not
            // offered at all, which is what keeps a light on a plume that has not started yet exactly where
            // it was, rather than switching it off before its smoke exists.
            g.particles.removeIf(p -> !p.slo$alive() || p.slo$heat()<=0);
            var hosts=new ArrayList<dev.sablelightoverhaul.api.EffectApi.Host>(g.particles.size());
            for(var p:g.particles) {
                var now3=new net.minecraft.world.phys.Vec3(p.slo$x(),p.slo$y(),p.slo$z());
                var previous=new net.minecraft.world.phys.Vec3(p.slo$px(),p.slo$py(),p.slo$pz());
                hosts.add(new dev.sablelightoverhaul.api.EffectApi.Host(0,previous,now3,
                    now3.subtract(previous),p.slo$size(),p.slo$heat(),false));
            }
            OFFER_TOKEN=g.token;
            dev.sablelightoverhaul.effects.EffectBus.trackedHost(
                new dev.sablelightoverhaul.api.EffectApi.TrackedHostEvent(level,
                    dev.sablelightoverhaul.api.EffectApi.HostKind.PARTICLE,List.copyOf(hosts),List.of(),
                    dev.sablelightoverhaul.api.EffectApi.Side.CLIENT,g.id,
                    dev.sablelightoverhaul.effects.DefaultSpecs.forTrackedHosts(),
                    dev.sablelightoverhaul.api.EffectApi.Cancellation.none()));
        }
    }
}
