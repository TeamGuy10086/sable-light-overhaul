package dev.sablelightoverhaul.api;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Client visuals only: never creates a light, smoke, trail, or gameplay projectile. */
public final class ClientMissileEffectsApi {
    public static final int VERSION=2;
    /** forward is the missile nose direction in world coordinates, not the exhaust direction. */
    public record Glow(Vec3 position,int rgb,float brightness,float radius,Vec3 forward) {
        /** Compatibility for omnidirectional providers; missiles should supply forward. */
        public Glow(Vec3 position,int rgb,float brightness,float radius) {this(position,rgb,brightness,radius,null);}
    }
    @FunctionalInterface public interface Provider {void collect(ClientLevel level,Consumer<Glow> sink);}
    private static final List<Provider> PROVIDERS=new CopyOnWriteArrayList<>();
    private static final ResourceLocation BASELINE_SPRITE = ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", "auto_muzzle");
    private static final ResourceLocation GAS_SPRITE = ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", "volume_white");
    private ClientMissileEffectsApi() {}
    /** Register once during client initialization; providers must scope results to the supplied level.
     * Advanced uses 128 flare slots, with command/shell flares taking priority; requires updated shader patch.
     * Emit important glows first. This API does not promise 512 visible glows in every backend.
     */
    public static void register(Provider provider){if(provider==null)throw new NullPointerException("provider"); if(!PROVIDERS.contains(provider))PROVIDERS.add(provider);}
    public static void unregister(Provider provider){PROVIDERS.remove(provider);}
    public static List<Glow> collect(ClientLevel level) {
        if(level==null||!dev.sablelightoverhaul.client.ClientFlashes.effectsEnabled()||PROVIDERS.isEmpty())return List.of();var result=new ArrayList<Glow>();
        var eye=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for(var p:PROVIDERS)p.collect(level,g->{if(result.size()<512&&g!=null&&g.position()!=null
                &&Double.isFinite(g.position().x)&&Double.isFinite(g.position().y)&&Double.isFinite(g.position().z)
                &&Float.isFinite(g.brightness())&&Float.isFinite(g.radius())&&g.brightness()>0&&g.radius()>0
                &&(g.forward()==null||dev.sablelightoverhaul.MissileGlowPolicy.visible(g.forward().x,g.forward().y,g.forward().z,
                    eye.x-g.position().x,eye.y-g.position().y,eye.z-g.position().z)))result.add(g);});
        return result;
    }
    public static ParticleRenderType flameLayer(){return dev.sablelightoverhaul.client.MuzzleParticleLayer.TYPE;}
    /** Caller supplies the same translucent particle layer as the autocannon muzzle flame. */
    public static void exhaust(VertexConsumer out,Camera camera,Vec3 nozzle,Vec3 backwards,double length,double width,double time,double seed) {
        if(!dev.sablelightoverhaul.client.ClientFlashes.effectsEnabled())return;
        // ParticleEngine owns this atlas and registers it with TextureManager, not ModelManager.
        // Resolve both atlas and sprite on each draw: resource reload can replace stitched UVs.
        var atlas=(TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        var config=dev.sablelightoverhaul.client.MuzzleClientConfig.FLAME_RENDERER.get();
        if(config==dev.sablelightoverhaul.client.MuzzleClientConfig.FlameRenderer.BASELINE_MESH) {
            var sprite=atlas.getSprite(BASELINE_SPRITE);
            dev.sablelightoverhaul.client.FlameVolumeMesh.baseline(out,camera,nozzle,backwards,length,width,.82f,seed,
                    sprite.getU0(),sprite.getU1(),sprite.getV0(),sprite.getV1());
        } else {
            var sprite=atlas.getSprite(GAS_SPRITE);
            dev.sablelightoverhaul.client.FlameVolumeMesh.gas(out,camera,nozzle,backwards,length,width,.82f,time,seed,false,12,
                    (sprite.getU0()+sprite.getU1())*.5f,(sprite.getV0()+sprite.getV1())*.5f);
        }
    }
}
