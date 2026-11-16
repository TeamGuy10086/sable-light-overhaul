package dev.sablelightoverhaul;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.nio.file.*;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcceptedDefaultsTest {
    private static final Gson GSON=new Gson();
    @Test void allClientDefaultsMatchAcceptedInstance()throws Exception{verify("client",MuzzleClientConfig.SPEC,297);}
    @Test void allServerDefaultsMatchAcceptedInstance()throws Exception{verify("server",LightingCommon.SPEC,184);}
    private static void verify(String side,ModConfigSpec spec,int count)throws Exception{
        var raw=new TomlParser().parse(Files.readString(Path.of("src/test/resources/defaults/"+side+".toml")));
        var expected=flatten(raw,"");assertEquals(count,expected.size());
        if(side.equals("client")) { expected.put("spotlights.beam.samples",12); expected.put("flares.glowShrinkDistance",160.0);
            // New 560 defaults; the accepted old snapshot remains unchanged.
            expected.put("particles.muzzle.bigCannon.thermalSmoke.enabled",true);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.scale",1.1925000000000001);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.length",42.157500000000006);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.diameter",8.924999999999999);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.lifetimeTicks",160);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.hotTicks",20);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.releaseTicks",5);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.lobesPerCharge",4);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.maxActive",512);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.opacity",0.8);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.riseSpeed",0.35);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.spread",0.85);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.expansion",1.6);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.fadeFrom",0.95);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.flashSizeLink",0.65);
            // New 562 defaults; the accepted old snapshot remains unchanged.
            expected.put("particles.muzzle.bigCannon.thermalSmoke.singlePass",true);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.chargeReference",4.0);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.chargeExponent",0.5);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.chargeMaxScale",6.0);
            // New 566 defaults; the accepted old snapshot remains unchanged.
            expected.put("particles.muzzle.bigCannon.thermalSmoke.flipbook",true);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.performanceMode",false);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.softEdgeCull",true);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.simpleLighting",true);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.ambientGain",1.0);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.emissionGain",128.0);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.rootHotScale",.35);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.tipHotScale",1.5);
            expected.put("particles.muzzle.tankCannon.smokeCached",true);
            expected.put("particles.groundDust.fieldCached",true);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.coldFraction",.2);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.coldSize",1.5);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.curl",.25);
            expected.put("particles.muzzle.bigCannon.thermalSmoke.densityDetail",3.0);
            expected.put("flares.shadows",false);
            expected.put("shockwave.sloOnlyRefraction",false);

 }
        if(side.equals("client")){
            for (String removed : java.util.List.of("budgets.receiverAreas", "budgets.receivers", "budgets.cpuLightSources", "budgets.additionalReceivers", "staticSources.sableBridge", "debug.legacySable")) expected.remove(removed);
            for (String kind : java.util.List.of("autocannon", "bigCannon")) {
                for (String key : java.util.List.of("lightSourceCount", "lightLength", "lightWidth")) {
                    expected.remove("particles.muzzle." + kind + "." + key);
                }
            }
            expected.put("navalAudio.reportsEnabled",true);expected.put("navalAudio.impactsEnabled",true);
            expected.put("navalAudio.nearRange",800);expected.put("navalAudio.reportRange",2400);
            expected.put("navalAudio.soundSpeed",343.0);expected.put("navalAudio.reportGain",1.0);
            expected.put("navalAudio.duckRange",48);expected.put("navalAudio.duckGain",1.0);expected.put("navalAudio.impactGain",1.0);
            expected.put("wind.mode","FIXED");expected.put("wind.direction",0.0);
            expected.put("wind.speed",0.0);expected.put("wind.period",120);
            expected.put("farContacts.requestChunks",0);expected.put("farContacts.focusAngle",10.0);expected.put("farContacts.focusSize",24);expected.put("farContacts.showHealth",true);expected.put("flakSmoke.compact",true);expected.put("flakSmoke.maxActive",256);expected.put("flakSmoke.densityDetail",1.5);expected.put("projectileStreak.minPixels",.75);expected.put("projectileStreak.maxWorldWidth",1.0);
            expected.put("projectileStreak.enabled",true);expected.put("projectileStreak.width",.035);
            expected.put("projectileStreak.autocannonWidth",.05);expected.put("projectileStreak.autocannonMinPixels",1.25);
            expected.put("projectileStreak.persistenceTicks",3.0);expected.put("projectileStreak.maxLength",48.0);
            expected.put("projectileStreak.color",0xFFE3AC);expected.put("projectileStreak.maxProjectiles",512);
        }else{expected.put("farContacts.enabled",false);expected.remove("voxy.saveChunks");expected.remove("voxy.saveIntervalTicks");expected.put("flakCone.range",24.0);expected.put("flakCone.damageScale",1.0);}
        var generated=CommentedConfig.inMemory();spec.correct(generated);
        assertTrue(spec.isCorrect(generated));
        var keys = flatten(generated,"").keySet();
        // Optional weapon profiles have independent catalogue and preset coverage.
        keys.removeIf(key -> key.startsWith("sableHostileSmoke."));
        assertEquals(expected.keySet(),keys);
        for(var entry:expected.entrySet()){
            String key=entry.getKey();Object actual=generated.get(key);
            assertEquals(GSON.toJsonTree(entry.getValue()),GSON.toJsonTree(actual),key);
            ModConfigSpec.ConfigValue<?> value=spec.getValues().get(key);
            assertEquals(GSON.toJsonTree(actual),GSON.toJsonTree(value.getDefault()),"reset "+key);
            assertEquals(GSON.toJsonTree(actual),GSON.toJsonTree(value.getSpec().getDefault()),"repair "+key);
            assertTrue(value.getSpec().test(value.getDefault()),"invalid default "+key);
        }
    }
    @Test void existingValidSettingsAreNotOverwritten(){
        var c=CommentedConfig.inMemory();MuzzleClientConfig.SPEC.correct(c);
        c.set("particles.muzzle.tankCannon.afterSmoke.exitSpeed",17.0);
        c.set("particles.muzzle.tankCannon.afterSmoke.enabled",false);
        MuzzleClientConfig.SPEC.correct(c);
        assertEquals(17.0,(Double)c.get("particles.muzzle.tankCannon.afterSmoke.exitSpeed"));
        assertEquals(false,c.get("particles.muzzle.tankCannon.afterSmoke.enabled"));
    }
    @Test void hullDustIsSlightlyWhiterWithoutChangingGroundColor(){
        assertEquals(0xBEA98C,LightingCommon.HULL_FLOW_COLOR.getDefault());
        assertEquals(0xB39A78,LightingCommon.CANNON_GROUND_COLOR.getDefault());
        assertEquals(1.0,LightingCommon.HULL_CLUSTER_DENSITY.getDefault());
        assertFalse(RetiredHullSettings.ENABLED);
        assertFalse(LightingCommon.HULL_SURFACE_COUPLING.getDefault());
        assertTrue(MuzzleClientConfig.HULL_SKIN_THICKNESS.getSpec().test(2.0));
    }
    @Test void newKeysKeepTheirAuthoredDefault(){
        var b=new AcceptedDefaults("client");var value=b.defineInRange("future.example",7,0,10);
        var spec=b.build();var c=CommentedConfig.inMemory();spec.correct(c);
        assertEquals(7,value.getDefault());assertEquals(7,(Integer)c.get("future.example"));
    }
    private static Map<String,Object> flatten(UnmodifiableConfig config,String prefix){
        Map<String,Object> out=new LinkedHashMap<>();
        for(var entry:config.valueMap().entrySet()){
            String key=prefix.isEmpty()?entry.getKey():prefix+"."+entry.getKey();
            if(entry.getValue() instanceof UnmodifiableConfig child)out.putAll(flatten(child,key));
            else out.put(key,entry.getValue());
        }
        return out;
    }
}
