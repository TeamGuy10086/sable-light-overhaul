package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.GasVertexData;
import dev.sablelightoverhaul.BlastVisualScale;
import java.util.ArrayDeque;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Subdivide long trails so they interleave with the smoke puffs in the shared translucent sort. */
public final class ExplosionTrailMesh {
    private static final int STEPS = 8;
    private static final int MAX_SEGMENTS = 2048;
    private static final ArrayDeque<Segment> segments = new ArrayDeque<>();
    private static ClientLevel owner;
    private static long prunedAt;

    private ExplosionTrailMesh() {}

    static void clear() {
        segments.forEach(Particle::remove);
        segments.clear();
        owner = null;
        prunedAt = Long.MIN_VALUE;
    }

    public static boolean available() {
        return ExtraConfig.EXPLOSION_MESH_TRAILS.get() && MissileSmokeMesh.available(Minecraft.getInstance().level);
    }

    public static void spawn(Vec3 origin, float power, boolean plume, int count) {
        if (available()) spawn(origin, power, plume, count, false, 1, 1, 42, .8f);
    }

    static void spawnImpact(Vec3 origin, float power, boolean ground, int count, Vec3 opening) {
        float opacity = ImpactExplosionConfig.trailOpacity.get().floatValue();
        if (count <= 0 || opacity <= 0) return;
        double reach = ImpactExplosionConfig.trailReach.get() * (ground ? 1.35 : 1);
        spawn(origin, power, ground, count, true, reach,
            ImpactExplosionConfig.trailWidth.get(), ImpactExplosionConfig.trailTicks.get(), opacity * .72f,
            opening, 0x6B665E);
    }

    private static void spawn(Vec3 origin, float power, boolean plume, int count, boolean impact,
                              double reach, double width, int life, float opacity) {
        spawn(origin, power, plume, count, impact, reach, width, life, opacity, Vec3.ZERO, 0x6B665E);
    }

    private static void spawn(Vec3 origin, float power, boolean plume, int count, boolean impact,
                              double reach, double width, int life, float opacity, Vec3 jet, int color) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (!MissileSmokeMesh.available(level) || !Float.isFinite(power)) return;
        double dimensionFactor = impact ? BlastVisualScale.dimensions(power) : 1;
        power = Math.clamp(power, .1f, 32);
        int light = LightTexture.pack(0, 15);
        BlockPos pos = BlockPos.containing(origin);
        if (level.hasChunkAt(pos)) light = LevelRenderer.getLightColor(level, pos);
        var random = level.random;
        boolean groundFan = impact && plume;
        int steps = groundFan ? 2 : STEPS;
        double interval = (impact ? 3.5 : 5.0) / steps;
        double radius = Math.clamp(power * .075, .12, .65) * width * dimensionFactor;
        int branches = impact ? count : Math.min(count, 24);
        for (int i = 0; i < branches; i++) {
            Vec3 velocity;
            Vec3 start = origin;
            Vec3 bend = Vec3.ZERO;
            double stagger = 0;
            if (impact) {
                double yaw = (i + random.nextDouble()) * Math.PI * 2 / count;
                Vec3 radial = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
                double scale = Math.sqrt(power / 3.0) * dimensionFactor;
                radial = ImpactExplosionSmoke.groundSpread(yaw, jet);
                start = origin.add(radial.scale(scale * (1.05 + .30 * random.nextDouble())));
                velocity = radial.scale(scale * reach * (.96 + .40 * random.nextDouble()))
                    .add(0, scale * (.12 + .12 * random.nextDouble()), 0);
                bend = new Vec3(0, scale * (groundFan ? .14 : .58), 0);
                stagger = .5 + random.nextDouble();
            } else {
                velocity = new Vec3(random.nextDouble() - random.nextDouble(),
                    random.nextDouble() - random.nextDouble() + (plume ? 1.7 : .25),
                    random.nextDouble() - random.nextDouble()).scale(power * .35);
            }
            Vec3 previous = start;
            float seed = random.nextFloat() * 30;
            for (int step = 0; step < steps; step++) {
                double t = (step + 1) * interval;
                double progress = (step + 1.0) / steps;
                Vec3 next = start.add(velocity.scale((1 - Math.pow(.85, t)) / .15))
                    .add(bend.scale(progress * progress))
                    .add(0, impact ? 0 : -.06 * t * t, 0);
                boolean clipped = false;
                if (level.hasChunkAt(BlockPos.containing(previous)) && level.hasChunkAt(BlockPos.containing(next))) {
                    var hit = level.clip(new ClipContext(previous, next, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, mc.player));
                    clipped = hit.getType() != HitResult.Type.MISS;
                    if (clipped) next = hit.getLocation();
                }
                float textureStep = .65f * STEPS / steps;
                var segment = new Segment(level, previous, next, stagger + step * interval, interval, life, radius,
                    opacity, seed + step * textureStep, seed + (step + 1) * textureStep, light, color,
                    step == 0, clipped || step == steps - 1, groundFan);
                add(mc, segment);
                if (clipped) break;
                previous = next;
            }
        }
    }

    static void addDebrisWake(Vec3 a, Vec3 b, float size, int life, float opacity, float u, float v, int light) {
        var mc = Minecraft.getInstance();
        if (!MissileSmokeMesh.available(mc.level)) return;
        double width = debrisWakeWidth(size);
        float textureScale = (float)(1.4 / Math.max(.28, width));
        add(mc, new Segment(mc.level, a, b, 0, 1, life, width,
            opacity, u * textureScale, v * textureScale, light, 0x6B665E, false, false, false));
    }

    static double debrisWakeWidth(float size) {
        return Math.max(.08, size * 1.15);
    }

    private static void add(Minecraft mc, Segment segment) {
        if (segment.a.distanceToSqr(segment.b) < 1e-6) return;
        if (owner != mc.level) {
            segments.clear();
            owner = mc.level;
            prunedAt = Long.MIN_VALUE;
        }
        long tick = owner.getGameTime();
        if (prunedAt != tick) {
            segments.removeIf(particle -> !particle.isAlive() || particle.epoch != segment.epoch);
            prunedAt = tick;
        }
        if (segments.size() >= MAX_SEGMENTS) segments.removeFirst().remove();
        segments.addLast(segment);
        mc.particleEngine.add(segment);
    }

    private static final class Segment extends Particle {
        private final Vec3 a, b;
        private final double born, delay, travel, width;
        private final int epoch = ClientFlashes.visualEpoch();
        private final int light, color;
        private final float opacity, u, v;
        private final boolean root, tip, groundFan;
        private final AABB launchBounds;
        private Vec3 previousWind = Vec3.ZERO;
        private Vec3 wind = Vec3.ZERO;

        Segment(ClientLevel level, Vec3 a, Vec3 b, double delay, double travel, int life, double width,
                float opacity, float u, float v, int light, int color, boolean root, boolean tip, boolean groundFan) {
            super(level, (a.x + b.x) * .5, (a.y + b.y) * .5, (a.z + b.z) * .5);
            this.a = a;
            this.b = b;
            this.delay = delay;
            this.travel = travel;
            born = level.getGameTime();
            this.width = width;
            lifetime = life;
            this.opacity = opacity;
            this.u = u;
            this.v = v;
            this.light = GasVertexData.ribbonLight(light);
            this.color = color;
            this.root = root;
            this.tip = tip;
            this.groundFan = groundFan;
            hasPhysics = false;
            xo = x;
            yo = y;
            zo = z;
            launchBounds = new AABB(a, b).inflate(width * (groundFan ? 5 : 2));
            setBoundingBox(launchBounds);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return MuzzleParticleLayer.TYPE;
        }

        @Override
        public void tick() {
            if (epoch != ClientFlashes.visualEpoch() || !ClientFlashes.effectsEnabled()
                || level.getGameTime() >= born + delay + travel + lifetime) {
                remove();
                return;
            }
            previousWind = wind;
            var gust = ParticleWind.step();
            if (gust.x() == 0 && gust.y() == 0 && gust.z() == 0) {
                xo = x; yo = y; zo = z;
                return;
            }
            wind = wind.add(gust.x(), gust.y(), gust.z());
            xo = x; yo = y; zo = z;
            setPos(x + gust.x(), y + gust.y(), z + gust.z());
            setBoundingBox(launchBounds.move(wind));
        }

        @Override
        public void render(VertexConsumer out, Camera camera, float partial) {
            if (!MuzzleParticleLayer.ribbonReady()) return;
            double elapsed = level.getGameTime() - born - delay + (Minecraft.getInstance().isPaused() ? 0 : partial);
            if (elapsed <= 0) return;
            if (SmokeResolution.capture(this)) return;
            double fill = Math.min(1, elapsed / travel);
            Vec3 drift = previousWind.lerp(wind, partial);
            Vec3 start = a.add(drift);
            Vec3 end = a.lerp(b, fill).add(drift);
            Vec3 eye = camera.getPosition();
            Vec3 axis = b.subtract(a);
            Vec3 side = groundFan ? new Vec3(-axis.z, 0, axis.x).normalize()
                : axis.cross(eye.subtract(start)).normalize();
            if (side.lengthSqr() < .5) side = new Vec3(camera.getLeftVector());
            double oldest = Math.clamp(elapsed / lifetime, 0, 1);
            double newest = Math.clamp((elapsed - travel) / lifetime, 0, 1);
            double wa = width * (1 + .7 * oldest);
            double wb = width * (1 + .7 * newest);
            if (groundFan) {
                double from = root ? .35 : 1.65;
                double to = tip ? 2.7 : 1.65;
                wa *= from;
                wb *= from + (to - from) * fill;
            }
            float aa = root ? 0 : (float)(opacity * Math.pow(1 - oldest, 1.4));
            float ab = tip || fill < 1 ? 0 : (float)(opacity * Math.pow(1 - newest, 1.4));
            if (aa + ab < .004) return;
            float endU = (float)(u + (v - u) * fill);
            vertex(out, start, side, wa, 1, aa, u, eye);
            vertex(out, end, side, wb, 1, ab, endU, eye);
            vertex(out, end, side, wb, -1, ab, endU, eye);
            vertex(out, start, side, wa, -1, aa, u, eye);
        }

        private void vertex(VertexConsumer out, Vec3 center, Vec3 side, double width, int across,
                            float alpha, float u, Vec3 eye) {
            Vec3 p = center.add(side.scale(width * across)).subtract(eye);
            out.addVertex((float)p.x, (float)p.y, (float)p.z).setUv(u, across)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (int)(alpha * 255)).setLight(light);
        }
    }
}
