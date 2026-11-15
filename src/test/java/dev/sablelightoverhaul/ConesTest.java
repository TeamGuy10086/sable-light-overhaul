package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.Cones;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The shadow cones' text, and the packed words the shader reads.
 *
 * <p>Two things matter beyond "does it parse": that a malformed text is refused rather than half-applied,
 * and that the packing is the exact byte order the shader unpacks. The second one is invisible from
 * either side alone -- the shader would simply test a different rectangle -- so it is pinned here with
 * values that would move if the order or the scale changed.
 */
class ConesTest {
    @Test void emptyMeansEveryDirection() {
        assertEquals(0, Cones.count(Cones.parse(null)));
        assertEquals(0, Cones.count(Cones.parse("")));
        assertEquals(0, Cones.count(Cones.parse("   ")));
        assertEquals(0, Cones.count(Cones.parse(";;")));
        assertEquals("全部方向", Cones.describe(Cones.NONE));
    }

    @Test void groupsArePackedInOrderAndScaledToTheirOwnAxis() {
        int[] packed = Cones.parse("{0,360,-90,90}");
        assertEquals(1, Cones.count(packed));
        // 0 and 360 are the ends of the horizontal scale; -90 and +90 the ends of the vertical one.
        assertEquals(0, packed[0] & 255, "horizontal start");
        assertEquals(255, (packed[0] >> 8) & 255, "horizontal end");
        assertEquals(0, (packed[0] >> 16) & 255, "vertical start");
        assertEquals(255, (packed[0] >> 24) & 255, "vertical end");
        // And the scale is 1.4 degrees: a value in the middle lands in the middle of both axes.
        int[] middle = Cones.parse("{180,181,0,1}");
        assertEquals(128, middle[0] & 255, "180 of 360 is half of a byte, rounded");
        assertEquals(128, (middle[0] >> 16) & 255, "0 of -90..90 is half of a byte, rounded");
    }

    @Test void severalGroupsAreCompactedAndStaySeparate() {
        int[] packed = Cones.parse("{0,90,-10,10}; {180,270,-45,45}");
        assertEquals(2, Cones.count(packed));
        assertEquals(Cones.pack(0, 90, -10, 10), packed[0]);
        assertEquals(Cones.pack(180, 270, -45, 45), packed[1]);
        assertEquals(0, packed[2], "the unused words are zero, which is how the count is taken");
        assertEquals("{" + Cones.h1(packed[0]) + "," + Cones.h2(packed[0]) + "," + Cones.v1(packed[0]) + ","
                + Cones.v2(packed[0]) + "}；{" + Cones.h1(packed[1]) + "," + Cones.h2(packed[1]) + ","
                + Cones.v1(packed[1]) + "," + Cones.v2(packed[1]) + "}", Cones.describe(packed));
    }

    @Test void malformedTextIsRefusedWhole() {
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{0,90,-10}"), "three angles");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{0,90,-10,ten}"), "not a number");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{90,90,-10,10}"), "empty horizontal range");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{10,5,-10,10}"), "end before start");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{0,361,-10,10}"), "past 360");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{-10,90,-10,10}"), "negative horizontal");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{0,90,-100,10}"), "vertical below -90");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{0,90,10,100}"), "vertical above 90");
        assertThrows(IllegalArgumentException.class, () -> Cones.parse("{0,90,20,20}"), "empty vertical range");
        assertThrows(IllegalArgumentException.class,
                () -> Cones.parse("{0,1,0,1};{0,1,0,1};{0,1,0,1};{0,1,0,1};{0,1,0,1}"), "more than four");
    }

    @Test void everyAngularRangeFitsInAByte() {
        // The ends are inclusive, so the whole domain has to be representable: a range the page accepts
        // must never pack to a word that reads back as "no cone" (zero) or as a different rectangle.
        for (int h = 0; h <= 360; h++) {
            int word = Cones.pack(h, Math.min(360, h + 1), -90, 90);
            assertTrue(word != 0, "h=" + h + " packed to zero");
            assertEquals(h, Cones.h1(word), 1, "h=" + h + " did not survive the round trip");
        }
        for (int v = -90; v <= 89; v++) {
            int word = Cones.pack(0, 1, v, v + 1);
            assertEquals(v, Cones.v1(word), 1, "v=" + v + " did not survive the round trip");
        }
    }
}
