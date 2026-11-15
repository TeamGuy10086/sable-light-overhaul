package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AreaLight;
import dev.sablelightoverhaul.FlareMotion;
import dev.sablelightoverhaul.FlarePayload;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.lighting.SourceColor;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import dev.sablelightoverhaul.lighting.SmokeParticleData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Vector4f;

/** Transient, dimension-scoped command effects. No entities or chunk tickets. */
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class DebugFlares {
 private static ResourceLocation dimension;
 private static List<FlarePayload.State> states=List.of();
 private static final Vector4f[] positions=new Vector4f[FlarePayload.MAX_FLARES],colors=new Vector4f[FlarePayload.MAX_FLARES];
 private static final float[] tint=new float[3];
 static {for(int i=0;i<positions.length;i++){positions[i]=new Vector4f();colors[i]=new Vector4f();}}
 private DebugFlares(){}
 public static void receive(FlarePayload payload){dimension=payload.dimension();states=payload.states();}
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){dimension=null;states=List.of();}
 private static boolean active(ClientLevel level){return level!=null && level.dimension().location().equals(dimension);}
 static boolean hasStates(){return !states.isEmpty() && active(Minecraft.getInstance().level);}
 private static double age(FlarePayload.State s,ClientLevel level,double partial){return Math.max(0,(level.getGameTime()-s.bornTick()+partial)/20.0);}
 private static double brightness(FlarePayload.State s,ClientLevel level,double partial){return FlareMotion.brightness(s.id(),age(s,level,partial),s.durationTicks()/20.0);}
 private static int palette(FlarePayload.State s){return s.illumination()?SourceColor.WARM:SourceColor.CUSTOM|s.rgb();}
 public static void append(ClientLevel level,Map<String,AreaSnapshot> world){
  if(!active(level))return;
  for(var s:states){
   double bright=brightness(s,level,0);if(bright<=0)continue;
   double y=FlareMotion.height(s.y(),age(s,level,0));
   // 150 degrees FULL opening; the original spotlight's axial range is reused.
   AreaLight light=s.illumination()
     ?AreaLight.axialFadeBeam(s.x(),y,s.z(),160,s.brightness())
     :AreaLight.signalPoint(s.x(),y,s.z(),s.brightness(),s.angleDegrees());
   world.put("flare/"+s.id(),new AreaSnapshot(light,AreaSnapshot.bounds(light),null,palette(s),false).withRadiance((float)bright));
  }
 }
 /** Per-frame source positions keep the descending burning point smooth even between ticks. */
 public static Vector4f position(int index){
  var out=positions[index];var mc=Minecraft.getInstance();var level=mc.level;
  if(!ClientFlashes.effectsEnabled()||!active(level)||index>=states.size())return out.zero();
  var s=states.get(index);double partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
  if(brightness(s,level,partial)<=0)return out.zero();
  var camera=mc.gameRenderer.getMainCamera().getPosition();
  return out.set((float)(s.x()-camera.x),(float)(FlareMotion.height(s.y(),age(s,level,partial))-camera.y),(float)(s.z()-camera.z),s.illumination()?.22F:.14F);
 }
 public static Vector4f color(int index){
  var out=colors[index];var mc=Minecraft.getInstance();var level=mc.level;
  if(!ClientFlashes.effectsEnabled()||!active(level)||index>=states.size())return out.zero();
  var s=states.get(index);SourceColor.rgb(palette(s),(int)level.getGameTime(),tint,ExtraConfig.GPU_RED.get().floatValue(),ExtraConfig.GPU_GREEN.get().floatValue(),ExtraConfig.GPU_BLUE.get().floatValue());
  return out.set(tint[0],tint[1],tint[2],(float)brightness(s,level,mc.getTimer().getGameTimeDeltaPartialTick(false))*(s.illumination()?1.4F:1F)*(s.brightness()/15F));
 }
 private static final SmokeParticleData WIND_SMOKE=new SmokeParticleData(.25f,0xFFFFFF,18,.75f,.55f);
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();var level=mc.level;
  if(!active(level)||mc.isPaused()||!ClientFlashes.effectsEnabled())return;
  // Four smoke puffs and four small flame particles per second, at most sixteen emitters.
  for(var s:states){
   if(Math.floorMod(level.getGameTime()+s.id(),5)!=0||brightness(s,level,0)<=0)continue;
   double y=FlareMotion.height(s.y(),age(s,level,0));
   if(mc.player==null||mc.player.distanceToSqr(s.x(),y,s.z())>256*256)continue;
   double dx=(level.random.nextDouble()-.5)*.08,dz=(level.random.nextDouble()-.5)*.08;
   var wind=ParticleWind.step();
   var smoke=wind.x()==0&&wind.z()==0 ? ParticleTypes.SMOKE : WIND_SMOKE;
   level.addParticle(smoke,s.x()+dx,y+.08,s.z()+dz,dx*.25,.04,dz*.25);
   level.addParticle(ParticleTypes.SMALL_FLAME,s.x()+dx,y,s.z()+dz,0,.01,0);
  }
 }
}
