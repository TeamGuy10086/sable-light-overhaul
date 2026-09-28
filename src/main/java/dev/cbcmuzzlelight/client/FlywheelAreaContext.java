package dev.cbcmuzzlelight.client;

import java.util.List;
import net.minecraft.core.SectionPos;

/** Scoped to one collector invocation; nested calls and worker threads cannot leak receiver coordinates. */
public final class FlywheelAreaContext {
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    public record Context(int x,int y,int z,List<AreaSnapshot> areas) {}
    public static Context enter(long section) {
        var old=CURRENT.get(); var areas=AdvancedLights.atSection(section);
        if(areas.isEmpty()) CURRENT.remove();
        else CURRENT.set(new Context(SectionPos.x(section)*16,SectionPos.y(section)*16,SectionPos.z(section)*16,areas));
        return old;
    }
    public static void restore(Context old) { if(old==null) CURRENT.remove(); else CURRENT.set(old); }
    public static int light(int x,int y,int z,int original) {
        var context=CURRENT.get();
        if(context==null || original>=15) return original;
        double result=original;
        for(var area:context.areas()) result=Math.max(result,area.at(context.x()+x+0.5,context.y()+y+0.5,context.z()+z+0.5));
        return (int)Math.ceil(result);
    }
}
