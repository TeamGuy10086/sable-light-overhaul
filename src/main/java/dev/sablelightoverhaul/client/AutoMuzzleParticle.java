package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.FlameShape;
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

/** A short axial density volume, tessellated into view-aligned mesh slices. */
public final class AutoMuzzleParticle extends TextureSheetParticle {
    private final int epoch;
    private final SpriteSet sprites;
    private final FlameShape.Basis basis;
    private final double length;
    private final double halfWidth;
    private final double roll;
    private final float flameOpacity;
    private AutoMuzzleParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level,x,y,z);
        ClientFlashes.prepareEffects(level);
        epoch=ClientFlashes.visualEpoch();
        this.sprites=sprites;
        basis = FlameShape.basis(dx,dy,dz);
        double sizeScale = MuzzleClientConfig.AUTO_SIZE.get();
        length = (0.65 + random.nextDouble()*0.25)*sizeScale*MuzzleClientConfig.AUTO_LENGTH_SCALE.get();
        halfWidth = (0.075 + random.nextDouble()*0.035)*sizeScale*MuzzleClientConfig.AUTO_WIDTH_SCALE.get();
        roll = random.nextDouble()*8;
        lifetime = MuzzleClientConfig.AUTO_FLAME_DURATION.get();
        flameOpacity = MuzzleClientConfig.AUTO_OPACITY.get().floatValue();
        hasPhysics = false;
        setParticleSpeed(0,0,0);
        pickSprite(sprites);
        MuzzleVisualProbe.born(false);
    }
    @Override public void tick() {
        if(!ClientFlashes.effectsEnabled() || epoch!=ClientFlashes.visualEpoch()) { remove(); return; }
        super.tick();
    }
    @Override public ParticleRenderType getRenderType() { return MuzzleParticleLayer.TYPE; }
    @Override public int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }
    public net.minecraft.world.phys.Vec3 sortingCentre() {
        return new net.minecraft.world.phys.Vec3(x+basis.forward().x()*length*0.5,
                y+basis.forward().y()*length*0.5,z+basis.forward().z()*length*0.5);
    }
    @Override public AABB getRenderBoundingBox(float partialTick) {
        var end = basis.forward().scale(length*1.1);
        return new AABB(x,y,z,x+end.x(),y+end.y(),z+end.z()).inflate(halfWidth*2);
    }
    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if(!ClientFlashes.effectsEnabled() || epoch!=ClientFlashes.visualEpoch()) return;
        MuzzleVisualProbe.draw(false);
        float progress = Math.clamp((age+partialTick)/lifetime,0,1);
        float opacity = flameOpacity*(1-progress*progress);
        if (opacity < 0.015f) return;
        double stretch = length*(0.85+0.25*progress);
        double width = halfWidth*(1.0-0.25*progress);
        boolean baseline=MuzzleClientConfig.FLAME_RENDERER.get()==MuzzleClientConfig.FlameRenderer.BASELINE_MESH;
        setSprite(sprites.get(baseline?0:1,1));
        if(baseline) {
            FlameVolumeMesh.baseline(buffer,camera,new net.minecraft.world.phys.Vec3(x,y,z),
                new net.minecraft.world.phys.Vec3(basis.forward().x(),basis.forward().y(),basis.forward().z()),
                stretch,width,opacity,roll,getU0(),getU1(),getV0(),getV1());
            return;
        }
        FlameVolumeMesh.gas(buffer,camera,new net.minecraft.world.phys.Vec3(x,y,z),
                new net.minecraft.world.phys.Vec3(basis.forward().x(),basis.forward().y(),basis.forward().z()),
                length,halfWidth,opacity,progress,roll,false,12,(getU0()+getU1())*0.5f,(getV0()+getV1())*0.5f);
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double dx, double dy, double dz) {
            if (!dev.sablelightoverhaul.FlashStore.valid(x,y,z,dx,dy,dz,1)) return null;
            return new AutoMuzzleParticle(level,x,y,z,dx,dy,dz,sprites);
        }
    }
}
