package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.lighting.ParticleFade;
import dev.sablelightoverhaul.lighting.SmokeParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;

/**
 * A smoke vent's puff: the vanilla frames with this mod's own opacity and fade.
 *
 * <p>Two page fields drive it -- how thick the plume leaves the pipe and how much of its life it spends
 * thinning out (see {@link ParticleFade}) -- neither of which the vanilla particle it replaces could be
 * told. Everything else is deliberately vanilla-like: it rises because the vent gives it an upward
 * velocity and because of a small buoyancy here, it does not collide, and it is drawn on the vanilla
 * translucent particle sheet, which is the one render type this project has actually seen work under a
 * shader pack.
 *
 * <p>It shares that sheet with other mods' smoke, which is a deliberate choice: a vent emits a handful
 * of particles at a time, not the burst a cannon or a shockwave does, so the 16384-slot queue is not
 * something it can flood -- and unlike the shockwave's own queue, this render type is known to survive
 * Iris.
 */
public final class SmokeParticle extends WindborneParticle {
    /** Upward acceleration per tick: what turns a puff into a plume once the vent's own speed is spent. */
    private static final float BUOYANCY = 0.002F;

    private final SpriteSet sprites;
    private final float baseOpacity;
    private final float fade;

    private SmokeParticle(ClientLevel level, double x, double y, double z,
                          double vx, double vy, double vz, SpriteSet sprites, SmokeParticleData data) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.quadSize = Math.min(Math.max(data.size(), 0.05F), 16.0F);
        this.lifetime = Math.min(Math.max(data.lifetime(), 2), 1200);
        this.baseOpacity = Math.min(Math.max(data.opacity(), 0.02F), 1.0F);
        this.fade = Math.min(Math.max(data.fade(), 0.02F), 1.0F);
        // Vanilla's gravity stays off: a vent's smoke is carried by the air, and this class adds the
        // only vertical acceleration it gets. Air drag is what stops the jet's own speed.
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        int c = data.color();
        this.rCol = ((c >> 16) & 0xFF) / 255.0F;
        this.gCol = ((c >> 8) & 0xFF) / 255.0F;
        this.bCol = (c & 0xFF) / 255.0F;
        this.setAlpha(this.baseOpacity);
        this.setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        this.yd += BUOYANCY;
        float progress = this.lifetime <= 0 ? 1.0F : (float) this.age / this.lifetime;
        this.setAlpha(ParticleFade.alpha(progress, this.baseOpacity, this.fade));
        this.setSpriteFromAge(this.sprites);
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Registered from {@code ClientBootstrap}; the frames come from {@code particles/smoke.json}. */
    public static final class Provider implements ParticleProvider<SmokeParticleData> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        @Override public SmokeParticle createParticle(SmokeParticleData data, ClientLevel level,
                                                      double x, double y, double z,
                                                      double vx, double vy, double vz) {
            return new SmokeParticle(level, x, y, z, vx, vy, vz, sprites, data);
        }
    }
}
