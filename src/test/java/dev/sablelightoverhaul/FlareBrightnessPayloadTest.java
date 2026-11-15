package dev.sablelightoverhaul;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A snapshot must carry each flare's own brightness, not an illumination-derived default. */
class FlareBrightnessPayloadTest {
    private static final ResourceLocation OVERWORLD=ResourceLocation.parse("minecraft:overworld");
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    private static FlarePayload.State state(int id,boolean lit,float brightness){
        return new FlarePayload.State(id,lit,1.0,64.0,-1.0,100L,200,0x00FF00,brightness);
    }

    @Test void distinctBrightnessSurvivesCodecForIlluminatedAndAmbientFlares(){
        // Both flags are used with both ends of the brightness range, so the value is not inferred from illumination.
        var states=new ArrayList<FlarePayload.State>();
        states.add(state(1,true,0F));
        states.add(state(2,true,15F));
        states.add(state(3,false,0F));
        states.add(state(4,false,2.5F));
        states.add(state(5,true,6F));
        var payload=new FlarePayload(OVERWORLD,states);
        var b=buffer();
        try{
            FlarePayload.CODEC.encode(b,payload);
            var back=FlarePayload.CODEC.decode(b);
            assertEquals(0,b.readableBytes());
            assertEquals(states,back.states());
            for(var s:back.states())assertTrue(Float.isFinite(s.brightness()));
        }finally{b.release();}
    }

    @Test void compatibilityConstructorDerivesBrightnessFromIllumination(){
        assertEquals(6F,new FlarePayload.State(1,true,0,0,0,0,10,0).brightness());
        assertEquals(15F,new FlarePayload.State(1,false,0,0,0,0,10,0).brightness());
    }

    @Test void nonFiniteOrOutOfRangeBrightnessIsRejected(){
        for(float v:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-0.01F,15.01F,Float.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->state(1,true,v),"brightness "+v);
    }

    @Test void tamperedWireBrightnessIsRejectedOnDecode(){
        var b=buffer();
        try{
            b.writeResourceLocation(OVERWORLD);
            b.writeVarInt(1);
            b.writeVarInt(7);b.writeBoolean(true);
            b.writeDouble(0);b.writeDouble(64);b.writeDouble(0);
            b.writeVarLong(0L);b.writeVarInt(200);b.writeInt(0xFFFFFF);
            b.writeFloat(20F);b.writeVarInt(360);
            assertThrows(IllegalArgumentException.class,()->FlarePayload.CODEC.decode(b));
        }finally{b.release();}
    }
}
