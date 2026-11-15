package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorridorClipperTest {
    private static List<float[]> quad(double originX, double originY, double originZ,
                                     double axisX, double axisY, double axisZ,
                                     float x, float y, float z, float half, double phase) {
        var vertices = new ArrayList<float[]>();
        var clipper = new CorridorClipper((a,b,c,d) -> {
            for (var point : List.of(a,b,c,d)) vertices.add(point.clone());
        });
        clipper.cylinder(originX, originY, originZ, axisX, axisY, axisZ, 2.0, phase);
        for (float[] corner : new float[][]{{-half,-half},{half,-half},{half,half},{-half,half}}) {
            clipper.vertex(x+corner[0], y+corner[1], z, corner[0], corner[1], 1, .5f, 0, 1);
        }
        return vertices;
    }

    @Test void protectsTheAxisAtBarrelElevationButLeavesGroundFarBelowIntact() {
        double diagonal = Math.sqrt(0.5);
        assertTrue(quad(0,0,0,diagonal,diagonal,0,4,4,0,.3f,0).isEmpty());
        var outside = quad(0,0,0,diagonal,diagonal,0,6,0,0,.3f,0);
        assertEquals(4, outside.size());
        assertTrue(outside.stream().allMatch(v -> v[8] == 1));
    }

    @Test void edgeFeathersWithoutLosingUvsAndVariesWithShotSeed() {
        var edge = quad(0,0,0,0,0,1,2,0,6,2,1);
        var differentShot = quad(0,0,0,0,0,1,2,0,6,2,3);
        assertTrue(edge.stream().anyMatch(v -> v[8] > 0 && v[8] < 1));
        assertTrue(edge.stream().allMatch(v -> Math.abs(v[0] - 2 - v[3]) < 1e-6 && v[1] == v[4]));
        assertNotEquals(edge.stream().map(v -> v[8]).toList(), differentShot.stream().map(v -> v[8]).toList());
        assertTrue(edge.stream().filter(v -> Math.hypot(v[0],v[1]) < 1.64).allMatch(v -> v[8] == 0));
    }

    @Test void cameraTranslationDoesNotMoveTheWorldMask() {
        var first = quad(0,0,0,0,0,1,2,0,6,2,1);
        var translated = quad(16,4,-8,0,0,1,18,4,-2,2,1);
        assertEquals(first.stream().map(v -> v[8]).toList(), translated.stream().map(v -> v[8]).toList());
    }
}
