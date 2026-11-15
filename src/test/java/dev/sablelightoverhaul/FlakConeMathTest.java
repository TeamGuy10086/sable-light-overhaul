package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlakConeMathTest {
    private boolean hit(double x,double y,double z,double radius){return FlakConeMath.intersects(x,y,z,1,0,0,10,.5,radius);}
    @Test void finiteConeHasClosedCapAndTip(){
        assertTrue(hit(0,0,0,0));assertTrue(hit(10,5,0,0));
        assertFalse(hit(-1,5,0,0));assertFalse(hit(11,0,0,.9));assertTrue(hit(11,0,0,1));
        assertFalse(hit(2,4,0,0));assertTrue(hit(5,2,0,0));
    }
    @Test void sideDistanceIsPerpendicularNotRadial(){
        // Gap .2 along radial direction means .2/sqrt(1+.5²)=.178885 perpendicular to the cone side.
        assertFalse(hit(5,2.7,0,.17));assertTrue(hit(5,2.7,0,.18));
        double n=1/Math.sqrt(1.25);
        assertTrue(hit(4-.5*n*.3,2+n*.3,0,.3));
        assertFalse(hit(4-.5*n*.3,2+n*.3,0,.29));
    }
    @Test void capAndRimDistanceAreEuclidean(){
        assertTrue(hit(10.1,2,0,.11));assertFalse(hit(10.1,2,0,.09));
        assertTrue(hit(10.3,5.4,0,.5));assertFalse(hit(10.3,5.4,0,.49));
    }
    @Test void axisRotationAndScaleDoNotChangeCone(){
        assertTrue(FlakConeMath.intersects(2,-4,0,0,-1,0,10,.5,0));
        assertFalse(FlakConeMath.intersects(2.1,-4,0,0,-1,0,10,.5,0));
        for(double k:new double[]{.01,1,1e8}){
            assertTrue(FlakConeMath.intersects(3,3,3,k,k,k,10,.5,0));
            assertFalse(FlakConeMath.intersects(-1,-1,-1,k,k,k,10,.5,0));
        }
    }
    @Test void zeroSpreadIsSegment(){assertTrue(FlakConeMath.intersects(5,.2,0,1,0,0,10,0,.21));assertFalse(FlakConeMath.intersects(5,.2,0,1,0,0,10,0,.19));}
    @Test void malformedDataCannotCauseDamage(){
        assertFalse(FlakConeMath.intersects(0,0,0,0,0,0,10,.5,0));
        assertFalse(FlakConeMath.intersects(0,0,0,1,0,0,-1,.5,0));
        assertFalse(FlakConeMath.intersects(0,0,0,1,0,0,10,-1,0));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertFalse(FlakConeMath.intersects(bad,0,0,1,0,0,10,.5,0));
    }
    @Test void damageFallsToThirtyFivePercentThenEnds(){
        assertEquals(1,FlakConeMath.falloff(0,10),1e-12);assertEquals(.675,FlakConeMath.falloff(5,10),1e-12);
        assertEquals(.35,FlakConeMath.falloff(10,10),1e-12);assertEquals(0,FlakConeMath.falloff(10.001,10));
        assertEquals(0,FlakConeMath.falloff(1,0));assertEquals(0,FlakConeMath.falloff(Double.NaN,10));
    }
}
