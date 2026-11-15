package dev.sablelightoverhaul;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.sablelightoverhaul.HullDustFootprint.Rect;

/** Behaviour of the conservative support radius: tolerances are loose ranges, never a copy of the formula. */
class HullDustFootprintTest {
    static final double SAFETY = .025, BISECT = 2.0 / 1024;
    static double r(List<Rect> faces, double u, double v) { return HullDustFootprint.radius(faces, u, v, 8); }

    @Test void interiorKeepsLargeRadiusWhileEdgeShrinksToBoundaryDistance() {
        var deck = List.of(new Rect(-5, -5, 5, 5));
        double centre = r(deck, 0, 0);
        assertTrue(centre > 4.94 && centre <= 5, "centre=" + centre);
        for (double[] p : new double[][]{{4, 0}, {0, -4.7}, {3, 3}, {-4, 4}}) {
            double bound = 5 - Math.max(Math.abs(p[0]), Math.abs(p[1]));
            double got = r(deck, p[0], p[1]);
            assertTrue(got <= bound, "edge from " + p[0] + "," + p[1]);
            assertTrue(got >= bound - .05, "edge too shy from " + p[0] + "," + p[1]);
        }
    }

    @Test void adjacentRectanglesStillSpanBlockSeams() {
        var strip = List.of(new Rect(0, 0, 1, 1), new Rect(1, 0, 2, 1), new Rect(2, 0, 3, 1));
        assertTrue(r(strip, 1, .5) > .45, "seam x=1");
        assertTrue(r(strip, 2, .5) > .45, "seam x=2");
        assertEquals(0,r(strip, 1, 1), "outer edge stays an edge");
        var grid=List.of(new Rect(0,0,1,1),new Rect(1,0,2,1),new Rect(0,1,1,2),new Rect(1,1,2,2));
        assertTrue(r(grid,1,1)>.94,"four-block internal seam must remain continuous");
        assertTrue(r(strip, 1, .5) <= .5 + 1e-9);
    }

    @Test void narrowSlitAndCentralHoleBlockTheRadiusNotJustTheOuterBox() {
        var slit = List.of(new Rect(0, 0, 1, 10), new Rect(1.2, 0, 3, 10));
        double atSlit = r(slit, 1.1, 5);
        assertEquals(0,atSlit,"centre in an empty slit has no support");
        assertTrue(r(slit, .5, 5) > .4, "far from slit the left plate is solid");
        var hole = List.of(new Rect(-3, -3, 3, -1), new Rect(-3, 1, 3, 3), new Rect(-3, -1, -1, 1), new Rect(1, -1, 3, 1));
        double atHole = r(hole, 0, 0);
        assertEquals(0,atHole,"centre in a hole has no support");
        assertTrue(r(hole, 0, 2) > .9, "solid band above hole");
        assertTrue(r(hole, 0, 0) < r(List.of(new Rect(-3, -3, 3, 3)), 0, 0));
    }

    @Test void longSkirtIsLimitedByItsShortSide() {
        var skirt = List.of(new Rect(-5, 0, 5, .45));
        double mid = r(skirt, 0, .225);
        assertTrue(mid <= .225 + 1e-9, "short side at " + mid);
        assertTrue(mid >= .225 - .04, "short side at " + mid);
        assertTrue(r(skirt, 4, .225) <= .225 + 1e-9);
        var wide = List.of(new Rect(0, 0, 1.2, 3));
        assertTrue(r(wide, .6, 1.5) <= .6 + 1e-9);
        assertTrue(r(wide, .6, 1.5) >= .6 - .05);
    }

    @Test void largePositiveAndNegativeOffsetsAgree() {
        double[][] probes={{9.3,4.1},{9.5,4},{10,4.5},{8.1,3.1}};
        var here=List.of(new Rect(8,3,10,6),new Rect(10,3,11,6));
        for(double[] shift:new double[][]{{8e6,-3e6},{-8e6,3e6}}){
            var moved=here.stream().map(f->new Rect(f.minU()+shift[0],f.minV()+shift[1],f.maxU()+shift[0],f.maxV()+shift[1])).toList();
            for(var p:probes){double base=r(here,p[0],p[1]);assertTrue(base>0);assertEquals(base,r(moved,p[0]+shift[0],p[1]+shift[1]),.01);}
        }
    }

    @Test void degenerateInputsAndOutsidePlaneReturnZero() {
        var deck = List.of(new Rect(0, 0, 4, 4));
        assertEquals(0, HullDustFootprint.radius(List.of(), 2, 2, 8));
        assertEquals(0, HullDustFootprint.radius(deck, 2, 2, 0));
        assertEquals(0, HullDustFootprint.radius(deck, 2, 2, -3));
        assertEquals(0, r(deck, 20, 2));
        assertEquals(0, r(deck, -1, -1));
        assertEquals(0, HullDustFootprint.radius(deck, Double.NaN, 2, 8));
        assertEquals(0, HullDustFootprint.radius(deck, 2, 2, Double.POSITIVE_INFINITY));
        double tiny = r(deck, 0, 0);
        assertTrue(tiny >= 0 && tiny <= SAFETY + BISECT, "safety margin clamps the corner radius: " + tiny);
    }
}
