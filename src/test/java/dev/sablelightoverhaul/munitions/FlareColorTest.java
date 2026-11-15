package dev.sablelightoverhaul.munitions;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlareColorTest {
    private static final int DEFAULT = 0xFF5030;
    private static FlareSettings parse(String json){
        return FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();
    }
    private static CompoundTag root(CompoundTag p){var r=new CompoundTag();r.put(FlareSettings.NBT_KEY,p);return r;}

    @Test void legacyNbtWithoutColorDefaultsToOrange(){
        var p=new CompoundTag();
        p.putInt("BurnSeconds",120);
        p.putFloat("Brightness",9F);
        assertEquals(new FlareSettings(120,9F,DEFAULT),FlareSettings.read(root(p)));
        assertEquals(DEFAULT,FlareSettings.read(root(new CompoundTag())).rgb());
        assertEquals(DEFAULT,FlareSettings.read(null).rgb());
    }

    @Test void twoArgConstructorDelegatesToDefaultColor(){
        assertEquals(DEFAULT,new FlareSettings(60,6F).rgb());
        assertEquals(DEFAULT,new FlareSettings(1,0F).rgb());
        assertEquals(FlareSettings.DEFAULT,new FlareSettings(60,6F));
        assertEquals(DEFAULT,FlareSettings.DEFAULT.rgb());
    }

    @Test void legacyComponentJsonWithoutColorDefaults(){
        assertEquals(DEFAULT,parse("{}").rgb());
        assertEquals(new FlareSettings(90,6F,DEFAULT),parse("{\"burn_seconds\":90}"));
        assertEquals(DEFAULT,parse("{\"brightness\":3.5}").rgb());
    }

    @Test void rgbRoundTripsThroughCodecNbtAndNetwork(){
        for(int color:List.of(0x000000,0xFFFFFF,0xFF5030,0x12AB34)){
            var v=new FlareSettings(60,6F,color);
            var named=v.color();
            assertEquals(v.rgb(),color);
            assertEquals(new RgbColor((color>>16)&0xFF,(color>>8)&0xFF,color&0xFF),named);
            // Component JSON codec: color is always written as {red,green,blue}.
            var json=FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,v).getOrThrow();
            var c0=json.getAsJsonObject().getAsJsonObject("color");
            if(color!=DEFAULT){
                assertEquals(named.red(),c0.get("red").getAsInt());
                assertEquals(named.green(),c0.get("green").getAsInt());
                assertEquals(named.blue(),c0.get("blue").getAsInt());
            } else assertNull(c0);
            assertEquals(v,FlareSettings.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());
            // Persistent root SloFlare{Color{Red,Green,Blue}}.
            var c=new CompoundTag();v.write(c);
            var n=c.getCompound(FlareSettings.NBT_KEY).getCompound("Color");
            assertEquals(named.red(),n.getInt("Red"));
            assertEquals(named.green(),n.getInt("Green"));
            assertEquals(named.blue(),n.getInt("Blue"));
            assertEquals(v,FlareSettings.read(c));
            // Network stream.
            ByteBuf b=Unpooled.buffer();
            try{
                FlareSettings.STREAM_CODEC.encode(b,v);
                assertEquals(v,FlareSettings.STREAM_CODEC.decode(b));
                assertEquals(0,b.readableBytes());
            }finally{b.release();}
        }
    }

    @Test void legacyPackedColorIsStillReadFromJsonAndNbt(){
        // Old component JSON int and old root int both keep working.
        assertEquals(0x12AB34,parse("{\"color\":"+0x12AB34+"}").rgb());
        assertEquals(DEFAULT,parse("{\"color\":"+DEFAULT+"}").rgb());
        var p=new CompoundTag();p.putInt("Color",0x12AB34);
        assertEquals(0x12AB34,FlareSettings.read(root(p)).rgb());
        // Serialization upgrades the legacy int to the named form.
        var upgraded=FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,parse("{\"color\":"+0x12AB34+"}")).getOrThrow();
        assertTrue(upgraded.getAsJsonObject().get("color").isJsonObject());
    }

    @Test void legacyTwoFieldNbtSurvivesWithDefaultColorAndAngle(){
        var p=new CompoundTag();p.putInt("BurnSeconds",120);p.putFloat("Brightness",9F);p.putInt("Color",0x00FF00);
        var v=FlareSettings.read(root(p));
        assertEquals(new FlareSettings(120,9F,0x00FF00),v);
        assertEquals(FlareSettings.DEFAULT_ANGLE,v.angleDegrees());
        var c=new CompoundTag();v.write(c);
        var n=c.getCompound(FlareSettings.NBT_KEY).getCompound("Color");
        assertEquals(0x00,n.getInt("Red"));assertEquals(0xFF,n.getInt("Green"));assertEquals(0x00,n.getInt("Blue"));
        assertEquals(v,FlareSettings.read(c));
    }

    @Test void colorIsOmittedFromJsonWhenDefault(){
        var json=FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,new FlareSettings(60,6F,DEFAULT)).getOrThrow();
        assertFalse(json.getAsJsonObject().has("color"));
        var custom=FlareSettings.CODEC.encodeStart(JsonOps.INSTANCE,new FlareSettings(60,6F,0x000000)).getOrThrow();
        var c=custom.getAsJsonObject().getAsJsonObject("color");
        assertEquals(0,c.get("red").getAsInt());assertEquals(0,c.get("green").getAsInt());assertEquals(0,c.get("blue").getAsInt());
    }

    @Test void illegalComponentColorsFailAtCodecBoundary(){
        for(String bad:List.of("{\"color\":-1}","{\"color\":16777216}","{\"color\":2147483647}",
                "{\"color\":{\"red\":256}}","{\"color\":{\"green\":-1}}","{\"color\":{\"blue\":999}}"))
            assertTrue(FlareSettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(bad)).error().isPresent(),bad);
        assertEquals(0x000000,parse("{\"color\":0}").rgb());
        assertEquals(0xFFFFFF,parse("{\"color\":16777215}").rgb());
        assertEquals(0x12AB34,parse("{\"color\":{\"red\":18,\"green\":171,\"blue\":52}}").rgb());
        assertEquals(0xFF0000,parse("{\"color\":{\"red\":255,\"green\":0,\"blue\":0}}").rgb());
    }

    @Test void constructorAndNbtReadClampColor(){
        assertEquals(0x000000,new FlareSettings(60,6F,-1).rgb());
        assertEquals(0xFFFFFF,new FlareSettings(60,6F,0x1000000).rgb());
        var p=new CompoundTag();
        p.putInt("Color",Integer.MAX_VALUE);
        assertEquals(0xFFFFFF,FlareSettings.read(root(p)).rgb());
        p.putInt("Color",-5);
        assertEquals(0x000000,FlareSettings.read(root(p)).rgb());
        // Non-numeric Color is treated as absent.
        p.remove("Color");
        p.putString("Color","red");
        assertEquals(DEFAULT,FlareSettings.read(root(p)).rgb());
        // Compound channels clamp individually on a direct NBT read; a missing one is 0.
        var n=new CompoundTag();n.putInt("Red",999);n.putInt("Green",-99);
        p.put("Color",n);
        assertEquals(0xFF0000,FlareSettings.read(root(p)).rgb());
        n.putInt("Blue",7);
        assertEquals(0xFF0007,FlareSettings.read(root(p)).rgb());
    }

    @Test void colorDoesNotDisturbBurnAndBrightness(){
        var v=new FlareSettings(-10,99F,0x00FF00);
        assertEquals(1,v.burnSeconds());
        assertEquals(15F,v.brightness());
        assertEquals(0x00FF00,v.rgb());
    }
}
