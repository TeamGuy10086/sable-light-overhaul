package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.CannonDustMotion;
import dev.sablelightoverhaul.CannonDustPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

/** Client simulation of one server-authored firing event. No Sable dependency for muzzle haze. */
public final class CannonDustEffects {
    private CannonDustEffects() {}
    public static void receive(CannonDustPayload event) {
        receive(event, false);
    }
    public static void receiveAutocannon(CannonDustPayload event) {
        receive(event, true);
    }
    private static void receive(CannonDustPayload event, boolean softHaze) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !level.dimension().location().equals(event.dimension()) || !ClientFlashes.prepareEffects(level)) return;
        Vec3 axis = event.axis().normalize();
        if (axis.lengthSqr() < .5) return;
        GroundDustParticle.pushHaze(event.muzzle(), axis, event.hazePushRadius(), event.hazePush());
        Random random = new Random(event.seed());
        var points = event.surfaces();
        if(event.hullFlow()!=null)HullDustParticle.beginBatch(level);
        int origins=Math.min(event.burstCount(),points.size());
        int count=event.hullFlow()==null?event.burstCount():dev.sablelightoverhaul.HullDustEmission.count(event.burstCount(),event.hullFlow().density());
        for (int i = 0; i < count && origins>0; i++) {
            // Server ordered the forward hull footprint; keep nearby cluster sources together and stable.
            var surface = points.get(i % origins);
            Vec3 n = surface.normal().normalize();
            Vec3 direction = CannonDustMotion.tangent(surface.position().subtract(event.muzzle()), n, axis);
            Vec3 at = surface.position().add(n.scale(.07));
            Vec3 speed = direction.scale(event.burstSpeed() * (.55 + random.nextDouble() * .45));
            var data = event.burst().with(event.burst().size() * (.8F + random.nextFloat() * .4F),
                event.burst().lifetime(), event.burst().stretch(), event.burst().damping(), event.burst().gravity(), event.burst().alpha());
            if(event.hullFlow()!=null){
                HullDustParticle.spawn(level,at,n,direction,event,i,origins,data,surface);
                continue;
            }
            // Hull dust lands in batches at a fixed speed and is then only damped: no sweep, so nothing accelerates on
        // its way out. The staggering between particles is now just where they are born, not how fast they go.
        var particle = GroundDustParticle.programmed(level, at, speed, null, 0, data);
            if (particle != null) mc.particleEngine.add(particle);
        }
        Vec3 side = axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(axis).normalize();
        // The local quota: a twin autocannon at 1200 rpm fires forty times a second, so a muzzle can be buried
        // in its own smoke. Past the quota this shot adds none; the puffs already there keep drifting.
        var muzzle = event.muzzle();
        double localRadius = ExtraConfig.MUZZLE_HAZE_LOCAL_RADIUS.get();
        long source = event.emitterId() != 0 ? Long.MIN_VALUE | Integer.toUnsignedLong(event.emitterId())
                : net.minecraft.core.BlockPos.containing(muzzle).asLong();
        for (int i = 0; i < event.hazeCount(); i++) {
            // Anywhere around the muzzle, not only down the barrel. The puff a shot leaves sits where the
            // gun's mouth is, and a burst reads as a cloud wrapped around the gun; a spawn that only ever
            // placed them forward built a cone of smoke out in front instead. Uniform on the sphere, so no
            // direction is favoured -- the ring's old vertical part is folded into this rather than added
            // to it, which is what keeps the cloud round.
            double spin = random.nextDouble() * Math.PI * 2;
            double height = random.nextDouble() * 2.0 - 1.0;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - height * height));
            Vec3 around = side.scale(Math.cos(spin) * ring).add(up.scale(Math.sin(spin) * ring)).add(axis.scale(height));
            Vec3 at = event.muzzle().add(around.scale(0.15 + random.nextDouble() * 0.45));
            // The outward speed arrives with the shot: how the haze moves is behaviour, and behaviour is the
            // server's. The client decides only where the puffs may sit and how many may exist.
            // Purely horizontal: thin muzzle haze spreads sideways and fades away -- it does not fly down
            // the barrel and it does not fall out of the sky. The cloud's vertical part is dropped for the
            // same reason, so the puff hangs at the height it was born at until it dissolves.
            Vec3 flat = new Vec3(around.x, 0, around.z);
            flat = flat.lengthSqr() < 1e-6 ? side : flat.normalize();
            double outward = event.hazeSpread() * (.6 + random.nextDouble() * .8);
            Vec3 downRange = new Vec3(axis.x, 0, axis.z);
            Vec3 velocity = (downRange.lengthSqr() > 1e-6
                ? downRange.normalize().scale(.02 + random.nextDouble() * .02) : Vec3.ZERO)
                .add(flat.scale(outward));
            var particle = GroundDustParticle.muzzleHaze(level, at, velocity, event.haze(),
                event.hazeDrift(), event.hazeSink(), event.hazeClearance(), event.hazeLightGain(), source,
                muzzle, localRadius, ExtraConfig.MUZZLE_HAZE_LOCAL_MAX.get(), softHaze);
            if (particle != null) mc.particleEngine.add(particle);
        }
    }
}
