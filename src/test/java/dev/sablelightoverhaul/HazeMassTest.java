package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HazeMassTest {
    @Test void singlePuffKeepsItsConfiguredOpacityAndLife() {
        var m=new HazeMass(200,0.3f); m.add(1000);
        for(int i=0;i<=200;i++) assertEquals(0.3*HazeEnvelope.opacity(i/200f,200),m.alpha(1000+i,0),1e-6);
        assertFalse(m.alive(1200));
    }
    @Test void mergedCarriersMatchIndependentPuffsInsteadOfClampingDensity() {
        var m=new HazeMass(100,0.3f); for(int n=0;n<12;n++) m.add(0);
        assertEquals(1-Math.pow(0.7,12),m.alpha(20,0),1e-6);
        assertTrue(m.alpha(20,0)>0.98,"dense 2A42-style smoke must remain possible");
    }
    @Test void aNewShotDoesNotResetTheOldCohortsAge() {
        var m=new HazeMass(20,0.3f); m.add(0); m.add(19);
        assertEquals(0.3*HazeEnvelope.opacity(1/20f,20),m.alpha(20,0),1e-6);
        assertFalse(m.alive(39));
    }
    @Test void boundedRingHandlesLongBurstsAndStopsAfterTheLastShot() {
        var m=new HazeMass(40,0.08f);
        for(int t=0;t<3000;t++) {
            m.add(t); double transmittance=1;
            for(int age=0;age<40 && age<=t;age++) transmittance*=1-0.08*HazeEnvelope.opacity(age/40f,40);
            assertEquals(1-transmittance,m.alpha(t,0),1e-6);
        }
        assertFalse(m.alive(3039)); assertEquals(0,m.alpha(3039,0));
    }
    @Test void cleanAndSmokyParametersRemainDistinct() {
        var a=new HazeMass(20,0.03f); var b=new HazeMass(200,0.3f);
        for(int i=0;i<40;i++){a.add(i);b.add(i);}
        assertTrue(b.alpha(40,0)>a.alpha(40,0));
        assertEquals(0,a.alpha(60,0)); assertTrue(b.alpha(60,0)>0.9);
    }
    @Test void drawingTwiceDoesNotAccumulateExtraSmoke() {
        var m=new HazeMass(40,0.3f);m.add(0);float expected=m.alpha(5,0.5f);
        for(int i=0;i<100;i++) assertEquals(expected,m.alpha(5,0.5f));
        assertThrows(IllegalArgumentException.class,()->m.add(-1));
    }
}
