package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.api.ClientMissileEffectsApi;
import dev.sablelightoverhaul.api.ClientMissileTrailApi.FrameEndpoint;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/** Small burning missile wreckage with its own flight and continuous smoke emitter. */
final class BurningMissileFragment extends TextureSheetParticle {
    private static final double GRAVITY = 9.8 / (20 * 20);
    private final int epoch = ClientFlashes.visualEpoch();
    private final Trail trail;
    private static final double MAX_TRAVEL = 30;
    private final boolean localLight;
    private double distance;

    static final class Trail {
        final double width;
        private long sequence;
        private Vec3 previous, current;

        Trail(Vec3 initial, double width) {
            this.width = width;
            previous = current = initial;
        }

        FrameEndpoint endpoint(float partial) {
            return new FrameEndpoint(sequence, Math.max(0, sequence - 1 + partial),
                previous.lerp(current, partial), true);
        }
    }

    BurningMissileFragment(ClientLevel level, Vec3 origin, Vec3 motion, float size, int color, int ticks,
                           boolean physical, boolean smoking, SpriteSet sprites) {
        super(level, origin.x, origin.y, origin.z);
        setParticleSpeed(motion.x, motion.y, motion.z);
        xo = x;
        yo = y;
        zo = z;
        quadSize = size * .5f; // SingleQuadParticle uses a half-width.
        setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f);
        setSprite(sprites.get(random));
        lifetime = ticks;
        hasPhysics = localLight = physical;
        trail = smoking ? new Trail(origin, Math.max(.40, size * 1.08)) : null;
        roll = oRoll = random.nextFloat() * (float)(2 * Math.PI);
        if (trail != null) MissileSmokeMesh.sample(level, trail, 0, origin, true);
    }

    @Override public void tick() {
        if (epoch != ClientFlashes.visualEpoch() || !ClientFlashes.effectsEnabled() || age >= lifetime || distance >= MAX_TRAVEL - 1e-6) {
            remove();
            return;
        }
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        roll += .32f;
        yd -= GRAVITY;
        double step = Math.sqrt(xd * xd + yd * yd + zd * zd);
        double remaining = MAX_TRAVEL - distance;
        double fraction = step > remaining ? remaining / step : 1;
        move(xd * fraction, yd * fraction, zd * fraction);
        var center = new Vec3(x, y, z);
        double covered = center.distanceToSqr(xo, yo, zo);
        distance += Math.sqrt(covered);
        age++;
        if (trail != null) {
            trail.previous = trail.current;
            trail.current = center;
            trail.sequence = age;
            MissileSmokeMesh.sample(level, trail, age, center, true);
        }
        if (hasPhysics && covered < step * step * fraction * fraction * .55) remove();
    }

    @Override public void remove() {
        if (trail != null) MissileSmokeMesh.stop(level, trail);
        super.remove();
    }

    @Override public ParticleRenderType getRenderType() {
        return MuzzleParticleLayer.ribbonReady() ? MuzzleParticleLayer.TYPE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override protected int getLightColor(float partial) {
        return trail != null ? LightTexture.FULL_BRIGHT : localLight ? super.getLightColor(partial) : LightTexture.pack(4, 15);
    }

    @Override public void render(VertexConsumer out, Camera camera, float partial) {
        if (epoch != ClientFlashes.visualEpoch() || !ClientFlashes.effectsEnabled()) return;
        super.render(out, camera, partial);
        if (trail == null || !MuzzleParticleLayer.ribbonReady()) return;
        Vec3 head = new Vec3(xo, yo, zo).lerp(new Vec3(x, y, z), partial);
        var back = new Vec3(xo - x, yo - y, zo - z).normalize();
        if (back.lengthSqr() < .5) back = new Vec3(0, 1, 0);
        double flameLength = Math.min(.65, .14 + quadSize * 1.8);
        double flameWidth = Math.min(.22, .05 + quadSize * .54);
        ClientMissileEffectsApi.exhaust(out, camera, head, back, flameLength,
            flameWidth, (age + partial) / 20.0, quadSize);
    }
}
