package dev.cbcmuzzlelight.client;

import dev.lambdaurora.lambdynlights.LambDynLights;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Owns only this mod's registrations, never clears other mods' Lamb lights. */
public final class LambAreaBackend {
    private static DynamicLightBehaviorManager manager;
    private static final Map<String,Behavior> active=new HashMap<>();
    static void install(DynamicLightBehaviorManager value) { manager=value; }
    static boolean ready() { return manager!=null && LambDynLights.get().config.getDynamicLightsMode().isEnabled(); }
    static void sync(Map<String,AreaSnapshot> next) {
        for(var it=active.entrySet().iterator();it.hasNext();) {
            var entry=it.next();
            if(!next.containsKey(entry.getKey())) {
                entry.getValue().removed=true; manager.remove(entry.getValue()); it.remove();
            }
        }
        for(var entry:next.entrySet()) {
            var behavior=active.get(entry.getKey());
            if(behavior==null) {
                behavior=new Behavior(entry.getValue()); active.put(entry.getKey(),behavior); manager.add(behavior);
            } else behavior.update(entry.getValue());
        }
    }
    static void clear() { if(manager!=null) sync(Map.of()); }

    public static final class Behavior implements DynamicLightBehavior {
        private volatile AreaSnapshot snapshot;
        private volatile boolean removed;
        private boolean dirty=true;
        Behavior(AreaSnapshot snapshot) { this.snapshot=snapshot; }
        void update(AreaSnapshot next) { dirty|=!next.same(snapshot); snapshot=next; }
        @Override public double lightAtPos(BlockPos pos,double falloffRatio) {
            return removed || LightReadScope.active()?0:snapshot.at(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        }
        @Override public BoundingBox getBoundingBox() {
            var b=snapshot.bounds;
            // The declared emissive volume covers the custom function's entire support, not just its center.
            return new BoundingBox((int)Math.floor(b.minX),(int)Math.floor(b.minY),(int)Math.floor(b.minZ),
                    (int)Math.ceil(b.maxX),(int)Math.ceil(b.maxY),(int)Math.ceil(b.maxZ));
        }
        @Override public boolean hasChanged() { boolean result=dirty; dirty=false; return result; }
        @Override public boolean isRemoved() { return removed; }
    }
}
