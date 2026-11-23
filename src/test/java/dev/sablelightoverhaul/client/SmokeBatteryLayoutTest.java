package dev.sablelightoverhaul.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeBatteryLayoutTest {
    @Test void downwardCameraCannotBuryTheBattery(){
        Vec3 eye=new Vec3(8,40,-10);
        for(int gun=0;gun<8;gun++){
            Vec3 a=SmokeBatteryLayout.muzzle(eye,new Vec3(0,0,1),gun);
            Vec3 b=SmokeBatteryLayout.muzzle(eye,new Vec3(0,-.99,.1),gun);
            assertEquals(a,b);
            assertTrue(a.y>=eye.y);assertEquals(-2,a.z-eye.z,1e-8);
            assertTrue(a.distanceTo(eye)<3.2);
        }
    }
    @Test void allGunsShareTheSameReleaseClock(){
        for(int volley=0;volley<3;volley++){
            int release=5,count=0;
            for(int gun=0;gun<8;gun++)for(int puff=0;puff<32;puff++){
                double born=SmokeBatteryLayout.born(volley,puff*release/32);
                assertTrue(born>=3+volley*10&&born<=3+volley*10+.2);count++;
            }
            assertEquals(256,count);
        }
    }
}
