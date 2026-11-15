package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeFlowTest {
    @Test void deterministicSeededAndBounded(){
        for(int i=0;i<200;i++){
            var a=SmokeFlow.sample(i*.17,-i*.31,i*.7,i*.05,42);
            assertEquals(a,SmokeFlow.sample(i*.17,-i*.31,i*.7,i*.05,42));
            assertTrue(Double.isFinite(a.x()+a.y()+a.z()));
            assertTrue(Math.max(Math.abs(a.x()),Math.max(Math.abs(a.y()),Math.abs(a.z())))<=1.875);
        }
        assertNotEquals(SmokeFlow.sample(.3,.6,.9,1,42),SmokeFlow.sample(.3,.6,.9,1,43));
    }
    @Test void continuousAcrossLatticeAndTime(){
        double h=1e-6;
        for(int i=-3;i<=3;i++){
            var a=SmokeFlow.sample(i-h,.4,-.3,0,9);var b=SmokeFlow.sample(i+h,.4,-.3,h,9);
            assertTrue(distance(a,b)<1e-4);
        }
    }
    @Test void curlHasNearZeroDivergence(){
        double h=1e-4;
        for(double x:new double[]{-1,-.001,0,.21,.999,1}){
            double y=.37,z=-.61;
            double dx=SmokeFlow.sample(x+h,y,z,0,12).x()-SmokeFlow.sample(x-h,y,z,0,12).x();
            double dy=SmokeFlow.sample(x,y+h,z,0,12).y()-SmokeFlow.sample(x,y-h,z,0,12).y();
            double dz=SmokeFlow.sample(x,y,z+h,0,12).z()-SmokeFlow.sample(x,y,z-h,0,12).z();
            assertEquals(0,(dx+dy+dz)/(2*h),1e-3);
        }
    }
    private static double distance(SmokeFlow.Vector a,SmokeFlow.Vector b){
        return Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z());
    }
}
