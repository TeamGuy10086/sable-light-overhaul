package dev.cbcmuzzlelight;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlameShapeTest {
    @Test void allBarrelDirectionsHaveFiniteOrthonormalFlameAxes() {
        double[][] directions={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},{1,3,-2},{0,0.9999,0.0001}};
        for (var d:directions) {
            var b=FlameShape.basis(d[0],d[1],d[2]);
            assertEquals(1,b.forward().dot(b.forward()),1e-9);
            assertEquals(1,b.right().dot(b.right()),1e-9);
            assertEquals(1,b.up().dot(b.up()),1e-9);
            assertEquals(0,b.forward().dot(b.right()),1e-9);
            assertEquals(0,b.forward().dot(b.up()),1e-9);
            assertEquals(0,b.right().dot(b.up()),1e-9);
        }
    }
    @Test void flashFadesInsteadOfEndingAsOpaquePatch() {
        assertEquals(1,FlameShape.envelope(0));
        assertEquals(0,FlameShape.envelope(1));
        assertEquals(0,FlameShape.envelope(2));
        assertTrue(FlameShape.envelope(0.25f)>FlameShape.envelope(0.75f));
        assertThrows(IllegalArgumentException.class,() -> FlameShape.basis(0,0,0));
    }
    @Test void registeredSpriteExistsAndHasTransparentBordersAndBrightCore() throws Exception {
        Path root=Path.of("src/main/resources/assets/cbcmuzzlelight");
        assertTrue(Files.readString(root.resolve("particles/auto_muzzle.json")).contains("cbcmuzzlelight:auto_muzzle"));
        var image=ImageIO.read(root.resolve("textures/particle/auto_muzzle.png").toFile());
        assertEquals(128,image.getWidth()); assertEquals(64,image.getHeight());
        for (int x=0;x<128;x++) {
            assertEquals(0,image.getRGB(x,0)>>>24);
            assertEquals(0,image.getRGB(x,63)>>>24);
        }
        for(int y=0;y<64;y++) assertEquals(0,image.getRGB(127,y)>>>24);
        int core=image.getRGB(30,32);
        assertTrue((core>>>24)>240); assertTrue((core&255)>190);
        assertTrue(width(image,30)>width(image,105));
    }
    private int width(java.awt.image.BufferedImage image,int x) {
        int count=0; for(int y=0;y<image.getHeight();y++) if ((image.getRGB(x,y)>>>24)>64) count++;
        return count;
    }
}
