package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.*;
import dev.sablelightoverhaul.SableLightOverhaul;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Diagnostic geometry only: never changes depth/blend of production particles. */
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class HullDustDebug {
    enum Mode { OFF, DEPTH, XRAY }
    private static Mode mode=Mode.OFF;
    private static boolean frozen,holdAttached=true;
    private static MuzzleClientConfig.HullRenderer override;
    private static Boolean correction;
    private static Object level;
    private record Sample(long tick,HullSurfaceAnchor.Diagnostic source,Vec3 center,boolean attached,List<Vec3> vertices){}
    private static final Map<Object,Sample> samples=new LinkedHashMap<>();
    static MuzzleClientConfig.HullRenderer renderer(){return override==null?MuzzleClientConfig.HULL_RENDERER.get():override;}
    static boolean visualEnabled(){return correction==null?MuzzleClientConfig.HULL_VISUAL_SURFACE.get():correction;}
    static boolean capture(){return mode!=Mode.OFF&&!frozen;}
    static void record(Object id,HullSurfaceAnchor.Diagnostic source,Vec3 center,boolean attached,List<Vec3> vertices){
        var current=Minecraft.getInstance().level;if(current==null||!capture())return;
        if(level!=current){samples.clear();level=current;}
        long tick=current.getGameTime();samples.values().removeIf(s->tick-s.tick>100);
        if(!samples.containsKey(id)&&samples.size()>=64)return;
        var previous=samples.get(id);
        if(holdAttached&&previous!=null&&previous.attached&&!previous.vertices.isEmpty())return;
        samples.put(id,new Sample(tick,source,center,attached,List.copyOf(vertices)));
    }
    /** Record exactly the submitted carrier vertices, in world space, while forwarding all attributes. */
    static final class Capture implements VertexConsumer {
        final VertexConsumer target;final Vec3 eye;final List<Vec3> vertices=new ArrayList<>();
        Capture(VertexConsumer target,Vec3 eye){this.target=target;this.eye=eye;}
        public VertexConsumer addVertex(float x,float y,float z){vertices.add(new Vec3(x,y,z).add(eye));target.addVertex(x,y,z);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){target.setColor(r,g,b,a);return this;}
        public VertexConsumer setUv(float u,float v){target.setUv(u,v);return this;}
        public VertexConsumer setUv1(int u,int v){target.setUv1(u,v);return this;}
        public VertexConsumer setUv2(int u,int v){target.setUv2(u,v);return this;}
        public VertexConsumer setNormal(float x,float y,float z){target.setNormal(x,y,z);return this;}
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){samples.clear();level=null;frozen=false;mode=Mode.OFF;override=null;correction=null;}
    private static RenderType make(boolean xray){
        return RenderType.create("slo_hull_diagnostic_"+xray,DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.DEBUG_LINES,65536,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setDepthTestState(xray?RenderStateShard.NO_DEPTH_TEST:RenderStateShard.LEQUAL_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    }
    private static final class Types {static final RenderType DEPTH=make(false),XRAY=make(true);}
    @SubscribeEvent public static void draw(RenderLevelStageEvent event){
        if(mode==Mode.OFF||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.level==null||level!=mc.level){samples.clear();return;}
        if(!frozen)samples.values().removeIf(s->mc.level.getGameTime()-s.tick>100);
        var type=mode==Mode.XRAY?Types.XRAY:Types.DEPTH;
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);var pose=event.getPoseStack();var eye=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-eye.x,-eye.y,-eye.z);
        try{for(var sample:samples.values()){
            if(sample.center.distanceToSqr(eye)>128*128)continue;
            var s=sample.source;
            if(s!=null){cross(pose,out,s.collision(),.12f,0x20CFFF);cross(pose,out,s.visual(),.10f,0xFFAD32);
                line(pose,out,s.collision(),s.visual(),0xFFAD32);line(pose,out,s.visual(),s.visual().add(s.normal().scale(.6)),0x50FF50);}
            cross(pose,out,sample.center,.06f,0xFFFFFF);
            for(int i=0;i+3<sample.vertices.size();i+=4)for(int j=0;j<4;j++)line(pose,out,sample.vertices.get(i+j),sample.vertices.get(i+(j+1)%4),sample.attached?0xEA70FF:0x999999);
        }}finally{pose.popPose();buffers.endBatch(type);}
    }
    private static void cross(PoseStack pose,VertexConsumer out,Vec3 p,float size,int color){for(var axis:List.of(new Vec3(size,0,0),new Vec3(0,size,0),new Vec3(0,0,size)))line(pose,out,p.subtract(axis),p.add(axis),color);}
    private static void line(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,int color){
        out.addVertex(pose.last().pose(),(float)a.x,(float)a.y,(float)a.z).setColor(0xFF000000|color);
        out.addVertex(pose.last().pose(),(float)b.x,(float)b.y,(float)b.z).setColor(0xFF000000|color);
    }
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event){
        var root=Commands.literal("slohull").executes(c->report(c.getSource()));
        for(var value:Mode.values())root.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).executes(c->{mode=value;return report(c.getSource());}));
        root.then(Commands.literal("hold").executes(c->{holdAttached=true;samples.clear();return report(c.getSource());}));
        root.then(Commands.literal("live").executes(c->{holdAttached=false;samples.clear();return report(c.getSource());}));
        root.then(Commands.literal("freeze").executes(c->{frozen=!frozen;return report(c.getSource());}));
        root.then(Commands.literal("clear").executes(c->{samples.clear();frozen=false;return report(c.getSource());}));
        root.then(Commands.literal("skin").executes(c->{override=MuzzleClientConfig.HullRenderer.SURFACE_SKIN;return report(c.getSource());}));
        root.then(Commands.literal("cluster").executes(c->{override=MuzzleClientConfig.HullRenderer.SOFT_CLUSTER;return report(c.getSource());}));
        root.then(Commands.literal("visual_on").executes(c->{correction=true;return report(c.getSource());}));
        root.then(Commands.literal("visual_off").executes(c->{correction=false;return report(c.getSource());}));
        root.then(Commands.literal("reset").executes(c->{mode=Mode.OFF;override=null;correction=null;frozen=false;samples.clear();return report(c.getSource());}));
        event.getDispatcher().register(root);
    }
    private static int report(net.minecraft.commands.CommandSourceStack source){
        var counts=new EnumMap<HullVisualSurfaces.Status,Integer>(HullVisualSurfaces.Status.class);int attached=0;
        for(var s:samples.values()){if(s.attached)attached++;if(s.source!=null)counts.merge(s.source.status(),1,Integer::sum);}
        String text="SLO hull: "+mode+" frozen="+frozen+" holdAttached="+holdAttached+" renderer="+renderer()+" visual="+visualEnabled()+" samples="+samples.size()+" attached="+attached+" "+counts+" | 蓝=碰撞点 橙=视觉面 绿=法线 紫=近面载体 灰=离体载体 白=模拟中心；hold保留首个可见近面世界快照5秒，live实时；freeze冻结，clear清除。";
        source.sendSuccess(()->Component.literal(text),false);com.mojang.logging.LogUtils.getLogger().info(text);return 1;
    }
}
