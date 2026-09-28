package dev.cbcmuzzlelight;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedLightTest {
    private AreaLight light() { return new AreaLight(0,0,0,1,0,0,30,12,0.25,15); }
    @Test void reachesBeyondVanillaRangeWithFiniteBoundary() {
        assertTrue(light().at(22,0,0)>0);
        assertEquals(0,light().at(30,0,0));
        assertEquals(0,light().at(31,0,0));
        assertEquals(0,light().at(0,12,0));
    }
    @Test void coreIsBrightAndFalloffMonotonic() {
        assertEquals(15,light().at(7,0,0));
        assertTrue(light().at(10,0,0)>light().at(20,0,0));
        assertEquals(15,light().at(0,0,0));
    }
    @Test void directionRotatesShapeAndBounds() {
        var l=new AreaLight(0,0,0,0,0,1,30,12,0.25,15);
        assertEquals(light().at(22,0,0),l.at(0,0,22),1e-10);
        assertEquals(12,l.extentX(),1e-10); assertEquals(30,l.extentZ(),1e-10);
    }
    @Test void pulseChangesIntensityWithoutChangingSupport() {
        var l=new AreaLight(0,0,0,1,0,0,30,12,0.25,7.5);
        assertEquals(light().at(20,0,0)*0.5,l.at(20,0,0),1e-10);
        var pulse=new LightCurve(5,0,0.5,0);
        assertEquals(1,pulse.brightness(0)); assertEquals(0.5,pulse.brightness(1)); assertEquals(0,pulse.brightness(2));
    }
    @Test void invalidShapesCannotReachRenderingWorkers() {
        assertThrows(IllegalArgumentException.class,() -> new AreaLight(0,0,0,0,0,0,30,12,0.25,15));
        assertThrows(IllegalArgumentException.class,() -> new AreaLight(0,0,0,1,0,0,30,12,1,15));
    }
    @Test void modesResolveWithoutSilentlyEnablingDisabledEffects() {
        // Compatible needs Lamb; without it the only remaining backend is vanilla block light.
        assertEquals(LightingMode.Legacy,LightingMode.Compatible.resolve(false,false));
        assertEquals(LightingMode.Compatible,LightingMode.Compatible.resolve(false,true));
        // The GPU tiers need their shader; otherwise they fall back to Compatible, then to Legacy.
        assertEquals(LightingMode.Compatible,LightingMode.DynamicOnly.resolve(false,true));
        assertEquals(LightingMode.Legacy,LightingMode.DynamicOnly.resolve(false,false));
        assertEquals(LightingMode.DynamicOnly,LightingMode.DynamicOnly.resolve(true,false));
        assertEquals(LightingMode.Advanced,LightingMode.Advanced.resolve(true,true));
        for(boolean gpu:new boolean[]{false,true}) for(boolean lamb:new boolean[]{false,true}) {
            assertEquals(LightingMode.None,LightingMode.None.resolve(gpu,lamb));
            assertEquals(LightingMode.Legacy,LightingMode.Legacy.resolve(gpu,lamb));
        }
    }
    @Test void twinBarrelsAndOtherGunsKeepIndependentPulses() {
        var s=new FlashStore(64);
        s.add(-0.25,0,0,0,0,1,1,10,LightCurve.AUTO,LightLayout.AUTO,new FlashStore.Emitter(12,0,0,0));
        s.add(0.25,0,0,0,0,1,1,11,LightCurve.AUTO,LightLayout.AUTO,new FlashStore.Emitter(12,0,0,0));
        s.add(0.5,0,0,0,0,1,1,11,LightCurve.AUTO,LightLayout.AUTO,new FlashStore.Emitter(13,0.5,0,0));
        assertEquals(3,s.snapshot().size(),"Legacy keeps individual barrel sources");
        assertEquals(3,s.areaFlashes().size());
        assertEquals(2,s.areaFlashes().values().stream().filter(f -> f.emitter().id()==12).count());
        s.clear(); assertTrue(s.areaFlashes().isEmpty());
    }
}
