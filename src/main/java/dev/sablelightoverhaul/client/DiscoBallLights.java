package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AreaLight;
import dev.sablelightoverhaul.BeamLight;
import dev.sablelightoverhaul.lighting.DiscoBallBlockEntity;
import dev.sablelightoverhaul.lighting.LightingBlocks;
import dev.sablelightoverhaul.lighting.LightingCommon;
import dev.sablelightoverhaul.lighting.SourceColor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * The disco ball's show: a handful of small coloured cones that spin around it.
 *
 * <p>Runs entirely on the client. The balls near the player are found by asking chunk sections for the
 * block (once every second, so a scan never happens per frame), and each one then throws its cones
 * into the same light buffer the muzzle flashes and spotlights go into.
 *
 * <p><b>A ball is almost always on a physics body</b> — placement requires one — and a body's blocks
 * live in its own plot, which is a different set of chunks from the main level. A scan of the main
 * level alone therefore never finds a ball that was actually placed, which is exactly how the block
 * ended up emitting nothing at all. Both places are searched, and a ball found in a plot is turned
 * into a world position through the body's pose every frame, so it follows a moving ship.
 *
 * <p>The show belongs to the ball: beams, range, width, intensity, spin and palette are read from the
 * ball's own block entity (which is what the page edits and what the server sends to everyone), so two
 * players standing side by side never watch two different shows. The client configuration is only a
 * fallback for a ball whose block entity has not arrived yet; view distance and scan rate stay local.
 */
public final class DiscoBallLights {
 /** One ball and the show it currently runs. {@code ship} is null when the ball is a plain world block. */
 private record Ball(SubLevel ship,BlockPos pos,int beams,int range,int width,double intensity,double spin,int falloff,DiscoBallBlockEntity settings,int[] colors){}
 private static final List<Ball> BALLS=new ArrayList<>();
 private static long lastScan=Long.MIN_VALUE;
 private DiscoBallLights(){}
 /** Called from the light pass, right after the registered sources have been gathered. */
 public static void append(Level level,Map<String,AreaSnapshot> world){
  if(!ExtraConfig.DISCO_ENABLED.get())return;
  ClientLevel client=Minecraft.getInstance().level;
  if(client==null||level!=client)return;
  long now=level.getGameTime();
  if(GameClock.due(now,lastScan,ExtraConfig.DISCO_SCAN_TICKS.get())){
   lastScan=now;
   scan(client);
  }
  if(BALLS.isEmpty())return;
  double cull=ExtraConfig.DISCO_CULL.get();
  var player=Minecraft.getInstance().player;
  double px=player==null?0.0:player.getX();
  double py=player==null?0.0:player.getY();
  double pz=player==null?0.0:player.getZ();
  int tick=(int)(now%72000L);
  for(Ball ball:BALLS){
   BlockPos pos=ball.pos();
   double cx=pos.getX()+0.5,cy=pos.getY()+0.5,cz=pos.getZ()+0.5;
   SubLevel ship=ball.ship();
   // One bake per ball per tick: the same matrix moves the center and turns the beam directions.
   Matrix4d pose=null;
   if(ship!=null){
    // Plot coordinates mean nothing in the world: the body's pose is what puts the ball where the
    // player can see it, and reading it every frame is what keeps the show on a moving ship.
    if(ship.isRemoved())continue;
    pose=ship.logicalPose().bakeIntoMatrix(new Matrix4d());
    if(!pose.isFinite())continue;
    Vector3d out=pose.transformPosition(new Vector3d(cx,cy,cz));
    cx=out.x;cy=out.y;cz=out.z;
   }else if(!level.hasChunkAt(pos))continue;
   // Redstone decides whether the show runs, read from the signal entering the ball's own block. The
   // ball's level is asked, not the client's main level: on a body the ball lives in that body's plot,
   // and that is where its neighbours are.
   Level signalLevel=ship==null?level:ship.getLevel();
   DiscoBallBlockEntity settings=ball.settings();
   if(settings!=null&&signalLevel!=null&&!settings.poweredOn(signalLevel.getBestNeighborSignal(pos)))continue;
   double dx=cx-px,dy=cy-py,dz=cz-pz;
   if(dx*dx+dy*dy+dz*dz>cull*cull)continue;
   var center=new net.minecraft.world.phys.Vec3(cx,cy,cz);
   int beams=ball.beams();
   int[] colors=ball.colors();
   boolean hemisphere=ExtraConfig.DISCO_HEMISPHERE.get();
   String key=ship==null?"disco/"+pos.asLong():"disco/"+ship.getUniqueId()+"/"+pos.asLong();
   for(int i=0;i<beams;i++){
    // The fan is built in the ball's own space and only then turned into a world direction, so "up" is
    // the ball's up: a body that rolls or pitches carries its upper hemisphere with it instead of
    // leaving the spots pointing at a fixed patch of world sky. On the default hemisphere setting every
    // cone is above the local horizontal: the elevation walks from nearly overhead down to just above
    // the horizon, so the ball reads as a lamp hanging from a ceiling instead of a flat ring of
    // searchlights, and the golden angle keeps consecutive cones from bunching into one visible spoke.
    double h=(i+0.5)/beams;
    double y=hemisphere?1.0-0.85*h:0.45;
    double r=Math.sqrt(Math.max(0.0,1.0-y*y));
    double angle=Math.toRadians(ball.spin()*tick+(hemisphere?i*137.507764:i*360.0/beams));
    var local=new Vector3d(Math.cos(angle)*r,y,Math.sin(angle)*r).normalize();
    // transformDirection rotates without translating, which is what a direction needs. It is normalized
    // again afterwards: a non-uniform pose scale would stretch the vector, and BeamLight refuses an
    // axis that is not unit length.
    var turned=pose==null?local:pose.transformDirection(local,new Vector3d()).normalize();
    var axis=new net.minecraft.world.phys.Vec3(turned.x,turned.y,turned.z);
    // The last argument is the spot's falloff profile, the same five the spotlights use.
    var light=BeamLight.create(center,axis,ball.range(),ball.width(),(int)Math.round(ball.intensity()),ball.falloff());
    world.put(key+"#"+i,
      new AreaSnapshot(light,AreaSnapshot.bounds(light),null,colors.length==0?0:colors[i%colors.length]));
   }
   // The ball no longer throws a steady glow of its own: the spots are the whole show, and a fixed
   // omnidirectional light under a spinning fan only ever hid the motion it is there to make.
  }
 }
 /** The show this ball is set to: its own block entity first, the client config only as a fallback. */ private static Ball settings(SubLevel ship,LevelChunk chunk,BlockPos pos){
  int beams=Math.max(1,ExtraConfig.DISCO_BEAMS.get());
  int range=ExtraConfig.DISCO_RANGE.get();
  int width=ExtraConfig.DISCO_WIDTH.get();
  double intensity=ExtraConfig.DISCO_INTENSITY.get();
  double spin=ExtraConfig.DISCO_SPIN.get();
  int falloff=ExtraConfig.DISCO_FALLOFF.get();
  int[] colors=colors(ExtraConfig.DISCO_COLORS.get());
  DiscoBallBlockEntity settings=null;
  if(chunk.getBlockEntity(pos) instanceof DiscoBallBlockEntity ball){
   settings=ball;
   beams=Math.max(1,ball.beams());
   range=Math.max(1,ball.range());
   width=Math.max(0,ball.width());
   intensity=ball.intensity();
   spin=ball.spin();
   falloff=ball.falloff();
   colors=ball.palette();
  }
  return new Ball(ship,pos,beams,range,width,intensity,spin,falloff,settings,colors);
 }
 /**
  * The colours to cycle through, written as a comma-separated list: {@code 1A2B3C,FF8800,4}.
  *
  * <p>Parsed by {@link SourceColor#parseList} -- the same parser the ball's own block entity uses, so the
  * page, the client fallback and a channel's message cannot disagree about what a list means.
  */
 private static int[] colors(String text){
  return SourceColor.parseList(text);
 }
 /** Finds the balls near the player: the main level, and the plots of the bodies around him. */
 private static void scan(ClientLevel level){
  BALLS.clear();
  var player=Minecraft.getInstance().player;
  if(player==null)return;
  int radius=Math.max(1,ExtraConfig.DISCO_SCAN_CHUNKS.get());
  BlockPos center=player.blockPosition();
  int cx=center.getX()>>4, cz=center.getZ()>>4;
  for(int x=-radius;x<=radius;x++){
   for(int z=-radius;z<=radius;z++){
    if(!level.hasChunk(cx+x,cz+z))continue;
    collect(null,level.getChunk(cx+x,cz+z));
   }
  }
  int onBodies=0;
  var container=SubLevelContainer.getContainer(level);
  if(container!=null){
   // Only the bodies that could be in view are asked, so this stays cheap however many exist.
   double reach=ExtraConfig.DISCO_CULL.get()+16.0;
   var around=new AABB(player.getX()-reach,player.getY()-reach,player.getZ()-reach,
     player.getX()+reach,player.getY()+reach,player.getZ()+reach);
   for(SubLevel ship:container.queryIntersecting(new BoundingBox3d(around))){
    if(ship.isRemoved())continue;
    for(var holder:ship.getPlot().getLoadedChunks()){
     LevelChunk chunk=holder.getChunk();
     if(chunk==null)continue;
     onBodies+=collect(ship,chunk);
    }
   }
  }
  if(LightingCommon.on(LightingCommon.GEN_DEBUG)){
   int total=BALLS.size();
   com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul disco: found {} ball(s) near {} ({} in the world, {} on bodies)",
     total,center,total-onBodies,onBodies);
  }
 }
 /** Every disco ball in one chunk, added to the list; returns how many were found. */
 private static int collect(SubLevel ship,LevelChunk chunk){
  int found=0;
  var sections=chunk.getSections();
  for(int index=0;index<sections.length;index++){
   var section=sections[index];
   if(section==null||section.hasOnlyAir())continue;
   if(!section.maybeHas(state->state.is(LightingBlocks.DISCO_BALL.get())))continue;
   int baseY=chunk.getSectionYFromSectionIndex(index)<<4;
   for(int y=0;y<16;y++){
    for(int z=0;z<16;z++){
     for(int x=0;x<16;x++){
      if(!section.getBlockState(x,y,z).is(LightingBlocks.DISCO_BALL.get()))continue;
      // Built from the chunk's own position, so the block entity is looked up at the position it is
      // really stored under: on a body that is plot space, not the world.
      var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
      BALLS.add(settings(ship,chunk,pos));
      found++;
     }
    }
   }
  }
  return found;
 }
}
