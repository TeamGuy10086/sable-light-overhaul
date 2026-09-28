package dev.cbcmuzzlelight.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cbcmuzzlelight.FlameShape;
import dev.cbcmuzzlelight.FlameShape.Vector;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.AABB;

/** Three tapered ribbons point down the bore; no camera-facing spherical flash sprite. */
public final class AutoMuzzleParticle extends TextureSheetParticle {
    private final int epoch=ClientFlashes.visualEpoch();
    private final FlameShape.Basis basis;
    private final double length;
    private final double halfWidth;
    private final double roll;
    private final float flameOpacity;
    private AutoMuzzleParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level,x,y,z);
        basis = FlameShape.basis(dx,dy,dz);
        double sizeScale = MuzzleClientConfig.AUTO_SIZE.get();
        length = (0.65 + random.nextDouble()*0.25)*sizeScale*MuzzleClientConfig.AUTO_LENGTH_SCALE.get();
        halfWidth = (0.075 + random.nextDouble()*0.035)*sizeScale*MuzzleClientConfig.AUTO_WIDTH_SCALE.get();
        roll = random.nextDouble()*Math.PI;
        lifetime = MuzzleClientConfig.AUTO_FLAME_DURATION.get();
        flameOpacity = MuzzleClientConfig.AUTO_OPACITY.get().floatValue();
        hasPhysics = false;
        setParticleSpeed(0,0,0);
        pickSprite(sprites);
    }
    @Override public void tick() {
        if(!ClientFlashes.effectsEnabled() || epoch!=ClientFlashes.visualEpoch()) { remove(); return; }
        super.tick();
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }
    @Override public AABB getRenderBoundingBox(float partialTick) {
        var end = basis.forward().scale(length*1.1);
        return new AABB(x,y,z,x+end.x(),y+end.y(),z+end.z()).inflate(halfWidth*2);
    }
    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if(!ClientFlashes.effectsEnabled() || epoch!=ClientFlashes.visualEpoch()) return;
        float progress = Math.clamp((age+partialTick)/lifetime,0,1);
        float opacity = flameOpacity*(1-progress*progress);
        if (opacity < 0.015f) return;
        var cameraPos = camera.getPosition();
        var origin = new Vector(x-cameraPos.x,y-cameraPos.y,z-cameraPos.z);
        double stretch = length*(0.85+0.25*progress);
        double width = halfWidth*(1.0-0.25*progress);
        for (int plane = 0; plane < 3; plane++) {
            double angle = roll + plane*Math.PI/3;
            var side = basis.right().scale(Math.cos(angle)).add(basis.up().scale(Math.sin(angle)));
            drawRibbon(buffer,origin,basis.forward(),side,stretch,width,opacity);
        }
    }
    private void drawRibbon(VertexConsumer buffer, Vector origin, Vector forward, Vector side,
                            double length, double width, float alpha) {
        Vector a = origin.add(side.scale(-width));
        Vector b = origin.add(forward.scale(length)).add(side.scale(-width));
        Vector c = origin.add(forward.scale(length)).add(side.scale(width));
        Vector d = origin.add(side.scale(width));
        // Select the front-facing winding instead of emitting the same translucent plane twice.
        if (forward.cross(side).dot(origin.scale(-1)) >= 0) {
            vertex(buffer,a,getU0(),getV1(),alpha); vertex(buffer,b,getU1(),getV1(),alpha);
            vertex(buffer,c,getU1(),getV0(),alpha); vertex(buffer,d,getU0(),getV0(),alpha);
        } else {
            vertex(buffer,d,getU0(),getV0(),alpha); vertex(buffer,c,getU1(),getV0(),alpha);
            vertex(buffer,b,getU1(),getV1(),alpha); vertex(buffer,a,getU0(),getV1(),alpha);
        }
    }
    private void vertex(VertexConsumer buffer, Vector p, float u, float v, float alpha) {
        buffer.addVertex((float)p.x(),(float)p.y(),(float)p.z()).setUv(u,v)
                .setColor(1f,1f,1f,alpha).setLight(LightTexture.FULL_BRIGHT);
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double dx, double dy, double dz) {
            if (!dev.cbcmuzzlelight.FlashStore.valid(x,y,z,dx,dy,dz,1)) return null;
            return new AutoMuzzleParticle(level,x,y,z,dx,dy,dz,sprites);
        }
    }
}
