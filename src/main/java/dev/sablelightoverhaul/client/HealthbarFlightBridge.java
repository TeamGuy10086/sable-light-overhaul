package dev.sablelightoverhaul.client;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
/** Optional client linkage boundary; no Healthbar symbols in its public contract or constant pool. */
public final class HealthbarFlightBridge {
 public record Light(long id,boolean auto,Vec3 position){}
 public interface Adapter {void tick(ClientLevel level);boolean owns(UUID carrier);List<Light> lights(float partial);Vec3 position(long id,float partial);Vec3 heading(UUID carrier);}
 private static Adapter adapter;
 private static boolean attempted;
 // Numeric feed ids alone cannot identify an untracked/reparented carrier. Entries live only for feed TTL.
 private record Carrier(UUID uuid,long tick){}
 private static final Map<Integer,Carrier> carriers=new HashMap<>();
 private static ClientLevel owner;
 private HealthbarFlightBridge(){}
 public static void tick(ClientLevel level){
  if(owner!=level){owner=level;carriers.clear();}
  HostileMathShells.tick(level);
  if(!attempted){attempted=true;
   if(net.neoforged.fml.ModList.get().isLoaded("sablehealthbar"))try{
    var loader=HealthbarFlightBridge.class.getClassLoader();
    Class<?> api=Class.forName("dev.sablehealthbar.api.client.FlightClientApi",false,loader);
    Class<?> id=Class.forName("dev.sablehealthbar.api.flight.ProjectileId",false,loader);api.getMethod("tracer",id);
    adapter=(Adapter)Class.forName("dev.sablelightoverhaul.client.compat.HealthbarFlightAdapter",true,loader).getMethod("create").invoke(null);
    com.mojang.logging.LogUtils.getLogger().info("SLO: Healthbar mathematical-flight adapter installed");
   }catch(ReflectiveOperationException|LinkageError e){com.mojang.logging.LogUtils.getLogger().warn("SLO: Healthbar flight adapter unavailable; preserving native feed",e);}
  }
  if(adapter!=null)adapter.tick(level);
  if(level!=null)carriers.entrySet().removeIf(e->level.getGameTime()-e.getValue().tick()>6);
 }
 public static boolean owns(UUID carrier){return carrier!=null&&(HostileMathShells.ended(owner,carrier)
     ||adapter!=null&&!HostileMathShells.released(owner,carrier)&&adapter.owns(carrier));}
 static Vec3 heading(UUID carrier){return adapter==null?null:adapter.heading(carrier);}
 public static boolean suppress(ClientLevel level,int id,UUID carrier){
  if(owner!=level){owner=level;carriers.clear();}
  var e=level.getEntity(id);if(carrier==null&&e!=null)carrier=e.getUUID();
  if(carrier!=null)carriers.put(id,new Carrier(carrier,level.getGameTime()));
  boolean managed=suppress(id);if(managed){ProjectileStreaks.retire(id);ExtraLights.retireFed(id);}
  return managed;
 }
 public static boolean suppress(long id){
  if(id<Integer.MIN_VALUE||id>Integer.MAX_VALUE)return false;
  Carrier c=carriers.get((int)id);return c!=null&&owns(c.uuid());
 }
 public static void claim(UUID carrier){
  for(var e:carriers.entrySet())if(e.getValue().uuid().equals(carrier)){ProjectileStreaks.retire(e.getKey());ExtraLights.retireFed(e.getKey());}
 }
 public static boolean mathKey(String key){
  if(key==null||!key.startsWith("tracer/"))return false;
  try{return Long.parseLong(key.substring(7))<Integer.MIN_VALUE;}catch(NumberFormatException e){return false;}
 }
 public static Vec3 position(String key,float partial){return !mathKey(key)||adapter==null?null:adapter.position(Long.parseLong(key.substring(7)),partial);}
 public static List<Light> lights(float partial){return adapter==null?List.of():adapter.lights(partial);}
}
