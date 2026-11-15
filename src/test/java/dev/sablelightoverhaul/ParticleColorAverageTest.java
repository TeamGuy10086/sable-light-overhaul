package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleColorAverageTest {
    @Test void nativeAbgrIsConvertedToRgb(){
        var mean=new ParticleColorAverage();mean.add(0xFF1234AB);
        assertEquals(0xAB3412,mean.color());
    }
    @Test void transparencyCannotContaminateRepresentativeColor(){
        var mean=new ParticleColorAverage();mean.add(0x0000FF00);assertEquals(-1,mean.color());
        mean.add(0xFF0000FF);assertEquals(0xFF0000,mean.color());
        mean.add(0x80FF0000);assertEquals(0xA90055,mean.color());
    }
    @Test void biomeTintAndTerrainShadeAreSeparateFromTheTexture(){
        assertEquals(0x996633,ParticleColorAverage.tint(0xFFAA55,0xFFFFFF,.6));
        assertEquals(0x306000,ParticleColorAverage.tint(0x80C840,0xA0CC00,.6));
    }
}
