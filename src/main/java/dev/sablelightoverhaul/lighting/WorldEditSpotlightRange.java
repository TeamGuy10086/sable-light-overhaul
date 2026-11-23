package dev.sablelightoverhaul.lighting;

import java.util.*;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.internal.util.VarIntIterator;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import org.enginehub.linbus.tree.*;

/** Loaded only by the optional WorldEdit reader hook. Metadata travels with the palette, not a global cache. */
public final class WorldEditSpotlightRange {
    private WorldEditSpotlightRange() {}
    public static int range(String state) {
        if(!state.startsWith(SpotlightRangeMigration.ID+"[")||!state.endsWith("]"))return 0;
        for(String property:state.substring(state.indexOf('[')+1,state.length()-1).split(","))
            if(property.startsWith("range="))return Math.max(1,Math.min(1024,Integer.parseInt(property.substring(6))));
        return 0;
    }
    public static String withoutRange(String state) {
        if(range(state)==0)return state;
        StringJoiner properties=new StringJoiner(",",SpotlightRangeMigration.ID+"[","]");
        for(String property:state.substring(state.indexOf('[')+1,state.length()-1).split(","))
            if(!property.startsWith("range="))properties.add(property);
        return properties.toString();
    }
    public static Map<Integer,BlockState> remember(LinCompoundTag raw,Map<Integer,BlockState> decoded) {
        Map<Integer,Integer> ranges=new HashMap<>();
        raw.value().forEach((name,tag)->{int range=range(name);if(range!=0&&tag instanceof LinIntTag id)ranges.put(id.valueAsInt(),range);});
        return ranges.isEmpty()?decoded:new RangePalette(decoded,ranges);
    }
    public static void restore(Clipboard clipboard,Map<Integer,BlockState> palette,byte[] data) throws WorldEditException {
        if(!(palette instanceof RangePalette legacy))return;
        int width=clipboard.getRegion().getWidth(),length=clipboard.getRegion().getLength(),index=0;
        VarIntIterator iterator=new VarIntIterator(data);
        while(iterator.hasNext()) {
            Integer range=legacy.ranges.get(iterator.nextInt());
            if(range!=null) {
                BlockVector3 pos=clipboard.getMinimumPoint().add(index%width,index/(width*length),(index/width)%length);
                var block=clipboard.getFullBlock(pos);
                // Do not resurrect an entry WorldEdit rejected for some unrelated reason.
                if(SpotlightRangeMigration.ID.equals(block.getBlockType().id())) {
                    var ref=block.getNbtReference();LinCompoundTag old=ref==null?null:ref.getValue();
                    var builder=old==null?LinCompoundTag.builder():old.toBuilder();
                    if(old==null||!old.value().containsKey("range")) {
                        var alias=old==null?null:old.findTag("slo_we_range",LinTagType.intTag());
                        builder.putInt("range",alias==null?range:alias.valueAsInt());
                    }
                    builder.putString("id",SpotlightRangeMigration.ID);
                    clipboard.setBlock(pos,block.toImmutableState().toBaseBlock(builder.build()));
                }
            }
            index++;
        }
    }
    private static final class RangePalette extends HashMap<Integer,BlockState> {
        final Map<Integer,Integer> ranges;
        RangePalette(Map<Integer,BlockState> states,Map<Integer,Integer> ranges){super(states);this.ranges=ranges;}
    }
}
