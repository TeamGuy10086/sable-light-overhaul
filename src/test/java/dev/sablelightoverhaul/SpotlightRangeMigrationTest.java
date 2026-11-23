package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.SpotlightRangeMigration;
import dev.sablelightoverhaul.lighting.WorldEditSpotlightRange;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class SpotlightRangeMigrationTest {
    static CompoundTag lamp(int range) {
        CompoundTag state=new CompoundTag(),props=new CompoundTag();state.putString("Name",SpotlightRangeMigration.ID);
        props.putString("range",""+range);props.putString("facing","up");props.putString("mode","0");props.putString("visible","true");
        state.put("Properties",props);return state;
    }
    static CompoundTag state(String name) {CompoundTag t=new CompoundTag();t.putString("Name",name);return t;}
    static ListTag list(Tag... tags){ListTag result=new ListTag();for(Tag tag:tags)result.add(tag);return result;}
    static CompoundTag packed(ListTag palette,int[] indices) {
        CompoundTag t=new CompoundTag();t.put("palette",palette);
        if(palette.size()>1)t.putLongArray("data",SpotlightRangeMigration.pack(indices,palette.size()));return t;
    }
    static CompoundTag entity(int x,int y,int z) {
        CompoundTag e=new CompoundTag();e.putString("id",SpotlightRangeMigration.ID);e.putInt("x",x);e.putInt("y",y);e.putInt("z",z);return e;
    }
    @Test void chunkKeepsPerPositionRangesAfterPaletteCollapseAndNbtRoundTrip() throws Exception {
        int[] indices=new int[4096];indices[0]=1;indices[15+16*7+256*9]=2;indices[4095]=3;
        CompoundTag states=packed(list(state("minecraft:air"),lamp(1),lamp(512),lamp(1024)),indices);
        CompoundTag section=new CompoundTag();section.putByte("Y",(byte)-2);section.put("block_states",states);
        CompoundTag chunk=new CompoundTag();chunk.putInt("xPos",-3);chunk.putInt("zPos",5);chunk.put("sections",list(section));
        CompoundTag original=entity(-48,-32,80);original.putDouble("azimuth",127.5);original.putString("channel","retain-me");
        chunk.put("block_entities",list(original));assertTrue(SpotlightRangeMigration.chunk(chunk));
        ListTag entities=chunk.getList("block_entities",10);
        assertEquals(3,entities.size());assertEquals(1,original.getInt("range"));assertEquals(127.5,original.getDouble("azimuth"));
        assertEquals("retain-me",original.getString("channel"));
        assertEquals(-33,entities.getCompound(1).getInt("x"));assertEquals(-23,entities.getCompound(1).getInt("y"));assertEquals(87,entities.getCompound(1).getInt("z"));
        assertEquals(512,entities.getCompound(1).getInt("range"));assertEquals(1024,entities.getCompound(2).getInt("range"));
        assertEquals(2,states.getList("palette",10).size());
        int[] remapped=SpotlightRangeMigration.unpack(states.getLongArray("data"),2);
        for(int i=0;i<4096;i++)assertEquals(indices[i]==0?0:1,remapped[i]);
        var bytes=new ByteArrayOutputStream();NbtIo.write(chunk,new DataOutputStream(bytes));
        var restored=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertFalse(SpotlightRangeMigration.chunk(restored));assertEquals(chunk,restored,"Reload is idempotent");
    }
    @Test void paddedFiveBitPaletteCompactsAcrossLongBoundaries() {
        ListTag palette=new ListTag();for(int i=1;i<=19;i++)palette.add(lamp(i*30));
        int[] indices=new int[4096];for(int i=0;i<4096;i++)indices[i]=i%19;
        CompoundTag states=packed(palette,indices);ListTag entities=new ListTag();
        SpotlightRangeMigration.section(states,entities,0,0,0);
        assertEquals(4096,entities.size());assertEquals(1,palette.size());assertFalse(states.contains("data"));
        for(int i=0;i<4096;i++)assertEquals((i%19+1)*30,entities.getCompound(i).getInt("range"));
    }
    @Test void modernNbtWinsAndOldWorldEditAliasRemainsReadable() {
        CompoundTag a=entity(0,0,0);a.putInt("range",73);a.putInt("slo_we_range",14);
        CompoundTag b=entity(1,0,0);b.putInt("slo_we_range",91);
        int[] indices=new int[4096];indices[0]=1;indices[1]=1;
        SpotlightRangeMigration.section(packed(list(state("minecraft:air"),lamp(500)),indices),list(a,b),0,0,0);
        assertEquals(73,a.getInt("range"));assertEquals(91,b.getInt("range"));
        assertEquals(73,SpotlightRangeMigration.readRange(a));assertEquals(1,SpotlightRangeMigration.readRange(new CompoundTag()));
        a.putInt("range",99999);assertEquals(1024,SpotlightRangeMigration.readRange(a));
        a.putInt("range",-3);assertEquals(1,SpotlightRangeMigration.readRange(a));
    }
    @Test void unrelatedOrAlreadyMigratedSectionsDoNotScanOrChangeData() {
        CompoundTag states=packed(list(state("minecraft:air"),state(SpotlightRangeMigration.ID)),new int[4096]);
        states.putLongArray("data",new long[]{123}); // Deliberately invalid: no legacy range means no decoding.
        CompoundTag before=states.copy();ListTag entities=new ListTag();
        SpotlightRangeMigration.section(states,entities,0,0,0);assertEquals(before,states);assertTrue(entities.isEmpty());
    }
    @Test void malformedOldStorageFailsBeforePartiallyChangingEntities() {
        CompoundTag states=packed(list(state("minecraft:air"),lamp(123)),new int[4096]);states.putLongArray("data",new long[]{1});
        CompoundTag before=states.copy();ListTag entities=new ListTag();
        assertThrows(IllegalArgumentException.class,()->SpotlightRangeMigration.section(states,entities,0,0,0));
        assertEquals(before,states);assertTrue(entities.isEmpty());
        long[] bad=new long[256];bad[255]=15;
        assertThrows(IllegalArgumentException.class,()->SpotlightRangeMigration.unpack(bad,2));
    }
    @Test void sablePlotUsesGlobalPlotCoordinatesAndSectionIndices() {
        int[] indices=new int[4096];indices[0]=1;
        CompoundTag section=new CompoundTag();section.put("block_states",packed(list(state("minecraft:air"),lamp(403)),indices));
        CompoundTag sections=new CompoundTag();sections.put("3",section);
        CompoundTag chunk=new CompoundTag();chunk.put("sections",sections);
        CompoundTag existing=entity(160000,-16,-320000);existing.putDouble("angle",41);chunk.put("block_entities",list(existing));
        SpotlightRangeMigration.plotChunk(chunk,160000,-4,-320000);
        assertEquals(1,chunk.getList("block_entities",10).size());assertEquals(403,existing.getInt("range"));assertEquals(41,existing.getDouble("angle"));
    }
    @Test void alternateStructurePalettesKeepIndependentRanges() {
        CompoundTag block=new CompoundTag();block.putInt("state",0);ListTag blocks=list(block);
        var first=SpotlightRangeMigration.structureBlocks(list(lamp(120)),blocks);
        var second=SpotlightRangeMigration.structureBlocks(list(lamp(900)),blocks);
        assertFalse(block.contains("nbt"));assertEquals(120,first.getCompound(0).getCompound("nbt").getInt("range"));
        assertEquals(900,second.getCompound(0).getCompound("nbt").getInt("range"));
    }
    @Test void createPalettedContraptionPreservesEachRangeAndClientUpdateTag() {
        CompoundTag a=new CompoundTag(),b=new CompoundTag(),root=new CompoundTag();a.putInt("State",0);b.putInt("State",1);
        a.put("UpdateTag",new CompoundTag());a.putLong("Pos",123L);b.putLong("Pos",456L);
        root.put("Palette",list(lamp(64),lamp(600)));root.put("BlockList",list(a,b));
        SpotlightRangeMigration.contraption(root,true);
        assertEquals(64,a.getCompound("Data").getInt("range"));assertEquals(64,a.getCompound("UpdateTag").getInt("range"));
        assertEquals(600,b.getCompound("Data").getInt("range"));assertEquals(0,b.getInt("State"));assertEquals(123L,a.getLong("Pos"));
        assertEquals(1,root.getList("Palette",10).size());CompoundTag before=root.copy();
        SpotlightRangeMigration.contraption(root,true);assertEquals(before,root);
    }
    @Test void oldUnpalettedContraptionAlsoMigrates() {
        CompoundTag block=new CompoundTag();block.put("Block",lamp(912));
        SpotlightRangeMigration.contraption(list(block),false);assertEquals(912,block.getCompound("Data").getInt("range"));
    }
    @Test void worldEditOldStringPalettesRemoveOnlyOurRange() {
        String old=SpotlightRangeMigration.ID+"[facing=west,range=350,mode=4,visible=false]";
        assertEquals(350,WorldEditSpotlightRange.range(old));
        assertEquals(SpotlightRangeMigration.ID+"[facing=west,mode=4,visible=false]",WorldEditSpotlightRange.withoutRange(old));
        assertEquals("other:lamp[range=350]",WorldEditSpotlightRange.withoutRange("other:lamp[range=350]"));
        assertEquals(0,WorldEditSpotlightRange.range(SpotlightRangeMigration.ID+"[mode=4]"));
    }
}
