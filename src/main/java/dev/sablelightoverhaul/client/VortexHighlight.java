package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.lighting.LightingBlocks;
import dev.sablelightoverhaul.lighting.VortexMarks;
import dev.sablelightoverhaul.lighting.VortexMarksPayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Outlines every block registered to a nearby vortex generator, while the wand is held.
 *
 * <p><b>One box per block, never one box around them all.</b> A wing is not a rectangle, so the only
 * honest way to show what is registered is to show each block: a bounding box would claim the gaps
 * and, worse, would still look right while the actual set was wrong.
 *
 * <p><b>Drawn through the hull.</b> The registered blocks are the wing's own blocks, so a box around
 * one of them is a box around solid geometry: with the depth test on, its lines either coincide with
 * the faces they sit on or stay hidden inside the hull -- the marking would be invisible exactly
 * where it is needed. Depth is off for this pass only, which is also what lets a wing inside a
 * fuselage be inspected from outside.
 *
 * <p>The marks arrive as offsets in the body's plot space, so they are drawn by putting the body's
 * own pose on the stack and drawing the boxes where the blocks are. That makes them follow the hull
 * for free -- the marks are not world positions and never need to be re-sent as it moves. The camera
 * is brought into the same space once per body so the distance cull is a plain subtraction, and the
 * whole set is capped so a huge selection cannot turn the highlight into the frame's main cost.
 */
public final class VortexHighlight {
 /** Most boxes drawn in one frame; past this the nearest marks win and the rest go undrawn. */
 private static final int MAX_BOXES=6000;
 /** How far from the camera a mark is still worth drawing, in blocks. */
 private static final double CULL=48.0;
 /** The boxes are drawn just inside their block, so no line sits exactly on a face. */
 private static final double INSET=0.004;
 private static final float MARK_R=0.25F,MARK_G=0.85F,MARK_B=1.0F,MARK_A=0.9F;
 private static final float CORNER_R=1.0F,CORNER_G=0.6F,CORNER_B=0.1F,CORNER_A=0.9F;
 private static final float GENERATOR_R=1.0F,GENERATOR_G=0.78F,GENERATOR_B=0.25F,GENERATOR_A=0.95F;
 /** One generator's marks, decoded once when the payload arrives rather than every frame. */
 private record Entry(UUID ship,BlockPos generator,VortexMarks marks){}
 private static volatile List<Entry> entries=List.of();
 private static volatile ResourceLocation dimension;
 private static volatile boolean hasCorner;
 private static volatile UUID cornerShip;
 private static volatile BlockPos cornerPos;
 private static long lastLog;
 private VortexHighlight(){}
 /** The server's answer for this player: decode it now, draw it every frame from here on. */
 public static void receive(VortexMarksPayload payload){
  List<Entry> decoded=new ArrayList<>(payload.marks().size());
  for(VortexMarksPayload.Mark mark:payload.marks()){
   VortexMarks marks=VortexMarks.fromArrays(mark.min(),mark.size(),mark.bits());
   if(marks.empty())continue;
   decoded.add(new Entry(mark.ship(),mark.generator(),marks));
  }
  entries=List.copyOf(decoded);
  dimension=payload.dimension();
  hasCorner=payload.hasCorner();
  cornerShip=payload.cornerShip();
  cornerPos=payload.cornerPos();
 }
 public static void onRenderLevelStage(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
  Minecraft minecraft=Minecraft.getInstance();
  ClientLevel level=minecraft.level;
  LocalPlayer player=minecraft.player;
  if(level==null||player==null)return;
  // The wand is the switch: no wand, no highlight, and nothing to keep in sync.
  if(!holdingWand(player))return;
  if(dimension==null||!dimension.equals(level.dimension().location()))return;
  List<Entry> current=entries;
  boolean corner=hasCorner;
  if(current.isEmpty()&&!corner)return;
  SubLevelContainer container=SubLevelContainer.getContainer(level);
  if(container==null)return;
  Vec3 camera=event.getCamera().getPosition();
  PoseStack pose=event.getPoseStack();
  MultiBufferSource.BufferSource buffers=minecraft.renderBuffers().bufferSource();
  VertexConsumer lines=buffers.getBuffer(RenderType.lines());
  int drawn=0,ships=0,bodies=0;
  for(Entry entry:current){
   SubLevel ship=find(container,entry.ship());
   if(ship==null||ship.isRemoved())continue;
   ships++;
   int before=drawn;
   drawn+=drawMarks(ship,entry,camera,pose,lines,MAX_BOXES-drawn);
   if(drawn>before)bodies++;
   if(drawn>=MAX_BOXES)break;
  }
  if(corner&&cornerShip!=null&&cornerPos!=null&&drawn<MAX_BOXES){
   SubLevel ship=find(container,cornerShip);
   if(ship!=null&&!ship.isRemoved()){
    pose.pushPose();
    place(ship,camera,pose);
    LevelRenderer.renderLineBox(pose,lines,box(cornerPos),CORNER_R,CORNER_G,CORNER_B,CORNER_A);
    pose.popPose();
    drawn++;
   }
  }
  if(drawn>0){
   // Depth off so the marks show through the hull they sit in; see the class note. Restored right
   // after the batch is flushed, so nothing outside this pass is affected.
   RenderSystem.disableDepthTest();
   buffers.endBatch(RenderType.lines());
   RenderSystem.enableDepthTest();
  }
  if(ExtraConfig.on(MuzzleClientConfig.ADV_DEBUG))log(current.size(),ships,bodies,drawn);
 }
 /**
  * Draw one body's marks, and the generator they belong to.
  *
  * <p>The generator is drawn too: it is what the marks belong to, and a wing registered from two
  * different blocks would otherwise give no clue which marks are whose.
  */
 private static int drawMarks(SubLevel ship,Entry entry,Vec3 camera,PoseStack pose,
                              VertexConsumer lines,int budget){
  if(budget<=0)return 0;
  if(ship.getPlot().getLoadedChunks().isEmpty())return 0;
  Matrix4d pose4=ship.logicalPose().bakeIntoMatrix(new Matrix4d());
  if(!pose4.isFinite()||Math.abs(pose4.determinant())<1.0E-12)return 0;
  Vector3d plotCamera=new Vector3d(camera.x,camera.y,camera.z);
  new Matrix4d(pose4).invert().transformPosition(plotCamera);
  BlockPos generator=entry.generator();
  int[] left=new int[]{budget};
  pose.pushPose();
  pose.translate(-camera.x,-camera.y,-camera.z);
  pose.last().pose().mul(new Matrix4f(pose4));
  entry.marks().forEach((x,y,z)->{
   if(left[0]<=0)return;
   BlockPos at=generator.offset(x,y,z);
   if(!near(at,plotCamera))return;
   left[0]--;
   LevelRenderer.renderLineBox(pose,lines,box(at),MARK_R,MARK_G,MARK_B,MARK_A);
  });
  if(left[0]>0&&near(generator,plotCamera)){
   left[0]--;
   LevelRenderer.renderLineBox(pose,lines,box(generator),GENERATOR_R,GENERATOR_G,GENERATOR_B,GENERATOR_A);
  }
  pose.popPose();
  return budget-left[0];
 }
 /** Put this body's pose on the stack, relative to the camera, so plot-space drawing lands right. */
 private static void place(SubLevel ship,Vec3 camera,PoseStack pose){
  pose.translate(-camera.x,-camera.y,-camera.z);
  pose.last().pose().mul(new Matrix4f(ship.logicalPose().bakeIntoMatrix(new Matrix4d())));
 }
 /** The sub-level with this id, found the same way the light projection finds its bodies. */
 private static SubLevel find(SubLevelContainer container,UUID id){
  if(id==null)return null;
  for(SubLevel ship:container.getAllSubLevels()){
   if(ship!=null&&!ship.isRemoved()&&id.equals(ship.getUniqueId()))return ship;
  }
  return null;
 }
 private static boolean near(BlockPos at,Vector3d plotCamera){
  double dx=at.getX()+0.5-plotCamera.x;
  double dy=at.getY()+0.5-plotCamera.y;
  double dz=at.getZ()+0.5-plotCamera.z;
  return dx*dx+dy*dy+dz*dz<=CULL*CULL;
 }
 private static AABB box(BlockPos at){
  return new AABB(at.getX()+INSET,at.getY()+INSET,at.getZ()+INSET,
                  at.getX()+1.0-INSET,at.getY()+1.0-INSET,at.getZ()+1.0-INSET);
 }
 private static boolean holdingWand(LocalPlayer player){
  return player.getMainHandItem().is(LightingBlocks.VORTEX_WAND.get())
    ||player.getOffhandItem().is(LightingBlocks.VORTEX_WAND.get());
 }
 /** One line a second while the debug flag is on: enough to tell "nothing arrived" from "nothing drawn". */
 private static void log(int generators,int ships,int bodies,int drawn){
  long now=System.currentTimeMillis();
  if(now-lastLog<1000L)return;
  lastLog=now;
  com.mojang.logging.LogUtils.getLogger().info(
    "Sable Light Overhaul vortex highlight: generators={} bodiesResolved={} bodiesDrawn={} boxes={}",
    generators,ships,bodies,drawn);
 }
}
