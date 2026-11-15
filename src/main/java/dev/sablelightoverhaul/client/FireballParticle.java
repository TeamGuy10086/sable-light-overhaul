package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.lighting.FireballParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** One connected muzzle gas volume per tank or big-cannon shot. */
public final class FireballParticle extends TextureSheetParticle {
    private static SpriteSet sharedSprites;
    private final boolean big;
    private final FireballParticleData data;
    private final SpriteSet sprites;
    private final Vec3 axis;
    private final int epoch;
    private final double phase;
    private final double width,volumeWidth;
    private FireballParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,
            FireballParticleData data,SpriteSet sprites,boolean big) {
        super(level,x,y,z);
        ClientFlashes.prepareEffects(level);
        epoch=ClientFlashes.visualEpoch();
        this.data=data;
        this.big=big;
        this.sprites=sprites;
        axis=new Vec3(dx,dy,dz).normalize();
        phase=(big?32:0)+random.nextDouble()*8;
        width=Math.max(data.size()*0.5,data.length()*Math.tan(Math.toRadians(data.coneAngle()))*0.45);
        volumeWidth=Math.max(data.size()*0.5,data.length()*Math.tan(Math.toRadians(data.coneAngle())));
        lifetime=Math.max(1,!big&&MuzzleClientConfig.TANK_FLAME_TICKS.get()>0?MuzzleClientConfig.TANK_FLAME_TICKS.get():data.lifetime());
        hasPhysics=false;
        setParticleSpeed(0,0,0);
        pickSprite(sprites);
        if(big) MuzzleVisualProbe.bigBorn(); else MuzzleVisualProbe.born(true);
    }
    @Override public void tick() {
        if(!ClientFlashes.effectsEnabled()||epoch!=ClientFlashes.visualEpoch()) {
            if(!big) MuzzleVisualProbe.gated(); remove(); return;
        }
        super.tick();
        if(removed&&!big) MuzzleVisualProbe.expired();
    }
    @Override public ParticleRenderType getRenderType() { return MuzzleParticleLayer.TYPE; }
    private double geometryScale() {
        return overallScale()*(big?Math.max(MuzzleClientConfig.BIG_CORE_SCALE.get(),Math.max(MuzzleClientConfig.BIG_GAS_SCALE.get(),MuzzleClientConfig.BIG_JET_SCALE.get()))
            :Math.max(MuzzleClientConfig.TANK_CORE_SCALE.get(),Math.max(MuzzleClientConfig.TANK_GAS_SCALE.get(),MuzzleClientConfig.TANK_JET_SCALE.get())));
    }
    private double overallScale() { return big?MuzzleClientConfig.BIG_FLAME_SCALE.get():MuzzleClientConfig.TANK_FLAME_SCALE.get(); }
    public Vec3 sortingCentre() { return new Vec3(x,y,z).add(axis.scale(data.length()*geometryScale()*0.5)); }
    @Override public AABB getRenderBoundingBox(float partialTick) {
        Vec3 end=new Vec3(x,y,z).add(axis.scale(data.length()*geometryScale()));
        return new AABB(x,y,z,end.x,end.y,end.z).inflate(Math.max(width,volumeWidth)*geometryScale()*1.1);
    }
    @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer out,net.minecraft.client.Camera camera,float partialTick) {
        if(!ClientFlashes.effectsEnabled()||epoch!=ClientFlashes.visualEpoch()) return;
        if(big) MuzzleVisualProbe.bigDraw(); else MuzzleVisualProbe.draw(true);
        float t=Math.clamp((age+partialTick)/lifetime,0,1);
        double bloom=0.55+0.45*Math.min(1,t/0.22);
        float opacity=data.alpha()*(float)Math.pow(1-t,1.6);
        boolean baseline=MuzzleClientConfig.FLAME_RENDERER.get()==MuzzleClientConfig.FlameRenderer.BASELINE_MESH;
        setSprite(sprites.get(baseline?0:1,1));
        if(baseline) {
            FlameVolumeMesh.baseline(out,camera,new Vec3(x,y,z),axis,data.length()*bloom*overallScale(),width*bloom*overallScale(),
                opacity,phase,getU0(),getU1(),getV0(),getV1());
            return;
        }
        FlameVolumeMesh.gas(out,camera,new Vec3(x,y,z),axis,data.length()*geometryScale(),volumeWidth*geometryScale(),
            opacity,t,phase,true,data.detail(),(getU0()+getU1())*0.5f,(getV0()+getV1())*0.5f);
    }
    /** Same configured half-width as the gas proxy, used only to bridge the new smoke envelope. */
    static double bigWidth(double chargeScale) {
        return Math.max(.5,MuzzleClientConfig.BIG_FIREBALL_LENGTH.get()*chargeScale*Math.tan(Math.toRadians(MuzzleClientConfig.BIG_FIREBALL_ANGLE.get())))
            *MuzzleClientConfig.BIG_FLAME_SCALE.get()*Math.max(MuzzleClientConfig.BIG_CORE_SCALE.get(),Math.max(MuzzleClientConfig.BIG_GAS_SCALE.get(),MuzzleClientConfig.BIG_JET_SCALE.get()));
    }
    public static void bigMuzzle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,float charge,int blastLife,float sizeScale) {
        if(!MuzzleClientConfig.BIG_FIREBALL_ENABLED.get()||sharedSprites==null) return;
        int configured=MuzzleClientConfig.BIG_FIREBALL_LIFETIME.get();
        int life=configured==0?blastLife:configured;
        double chargeScale=dev.sablelightoverhaul.BigSmokeModel.chargeScale(charge,BigSmokeConfig.CHARGE_REFERENCE.get(),BigSmokeConfig.CHARGE_EXPONENT.get(),BigSmokeConfig.CHARGE_MAX.get());
        var data=new FireballParticleData(sizeScale,Math.max(1,life),MuzzleClientConfig.BIG_FIREBALL_ALPHA.get().floatValue(),
            (float)(MuzzleClientConfig.BIG_FIREBALL_LENGTH.get()*chargeScale*sizeScale),MuzzleClientConfig.BIG_FIREBALL_ANGLE.get().floatValue(),24);
        net.minecraft.client.Minecraft.getInstance().particleEngine.add(new FireballParticle(level,x,y,z,dx,dy,dz,data,sharedSprites,true));
    }
    public static final class Provider implements ParticleProvider<FireballParticleData> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites=sprites; sharedSprites=sprites; }
        @Override public Particle createParticle(FireballParticleData data,ClientLevel level,
                double x,double y,double z,double dx,double dy,double dz) {
            if(!dev.sablelightoverhaul.FlashStore.valid(x,y,z,dx,dy,dz,1)||
                    !Float.isFinite(data.length())||data.length()<=0||!Float.isFinite(data.size())||data.size()<=0||
                    !Float.isFinite(data.alpha())||!Float.isFinite(data.coneAngle())||
                    dx*dx+dy*dy+dz*dz<1e-12) return null;
            return new FireballParticle(level,x,y,z,dx,dy,dz,data,sprites,false);
        }
    }
}
