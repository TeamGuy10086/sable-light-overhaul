package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundDustFieldPayloadTest {
    private static final Settings SETTINGS=new Settings(16,4,3,.55,3.5,1.8,.7,.3,1,1.25);
    private static GroundDustFieldPayload event(List<Patch> patches){
        return new GroundDustFieldPayload(ResourceLocation.parse("minecraft:overworld"),new Point(100,80,200),
            new Point(0,0,1),90,17,SETTINGS,patches);
    }
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    @Test void fullFieldRoundTripsWithWorldCoordinatesAndColors(){
        var patches=new ArrayList<Patch>();
        for(int i=0;i<64;i++)patches.add(new Patch(new Point(96+i%8,72+i%3*.5,196+i/8),new Point(0,1,0),0x123456+i,
            100+i,net.minecraft.core.BlockPos.asLong(96+i%8,71,196+i/8)));
        var p=event(patches);patches.clear();assertEquals(64,p.patches().size());
        var b=buffer();try{
            GroundDustFieldPayload.CODEC.encode(b,p);
            assertTrue(b.readableBytes()<5120,"bounded material field event must fit in 5 KiB");
            assertEquals(p,GroundDustFieldPayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    @Test void decodeRejectsBadCountBeforeReadingOrAllocatingPatches(){
        for(int count:new int[]{-1,65,Integer.MAX_VALUE}){
            var b=buffer();try{
                GroundDustFieldPayload.CODEC.encode(b,event(List.of()));
                b.writerIndex(b.writerIndex()-1);b.writeVarInt(count);
                assertThrows(IllegalArgumentException.class,()->GroundDustFieldPayload.CODEC.decode(b));
            }finally{b.release();}
        }
    }
    @Test void rejectsPlotLikeOutliersAndInvalidAxis(){
        assertThrows(IllegalArgumentException.class,()->event(List.of(new Patch(new Point(100,72,200),new Point(0,1,0),0,10,0))));
        assertThrows(IllegalArgumentException.class,()->event(List.of(new Patch(new Point(1e6,72,1e6),new Point(0,1,0),0))));
        assertThrows(IllegalArgumentException.class,()->event(List.of(new Patch(new Point(100,82,200),new Point(0,1,0),0))));
        assertThrows(IllegalArgumentException.class,()->event(List.of(new Patch(new Point(100,-50,200),new Point(0,1,0),0))));
        assertThrows(IllegalArgumentException.class,()->new GroundDustFieldPayload(ResourceLocation.parse("minecraft:overworld"),
            new Point(0,0,0),new Point(0,0,0),0,0,SETTINGS,List.of()));
    }
}
