package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.sablelightoverhaul.lighting.ShockwaveParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * One puff of a shockwave: a large, soft, fading quad, and the queue it lives in.
 *
 * <p>Two things separate this from simply pointing {@code explosions.shockwaveParticle} at a vanilla
 * particle, and both are the reason it exists:
 *
 * <ul>
 *   <li><b>{@link #RENDER_TYPE} is its own queue.</b> Vanilla gives every particle type a
 *       {@code ParticleRenderType}, and the queue is keyed by that object: each one holds 16384
 *       particles and evicts from its head when full. Create: Big Cannons' muzzle smoke shares
 *       {@code PARTICLE_SHEET_TRANSLUCENT} with vanilla's cloud puffs, so a wave drawn with cloud puffs
 *       competes with that smoke for room. This one asks for a separate queue, which is why a shockwave
 *       can no longer push smoke out -- structurally, not by budget.</li>
 *   <li><b>The size comes from the wave.</b> A puff that is part of a shell has to cover its share of it,
 *       so the useful size grows with the front: the caller passes {@code size} in (about a fifth of the
 *       current radius) and roughly sixty of these cover what several hundred one-block puffs did.</li>
 * </ul>
 *
 * <p>Blending is the same alpha blend a smoke puff uses: unlike the mesh band this is meant to look like
 * matter, not like light, so it should be able to darken what is behind it as well as brighten it.
 */
public final class ShockwaveParticle extends TextureSheetParticle {
    /**
     * The queue. Deliberately a separate object from {@code ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT}
     * -- sharing that instance would put these back in the layer this class exists to leave.
     */
    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        @Override public boolean isTranslucent() { return true; }
        @Override public String toString() { return "sablelightoverhaul:shockwave"; }
    };

    private final SpriteSet sprites;

    private ShockwaveParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              SpriteSet sprites, ShockwaveParticleData data) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.quadSize = Math.max(0.5F, data.size());
        this.lifetime = Math.max(2, data.lifetime());
        this.gravity = 0.0F;
        // Slower than smoke: the front's own speed already carries it outward, and what a wave does over
        // ten ticks is decelerate.
        this.friction = 0.94F;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        int c = data.color();
        this.rCol = ((c >> 16) & 0xFF) / 255.0F;
        this.gCol = ((c >> 8) & 0xFF) / 255.0F;
        this.bCol = (c & 0xFF) / 255.0F;
        this.setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        // Fade out over the second half of a short life: a wave is a front, not a cloud that lingers.
        float left = 1.0F - (float) this.age / Math.max(1, this.lifetime);
        this.alpha = Math.min(1.0F, Math.max(0.0F, left * 2.0F));
    }

    @Override public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }

    /** Registered from {@code ClientBootstrap}; the sprite set comes from {@code particles/shockwave.json}. */
    public static final class Provider implements ParticleProvider<ShockwaveParticleData> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        @Override public ShockwaveParticle createParticle(ShockwaveParticleData data, ClientLevel level,
                                                          double x, double y, double z,
                                                          double vx, double vy, double vz) {
            return new ShockwaveParticle(level, x, y, z, vx, vy, vz, sprites, data);
        }
    }
}
