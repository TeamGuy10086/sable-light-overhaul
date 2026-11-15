package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.MuzzleGasModel;
import dev.sablelightoverhaul.lighting.PlumeSmokeParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;

/** World-space cooling smoke: continuous soft mask, with vanilla cloud sprites as a pack fallback. */
public final class PlumeSmokeParticle extends WindborneParticle implements TrackedSmoke {
    private final SpriteSet sprites;
    private final PlumeSmokeParticleData data;
    private final SmokeTracker.Group group;
    private final float birthSize,spin;
    private final int epoch,maskSeed;
    private final boolean nativeStyle;
    /** Per-puff anisotropy: two puffs from one emitter no longer grow as the same circle. */
    private final float shapeBias,growth;
    /** Fast launch then fast braking: v = v0 / (1 + brake*age*3), not a constant velocity. */
    private final double vx0,vy0,vz0,brake;
    private final float fadeStart,fadeRate,fadeCurve;
    private PlumeSmokeParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,
            PlumeSmokeParticleData data,SpriteSet sprites) {
        super(level,x,y,z);
        ClientFlashes.prepareEffects(level);
        epoch=ClientFlashes.visualEpoch();
        this.data=data;this.sprites=sprites;
        group=SmokeTracker.current();
        nativeStyle=data.expansion()<=0;
        birthSize=Math.max(.05f,data.size());
        int customLifetime=MuzzleClientConfig.TANK_CLOUD_LIFETIME.get();
        int baseLifetime=customLifetime>0?customLifetime:data.lifetime();
        lifetime=MuzzleGasModel.tankLifetime(baseLifetime,
            MuzzleClientConfig.TANK_CLOUD_LIFETIME_JITTER.get().floatValue(),random.nextFloat());
        hasPhysics=false;gravity=0;
        setParticleSpeed(dx,dy,dz);
        vx0=dx;vy0=dy;vz0=dz;
        brake=MuzzleClientConfig.TANK_CLOUD_BRAKE.get();
        fadeStart=MuzzleClientConfig.TANK_CLOUD_FADE_START.get().floatValue();
        fadeRate=MuzzleClientConfig.TANK_CLOUD_FADE_RATE.get().floatValue();
        fadeCurve=MuzzleClientConfig.TANK_CLOUD_FADE_CURVE.get().floatValue();
        shapeBias=random.nextFloat()*2-1;
        growth=MuzzleClientConfig.TANK_CLOUD_GROWTH.get().floatValue();
        spin=(random.nextFloat()-.5f)*.09f;
        maskSeed=random.nextInt(64);
        roll=random.nextFloat()*Mth.TWO_PI;oRoll=roll;
        alpha=data.alpha();
        quadSize=getQuadSize(0);
        setColor(.75f,.70f,.65f);
        setSpriteFromAge(sprites);
        if(group!=null)group.capture(this);
        MuzzleVisualProbe.ownedTankSmoke();
    }
    @Override public void tick() {
        xo=x;yo=y;zo=z;oRoll=roll;
        if(!ClientFlashes.effectsEnabled()||epoch!=ClientFlashes.visualEpoch()||age++>=lifetime) {
            remove();MuzzleVisualProbe.smokeTick(false,age,lifetime,alpha);return;
        }
        float progress=(float)age/lifetime;
        quadSize=getQuadSize(0);roll+=spin;
        // A short warm grey onset cools into neutral smoke rather than another flame cloud.
        float cool=Mth.clamp(progress*2,0,1);
        setColor(Mth.lerp(cool,.75f,.46f),Mth.lerp(cool,.70f,.46f),Mth.lerp(cool,.65f,.46f));
        alpha=MuzzleGasModel.tankFadeAlpha(data.alpha(),progress,fadeStart,fadeRate,fadeCurve);
        setSpriteFromAge(sprites);
        // Fast launch, fast braking. A fixed per-tick damping either stopped the cloud at the
        // muzzle or kept it moving at nearly constant speed; this envelope has its largest change
        // in the first few ticks and then leaves the cloud nearly stationary.
        double envelope=1.0/(1.0+brake*age*3.0);
        xd=vx0*envelope;yd=vy0*envelope+0.0008;zd=vz0*envelope;
        move(xd,yd,zd);
        MuzzleVisualProbe.smokeTick(true,age,lifetime,alpha);
    }
    @Override public float getQuadSize(float partialTick) {
        float progress=(age+partialTick)/Math.max(1,lifetime);
        return birthSize*(nativeStyle?MuzzleGasModel.nativeSmokeGrowth(progress):MuzzleGasModel.smokeGrowth(progress,data.expansion()));
    }
    @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer out,net.minecraft.client.Camera camera,float partialTick) {
        // Cached mode retains a soft three-lobe field and scene lighting on one quad.
        // The legacy procedural path remains available for visual comparison.
        float size=getQuadSize(partialTick);
        boolean soft=MuzzleParticleLayer.proceduralSmoke();
        float rollNow=Mth.lerp(partialTick,oRoll,roll);
        if (soft) {
            float progress=(age+partialTick)/Math.max(1,lifetime);
            float anisotropy=growth*shapeBias*progress;
            float sizeX=size*(1+anisotropy*0.45f);
            float sizeY=size*(1-anisotropy*0.30f);
            if(MuzzleClientConfig.TANK_SMOKE_CACHED.get() && MuzzleParticleLayer.mode()!=MuzzleParticleLayer.Mode.SINGLE)
                MuzzleSmokeMesh.drawCachedPuff(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX,sizeY,rollNow,maskSeed,
                    rCol,gCol,bCol,alpha,getLightColor(partialTick));
            else MuzzleSmokeMesh.drawPuffShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX,sizeY,rollNow,maskSeed,
                rCol,gCol,bCol,alpha,getLightColor(partialTick));
        } else {
            MuzzleSmokeMesh.draw(out,camera,x,y,z,xo,yo,zo,partialTick,size,rollNow,
                getU0(),getU1(),getV0(),getV1(),rCol,gCol,bCol,alpha,getLightColor(partialTick));
        }
        MuzzleVisualProbe.smokeDraw(alpha,size);
        MuzzleVisualProbe.smokePose(x,y,z,camera.getPosition(),soft?"slo:soft_smoke":sprite.contents().name().toString(),
            maskSeed*4-1,maskSeed*4+1,1,-1);
    }
    @Override public ParticleRenderType getRenderType() { return MuzzleParticleLayer.TYPE; }
    @Override public int getLightColor(float partialTick) {
        int packed=super.getLightColor(partialTick);
        float hot=Math.max(0,1-(age+partialTick)/Math.max(1,lifetime)*1.5f);
        int block=Math.min(240,(packed&255)+(int)(hot*hot*hot*hot*240));
        return MuzzleParticleLayer.proceduralSmoke()?dev.sablelightoverhaul.GasVertexData.smokeLight((packed&0xFFFF0000)|block)
            :MuzzleParticleLayer.markLight((packed&0xFFFF0000)|block,false);
    }
    public double slo$x(){return x;} public double slo$y(){return y;} public double slo$z(){return z;}
    public double slo$px(){return xo;} public double slo$py(){return yo;} public double slo$pz(){return zo;}
    public double slo$size(){return getQuadSize(0);} public boolean slo$alive(){return !removed;}
    public double slo$heat(){return group==null?0:Math.max(0,1-age/(lifetime*group.hotFraction));}
    public boolean slo$muzzleManaged(){return true;} public boolean slo$tankControlled(){return true;}
    public static final class Provider implements ParticleProvider<PlumeSmokeParticleData> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites){this.sprites=sprites;BigSmokeParticle.sprites(sprites);TankAfterSmokeClient.sprites(sprites);GroundDustFieldClient.sprites(sprites);}
        public Particle createParticle(PlumeSmokeParticleData data,ClientLevel level,double x,double y,double z,double dx,double dy,double dz){
            if(!dev.sablelightoverhaul.FlashStore.valid(x,y,z,dx,dy,dz,2)||!Float.isFinite(data.size())||data.size()<=0)return null;
            return new PlumeSmokeParticle(level,x,y,z,dx,dy,dz,data,sprites);
        }
    }
}
