package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.lighting.PlumeEmitterParticleData;
import dev.sablelightoverhaul.lighting.PlumeSmokeParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.world.phys.Vec3;

/** A short release at the birth muzzle, never a moving emitter laying an endless smoke column. */
public final class PlumeEmitterParticle extends NoRenderParticle {
    private final Vec3 axis,side,up,origin;
    private final PlumeEmitterParticleData data;
    private SmokeTracker.Group group;
    private final int epoch;
    private final float initial,fadeFrom,fadeRate,smokeScale,smokeSize;
    private final dev.sablelightoverhaul.MuzzleGasModel.SmokeStyle style;
    private PlumeEmitterParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,
            PlumeEmitterParticleData data) {
        super(level,x,y,z);
        ClientFlashes.prepareEffects(level);
        epoch=ClientFlashes.visualEpoch();
        this.data=data;
        smokeScale=MuzzleClientConfig.TANK_SMOKE_SCALE.get().floatValue();
        smokeSize=MuzzleClientConfig.TANK_SMOKE_SIZE.get().floatValue();
        style=MuzzleClientConfig.TANK_SMOKE_STYLE.get();
        lifetime=Math.max(1,data.lifetime());
        axis=new Vec3(dx,dy,dz).normalize();
        side=axis.cross(Math.abs(axis.y)>0.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        up=axis.cross(side);
        origin=new Vec3(x,y,z);
        initial=ExtraConfig.TANK_PLUME_ALPHA.get().floatValue()*data.alpha();
        fadeFrom=ExtraConfig.TANK_PLUME_FADE_FROM.get().floatValue();
        fadeRate=ExtraConfig.TANK_PLUME_FADE_RATE.get().floatValue();
        setParticleSpeed(0,0,0);
    }
    /** Legacy accessor retained for callers; child opacity is now explicit in its data. */
    public static float activeAlpha() { return 1; }
    @Override public void tick() {
        if(!ClientFlashes.effectsEnabled()||epoch!=ClientFlashes.visualEpoch()||age>=lifetime) { remove(); return; }
        int releaseTick=age++;
        float t=(releaseTick+0.5f)/lifetime;
        int quality=switch(net.minecraft.client.Minecraft.getInstance().options.particles().get()) {
            case ALL -> 5; case DECREASED -> 3; case MINIMAL -> 1;
        };
        int count=dev.sablelightoverhaul.MuzzleGasModel.smokeChildren(Math.round(data.power()),lifetime,releaseTick,quality);
        if(count==0)return;
        // A new group is created only on the first actual emission, not while it is still empty.
        if(group==null) group=SmokeTracker.create(level.getGameTime());
        group.controlledTank=true;
        group.controlledExpansion=style==dev.sablelightoverhaul.MuzzleGasModel.SmokeStyle.COMPACT?data.expansion():0;
        group.controlledFadeFrom=fadeFrom;
        group.controlledFadeRate=fadeRate;
        // Tank smoke has its own lifetime; it must never bind a neighbouring big-cannon flash.
        group.token=0;
        float alpha=dev.sablelightoverhaul.MuzzleGasModel.smokeBirthAlpha(initial,t,fadeFrom,fadeRate,style);
        group.birthAlpha=alpha;
        var previous=SmokeTracker.enter(group);
        try {
            // One residual cloud per puff, shaped by cloud.forward/spread/growth. The former
            // radial burst was removed: the muzzle flash covers it completely.
            double forward=MuzzleClientConfig.TANK_CLOUD_FORWARD.get();
            double spread=MuzzleClientConfig.TANK_CLOUD_SPREAD.get();
            double axialSpread=Math.min(1.0,spread);
            for(int i=0;i<count;i++) {
                double axialJitter=1+(random.nextDouble()*2-1)*0.35*axialSpread;
                Vec3 at=origin.add(axis.scale(data.travel()*(0.15+0.45*t
                        +(random.nextDouble()-0.5)*0.12*axialSpread)))
                    .add(side.scale((random.nextDouble()-0.5)*data.size()*0.40))
                    .add(up.scale((random.nextDouble()-0.5)*data.size()*0.40));
                // Every puff gets its own lateral/vertical kick and its own axial speed; with
                // spread=0 it stays on the axis and moves as one stream.
                double lateral=data.size()*(0.06+0.24*spread);
                Vec3 velocity=axis.scale(dev.sablelightoverhaul.MuzzleGasModel.smokeSpeed(data.travel(),t)
                        *forward*axialJitter)
                    .add(side.scale((random.nextDouble()*2-1)*lateral))
                    .add(up.scale((random.nextDouble()*2-1)*lateral*0.75));
                var child=new PlumeSmokeParticleData(
                    dev.sablelightoverhaul.MuzzleGasModel.smokeHalfWidth(data.size(),smokeSize,smokeScale,0.85f+random.nextFloat()*0.3f),
                    dev.sablelightoverhaul.MuzzleGasModel.smokeLifetime(data.childLifetime(),style),alpha,
                    style==dev.sablelightoverhaul.MuzzleGasModel.SmokeStyle.NATIVE?1:fadeFrom,fadeRate,
                    style==dev.sablelightoverhaul.MuzzleGasModel.SmokeStyle.NATIVE?0:data.expansion());
                // Explicit world-space smoke, without the ClientLevel distance/renderer dispatch.
                var particle=net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(child,
                    at.x,at.y,at.z,velocity.x,velocity.y,velocity.z);
                if(particle!=null) MuzzleVisualProbe.smokeSpawn(origin,at);
            }
        } finally { SmokeTracker.enter(previous); }
    }
    public static final class Provider implements ParticleProvider<PlumeEmitterParticleData> {
        public Provider(SpriteSet sprites) {}
        @Override public Particle createParticle(PlumeEmitterParticleData data,ClientLevel level,
                double x,double y,double z,double dx,double dy,double dz) {
            if(!dev.sablelightoverhaul.FlashStore.valid(x,y,z,dx,dy,dz,1)||dx*dx+dy*dy+dz*dz<1e-12||
                !Float.isFinite(data.size())||data.size()<=0||!Float.isFinite(data.travel())||data.travel()<=0||
                !Float.isFinite(data.power())||!Float.isFinite(data.expansion())||!Float.isFinite(data.alpha()))return null;
            return new PlumeEmitterParticle(level,x,y,z,dx,dy,dz,data);
        }
    }
}
