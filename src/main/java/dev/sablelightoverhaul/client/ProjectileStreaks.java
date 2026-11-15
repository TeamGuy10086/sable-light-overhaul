package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.api.EffectApi;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One bounded, non-drifting trajectory per projectile, in world coordinates. No smoke sampling. */
public final class ProjectileStreaks {
    private static final int PREDICTION_TICKS=2;
    private static final int NETWORK_HOLD_TICKS=2;
    private static final int HISTORY_LIMIT=10;
    private static final double MAX_PREDICTION_DISTANCE=36;
    private static final Map<Long,Streak> ACTIVE = new HashMap<>();
    private static ClientLevel owner;
    private static long visualTick;
    private static SpriteSet sprites;
    private ProjectileStreaks() {}
    static void clientTick(ClientLevel level, boolean running) {
        if(level!=null&&running)visualTick++;
        maintain(level);
    }
    static long visualTick(){return visualTick;}
    public static void maintain(ClientLevel level) {
        if(owner!=level){new ArrayList<>(ACTIVE.values()).forEach(Particle::remove);ACTIVE.clear();owner=level;}
        if(level!=null)for(var it=ACTIVE.entrySet().iterator();it.hasNext();){
            var p=it.next().getValue();
            if(!p.isAlive()||visualTick-p.updated>p.timeout()){it.remove();p.remove();}
        }
    }
    public static void accept(ClientLevel level, EffectApi.Host host) {
        accept(level,host,false,false);
    }
    public static void accept(ClientLevel level, EffectApi.Host host, boolean autocannon, boolean flak) {
        accept(level,host,autocannon,flak,false,false);
    }
    static void acceptConfirmedNaval(ClientLevel level,long id,Vec3 from,Vec3 to,boolean terminal) {
        accept(level,new EffectApi.Host(id,from,to,to.subtract(from),0,0,true),false,false,false,true,terminal);
    }
    private static void accept(ClientLevel level, EffectApi.Host host, boolean autocannon, boolean flak, boolean math, boolean confirmed) {
        accept(level,host,autocannon,flak,math,confirmed,false);
    }
    private static void accept(ClientLevel level, EffectApi.Host host, boolean autocannon, boolean flak,
                               boolean math, boolean confirmed, boolean terminal) {
        if(owner!=level)maintain(level);
        if(!ExtraConfig.STREAK_ENABLED.get()||sprites==null)return;
        Vec3 from=host.previous(),to=host.current();
        if(!finite(from)||!finite(to)||from.distanceToSqr(to)<1e-8||from.distanceToSqr(to)>4096*4096)return;
        Vec3 motion=to.subtract(from);
        var guard=ClientFlashes.STORE.trailGuard(from,to,level.getGameTime(),baseWidth(autocannon),0);
        if(guard!=null){from=guard.clipStart(from,to);if(from==null)return;}
        Streak streak=ACTIVE.get(host.id());
        if(streak==null||!streak.isAlive()){
            if(ACTIVE.size()>=ExtraConfig.STREAK_MAX.get())return;
            streak=new Streak(level,host.id(),from,to,sprites,autocannon,flak,math,confirmed,motion); ACTIVE.put(host.id(),streak);
            Minecraft.getInstance().particleEngine.add(streak);
        }else if(!streak.finished||terminal){
            streak.measured |= confirmed || !autocannon && !math && HostileShellClient.measured(level,host.id());
            streak.update(from,to,motion);
        }
    }
    /** Consecutive API frame endpoints; its clock hold is independent of local game time correction. */
    public static void acceptMath(ClientLevel level,long id,Vec3 from,Vec3 to,long stateTick,boolean hold,boolean autocannon,boolean flak){
        Streak prior=ACTIVE.get(id);
        if(prior!=null&&(!prior.math||prior.finished))retire(id);
        prior=ACTIVE.get(id);
        if(prior!=null&&prior.math&&prior.mathState!=stateTick&&stateTick-prior.mathState!=1)retire(id);
        prior=ACTIVE.get(id);
        if(prior==null||!prior.isAlive()||!prior.math||prior.mathState!=stateTick){
            accept(level,new EffectApi.Host(id,from,to,to.subtract(from),0,0,true),autocannon,flak,true,false);
        }
        Streak p=ACTIVE.get(id);if(p!=null){p.math=true;p.mathState=stateTick;p.mathHold=hold;p.updated=visualTick;}
    }
    public static boolean active(long id){var p=ACTIVE.get(id);return p!=null&&p.isAlive()&&ExtraConfig.STREAK_ENABLED.get();}
    public static void finish(long id){var p=ACTIVE.get(id);if(p!=null){p.finished=true;p.clearPrediction();}}
    public static void retire(long id){var p=ACTIVE.get(id);if(p!=null)p.remove();}
    static void retireConfirmedAutocannons(){
        for(long id:List.copyOf(ACTIVE.keySet()))if(id>Integer.MAX_VALUE)retire(id);
    }
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static double baseWidth(boolean autocannon){
        return (autocannon ? ExtraConfig.STREAK_AUTO_WIDTH : ExtraConfig.STREAK_WIDTH).get();
    }
    static boolean canReconnect(long elapsed,Vec3 visible,Vec3 from,Vec3 to,Vec3 motion){
        double tolerance=Math.min(MAX_PREDICTION_DISTANCE,Math.max(2,motion.length()*2));
        return elapsed>=1&&elapsed<=PREDICTION_TICKS+1&&
            visible.distanceToSqr(from)<=tolerance*tolerance&&to.subtract(visible).dot(motion)>0;
    }
    static boolean followsAutocannonHeading(Vec3 visible,Vec3 to,Vec3 motion){
        Vec3 bridge=to.subtract(visible);
        double forward=bridge.dot(motion);
        // A delayed correction may move the head a little, but it must not draw a near-perpendicular line.
        return forward>0&&forward*forward>=.88*bridge.lengthSqr()*motion.lengthSqr();
    }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet set){sprites=set;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double dx,double dy,double dz){
            return null; // The authoritative host feed owns creation/update; never a per-point particle loop.
        }
    }
    record Step(Vec3 from,Vec3 to,double time,double ticks){
        double fraction(double at){return Math.clamp((at-time+ticks)/ticks,0,1);}
    }

    static void appendArrivalBurst(ArrayDeque<Step> steps,Vec3 from,Vec3 to,long now) {
        var burst=new ArrayList<Step>();
        burst.add(new Step(from,to,now,1));
        // Fit confirmed segments received together into one display tick, retaining their bends.
        while(!steps.isEmpty()&&steps.peekFirst().time()>now-1){
            var earlier=steps.removeFirst();
            if(burst.size()<HISTORY_LIMIT)burst.add(earlier);
        }
        double duration=1.0/burst.size();
        for(int index=burst.size()-1;index>=0;index--){
            var step=burst.get(index);
            steps.addFirst(new Step(step.from(),step.to(),now-index*duration,duration));
        }
        while(steps.size()>HISTORY_LIMIT)steps.removeLast();
    }

    static double jitterClock(double time,double end,boolean hold) {
        return hold&&time>end?end+Math.max(0,time-end-NETWORK_HOLD_TICKS):time;
    }
    public static final class Streak extends TextureSheetParticle {
        private final long id;
        private final ArrayDeque<Step> steps=new ArrayDeque<>();
        private long updated,mathState;
        private final boolean autocannon,flak;
        private boolean math,mathHold,finished,measured;
        private Step prediction;
        private boolean predictionChecked;
        private Vec3 motion;
        private Streak(ClientLevel level,long id,Vec3 from,Vec3 to,SpriteSet sprites,boolean autocannon,boolean flak,boolean math,boolean confirmed,Vec3 motion){
            super(level,to.x,to.y,to.z);this.id=id;this.autocannon=autocannon;this.flak=flak;this.math=math;hasPhysics=false;
            measured=confirmed||!autocannon&&!math&&HostileShellClient.measured(level,id);
            pickSprite(sprites);update(from,to,motion);
        }
        void update(Vec3 from,Vec3 to,Vec3 newMotion){
            long now=visualTick;
            Step oldPrediction=prediction;
            clearPrediction();
            predictionChecked=false;
            Step previous=steps.peekFirst();
            if(previous!=null&&previous.time()==now){
                if(previous.from().equals(from)&&previous.to().equals(to))return;
                if(!math&&previous.to().distanceToSqr(from)<=1e-8){
                    appendArrivalBurst(steps,from,to,now);
                    acceptHead(from,to,newMotion,now);
                    return;
                }
                if(math)steps.removeFirst();
                else while(!steps.isEmpty()&&steps.peekFirst().time()>now-1)steps.removeFirst();
                previous=steps.peekFirst();
            }
            int span=1;
            if(previous!=null&&previous.to().distanceToSqr(from)>(math?4:1e-8)){
                long elapsed=now-(long)Math.ceil(previous.time());
                Vec3 visible=oldPrediction==null?previous.to():
                    oldPrediction.from().lerp(oldPrediction.to(),oldPrediction.fraction(now-1));
                if(autocannon&&!math){
                    // Stretch the display time of the received segment without drawing through the missing space.
                    if(canReconnect(elapsed,visible,from,to,newMotion)&&followsAutocannonHeading(visible,to,newMotion))
                        span=(int)elapsed;
                    steps.clear();
                }else if(!math&&!measured&&canReconnect(elapsed,visible,from,to,newMotion)){
                    // Continue from the displayed head. The incoming position is reached over one render tick.
                    from=visible;
                    if(elapsed>1&&previous.to().distanceToSqr(visible)>1e-8){
                        steps.addFirst(new Step(previous.to(),visible,now-1,(int)elapsed-1));
                    }else if(elapsed==1){
                        from=previous.to();
                    }else{
                        span=(int)elapsed;
                    }
                }else{
                    steps.clear();
                }
            }
            steps.addFirst(new Step(from,to,now,span));
            while(steps.size()>HISTORY_LIMIT)steps.removeLast();
            acceptHead(from,to,newMotion,now);
        }
        private void acceptHead(Vec3 from,Vec3 to,Vec3 newMotion,long now){
            motion=newMotion;
            updated=now;xo=from.x;yo=from.y;zo=from.z;setPos(to.x,to.y,to.z);
        }
        private long timeout(){return (long)Math.ceil(math?ExtraConfig.STREAK_TICKS.get():persistence())+(math?2:PREDICTION_TICKS+2);}
        private void clearPrediction(){
            if(prediction!=null){steps.removeFirst();prediction=null;}
        }
        private void preparePrediction(double time){
            // Missing updates can mean impact. A local ray cannot verify moving Sable hulls or unloaded terrain.
            if(autocannon||math||measured||finished||predictionChecked||time<=updated||steps.isEmpty())return;
            predictionChecked=true;
            Vec3 start=steps.peekFirst().to();
            Vec3 travel=motion.scale(PREDICTION_TICKS);
            double distance=travel.length();
            if(distance<1e-5)return;
            Vec3 end=start.add(travel.scale(Math.min(1,MAX_PREDICTION_DISTANCE/distance)));
            var hit=level.clip(new net.minecraft.world.level.ClipContext(start,end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=hit.getLocation();
            if(start.distanceToSqr(end)<1e-8)return;
            prediction=new Step(start,end,updated+PREDICTION_TICKS,PREDICTION_TICKS);
            steps.addFirst(prediction);
        }
        @Override public void tick(){
            if(owner!=level||HealthbarFlightBridge.suppress(id)||!ExtraConfig.STREAK_ENABLED.get()||visualTick-updated>timeout())remove();
        }
        @Override public void remove(){super.remove();ACTIVE.remove(id,this);}
        private double renderClock(float partial){
            double time=visualTick-1+partial;
            if(steps.isEmpty())return time;
            if(!math){
                boolean hold=!finished&&(autocannon||measured)&&!Minecraft.getInstance().hasSingleplayerServer();
                return jitterClock(time,steps.peekFirst().time(),hold);
            }
            long elapsed=Math.max(0,visualTick-updated);
            return dev.sablelightoverhaul.FlightVisualClock.time((long)steps.peekFirst().time(),mathHold,partial)
                + elapsed + (finished&&mathHold ? Math.clamp(partial,0,1) : 0);
        }
        private double persistence(){
            return autocannon ? 1.0 : ExtraConfig.STREAK_TICKS.get();
        }
        private double lengthBudget(double time){
            double limit=autocannon ? 18.0 : ExtraConfig.STREAK_LENGTH.get();
            Step latest=steps.peekFirst();
            if(latest==null||time<=latest.time())return limit;
            // Start shortening immediately even when the live line was capped by distance.
            double window=persistence(), length=0;
            for(Step step:steps){
                double fraction=1-step.fraction(latest.time()-window);
                length+=step.from().distanceTo(step.to())*fraction;
            }
            return Math.min(limit,length)*Math.clamp(1-(time-latest.time())/window,0,1);
        }
        public Vec3 sortingCentre(float partial){
            double time=renderClock(partial);
            preparePrediction(time);
            double cut=time-persistence(),remaining=lengthBudget(time);
            Vec3 head=null,tail=null;
            for(var s:steps){
                double end=s.fraction(time),start=s.fraction(cut);
                if(end<=start||remaining<=1e-6)continue;
                Vec3 b=s.from().lerp(s.to(),end),a=s.from().lerp(s.to(),start);double distance=a.distanceTo(b);
                if(distance<1e-5)continue;
                if(head==null)head=b;
                if(distance>remaining){a=b.add(a.subtract(b).scale(remaining/distance));distance=remaining;}
                tail=a;remaining-=distance;
            }
            return head==null?new Vec3(x,y,z):head.lerp(tail,.5);
        }
        @Override public ParticleRenderType getRenderType(){return MuzzleParticleLayer.TYPE;}
        @Override public AABB getRenderBoundingBox(float partial){
            // NeoForge explicitly supports INFINITE bounds. Only this bounded tracer batch
            // bypasses the terrain frustum; the GPU still clips XY/near and tests scene depth.
            if(ClientFlashes.inPackLighting()||!IrisCompat.shaderPackInUse())return AABB.INFINITE;
            preparePrediction(renderClock(partial));
            var first=steps.peekFirst();if(first==null)return super.getRenderBoundingBox(partial);
            AABB box=new AABB(first.from(),first.to());
            for(var s:steps)box=box.minmax(new AABB(s.from(),s.to()));
            return box.inflate(Math.max(baseWidth(autocannon),autocannon ? 1.0 : ExtraConfig.STREAK_MAX_WIDTH.get())*(flak?4:1));
        }
        @Override public void render(VertexConsumer out,Camera camera,float partial){
            if(!isAlive()||steps.isEmpty()||!ExtraConfig.STREAK_ENABLED.get()||HealthbarFlightBridge.suppress(id))return;
            double renderTime=renderClock(partial);
            preparePrediction(renderTime);
            double cutoff=renderTime-persistence();
            double remaining=lengthBudget(renderTime),width=baseWidth(autocannon);
            double focal=Math.abs(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().m11())*Minecraft.getInstance().getWindow().getHeight()*.5;
            double minimumPixels=(autocannon ? ExtraConfig.STREAK_AUTO_MIN_PIXELS : ExtraConfig.STREAK_MIN_PIXELS).get();
            double maxWidth=autocannon ? 1.0 : ExtraConfig.STREAK_MAX_WIDTH.get();
            int rgb=autocannon ? 0xFFD800 : ExtraConfig.STREAK_COLOR.get();
            int light=(ClientFlashes.inPackLighting()||!IrisCompat.shaderPackInUse())
                ?dev.sablelightoverhaul.GasVertexData.streakLight(LightTexture.FULL_BRIGHT)
                :MuzzleParticleLayer.markLight(LightTexture.FULL_BRIGHT,true);
            Vec3 eye=camera.getPosition();
            for(Step s:steps){
                double end=s.fraction(renderTime),start=s.fraction(cutoff);
                if(end<=start||remaining<=1e-6)continue;
                Vec3 b=s.from().lerp(s.to(),end),a=s.from().lerp(s.to(),start);
                double length=a.distanceTo(b);if(length<1e-5)continue;
                if(length>remaining){a=b.add(a.subtract(b).scale(remaining/length));length=remaining;}
                remaining-=length;
                double depth=Math.max(a.distanceTo(eye),b.distanceTo(eye));
                double drawWidth=Math.max(width,Math.min(maxWidth,minimumPixels*depth/Math.max(1,focal)))*(flak?4:1);
                if(flak){
                    // A single depth key cannot sort a long prism that ends inside the smoke.
                    b=FlakCloudPuffParticle.clipStreakHead(level,a,b,partial);
                    if(a.distanceToSqr(b)<1e-10)continue;
                }
                prism(out,a.subtract(eye),b.subtract(eye),drawWidth*.5,rgb,light);
            }
        }
        private void prism(VertexConsumer out,Vec3 a,Vec3 b,double width,int rgb,int light){
            float u=(getU0()+getU1())*.5f,v=(getV0()+getV1())*.5f;
            // CBC's amber outline and yellow core, both within the same width budget.
            if(autocannon)dev.sablelightoverhaul.TracerPrism.emit(a,b,width,p->vertex(out,p,u,v,0xFF5000,light));
            dev.sablelightoverhaul.TracerPrism.emit(a,b,autocannon ? width*.7 : width,p->vertex(out,p,u,v,rgb,light));
        }
        private void vertex(VertexConsumer out,Vec3 p,float u,float v,int rgb,int light){
            out.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v)
                .setColor(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1f).setLight(light);
        }
    }
}
