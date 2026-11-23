package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.BlastPreviewPayload;
import dev.sablelightoverhaul.api.ClientMissileEffectsApi;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Visual lead-in for the blast debugger. The server owns placement and detonation. */
final class DebugMissileApproach extends Particle {
    private final Vec3 start, end, backward;
    private final Object trail = new Object();
    private final int epoch = ClientFlashes.visualEpoch();

    private DebugMissileApproach(ClientLevel level, Vec3 end, Vec3 motion) {
        super(level, end.x, end.y, end.z);
        this.end = end;
        lifetime = BlastPreviewPayload.Approach.TICKS;
        start = end.subtract(motion.scale(lifetime));
        backward = motion.normalize().reverse();
        hasPhysics = false;
        setBoundingBox(new AABB(start, end).inflate(2));
    }

    static void receive(BlastPreviewPayload.Approach payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(payload.dimension())
            || !MissileSmokeMesh.available(mc.level) || !Double.isFinite(payload.end().lengthSqr())
            || !Double.isFinite(payload.motion().lengthSqr()) || payload.motion().lengthSqr() < .01) return;
        var approach = new DebugMissileApproach(mc.level, payload.end(), payload.motion());
        mc.particleEngine.add(approach);
        MissileSmokeMesh.sample(mc.level, approach.trail, 0, approach.start, true);
    }

    @Override public ParticleRenderType getRenderType() { return MuzzleParticleLayer.TYPE; }

    @Override public void tick() {
        if (epoch != ClientFlashes.visualEpoch() || !ClientFlashes.effectsEnabled()) {
            remove();
            return;
        }
        int tick = ++age;
        MissileSmokeMesh.sample(level, trail, tick, start.lerp(end, Math.min(1, (double)tick / lifetime)), true);
        if (age >= lifetime) {
            MissileSmokeMesh.stop(level, trail);
            remove();
        }
    }

    @Override public void render(VertexConsumer out, Camera camera, float partial) {
        Vec3 head = start.lerp(end, Math.clamp((age + partial) / lifetime, 0, 1));
        ClientMissileEffectsApi.exhaust(out, camera, head, backward, 2.7, .38, (age + partial) / 20.0, .3);
    }
}
