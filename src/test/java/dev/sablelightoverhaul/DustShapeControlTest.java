package dev.sablelightoverhaul;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DustShapeControlTest {
 private GroundDustFieldModel field(double outer,double height){return new GroundDustFieldModel(new Point(0,0,0),new Point(1,0,0),List.of(new Patch(new Point(1,0,1),new Point(0,1,0),0),new Patch(new Point(14,0,2),new Point(0,1,0),0)),new Settings(16,4,3,.2,0,1.8,0,.4,1,8,1,6,outer,.65,height),17);}
 @Test void farBankLiftFallsOffAndHeightScaleControlsBothThicknessAndRise(){var full=field(1,1);var low=field(.15,.6);for(int i=0;i<35;i++){full.tick();low.tick();}var a=full.nodes().get(1);var b=low.nodes().get(1);assertTrue(b.position.y()<a.position.y()*.7);assertTrue(b.height(1)<a.height(1)*.7);assertEquals(3,b.corridorHalfWidth());}
 @Test void settingsRejectNonFiniteAndControlsOutsideBounds(){assertThrows(IllegalArgumentException.class,()->new Settings(16,4,3,.2,0,1.8,0,.4,1,8,1,Double.NaN,.15,.65,.6));}
}
