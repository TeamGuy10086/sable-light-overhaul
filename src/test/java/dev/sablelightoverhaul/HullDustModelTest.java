package dev.sablelightoverhaul;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.HullDustModel.Settings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullDustModelTest {
    private HullDustModel model(long seed,Settings settings){return new HullDustModel(new Point(0,1,0),new Point(0,1,0),new Point(1,0,0),new Point(2,1,0),seed,2.5,2,3,.8,.1,.3,.5,settings);}
    @Test void deterministicButDifferentSeedsVary(){var a=model(7,new Settings(1,.7,.2));var b=model(7,new Settings(1,.7,.2));var c=model(8,new Settings(1,.7,.2));for(int i=0;i<30;i++){a.tick();b.tick();c.tick();assertEquals(a.position,b.position);}assertNotEquals(a.position,c.position);}
    @Test void sweepsAndPeelsWithoutOrbiting(){var m=model(3,new Settings(16,8,0));double top=1;for(int i=0;i<40;i++){m.tick();assertTrue(m.position.finite());assertTrue(m.position.y()>=1);top=Math.max(top,m.position.y());assertTrue(m.position.subtract(new Point(0,1,0)).length()<=32.00001);}assertTrue(top>3);assertTrue(m.position.x()>.1);}
    @Test void delayBirthAndDeathAreContinuous(){var m=model(5,new Settings(1,.7,3));assertEquals(0,m.alpha(1));m.tick();assertEquals(new Point(0,1,0),m.position);double peak=0;for(int i=0;i<120;i++){m.tick();peak=Math.max(peak,m.alpha(1));}assertTrue(peak>0);assertEquals(0,m.alpha(1));assertTrue(m.finished());}
    @Test void invalidSettingsRejected(){assertThrows(IllegalArgumentException.class,()->new Settings(Double.NaN,1,1));assertThrows(IllegalArgumentException.class,()->new Settings(1,Double.POSITIVE_INFINITY,1));assertThrows(IllegalArgumentException.class,()->new Settings(1,1,4));}
    @Test void fallbackDoesNotRotateOrOrbitWhenTurbulenceIsDisabled(){var m=model(3,new Settings(1,0,0));double roll=m.roll(1);for(int i=0;i<35;i++){m.tick();assertEquals(0,m.position.z(),1e-8);assertEquals(roll,m.roll(1),1e-8);}}
}
