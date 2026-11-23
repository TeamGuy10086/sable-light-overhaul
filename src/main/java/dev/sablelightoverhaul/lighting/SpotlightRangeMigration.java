package dev.sablelightoverhaul.lighting;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Pure NBT migration, run before a palette is decoded. Never loads chunks or touches world registries. */
public final class SpotlightRangeMigration {
    public static final String ID = "sablelightoverhaul:spotlight";
    private SpotlightRangeMigration() {}

    public static int readRange(CompoundTag tag) {
        return clamp(tag.contains("range", Tag.TAG_ANY_NUMERIC) ? tag.getInt("range")
            : tag.contains("slo_we_range", Tag.TAG_ANY_NUMERIC) ? tag.getInt("slo_we_range") : 1);
    }
    private static int clamp(int value) { return Math.max(1, Math.min(1024, value)); }
    public static int legacyRange(CompoundTag state) {
        if (!ID.equals(state.getString("Name"))) return 0;
        CompoundTag properties = state.getCompound("Properties");
        if (!properties.contains("range", Tag.TAG_STRING)) return 0;
        try { return clamp(Integer.parseInt(properties.getString("range"))); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid legacy spotlight range", invalid); }
    }
    private static void merge(CompoundTag entity, int range) {
        if (!entity.contains("range", Tag.TAG_ANY_NUMERIC))
            entity.putInt("range", entity.contains("slo_we_range", Tag.TAG_ANY_NUMERIC) ? readRange(entity) : range);
    }
    private static int[] ranges(ListTag palette) {
        int[] result = null;
        for (int i=0;i<palette.size();i++) {
            int range=legacyRange(palette.getCompound(i));
            if(range!=0){if(result==null)result=new int[palette.size()];result[i]=range;}
        }
        return result;
    }
    public static boolean chunk(CompoundTag root) {
        int x=root.getInt("xPos")<<4, z=root.getInt("zPos")<<4;
        ListTag entities=root.getList("block_entities",Tag.TAG_COMPOUND);
        boolean migrated=false;
        for (Tag entry:root.getList("sections",Tag.TAG_COMPOUND)) {
            CompoundTag section=(CompoundTag)entry;
            migrated|=section(section.getCompound("block_states"),entities,x,section.getByte("Y")<<4,z);
        }
        if (!entities.isEmpty()) root.put("block_entities",entities);
        return migrated;
    }
    /** Sable stores section *indices*, and block entities retain plot-space global coordinates. */
    public static void plotChunk(CompoundTag chunk, int blockX, int minSection, int blockZ) {
        ListTag entities=chunk.getList("block_entities",Tag.TAG_COMPOUND);
        CompoundTag sections=chunk.getCompound("sections");
        for (String key:sections.getAllKeys()) {
            int y=(Integer.parseInt(key)+minSection)<<4;
            section(sections.getCompound(key).getCompound("block_states"),entities,blockX,y,blockZ);
        }
        if (!entities.isEmpty()) chunk.put("block_entities",entities);
    }
    public static boolean section(CompoundTag states,ListTag entities,int x,int y,int z) {
        ListTag palette=states.getList("palette",Tag.TAG_COMPOUND);
        int[] ranges=ranges(palette);
        if(ranges==null)return false; // Modern chunks only inspect the small palette, never scan 4096 positions.
        int[] indices=unpack(states.getLongArray("data"),palette.size());
        Map<BlockPos,CompoundTag> byPosition=new HashMap<>();
        for(Tag entry:entities) {
            CompoundTag entity=(CompoundTag)entry;
            byPosition.put(new BlockPos(entity.getInt("x"),entity.getInt("y"),entity.getInt("z")),entity);
        }
        for(int i=0;i<4096;i++) {
            int range=ranges[indices[i]];
            if(range==0)continue;
            BlockPos pos=new BlockPos(x+(i&15),y+(i>>8),z+((i>>4)&15));
            CompoundTag entity=byPosition.get(pos);
            if(entity==null) {
                entity=new CompoundTag();entity.putString("id",ID);
                entity.putInt("x",pos.getX());entity.putInt("y",pos.getY());entity.putInt("z",pos.getZ());
                entities.add(entity);byPosition.put(pos,entity);
            }
            merge(entity,range);
        }
        // Removing range merges palette entries. Remap indices explicitly rather than relying on codec internals.
        int[] remap=compact(palette);
        for(int i=0;i<indices.length;i++)indices[i]=remap[indices[i]];
        if(palette.size()==1)states.remove("data");
        else states.putLongArray("data",pack(indices,palette.size()));
        return true;
    }
    static int bits(int size){return Math.max(4,32-Integer.numberOfLeadingZeros(size-1));}
    public static int[] unpack(long[] data,int size) {
        if(size<1)throw new IllegalArgumentException("Empty legacy block palette");
        int[] result=new int[4096];
        if(size==1)return result;
        int bits=bits(size), perLong=64/bits;
        if(data.length!=(4096+perLong-1)/perLong)throw new IllegalArgumentException("Invalid legacy block palette storage");
        long mask=(1L<<bits)-1;
        for(int i=0;i<4096;i++) {
            int index=(int)((data[i/perLong]>>>((i%perLong)*bits))&mask);
            if(index>=size)throw new IllegalArgumentException("Legacy block palette index out of bounds");
            result[i]=index;
        }
        return result;
    }
    public static long[] pack(int[] indices,int size) {
        int bits=bits(size),perLong=64/bits;
        long[] result=new long[(4096+perLong-1)/perLong];
        for(int i=0;i<4096;i++) result[i/perLong]|=(long)indices[i]<<((i%perLong)*bits);
        return result;
    }
    private static int[] compact(ListTag palette) {
        Map<CompoundTag,Integer> unique=new LinkedHashMap<>();
        int[] remap=new int[palette.size()];
        for(int i=0;i<remap.length;i++) {
            CompoundTag state=palette.getCompound(i).copy();
            if(legacyRange(state)!=0)state.getCompound("Properties").remove("range");
            remap[i]=unique.computeIfAbsent(state,ignored->unique.size());
        }
        palette.clear();unique.keySet().forEach(palette::add);
        return remap;
    }
    /** Copy the blocks per palette: a structure can hold different ranges in alternate palettes. */
    public static ListTag structureBlocks(ListTag palette,ListTag blocks) {
        int[] ranges=ranges(palette);
        if(ranges==null)return blocks;
        ListTag copy=blocks.copy();
        for(Tag entry:copy) {
            CompoundTag block=(CompoundTag)entry;
            int index=block.getInt("state");
            if(index<0||index>=ranges.length)throw new IllegalArgumentException("Invalid structure palette index");
            if(ranges[index]!=0)carry(block,"nbt",ranges[index]);
        }
        return copy;
    }
    private static void carry(CompoundTag block,String key,int range) {
        CompoundTag entity=block.getCompound(key);
        if(!entity.contains("id",Tag.TAG_STRING))entity.putString("id",ID);
        merge(entity,range);block.put(key,entity);
    }
    public static void contraption(Tag root,boolean paletted) {
        if(paletted && root instanceof CompoundTag compound) {
            ListTag palette=compound.getList("Palette",Tag.TAG_COMPOUND);
            int[] ranges=ranges(palette);if(ranges==null)return;
            ListTag blocks=compound.getList("BlockList",Tag.TAG_COMPOUND);
            // Validate all indices before modifying the input.
            for(Tag entry:blocks) {
                int index=((CompoundTag)entry).getInt("State");
                if(index<0||index>=ranges.length)throw new IllegalArgumentException("Invalid contraption palette index");
            }
            for(Tag entry:blocks) {
                CompoundTag block=(CompoundTag)entry;int range=ranges[block.getInt("State")];
                if(range!=0) {carry(block,"Data",range);if(block.contains("UpdateTag",Tag.TAG_COMPOUND))carry(block,"UpdateTag",range);}
            }
            int[] remap=compact(palette);
            for(Tag entry:blocks) {CompoundTag block=(CompoundTag)entry;block.putInt("State",remap[block.getInt("State")]);}
        } else if(root instanceof ListTag blocks) {
            for(Tag entry:blocks) {
                CompoundTag block=(CompoundTag)entry;int range=legacyRange(block.getCompound("Block"));
                if(range!=0){carry(block,"Data",range);if(block.contains("UpdateTag",Tag.TAG_COMPOUND))carry(block,"UpdateTag",range);}
            }
        }
    }
}
