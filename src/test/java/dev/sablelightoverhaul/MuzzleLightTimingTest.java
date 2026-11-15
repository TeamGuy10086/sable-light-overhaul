package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuzzleLightTimingTest {
    @Test void longSmokeIsNotTruncatedByOldThirtyTickStore() {
        var p=MuzzleLightTiming.big(new LightCurve(30,0,.02,4),LightLayout.BIG,
            new LightCurve(8,0,.1,2),new LightCurve(48,0,.02,2));
        assertEquals(56,p.lifetime().durationTicks());assertEquals(8,p.layout().muzzleHoldTicks());
        assertEquals(0,p.layout().fallbackTravel(8));assertTrue(p.layout().fallbackTravel(9)>0);
        var store=new FlashStore(8);assertTrue(store.add(0,0,0,1,0,0,0,100,p.lifetime(),p.layout()));
        store.expire(155);assertEquals(1,store.areaFlashes().size());
        store.expire(156);assertTrue(store.areaFlashes().isEmpty());
    }
    @Test void longerExplicitLifetimeRemainsAndStageOverridesApply() {
        var p=MuzzleLightTiming.big(new LightCurve(120,0,.02),LightLayout.BIG,
            new LightCurve(12,0,.1),new LightCurve(18,0,.02));
        assertEquals(120,p.lifetime().durationTicks());assertEquals(12,p.layout().muzzleHoldTicks());
    }
    @Test void tankPulseCanOutliveTheAutocannonWindow() {
        var store=new FlashStore(8);
        assertTrue(store.add(0,0,0,1,0,0,2,100,new LightCurve(10,0,.1,2),new LightLayout(0,0)));
        store.expire(109);assertEquals(1,store.areaFlashes().size());
        store.expire(110);assertTrue(store.areaFlashes().isEmpty());
    }
}
