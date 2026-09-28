package dev.cbcmuzzlelight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Main-thread writes and immutable snapshots. Bounded shots; auto muzzles retain sub-voxel separation. */
public final class FlashStore {
    public record Emitter(int id,double x,double y,double z,double charge,double plumeSize) {
        public Emitter(int id,double x,double y,double z) { this(id,x,y,z,4,4); }
    }
    public record Flash(long token, double x, double y, double z, double dx, double dy, double dz,
                        int kind, long born, long expires, double tipX, double tipY, double tipZ,
                        boolean attached, LightCurve curve, LightLayout layout, long trackedAt, boolean hot, Emitter emitter, double smokeSize) {
        public int blockX() { return (int)Math.floor(x); }
        public int blockY() { return (int)Math.floor(y); }
        public int blockZ() { return (int)Math.floor(z); }
        public float strength(long now) {
            return (float)curve.brightness(now-born);
        }
        public double distanceSquared(double cx, double cy, double cz) {
            return square(x-cx) + square(y-cy) + square(z-cz);
        }
        private Flash withTip(double tx, double ty, double tz, boolean bound, long time, boolean hot) {
            return new Flash(token,x,y,z,dx,dy,dz,kind,born,expires,tx,ty,tz,bound,curve,layout,time,hot,emitter,smokeSize);
        }
    }
    public record LightSample(double x, double y, double z, int level, float strength) {
        public double distanceSquared(double cx, double cy, double cz) {
            return square(x-cx) + square(y-cy) + square(z-cz);
        }
    }
    private final int limit;
    private long serial;
    private volatile List<Flash> snapshot = List.of();
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
    public void clear() { snapshot = List.of(); } // Never reuse a particle's token after a world switch.
    public void expire(long now) { snapshot = snapshot.stream().filter(f -> f.expires > now).toList(); }
    public static boolean valid(double x, double y, double z, double dx, double dy, double dz, int kind) {
        double norm = dx*dx + dy*dy + dz*dz;
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000 && Math.abs(y) <= 20_000_000
            && Double.isFinite(norm) && norm > 1.0e-8 && norm < 1.0e8 && (kind == 0 || kind == 1 || kind == 2);
    }
    public boolean add(double x, double y, double z, double dx, double dy, double dz, int kind, long now) {
        return add(x,y,z,dx,dy,dz,kind,now,LightCurve.DEFAULT_BIG);
    }
    public boolean add(double x, double y, double z, double dx, double dy, double dz, int kind, long now, LightCurve bigCurve) {
        // Kinds 1 and 2 are the barrel weapons (autocannon, tank cannon): a short flash at the muzzle.
        // Only the big cannon (kind 0) tracks a native plume over its own lifetime.
        return add(x,y,z,dx,dy,dz,kind,now,kind == 1 || kind == 2 ? LightCurve.AUTO : bigCurve,
                kind == 1 || kind == 2 ? LightLayout.AUTO : LightLayout.BIG);
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
        return true;
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
    public List<LightSample> samples(long now) {
        var result = new ArrayList<LightSample>();
        for (var f : snapshot) {
            if (now < f.born || now >= f.expires) continue;
            float strength = f.strength(now);
            long age = now-f.born;
            int light = f.curve.blockLevel(15,age);
            if (light == 0) continue;
            double cx=f.x, cy=f.y, cz=f.z;
            if (f.kind == 0 && age >= f.layout.muzzleHoldTicks()) {
                if (f.trackedAt != Long.MIN_VALUE) {
                    if (!f.hot || now-f.trackedAt > 2) continue; // Dead/cold particles must not leave frozen light.
                    cx=f.tipX; cy=f.tipY; cz=f.tipZ;
                } else {
                    double travel=f.layout.fallbackTravel(age);
                    cx+=f.dx*travel; cy+=f.dy*travel; cz+=f.dz*travel;
                }
            } else if (f.kind == 1 || f.kind == 2) {
                // Center the auto layout ahead of the muzzle; no rearward source inside the barrel.
                cx+=f.dx*f.layout.length()/2; cy+=f.dy*f.layout.length()/2; cz+=f.dz*f.layout.length()/2;
            }
            if (f.kind == 0 && age < f.layout.muzzleHoldTicks()) {
                result.add(new LightSample(cx,cy,cz,light,strength));
            } else for (var offset : f.layout.offsets(f.dx,f.dy,f.dz)) {
                result.add(new LightSample(cx+offset.x(),cy+offset.y(),cz+offset.z(),light,strength));
            }
        }
        return List.copyOf(result);
    }
    public List<LightSample> nearest(double x, double y, double z, int count, long now) {
        return samples(now).stream().sorted(Comparator.comparingDouble(f -> f.distanceSquared(x,y,z))).limit(count).toList();
    }
    private static double square(double n) { return n*n; }
}
