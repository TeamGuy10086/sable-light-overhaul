package dev.sablelightoverhaul.munitions;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlareAngleSettingsTest {
    private static final int DEFAULT_RGB = 0xFF5030;
    private static FlareSettings parse(String json){
        return FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();
    }
    private static CompoundTag root(CompoundTag p){var r=new CompoundTag();r.put(FlareSettings.NBT_KEY,p);return r;}

    @Test void defaultAngleIsFullCircle(){
        assertEquals(360,FlareSettings.DEFAULT.angleDegrees());
        assertEquals(FlareSettings.DEFAULT,new FlareSettings(60,6F));
        assertEquals(FlareSettings.DEFAULT,new FlareSettings(60,6F,DEFAULT_RGB));
        assertEquals(360,FlareSettings.read(new CompoundTag()).angleDegrees());
        assertEquals(360,parse("{}").angleDegrees());
        assertEquals(360,parse("{\"burn_seconds\":90}").angleDegrees());
    }

    @Test void constructorClampsAngleToOneThroughThreeSixty(){
        assertEquals(1,new FlareSettings(60,6F,DEFAULT_RGB,-90).angleDegrees());
        assertEquals(1,new FlareSettings(60,6F,DEFAULT_RGB,0).angleDegrees());
        assertEquals(360,new FlareSettings(60,6F,DEFAULT_RGB,361).angleDegrees());
        assertEquals(360,new FlareSettings(60,6F,DEFAULT_RGB,Integer.MAX_VALUE).angleDegrees());
        assertEquals(45,new FlareSettings(60,6F,DEFAULT_RGB,45).angleDegrees());
        // Angle does not disturb the other three fields.
        var v=new FlareSettings(120,9F,0x00FF00,90);
        assertEquals(120,v.burnSeconds());assertEquals(9F,v.brightness());assertEquals(0x00FF00,v.rgb());
    }

    @Test void codecRoundTripsAngleAndRejectsOutOfRange(){
        for(int angle:List.of(1,45,90,180,360)){
            var v=new FlareSettings(60,6F,DEFAULT_RGB,angle);
            var json=FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,v).getOrThrow();
            if(angle!=360) assertEquals(angle,json.getAsJsonObject().get("angle").getAsInt());
            else assertFalse(json.getAsJsonObject().has("angle"));
            assertEquals(v,FlareSettings.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
        }
        assertEquals(90,parse("{\"angle\":90}").angleDegrees());
        // Equal to the default, so it is omitted like the other optional fields.
        assertFalse(FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,FlareSettings.DEFAULT).getOrThrow()
            .getAsJsonObject().has("angle"));
        for(String bad:List.of("{\"angle\":0}","{\"angle\":-1}","{\"angle\":361}","{\"angle\":2147483647}"))
            assertTrue(FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(bad)).error().isPresent(),bad);
    }

    @Test void networkStreamCarriesVarIntAngleAndConsumesAllBytes(){
        for(int angle:List.of(1,255,256,360)){
            var v=new FlareSettings(123,7.25F,0x12AB34,angle);
            ByteBuf b=Unpooled.buffer();
            try{
                FlareSettings.STREAM_CODEC.encode(b,v);
                assertEquals(v,FlareSettings.STREAM_CODEC.decode(b));
                assertEquals(0,b.readableBytes());
            }finally{b.release();}
        }
        ByteBuf b=Unpooled.buffer();
        try{
            FlareSettings.STREAM_CODEC.encode(b,new FlareSettings(60,6F,DEFAULT_RGB,45));
            assertEquals(60,net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(b).intValue());assertEquals(6F,b.readFloat());
            assertEquals(DEFAULT_RGB,net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(b).intValue());assertEquals(45,net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(b).intValue());
        }finally{b.release();}
    }

    @Test void nbtRoundTripsAngleAndAbsentOrOutOfRangeFallsBackToDefaults(){
        for(int angle:List.of(1,90,360)){
            var v=new FlareSettings(60,6F,DEFAULT_RGB,angle);
            var c=new CompoundTag();v.write(c);
            assertEquals(angle,c.getCompound(FlareSettings.NBT_KEY).getInt("Angle"));
            assertEquals(v,FlareSettings.read(c));
        }
        // An absent Angle defaults to a full circle rather than failing.
        var p=new CompoundTag();p.putInt("BurnSeconds",120);p.putFloat("Brightness",9F);
        assertEquals(new FlareSettings(120,9F,DEFAULT_RGB,360),FlareSettings.read(root(p)));
        // A direct NBT edit that slips past the codec still clamps.
        p.putInt("Angle",-5);
        assertEquals(1,FlareSettings.read(root(p)).angleDegrees());
        p.putInt("Angle",100000);
        assertEquals(360,FlareSettings.read(root(p)).angleDegrees());
        // A wrong tag type inside the payload is treated as absent.
        p.putString("Angle","x");
        assertEquals(360,FlareSettings.read(root(p)).angleDegrees());
    }
}
