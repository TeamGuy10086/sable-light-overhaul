package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Main-thread writes and immutable snapshots. Bounded shots; auto muzzles retain sub-voxel separation. */
public final class FlashStore {
    /**
     * The light behind a flash, and the form the firing cannon was configured with.
     *
     * <p>The form rides here rather than on the flash itself because it belongs to whatever produced the
     * light: it decides that light's shape, and every path that builds one already reads the emitter for its
     * charge and its plume size. Null means the shot had no form and the profile is read from configuration.
     */
    public record Emitter(int id,double x,double y,double z,double charge,double plumeSize,float[] profile) {
        public Emitter(int id,double x,double y,double z) { this(id,x,y,z,4,4,null); }
        public Emitter(int id,double x,double y,double z,double charge,double plumeSize) { this(id,x,y,z,charge,plumeSize,null); }
        /** True when the firing cannon had a form of its own. */
        public boolean hasProfile() { return profile != null && profile.length > 0; }
    }
    public record Flash(long token, double x, double y, double z, double dx, double dy, double dz,
                        int kind, long born, long expires, double tipX, double tipY, double tipZ,
                        boolean attached, LightCurve curve, LightLayout layout, long trackedAt, boolean hot, Emitter emitter, double smokeSize) {
        public int blockX() { return (int)Math.floor(x); }
        public int blockY() { return (int)Math.floor(y); }
        public int blockZ() { return (int)Math.floor(z); }
        public double distanceSquared(double cx, double cy, double cz) {
            return square(x-cx) + square(y-cy) + square(z-cz);
        }
        private Flash withTip(double tx, double ty, double tz, boolean bound, long time, boolean hot) {
            return new Flash(token,x,y,z,dx,dy,dz,kind,born,expires,tx,ty,tz,bound,curve,layout,time,hot,emitter,smokeSize);
        }
    }
    private final int limit;
    private long serial;
    private volatile List<Flash> snapshot = List.of();
    private List<Flash> muzzles=List.of();
    public FlashStore(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
    }
    public List<Flash> snapshot() { return snapshot; }
    public java.util.Map<String,Flash> areaFlashes() {
        var selected=new java.util.LinkedHashMap<String,Flash>();
        for(var f:snapshot) selected.put("shot/"+f.token,f);
        return selected;
    }
    public void clear() { snapshot = List.of(); muzzles=List.of(); } // Never reuse a particle's token after a world switch.
    public void expire(long now) { snapshot = snapshot.stream().filter(f -> f.expires > now).toList();
        muzzles=muzzles.stream().filter(f->now-f.born()<=8).toList(); }
    public static boolean valid(double x, double y, double z, double dx, double dy, double dz, int kind) {
        double norm = dx*dx + dy*dy + dz*dz;
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000 && Math.abs(y) <= 20_000_000
            && Double.isFinite(norm) && norm > 1.0e-8 && norm < 1.0e8 && (kind >= 0 && kind <= 3);
    }
    public boolean add(double x, double y, double z, double dx, double dy, double dz, int kind, long now) {
        return add(x,y,z,dx,dy,dz,kind,now,LightCurve.DEFAULT_BIG);
    }
    public boolean add(double x, double y, double z, double dx, double dy, double dz, int kind, long now, LightCurve bigCurve) {
        // Kinds 1 and 2 are the barrel weapons (autocannon, tank cannon): a short flash at the muzzle.
        // Only the big cannon (kind 0) tracks a native plume over its own lifetime.
        //
        // The curve chosen here is only the store's own lifetime; how bright the light is at each age comes from
        // the profile in AdvancedLights (kind 2 reads [lightProfile.tankCannon]). Reading a config value here would
        // also make this class untestable on its own, which is what it is: no client, no config, just the store.
        return add(x,y,z,dx,dy,dz,kind,now,kind != 0 ? LightCurve.AUTO : bigCurve,
                kind != 0 ? LightLayout.AUTO : LightLayout.BIG);
    }
    public boolean add(double x, double y, double z, double dx, double dy, double dz, int kind, long now,
                       LightCurve curve, LightLayout layout) {
        return add(x,y,z,dx,dy,dz,kind,now,curve,layout,new Emitter(0,x,y,z));
    }
    public boolean add(double x,double y,double z,double dx,double dy,double dz,int kind,long now,
                       LightCurve curve,LightLayout layout,Emitter emitter) {
        if (!valid(x,y,z,dx,dy,dz,kind)) return false;
        double inv = 1.0 / Math.sqrt(dx*dx+dy*dy+dz*dz);
        var flash = new Flash(++serial,x,y,z,dx*inv,dy*inv,dz*inv,kind,now,now+curve.durationTicks(),x,y,z,false,
                java.util.Objects.requireNonNull(curve),java.util.Objects.requireNonNull(layout),Long.MIN_VALUE,true,emitter,0);
        var next = new ArrayList<>(snapshot);
        // Twin barrels can share a voxel. Only refresh the same precise autocannon muzzle;
        // retain both barrels' shot lifetimes, then merge overlapping block-light voxels downstream.
        next.removeIf(f -> f.expires <= now || (f.kind == kind && (kind != 0
                ? f.distanceSquared(x,y,z) < 0.0001
                : f.blockX() == flash.blockX() && f.blockY() == flash.blockY() && f.blockZ() == flash.blockZ())));
        if (next.size() >= limit) next.removeFirst();
        next.add(flash);
        snapshot = List.copyOf(next);
        if(kind==0||kind==2) {
            var recent=new ArrayList<>(muzzles);
            recent.removeIf(f->now-f.born()>8);
            if(recent.size()>=limit)recent.removeFirst();
            recent.add(flash);muzzles=List.copyOf(recent);
        }
        return true;
    }
    /** Match a launch segment to a recent authoritative muzzle, even after its two-tick light expired. */
    public TrailMuzzleGuard trailGuard(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,long now,double radius,double drift) {
        var delta=to.subtract(from);
        double length=delta.length();if(length<1e-6)return null;
        var direction=delta.scale(1/length);
        Flash best=null;double score=Double.POSITIVE_INFINITY;
        for(var f:muzzles) {
            if(now<f.born()||now-f.born()>8)continue;
            double alignment=f.dx()*direction.x+f.dy()*direction.y+f.dz()*direction.z;
            if(alignment<.9)continue;
            double ox=from.x-f.x(),oy=from.y-f.y(),oz=from.z-f.z();
            double along=ox*f.dx()+oy*f.dy()+oz*f.dz();
            double perp=Math.max(0,ox*ox+oy*oy+oz*oz-along*along);
            // Only launch-near frames need the guard; never pull a distant/slow shell back to the muzzle.
            if(along< -8||along>length*Math.max(0,drift)*10+radius+2||perp>(radius+1.5)*(radius+1.5))continue;
            double candidate=perp+Math.abs(along)*.01+(1-alignment)*64+(now-f.born())*.01;
            if(candidate<score){score=candidate;best=f;}
        }
        return best==null?null:new TrailMuzzleGuard(new net.minecraft.world.phys.Vec3(best.x(),best.y(),best.z()),
            new net.minecraft.world.phys.Vec3(best.dx(),best.dy(),best.dz()),radius);
    }
    /**
     * The kind of shot a plume generator belongs to, or -1 when no recent shot matches.
     *
     * <p>Every shot already crosses to this side as a flash carrying its weapon kind (0 big cannon, 1/3
     * autocannon, 2 tank cannon). CBC's plume generator is spawned at the muzzle in the same tick, so the
     * generator can be attributed to the shot by position and time instead of by asking the world which
     * contraption happens to be nearest -- one scan of the (bounded) flash list, no entity query, and it works
     * even when several cannons stand side by side.
     *
     * <p>The caller tries this first and falls back to the nearest contraption only when it returns -1 (for
     * example when the muzzle-flash switch is off, so no flash was sent but CBC's plume still spawns).
     */
    public int plumeKind(double x, double y, double z, double dx, double dy, double dz, long now) {
        Flash best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Flash f : snapshot) {
            if (now < f.born || now - f.born > 4 || f.expires <= now) continue;
            // The generator is born just past the muzzle and aimed the same way; 3 blocks and a broad cosine
            // cover a long barrel's own offset while still rejecting a different cannon's shot.
            if (f.dx*dx + f.dy*dy + f.dz*dz < 0.7) continue;
            double distance = f.distanceSquared(x, y, z);
            if (distance <= 9.0 && distance < bestDistance) {
                bestDistance = distance;
                best = f;
            }
        }
        return best == null ? -1 : best.kind;
    }
    /** Match one native big-cannon plume to its recent authoritative shot. */
    public long attachBigPlume(double x, double y, double z, double dx, double dy, double dz, long now) {
        var match = snapshot.stream().filter(f -> f.kind == 0 && !f.attached && now >= f.born
                && now - f.born <= 4 && f.expires > now && f.distanceSquared(x,y,z) <= 2.25
                && f.dx*dx+f.dy*dy+f.dz*dz > 0.9)
            .min(Comparator.comparingDouble(f -> f.distanceSquared(x,y,z))).orElse(null);
        if (match == null) return 0;
        snapshot = snapshot.stream().map(f -> f.token == match.token
                ? f.withTip(f.x,f.y,f.z,true,Long.MIN_VALUE,true) : f).toList();
        return match.token;
    }
    public void followBigPlume(long token, double x, double y, double z, long now) {
        followHotCloud(token,x,y,z,now,true);
    }
    /** Position is the real hot-particle centroid, never the invisible emitter. */
    public void followHotCloud(long token, double x, double y, double z, long now, boolean hot) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        snapshot = snapshot.stream().map(f -> {
            if (f.token != token || !f.attached || f.kind != 0 || now < f.born || f.expires <= now) return f;
            // Invalid runaway positions switch off; never pin a light at a distance boundary.
            return f.withTip(x,y,z,true,now,hot && f.distanceSquared(x,y,z) <= 128*128);
        }).toList();
    }
    public void smokeSize(long token,double size) {
        if(!Double.isFinite(size) || size<=0) return;
        snapshot=snapshot.stream().map(f -> f.token!=token ? f : new Flash(f.token,f.x,f.y,f.z,f.dx,f.dy,f.dz,
            f.kind,f.born,f.expires,f.tipX,f.tipY,f.tipZ,f.attached,f.curve,f.layout,f.trackedAt,f.hot,f.emitter,size)).toList();
    }
    public Flash find(long token) { return snapshot.stream().filter(f -> f.token == token).findFirst().orElse(null); }
    private static double square(double n) { return n*n; }
}
