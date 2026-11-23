package dev.sablelightoverhaul.client;

import com.mojang.logging.LogUtils;
import dev.sablelightoverhaul.SableLightOverhaul;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import static org.lwjgl.opengl.GL33.*;

/** Deterministic wall-clock battery. GPU timestamps are polled, never synchronously waited on. */
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class SmokeBatteryBenchmark {
    private static final int[] ORDER={1,2,4,4,2,1};
    private static final int[] TIERS={1,2,4,8,16,16,8,4,2,1};
    private enum Mode { BASELINE, SWEEP, TIERS }
    private static final int[] BIASES = java.util.stream.IntStream.rangeClosed(0,20).map(i->i*5).toArray();
    private static final double[] CAPTURE_TIMES={3.5,6,9};
    private static Run run;
    private static Query writing;
    private record Puff(BigSmokeParticle particle,double born){}
    private static final class Query {
        final int[] ids={glGenQueries(),glGenQueries(),glGenQueries(),glGenQueries()};
        boolean pending;int phase,divisor,selectedDivisor,count,bias;double time,area;
    }
    private static final class Run {
        final net.minecraft.client.multiplayer.ClientLevel level=Minecraft.getInstance().level;
        final boolean pack = IrisCompat.shaderPackInUse();
        final boolean sweep;
        final int[] order;
        final Vec3 eye,look;
        final float yaw,pitch;
        final int previous=SmokeResolution.override;
        final List<Puff> puffs=new ArrayList<>();
        final Set<BigSmokeParticle> fixture=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<String> frames=new ArrayList<>(),gpu=new ArrayList<>();
        final List<Query> queries=new ArrayList<>();
        final Path output;
        long start=System.nanoTime(),lastFrame=start,excludeUntil;
        int phase,shot,skippedQueries;
        double seconds;boolean screenshot;
        Run(Minecraft mc,Mode mode)throws java.io.IOException{
            sweep=mode==Mode.SWEEP;
            order=switch(mode){
                case SWEEP -> BIASES;
                case TIERS -> TIERS;
                case BASELINE -> pack ? new int[]{1,1} : ORDER;
            };
            eye=mc.gameRenderer.getMainCamera().getPosition();look=Vec3.directionFromRotation(0,mc.player.getYRot());
            yaw=mc.player.getYRot();pitch=mc.player.getXRot();
            output=mc.gameDirectory.toPath().resolve("logs/slo-smoke-battery-"+System.currentTimeMillis());Files.createDirectories(output);
            frames.add("phase,requested_divisor,time_s,frame_ms,screenshot_excluded,bias");
            gpu.add("phase,divisor,time_s,puffs,depth_ms,smoke_ms,composite_ms,total_ms,bias,projected_area_sum,selected_divisor");
            for(int i=0;i<16;i++)queries.add(new Query());
            Files.writeString(output.resolve("settings.txt"),"In-game smoke battery; no entities/projectiles/explosions.\n"
                +"shaderPack="+pack+" backend="+ClientFlashes.effectiveMode()+" biasSweep="+sweep+" fixedTiers="+(mode==Mode.TIERS)+" order="+Arrays.toString(order)+"\n"
                +"NEAR SALVO: 8 guns x 32 puffs; muzzle plane 2 blocks behind camera, horizontal fire forward, 4x2 grid. Volleys at t=3,13,23s; phase=30s\n"
                +"Screenshots at exact fixture t=3.5,6,9s; screenshot frame and next 500ms excluded. GPU query ring is nonblocking.\n"
                +"eye="+eye+" yaw="+yaw+" pitch="+pitch+" resolution="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()
                +" vsync="+mc.options.enableVsync().get()+" fpsLimit="+mc.options.framerateLimit().get()
                +" performanceMaterial="+BigSmokeConfig.PERFORMANCE.get()+" edgeThreshold="+BigSmokeConfig.SOFT_EDGE_THRESHOLD.get()+"\n"
                +new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(new dev.sablelightoverhaul.client.ui.TomlSource(MuzzleClientConfig.SPEC,Set.of()).read()));
            rebuild();
        }
        void rebuild(){
            puffs.clear();fixture.clear();
            int release=BigSmokeConfig.RELEASE_TICKS.get();
            for(int volley=0;volley<3;volley++){
                for(int gun=0;gun<8;gun++){
                    Vec3 origin=SmokeBatteryLayout.muzzle(eye,look,gun);
                    for(int i=0;i<32;i++){
                        int batch=i*release/32;
                        // The local index preserves plume placement. Random streams are unique across guns and volleys.
                        long seed=0x534c4fL+31L*((volley*8+gun)*32+i);
                        var p=BigSmokeParticle.batteryPuff(level,origin,look,i,batch,seed);
                        puffs.add(new Puff(p,SmokeBatteryLayout.born(volley,batch)));fixture.add(p);
                    }
                }
            }
        }
    }
    public static boolean active(){return run!=null;}
    static boolean fixture(net.minecraft.client.particle.Particle p){return run!=null&&run.fixture.contains(p);}
    static int resolutionBias(){
        Run r=run;
        return r!=null&&r.sweep ? r.order[r.phase] : SmokeResolution.configuredBias();
    }
    public static int start(){return start(Mode.BASELINE);}
    public static int startSweep(){return start(Mode.SWEEP);}
    public static int startTiers(){return start(Mode.TIERS);}
    private static int start(Mode mode){
        var mc=Minecraft.getInstance();
        if(run!=null||mc.level==null||mc.player==null||!BigSmokeParticle.available()||(IrisCompat.shaderPackInUse()&&!ClientFlashes.inPackLighting())
            ||MuzzleParticleLayer.mode()!=MuzzleParticleLayer.Mode.FIXED){message("Use native/SLO Only or a supported Advanced shader pack, enter a world, select /slosmoke fixed, and stop any active battery first.");return 0;}
        if(mode!=Mode.BASELINE && IrisCompat.shaderPackInUse() && !SmokeResolution.photon()){
            message("Shader resolution comparisons require the patched Photon pack in Advanced mode.");
            return 0;
        }
        if(!IrisCompat.shaderPackInUse()&&mc.options.graphicsMode().get()==net.minecraft.client.GraphicsStatus.FABULOUS){message("Use Fast/Fancy for this native framebuffer experiment.");return 0;}
        try{SmokeBenchmark.stop();run=new Run(mc,mode);SmokeResolution.override=run.sweep?0:run.order[0];message("Smoke battery started: "+run.order.length+" x 30s, 8 guns x 32 puffs. "+(run.sweep?"Adaptive bias 0 to 100, step 5. ":mode==Mode.TIERS?"Fixed divisors 1, 2, 4, 8, 16. ":run.pack?"Shader pack full-resolution baseline. ":"Native resolution sweep. ")+"Keep camera fixed.");return 1;}
        catch(Exception ex){LogUtils.getLogger().error("SLO battery start",ex);message(ex.toString());return 0;}
    }
    public static int stop(){if(run==null)return 0;finish("cancelled");return 1;}
    @SubscribeEvent public static void pre(RenderFrameEvent.Pre event){
        Run r=run;if(r==null)return;var mc=Minecraft.getInstance();
        if(mc.level!=r.level||mc.player==null||mc.isPaused()||IrisCompat.shaderPackInUse()!=r.pack||(r.pack&&!ClientFlashes.inPackLighting())
            ||mc.gameRenderer.getMainCamera().getPosition().distanceTo(r.eye)>.3||Math.abs(mc.player.getYRot()-r.yaw)>.1||Math.abs(mc.player.getXRot()-r.pitch)>.1){finish("cancelled: camera/world/pack/pause changed");return;}
        poll(r);long now=System.nanoTime();double elapsed=(now-r.start)/1e9;
        if(elapsed>=30){
            r.phase++;if(r.phase==r.order.length){finish("complete");return;}
            r.start=now;r.lastFrame=now;r.shot=0;elapsed=0;
            message("Smoke battery "+(r.phase+1)+"/"+r.order.length+(r.sweep?": performance bias ":": divisor ")+r.order[r.phase]);
        }
        SmokeResolution.override=r.sweep?0:r.order[r.phase];
        r.screenshot=r.shot<CAPTURE_TIMES.length&&elapsed>=CAPTURE_TIMES[r.shot];
        r.seconds=r.screenshot?CAPTURE_TIMES[r.shot]:elapsed;
        if(r.screenshot)r.excludeUntil=now+500_000_000L;
        if(elapsed>=2)r.frames.add(String.format(Locale.ROOT,"%d,%d,%.5f,%.5f,%s,%d",r.phase,SmokeResolution.override,elapsed,(now-r.lastFrame)/1e6,now<r.excludeUntil,r.sweep?r.order[r.phase]:-1));
        r.lastFrame=now;
    }
    static void collect(List<? super BigSmokeParticle> into,Camera camera){
        Run r=run;if(r==null)return;
        for(Puff puff:r.puffs){double age=(r.seconds-puff.born)*20;
            if(age<0||age>=puff.particle.batteryLife())continue;
            puff.particle.batteryAge((float)age);into.add(puff.particle);
        }
    }
    @SubscribeEvent public static void post(RenderFrameEvent.Post event){
        Run r=run;if(r==null||!r.screenshot)return;r.screenshot=false;
        String name="battery-"+r.output.getFileName()+"-p"+r.phase+(r.sweep?"-bias":"-d")+r.order[r.phase]+"-t"+CAPTURE_TIMES[r.shot]+".png";
        Screenshot.grab(Minecraft.getInstance().gameDirectory,name,Minecraft.getInstance().getMainRenderTarget(),c->{});
        r.shot++;
    }
    static void gpuStart(int divisor,int selectedDivisor,int count,double area){
        writing=null;Run r=run;if(r==null||r.seconds<2||System.nanoTime()<r.excludeUntil)return;
        for(Query q:r.queries)if(!q.pending){writing=q;q.phase=r.phase;q.divisor=divisor;q.count=count;q.time=r.seconds;q.selectedDivisor=selectedDivisor;
            q.bias=r.sweep?r.order[r.phase]:-1;q.area=area;
            glQueryCounter(q.ids[0],GL_TIMESTAMP);return;}
        r.skippedQueries++;
    }
    static void gpuDivisor(int divisor){if(writing!=null)writing.divisor=divisor;}
    static void gpuStamp(int i){if(writing!=null)glQueryCounter(writing.ids[i],GL_TIMESTAMP);}
    static void gpuEnd(){if(writing!=null){glQueryCounter(writing.ids[3],GL_TIMESTAMP);writing.pending=true;writing=null;}}
    private static void poll(Run r){
        for(Query q:r.queries)if(q.pending&&glGetQueryObjecti(q.ids[3],GL_QUERY_RESULT_AVAILABLE)!=0){
            long[] t=new long[4];for(int i=0;i<4;i++)t[i]=glGetQueryObjecti64(q.ids[i],GL_QUERY_RESULT);
            r.gpu.add(String.format(Locale.ROOT,"%d,%d,%.5f,%d,%.6f,%.6f,%.6f,%.6f,%d,%.5f,%d",q.phase,q.divisor,q.time,q.count,
                (t[1]-t[0])/1e6,(t[2]-t[1])/1e6,(t[3]-t[2])/1e6,(t[3]-t[0])/1e6,q.bias,q.area,q.selectedDivisor));q.pending=false;
        }
    }
    private static void finish(String reason){
        Run r=run;if(r==null)return;poll(r);run=null;writing=null;SmokeResolution.override=r.previous;
        long pending=r.queries.stream().filter(q->q.pending).count();
        for(Query q:r.queries)for(int id:q.ids)glDeleteQueries(id);
        try{Files.write(r.output.resolve("frames.csv"),r.frames);Files.write(r.output.resolve("gpu.csv"),r.gpu);
            Files.writeString(r.output.resolve("result.txt"),reason+"\nskippedQueryFrames="+r.skippedQueries+"\npendingAtEnd="+pending+"\n");}
        catch(Exception ex){LogUtils.getLogger().error("SLO battery save",ex);}
        LogUtils.getLogger().info("SLO battery {}: {}",reason,r.output);message("Smoke battery "+reason+": "+r.output);
    }
    private static void message(String s){var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal(s),false);}
}
