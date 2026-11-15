package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.lighting.LightingBlocks;
import dev.sablelightoverhaul.lighting.SourceAnchor;
import dev.sablelightoverhaul.lighting.SourcePayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The four axis lines of a lamp's angle frame, drawn while the registrar tool is held.
 *
 * <p>A cone is a set of angles, and angles are only meaningful against a frame: without seeing where the
 * zero is, a player writes numbers and checks the result by trial. So while the tool is in hand every
 * registered lamp within reach shows four one-block lines -- horizontal 0 and its opposite, and the
 * vertical pair -- in the frame the shader will measure the cones in, which means they turn with the hull
 * a lamp is mounted on.
 *
 * <p><b>The tool is the switch</b>, the same way the wand is the vortex marks' switch: there is no key to
 * learn, nothing is drawn while the player is doing anything else, and walking up to a lamp with the tool
 * in hand is already the gesture that means "I am working on this one".
 *
 * <p>Drawn through geometry on purpose. These lines are meant to be read against a hull and inside a
 * fuselage, so a depth test would hide them exactly where they are wanted; depth is off for this pass
 * only, and restored as soon as the batch is flushed.
 */
public final class LampAxes {
    /** How far from the camera a lamp's frame is still worth drawing, in blocks. */
    private static final double RANGE=24.0;
    /** The lines are one block long, from the lamp's centre outwards. */
    private static final double LENGTH=1.0;
    // The two axes are told apart by colour, and the two ends of each axis by brightness: a frame whose
    // 0 and 180 ends look alike would be no use for reading a range off it.
    private static final float H0_R=1.00F,H0_G=0.28F,H0_B=0.22F;
    private static final float H1_R=0.45F,H1_G=0.12F,H1_B=0.10F;
    private static final float V0_R=0.35F,V0_G=0.65F,V0_B=1.00F;
    private static final float V1_R=0.14F,V1_G=0.28F,V1_B=0.48F;
    private static final float ALPHA=0.95F;
    private LampAxes(){}
    /**
     * The four axis directions of a lamp's frame, in world axes.
     *
     * <p>Pure, so the convention can be pinned without a game: horizontal 0 is +X and grows towards +Z
     * (the same <em>atan(z, x)</em> the shader uses), the opposite line is -X, and the vertical pair is up
     * and down. A lamp on a hull passes that hull's rotation, which is what makes the lines describe the
     * frame its cones are actually measured in; a lamp on the ground passes null and gets the world.
     */
    public static Vec3[] axes(float[] bodyRotation) {
        Vec3[] unit={new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0)};
        if(bodyRotation==null)return unit;
        Quaternionf rotation=new Quaternionf(bodyRotation[0],bodyRotation[1],bodyRotation[2],bodyRotation[3]);
        Vec3[] turned=new Vec3[unit.length];
        for(int i=0;i<unit.length;i++){
            Vector3f direction=rotation.transform(new Vector3f((float)unit[i].x,(float)unit[i].y,(float)unit[i].z));
            turned[i]=new Vec3(direction.x,direction.y,direction.z);
        }
        return turned;
    }
    /** The switch: the registrar tool, in either hand. */
    private static boolean holdingTool(LocalPlayer player){
        return player.getMainHandItem().is(LightingBlocks.TOOL.get())
                ||player.getOffhandItem().is(LightingBlocks.TOOL.get());
    }
    public static void onRenderLevelStage(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        Minecraft minecraft=Minecraft.getInstance();
        ClientLevel level=minecraft.level;
        LocalPlayer player=minecraft.player;
        if(level==null||player==null)return;
        if(!holdingTool(player))return;
        List<SourcePayload.State> sources=ExtraLights.sourcesFor(level);
        if(sources.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        MultiBufferSource.BufferSource buffers=minecraft.renderBuffers().bufferSource();
        VertexConsumer lines=buffers.getBuffer(RenderType.lines());
        int drawn=0;
        for(SourcePayload.State source:sources){
            // A beam has a direction of its own, and its own aim is what a player reads; the frame the
            // cones are measured in is a sphere's problem.
            if(source.spotlight())continue;
            SourceAnchor anchor=source.anchor();
            BlockPos resolved=anchor.resolve(level);
            if(resolved==null)continue;
            Vec3 center=anchor.world(level,resolved,Vec3.atCenterOf(resolved));
            if(center==null||center.distanceToSqr(camera)>RANGE*RANGE)continue;
            Vec3[] axes=axes(anchor.rotation(level));
            pose.pushPose();
            pose.translate(-camera.x,-camera.y,-camera.z);
            line(pose,lines,center,center.add(axes[0].scale(LENGTH)),H0_R,H0_G,H0_B);
            line(pose,lines,center,center.add(axes[1].scale(LENGTH)),H1_R,H1_G,H1_B);
            line(pose,lines,center,center.add(axes[2].scale(LENGTH)),V0_R,V0_G,V0_B);
            line(pose,lines,center,center.add(axes[3].scale(LENGTH)),V1_R,V1_G,V1_B);
            pose.popPose();
            drawn++;
        }
        if(drawn>0){
            RenderSystem.disableDepthTest();
            buffers.endBatch(RenderType.lines());
            RenderSystem.enableDepthTest();
        }
    }
    /** One line segment, in the world space the caller has already brought the stack into. */
    private static void line(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to,float r,float g,float b){
        var matrix=pose.last();
        lineVertex(out,matrix,from,r,g,b);
        lineVertex(out,matrix,to,r,g,b);
    }
    private static void lineVertex(VertexConsumer out,PoseStack.Pose pose,Vec3 at,float r,float g,float b){
        out.addVertex(pose.pose(),(float)at.x,(float)at.y,(float)at.z)
            .setColor(r,g,b,ALPHA)
            .setNormal(pose,0.0F,1.0F,0.0F);
    }
}
