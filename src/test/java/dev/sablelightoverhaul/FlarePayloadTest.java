package dev.sablelightoverhaul;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlarePayloadTest {
    private static final ResourceLocation OVERWORLD=ResourceLocation.parse("minecraft:overworld");
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    private static FlarePayload.State state(int id,double x,double y,double z,long born,int duration,int rgb){
        return new FlarePayload.State(id,true,x,y,z,born,duration,rgb);
    }
    // The decoder's prologue only -- dimension, then count -- so the count can be tampered with on its own.
    private static RegistryFriendlyByteBuf header(int count){
        var b=buffer();
        b.writeResourceLocation(OVERWORLD);b.writeVarInt(count);
        return b;
    }
    @Test void fullSnapshotOfSixteenRoundTripsWithFractionalCoordinatesAndShadowedColors(){
        var states=new ArrayList<FlarePayload.State>();
        for(int i=0;i<FlarePayload.MAX_FLARES;i++){
            // Every color here has bit 23 set, so it is wider than a signed short and survives only a full int.
            states.add(state(1000+i,i*.25+0.125,6400.5-i*.0625,-30000000.0+i*.1,72000L+i,duration(i),0x800000|(i<<12)));
        }
        var payload=new FlarePayload(OVERWORLD,states);states.clear();
        assertEquals(16,payload.states().size());
        var b=buffer();try{
            FlarePayload.CODEC.encode(b,payload);
            assertEquals(payload,FlarePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    /** The duration bounds themselves and an ordinary value, so the two ends travel as well as the middle. */
    private static int duration(int i){
        return switch(i%3){case 0->2;case 1->72000;default->600+i;};
    }
    @Test void sourceListIsCopiedAndAnEmptySnapshotIsAValidClear(){
        var states=new ArrayList<>(List.of(state(1,-8.5,63.0,12.25,100L,200,0x00FF00)));
        var payload=new FlarePayload(OVERWORLD,states);states.clear();
        assertThrows(UnsupportedOperationException.class,()->payload.states().add(state(2,0,0,0,0,10,0)));
        assertEquals(1,payload.states().size());
        assertThrows(NullPointerException.class,()->new FlarePayload(OVERWORLD,null));
        assertThrows(NullPointerException.class,()->new FlarePayload(null,List.of()));
        // An empty snapshot is not an error: it is how a client is told every flare is gone.
        var empty=new FlarePayload(OVERWORLD,List.of());
        assertTrue(empty.states().isEmpty());
        var b=buffer();try{
            FlarePayload.CODEC.encode(b,empty);
            assertEquals(empty,FlarePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    @Test void decodeRejectsInvalidCountBeforeReadingOrAllocatingAnyState(){
        var tooMany=header(FlarePayload.MAX_FLARES+1);
        try{assertThrows(IllegalArgumentException.class,()->FlarePayload.CODEC.decode(tooMany));}finally{tooMany.release();}
        for(int count:new int[]{-1,Integer.MAX_VALUE}){
            var b=header(count);
            // No state follows, so a decoder that read any of them would fail some other way than the bound.
            try{assertThrows(IllegalArgumentException.class,()->FlarePayload.CODEC.decode(b));
                assertEquals(0,b.readableBytes(),"count "+count+" must be rejected before any state is read");
            }finally{b.release();}
        }
    }
    @Test void nonFiniteCoordinatesAndOutOfRangeDurationsOrColorsAreRejected(){
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->state(1,x,64,0,0,100,0),Double.toString(x));
        // Non-finite on the other two axes too: the check covers each independently.
        assertThrows(IllegalArgumentException.class,()->state(1,0,Double.NaN,0,0,100,0));
        assertThrows(IllegalArgumentException.class,()->state(1,0,0,Double.POSITIVE_INFINITY,0,100,0));
        for(int duration:new int[]{Integer.MIN_VALUE,-1,0,1,72001,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->state(1,0,64,0,0,duration,0),"duration "+duration);
        for(int rgb:new int[]{-1,0x1000000,Integer.MIN_VALUE})
            assertThrows(IllegalArgumentException.class,()->state(1,0,64,0,0,100,rgb),"rgb "+rgb);
        assertDoesNotThrow(()->state(1,0,64,0,0,2,0));
        assertDoesNotThrow(()->state(1,0,64,0,0,72000,0xFFFFFF));
    }
}
