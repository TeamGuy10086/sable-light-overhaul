package dev.sablelightoverhaul.client.ui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeviceModelResourcesTest {
    @Test void allSixModelsHaveEditableSourcesResolvedAtlasAndHandTransforms() throws Exception {
        Path assets=Path.of("src/main/resources/assets/sablelightoverhaul");
        for(String name:List.of("flashlight","dust_generator","smoke_generator","vortex_generator","spotlight","disco_ball")){
            var model=JsonParser.parseString(Files.readString(assets.resolve("models/"+(name.equals("flashlight")?"item/":"block/")+name+".json"))).getAsJsonObject();
            var editable=JsonParser.parseString(Files.readString(Path.of("art/blockbench",name+".bbmodel"))).getAsJsonObject();
            assertEquals(editable.getAsJsonArray("elements").size(),model.getAsJsonArray("elements").size(),name);
            assertTrue(model.getAsJsonArray("elements").size()<=12,name);
            for(String slot:List.of("gui","firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand"))
                assertTrue(model.getAsJsonObject("display").has(slot),name+" "+slot);
            for(var texture:model.getAsJsonObject("textures").entrySet()){
                String path=texture.getValue().getAsString();
                if(path.startsWith("#"))continue;
                assertTrue(Files.exists(assets.resolve("textures/"+path.split(":",2)[1]+".png")),name+" "+path);
            }
            for(var element:model.getAsJsonArray("elements")){
                var cube=element.getAsJsonObject();assertFalse(cube.has("light_emission"),"Not a 1.21.1 field");
                for(int axis=0;axis<3;axis++)assertTrue(cube.getAsJsonArray("from").get(axis).getAsDouble()<cube.getAsJsonArray("to").get(axis).getAsDouble());
            }
        }
    }

    @Test void spotlightPartsAreCompleteDisjointAndShareLensPivot() throws Exception {
        Path assets=Path.of("src/main/resources/assets/sablelightoverhaul");
        java.util.Map<String,com.google.gson.JsonElement> expected=new java.util.HashMap<>(),actual=new java.util.HashMap<>();
        var all=JsonParser.parseString(Files.readString(assets.resolve("models/block/spotlight.json"))).getAsJsonObject();
        for(var element:all.getAsJsonArray("elements"))expected.put(element.getAsJsonObject().get("name").getAsString(),element);
        for(String part:List.of("base","yoke","head","lens")){
            var model=JsonParser.parseString(Files.readString(assets.resolve("models/block/spotlight_"+part+".json"))).getAsJsonObject();
            for(var element:model.getAsJsonArray("elements")){
                String name=element.getAsJsonObject().get("name").getAsString();
                assertNull(actual.put(name,element),"drawn twice: "+name);
            }
        }
        assertEquals(expected,actual,"splitting must not lose or duplicate any model surface");
        var lens=actual.get("lens").getAsJsonObject();
        var from=lens.getAsJsonArray("from");var to=lens.getAsJsonArray("to");
        assertEquals(0.5,(from.get(0).getAsDouble()+to.get(0).getAsDouble())/32,1e-9);
        assertEquals(dev.sablelightoverhaul.SpotlightPose.PIVOT_Y,(from.get(1).getAsDouble()+to.get(1).getAsDouble())/32,1e-9);
        assertEquals(dev.sablelightoverhaul.SpotlightPose.LENS_REACH,.5-from.get(2).getAsDouble()/16,1e-9);
        assertEquals(dev.sablelightoverhaul.SpotlightPose.APERTURE,(to.get(1).getAsDouble()-from.get(1).getAsDouble())/32,1e-9);
        for(var face:lens.getAsJsonObject("faces").entrySet())assertEquals(0,face.getValue().getAsJsonObject().get("tintindex").getAsInt());
        var states=JsonParser.parseString(Files.readString(assets.resolve("blockstates/spotlight.json"))).getAsJsonObject();
        for(var entry:states.getAsJsonObject("variants").entrySet())assertEquals("sablelightoverhaul:block/spotlight_base",entry.getValue().getAsJsonObject().get("model").getAsString());
    }
}
