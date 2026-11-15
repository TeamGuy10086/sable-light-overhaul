package dev.sablelightoverhaul;
import java.util.*;

/** A draw-only combined queue. Tick/admission queues remain intact, even on draw failure. */
public final class TransientParticleBatch<K,T> implements AutoCloseable {
    private final Queue<T> merged=new ArrayDeque<>(),empty=new ArrayDeque<>();
    private Map<K,Queue<T>> map;
    private K source,destination;
    private Queue<T> originalSource,originalDestination;
    public boolean begin(Map<K,Queue<T>> map,K source,K destination,int cap){
        if(this.map!=null)return false;
        Queue<T> a=map.get(source),b=map.get(destination);
        if(a==null||b==null||a.isEmpty()||b.isEmpty()||(cap>0&&(long)a.size()+b.size()>cap))return false;
        this.map=map;this.source=source;this.destination=destination;originalSource=a;originalDestination=b;
        merged.addAll(b);merged.addAll(a);map.put(source,empty);map.put(destination,merged);return true;
    }
    @Override public void close(){
        if(map==null)return;
        map.put(source,originalSource);map.put(destination,originalDestination);
        merged.clear();empty.clear();map=null;originalSource=null;originalDestination=null;source=null;destination=null;
    }
}
