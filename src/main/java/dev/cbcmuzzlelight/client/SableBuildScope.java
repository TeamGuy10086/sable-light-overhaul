package dev.cbcmuzzlelight.client;
import java.util.*;
public final class SableBuildScope implements AutoCloseable {
 private static final Map<Object,BuildOrder> ORDERS=Collections.synchronizedMap(new WeakHashMap<>());
 private static final ThreadLocal<Ticket> CURRENT=new ThreadLocal<>();
 public record Ticket(Object section,BuildOrder order,long version,Map<Long,List<AreaSnapshot>> lights){}
 private final Ticket old;
 private SableBuildScope(Ticket ticket){old=CURRENT.get();CURRENT.set(ticket);}
 public static Ticket request(Object section){BuildOrder order; synchronized(ORDERS){order=ORDERS.computeIfAbsent(section,k->new BuildOrder());}return new Ticket(section,order,order.request(),AdvancedLights.snapshot());}
 public static SableBuildScope enter(Ticket ticket){return new SableBuildScope(ticket);}
 public static Ticket current(){return CURRENT.get();}
 @Override public void close(){if(old==null)CURRENT.remove();else CURRENT.set(old);}
}
