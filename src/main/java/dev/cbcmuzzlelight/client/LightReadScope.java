package dev.cbcmuzzlelight.client;
import java.util.*;
/** One immutable light generation per mesh build, with proper nesting and exception cleanup. */
public final class LightReadScope implements AutoCloseable {
 private static final ThreadLocal<Map<Long,List<AreaSnapshot>>> CURRENT=new ThreadLocal<>();
 private final Map<Long,List<AreaSnapshot>> previous;
 private LightReadScope(Map<Long,List<AreaSnapshot>> snapshot){previous=CURRENT.get();CURRENT.set(snapshot);}
 public static LightReadScope enter(){return new LightReadScope(CURRENT.get()==null?AdvancedLights.snapshot():CURRENT.get());}
 public static LightReadScope enter(Map<Long,List<AreaSnapshot>> snapshot){return new LightReadScope(snapshot);}
 public static boolean active(){return CURRENT.get()!=null;}
 public static Map<Long,List<AreaSnapshot>> current(){var value=CURRENT.get();return value==null?AdvancedLights.snapshot():value;}
 @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
