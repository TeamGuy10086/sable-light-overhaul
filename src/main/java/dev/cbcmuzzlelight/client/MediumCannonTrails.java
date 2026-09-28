package dev.cbcmuzzlelight.client;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.ModList;

/**
 * The medium cannon's shells, which live in CBC Neo Warfare and may not be installed at all.
 *
 * <p>The addon is not on this mod's compile classpath -- it is optional, and the mixins that touch it name
 * their targets as strings for the same reason -- so its shell type is looked up by name once and asked
 * through reflection afterwards. Nothing here can fail to load when the addon is missing, and a different
 * addon version with the same class and method still works.
 *
 * <p>The medium cannon shells do not extend CBC's {@code AbstractBigCannonProjectile}: they branch off
 * {@code AbstractCannonProjectile} directly and carry their own {@code isTracer} flag, which is why the
 * big cannon test cannot see them and they need a test of their own. The server-side trail feed needs the
 * same questions answered, which is why this is public rather than package-private.
 */
public final class MediumCannonTrails {
 /** The class CBC Neo Warfare puts its medium cannon shells under. */
 private static final String SHELL_TYPE="riftyboi.cbcmodernwarfare.munitions.medium_cannon.AbstractMediumcannonProjectile";
 /** Mod id of CBC Neo Warfare, as declared in its own metadata. */
 private static final String MOD_ID="cbcmodernwarfare";
 private static boolean looked;
 private static Class<?> shellClass;
 /** One resolved no-argument boolean method per name; a failed lookup is remembered, not retried. */
 private static final Map<String,Method> FLAGS=new HashMap<>();
 private MediumCannonTrails(){}
 /** True when CBC Neo Warfare is loaded, so its shells can be recognised at all. */
 public static boolean present(){return ModList.get().isLoaded(MOD_ID);}
 /** True when this entity is a medium cannon shell, tracer or not. */
 public static boolean shellType(Object entity){
  Class<?> type=shellClass();
  return type!=null&&type.isInstance(entity);
 }
 /** True for a medium cannon shell flying with its tracer lit. */
 public static boolean tracer(Object entity){
  return flag(entity,"isTracer");
 }
 /**
  * True for a medium cannon shell that has landed.
  *
  * <p>CBC keeps a landed projectile for 400 ticks before removing it, so "still in the world" is not the
  * same as "still flying": a trail that stops only at removal keeps drawing a dot where the shell fell.
  */
 public static boolean inGround(Object entity){
  return flag(entity,"isInGround");
 }
 /** The shell class, or null when the addon is absent or has moved its classes. */
 private static Class<?> shellClass(){
  if(!looked){
   looked=true;
   try{
    shellClass=Class.forName(SHELL_TYPE,false,MediumCannonTrails.class.getClassLoader());
   }catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){
    shellClass=null;
   }
  }
  return shellClass;
 }
 /** One no-argument boolean method of the shell class, by name. */
 private static Method flagMethod(String name){
  if(FLAGS.containsKey(name))return FLAGS.get(name);
  Method method=null;
  Class<?> type=shellClass();
  if(type!=null){
   try{
    method=type.getMethod(name);
   }catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){
    method=null;
   }
  }
  // The null is recorded too: a lookup that failed must not be repeated on every tick of every shell.
  FLAGS.put(name,method);
  return method;
 }
 /** Asks one flag, treating anything that cannot be answered as false. */
 private static boolean flag(Object entity,String name){
  Method method=flagMethod(name);
  if(method==null||!method.getDeclaringClass().isInstance(entity))return false;
  try{
   return Boolean.TRUE.equals(method.invoke(entity));
  }catch(ReflectiveOperationException|RuntimeException ignored){
   // A shell that cannot be asked is treated as not flying: never worth an exception in a tick.
   return false;
  }
 }
}