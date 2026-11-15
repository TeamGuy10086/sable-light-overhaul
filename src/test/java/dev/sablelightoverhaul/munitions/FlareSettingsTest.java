package dev.sablelightoverhaul.munitions;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlareSettingsTest {
    private static CompoundTag payload(Integer burn,Float bright){
        var p=new CompoundTag();
        if(burn!=null)p.putInt("BurnSeconds",burn);
        if(bright!=null)p.putFloat("Brightness",bright);
        return p;
    }
    private static CompoundTag root(CompoundTag p){var r=new CompoundTag();r.put(FlareSettings.NBT_KEY,p);return r;}

    @Test void nbtRoundTripsAndAbsentOrPartialRootFallsBackToDefaults(){
        for(var v:List.of(FlareSettings.DEFAULT,new FlareSettings(1,0F),new FlareSettings(3600,15F),new FlareSettings(123,7.25F))){
            var c=new CompoundTag();v.write(c);
            assertEquals(v,FlareSettings.read(c));
            // Root carries SloFlare{BurnSeconds,Brightness}.
            var p=c.getCompound(FlareSettings.NBT_KEY);
            assertEquals(v.burnSeconds(),p.getInt("BurnSeconds"));
            assertEquals(v.brightness(),p.getFloat("Brightness"));
        }
        assertEquals(FlareSettings.DEFAULT,FlareSettings.read(null));
        assertEquals(FlareSettings.DEFAULT,FlareSettings.read(new CompoundTag()));
        // Partial: the present key is kept, the missing one defaults.
        assertEquals(new FlareSettings(120,6F),FlareSettings.read(root(payload(120,null))));
        assertEquals(new FlareSettings(60,9.5F),FlareSettings.read(root(payload(null,9.5F))));
        // A non-compound slot is treated as absent.
        var wrong=new CompoundTag();wrong.putInt(FlareSettings.NBT_KEY,5);
        assertEquals(FlareSettings.DEFAULT,FlareSettings.read(wrong));
        // A wrong tag type inside the payload is treated as absent.
        var p=new CompoundTag();p.putString("BurnSeconds","x");p.putString("Brightness","y");
        assertEquals(FlareSettings.DEFAULT,FlareSettings.read(root(p)));
    }

    @Test void directNbtOutOfRangeAndNonFiniteValuesClampToFiniteSafety(){
        assertEquals(new FlareSettings(1,0F),FlareSettings.read(root(payload(-50,-3F))));
        assertEquals(new FlareSettings(3600,15F),FlareSettings.read(root(payload(Integer.MAX_VALUE,99F))));
        assertEquals(new FlareSettings(60,6F),FlareSettings.read(root(payload(null,Float.NaN))));
        assertEquals(new FlareSettings(60,6F),FlareSettings.read(root(payload(null,Float.POSITIVE_INFINITY))));
        assertEquals(new FlareSettings(60,6F),FlareSettings.read(root(payload(null,Float.NEGATIVE_INFINITY))));
        assertEquals(new FlareSettings(1,6F),FlareSettings.read(root(payload(Integer.MIN_VALUE,Float.NaN))));
        var s=new FlareSettings(Integer.MAX_VALUE,Float.POSITIVE_INFINITY);
        assertEquals(3600,s.burnSeconds());
        assertEquals(6F,s.brightness());
        assertTrue(Float.isFinite(s.brightness()));
    }

    @Test void codecRoundTripsPerItemJsonAndRejectsOutOfRange(){
        for(var v:List.of(new FlareSettings(1,0F),new FlareSettings(60,6F),new FlareSettings(3600,15F),new FlareSettings(123,7.25F))){
            var json=FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,v).getOrThrow();
            assertTrue(json.isJsonObject());
            // Optional fields equal to their defaults are deliberately omitted by the codec.
            assertTrue(Set.of("burn_seconds","brightness").containsAll(json.getAsJsonObject().keySet()));
            assertEquals(v,FlareSettings.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
        }
        assertEquals(new FlareSettings(90,6F),FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"burn_seconds\":90}")).getOrThrow());
        assertEquals(FlareSettings.DEFAULT,FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{}")).getOrThrow());
        for(var bad:List.of("{\"burn_seconds\":0}","{\"burn_seconds\":3601}","{\"brightness\":-0.1}","{\"brightness\":15.1}"))
            assertTrue(FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(bad)).error().isPresent(),bad);
    }

    @Test void networkStreamRoundTripsConsumingAllBytes(){
        for(var v:List.of(new FlareSettings(1,0F),new FlareSettings(60,6F),new FlareSettings(3600,15F),new FlareSettings(123,7.25F))){
            ByteBuf b=Unpooled.buffer();
            try{
                FlareSettings.STREAM_CODEC.encode(b,v);
                assertEquals(v,FlareSettings.STREAM_CODEC.decode(b));
                assertEquals(0,b.readableBytes());
            }finally{b.release();}
        }
    }
}
