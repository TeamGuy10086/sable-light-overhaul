package dev.sablelightoverhaul;
import java.util.*;
import java.util.function.LongConsumer;
/** Session-qualified identity and ownership survive local visual expiry. No optional-mod links. */
public final class FlightOwnership {
 public record Key(UUID session,long number){}
 public record Entry(Key key,UUID carrier,long visualId,boolean tracer,boolean auto,String dimension){}
 private final Map<Key,Entry> entries=new LinkedHashMap<>();
 private final Map<UUID,Key> carriers=new HashMap<>();
 private final Map<Long,Key> visuals=new HashMap<>();
 private final LongConsumer retire,finish;
 private long next=Long.MIN_VALUE;
 public FlightOwnership(LongConsumer retire){this(retire,retire);}
 public FlightOwnership(LongConsumer retire,LongConsumer finish){this.retire=retire;this.finish=finish;}
 public Entry launch(Key key,UUID carrier,boolean tracer,boolean auto,String dimension){
  Entry old=entries.remove(key);if(old!=null){retire.accept(old.visualId());carriers.remove(old.carrier(),key);visuals.remove(old.visualId());}
  Key alias=carriers.get(carrier);if(alias!=null&&!alias.equals(key))end(alias);
  Entry e=new Entry(key,carrier,old==null?next++:old.visualId(),tracer,auto,dimension);
  entries.put(key,e);carriers.put(carrier,key);visuals.put(e.visualId(),key);return e;
 }
 public Entry visual(long id){return entries.get(visuals.get(id));}
 public Entry get(Key key){return entries.get(key);}
 public List<Entry> entries(){return List.copyOf(entries.values());}
 public Entry carrier(UUID carrier){return entries.get(carriers.get(carrier));}
 public boolean owns(UUID carrier){return carrier!=null&&carriers.containsKey(carrier);}
 public void visualRemoved(Key key){Entry e=entries.get(key);if(e!=null)finish.accept(e.visualId());}
 public void end(Key key){remove(key,finish);}
 public void handoff(Key key){remove(key,retire);}
 private void remove(Key key,LongConsumer action){
  Entry e=entries.remove(key);
  if(e!=null){carriers.remove(e.carrier(),key);visuals.remove(e.visualId());action.accept(e.visualId());}
 }
 public void reset(){for(var e:entries.values())retire.accept(e.visualId());entries.clear();carriers.clear();visuals.clear();}
}
