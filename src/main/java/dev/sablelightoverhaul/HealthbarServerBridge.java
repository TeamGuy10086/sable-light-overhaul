package dev.sablelightoverhaul;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerLevel;
/** Lazy common-side boundary: never resolves Healthbar client classes on a dedicated server. */
public final class HealthbarServerBridge {
 private static BiPredicate<ServerLevel,UUID> owns;
 private HealthbarServerBridge(){}
 public static void installAutocannonFeed(){
  if(!net.neoforged.fml.ModList.get().isLoaded("sablehealthbar"))return;
  try{dev.sablelightoverhaul.compat.HealthbarServerFlights.installAutocannonFeed();}
  catch(LinkageError incompatible){com.mojang.logging.LogUtils.getLogger().info("SLO: confirmed autocannon flight API unavailable");}
 }
 public static boolean owns(ServerLevel level,UUID carrier){
  if(owns==null){owns=(l,id)->false;
   if(net.neoforged.fml.ModList.get().isLoaded("sablehealthbar"))try{
    Class<?> api=Class.forName("dev.sablehealthbar.api.flight.FlightApi",false,HealthbarServerBridge.class.getClassLoader());
    Class<?> id=Class.forName("dev.sablehealthbar.api.flight.ProjectileId",false,HealthbarServerBridge.class.getClassLoader());
    api.getMethod("tracer",ServerLevel.class,id);
    owns=dev.sablelightoverhaul.compat.HealthbarServerFlights.provider();
   }catch(ReflectiveOperationException|LinkageError e){com.mojang.logging.LogUtils.getLogger().info("SLO: Healthbar flight metadata unavailable; keeping native feed");}
  }
  return carrier!=null&&owns.test(level,carrier);
 }
}
