package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.MuzzleLight;
import dev.cbcmuzzlelight.ShellTrackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The bright trail behind a tracer shell, drawn from the positions the server sends.
 *
 * <p>Every message carries one shell's two endpoints — where it was a tick ago and where it is now — and
 * this runs CBC's own trail loop between them, frame for frame: ten frames interpolated at
 * {@code i * 0.1f}, each one particle of this mod's own trail type, with the shell's step as a small
 * backward nudge so the frames do not stack. That is the entire class. There is no local entity path, no
 * prediction, no lookahead, no remembered position, and no hand-drawn geometry: each of those existed in
 * some earlier version of this file and each was a place to be wrong. The server already knows both ends
 * of the step, and a lost message costs one tick of rope and nothing else.
 */
@EventBusSubscriber(modid=MuzzleLight.ID,value=Dist.CLIENT)
public final class TrailTrack {
 private static ClientLevel owner;
 private static long lastLog=Long.MIN_VALUE;
 private static int laid,shellsDrawn;
 private TrailTrack(){}
 /** The receiver the mod's network hook calls; no client class is resolved on a dedicated server. */
 public static void receive(ShellTrackPayload payload){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null)return;
  if(owner!=level){owner=level;laid=0;shellsDrawn=0;}
  if(!level.dimension().location().equals(payload.dimension()))return;
  if(!ExtraConfig.TRAIL_ENABLED.get())return;
  for(ShellTrackPayload.Shell shell:payload.shells()){
   lay(level,shell);
  }
 }
 /**
  * CBC's loop, verbatim in everything that matters.
  *
  * <p>The frame count is ten, as CBC uses it, except when the tick covered less than a frame's width:
  * a shell that has almost stopped would otherwise stack ten frames on one spot, which is a bright dot
  * rather than a rope.
  */
 private static void lay(ClientLevel level,ShellTrackPayload.Shell shell){
  Vec3 from=new Vec3(shell.px(),shell.py(),shell.pz());
  Vec3 to=new Vec3(shell.x(),shell.y(),shell.z());
  Vec3 motion=new Vec3(shell.mx(),shell.my(),shell.mz());
  int frames=from.distanceToSqr(to)>0.5625?10:1;
  double drift=ExtraConfig.TRAIL_DRIFT.get();
  for(int i=0;i<frames;i++){
   double partial=frames==1?1.0:i*0.1;
   Vec3 at=new Vec3(
     Mth.lerp(partial,from.x,to.x),
     Mth.lerp(partial,from.y,to.y),
     Mth.lerp(partial,from.z,to.z));
   // The frame's own velocity is the shell's, backwards and damped, plus a whisper of jitter -- the exact
   // form of the /mkTrail command in the version the user verified. The force flag is set: without it
   // vanilla culls a frame beyond 32 blocks, which is exactly the range this feed exists to cover.
   level.addParticle(MuzzleLight.TRAIL.get(),true,at.x,at.y,at.z,
     -motion.x*drift+(level.random.nextDouble()-0.5)*0.03,
     -motion.y*drift+(level.random.nextDouble()-0.5)*0.03,
     -motion.z*drift+(level.random.nextDouble()-0.5)*0.03);
   laid++;
  }
  shellsDrawn++;
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null){owner=null;return;}
  if(owner!=level){owner=level;laid=0;shellsDrawn=0;}
  if(ExtraConfig.TRAIL_DEBUG.get()&&(lastLog==Long.MIN_VALUE||level.getGameTime()-lastLog>=20)){
   if(laid>0||shellsDrawn>0){
    com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light trail: {} shell(s) drawn from the feed, {} frame(s) laid in the last second",
      shellsDrawn,laid);
   }
   lastLog=level.getGameTime();laid=0;shellsDrawn=0;
  }
 }
 /** What the debug command feeds: one straight step laid through the same code the feed uses. */
 public static void debugTrail(Vec3 from,Vec3 to){
  ClientLevel level=Minecraft.getInstance().level;
  if(level==null)return;
  Vec3 motion=to.subtract(from);
  lay(level,new ShellTrackPayload.Shell(-1,to.x,to.y,to.z,from.x,from.y,from.z,motion.x,motion.y,motion.z));
 }
}
