package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GroundDustFieldModelTest {
    @Test void extremeSpeedCannotJumpMissingSurfaceCell(){
        var m=field(settings(6,1,4,.1,32,0,0,.8,1,.5),1,patch(1,0),patch(3,0));
        for(int tick=0;tick<80;tick++){
            m.tick();assertTrue(m.nodes().get(0).position.x()<=1.80001,"must stop before unsupported gap");
        }
    }
    @Test void rejectsCliffAndFadesInvalidActiveSupport(){
        var m=field(settings(6,1,4,.1,5,0,0,.8,1,.5),1,patch(1,0),
            new Patch(new Point(2,5,0),new Point(0,1,0),0));
        for(int tick=0;tick<20;tick++){m.tick();assertTrue(m.nodes().get(0).position.y()<1);}
        var node=m.nodes().get(0);assertTrue(node.alpha(1)>0);
        m.invalidate(0);m.tick();assertTrue(node.alpha(1)>0,"already airborne smoke fades, not instant deletion");
        for(int tick=0;tick<6;tick++)m.tick();
        assertEquals(0,node.alpha(1));assertTrue(node.finished());
    }
    private static Settings settings(double... o) {
        if (o.length == 0) return new Settings(6, 0.5, 2, 1, 1, 0, 0.5, 0.8, 1, 0.5);
        return new Settings(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], o[8], o[9]);
    }
    private static Patch patch(double x, double z) {
        return new Patch(new Point(x, 0, z), new Point(0, 1, 0), 0xAAAAAA);
    }
    private static GroundDustFieldModel field(Settings s, long seed, Patch... patches) {
        return new GroundDustFieldModel(new Point(0, 0, 0), new Point(1, 0, 0), List.of(patches), s, seed);
    }
    private static List<List<Double>> trace(GroundDustFieldModel m, int ticks) {
        var out = new ArrayList<List<Double>>();
        for (int t = 0; t < ticks; t++) {
            m.tick();
            for (Node n : m.nodes()) {
                Point p = n.position;
                out.add(List.of(p.x(), p.y(), p.z(), n.width(0.5), n.roll(0.5)));
            }
        }
        return out;
    }

    @Test void sameSeedProducesDeterministicMotion() {
        Patch[] ps = {patch(1, 0), patch(3, 1), patch(5, -2), patch(2, -4)};
        assertEquals(trace(field(settings(), 42, ps), 80), trace(field(settings(), 42, ps), 80));
        assertNotEquals(trace(field(settings(), 42, ps), 80), trace(field(settings(), 43, ps), 80));
    }

    @Test void nearerPatchActivatesFirstAndAllFinish() {
        Settings s = settings(6, 0.5, 2, 1, 1, 0, 0.5, 0.8, 1, 0.5);
        var m = field(s, 7, patch(0.5, 0), patch(5.5, 0));
        Node near = m.nodes().get(0), far = m.nodes().get(1);
        assertTrue(near.delay < far.delay);
        int nearAt = -1, farAt = -1;
        for (int t = 1; t <= 200; t++) {
            m.tick();
            if (nearAt < 0 && near.alpha(0.5) > 0) nearAt = t;
            if (farAt < 0 && far.alpha(0.5) > 0) farAt = t;
            assertTrue(farAt < 0 || nearAt > 0);
        }
        assertTrue(nearAt > 0 && nearAt < farAt);
        assertTrue(m.finished());
        assertTrue(m.nodes().stream().allMatch(Node::finished));
    }

    @Test void invalidationPreventsDelayedBirth() {
        Settings s = settings(6, 0.5, 2, 5, 1, 0, 0.5, 0.8, 1, 0.5);
        var m = field(s, 3, patch(0.5, 0), patch(5.5, 0));
        Node far = m.nodes().get(1);
        assertTrue(far.delay > 1.0);
        m.tick();
        m.invalidate(1);
        for (int t = 0; t < 80; t++) {
            assertEquals(0, far.alpha(0.5));
            assertEquals(0, far.alpha(1));
            m.tick();
        }
        assertTrue(far.finished());
    }

    @Test void rejectsNonfiniteSettingsAndTooManyPatches() {
        for (int i = 0; i < 10; i++) {
            double[] base = {6, 0.5, 2, 1, 1, 0, 0.5, 0.8, 1, 0.5};
            double[] bad = base.clone();
            bad[i] = Double.NaN;
            assertThrows(IllegalArgumentException.class, () -> settings(bad));
            double[] inf = base.clone();
            inf[i] = Double.POSITIVE_INFINITY;
            assertThrows(IllegalArgumentException.class, () -> settings(inf));
        }
        Patch[] many = new Patch[GroundDustFieldModel.MAX_PATCHES + 1];
        Arrays.fill(many, patch(1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new GroundDustFieldModel(new Point(0, 0, 0), new Point(1, 0, 0), Arrays.asList(many), settings(), 1));
    }

    @Test void cloudIsWorldFixedAndMaxSettingsStayFinite() {
        Point o = new Point(0, 0, 0), a = new Point(1, 0, 0);
        Patch p = patch(2, 1);
        var patches = new ArrayList<Patch>(List.of(p));
        Settings s = settings(6, 0.5, 2, 1, 1, 0, 0.5, 0.8, 1, 0.5);
        var m = new GroundDustFieldModel(o, a, patches, s, 5);
        patches.clear();
        assertEquals(1, m.nodes().size());
        assertSame(p, m.nodes().get(0).patch);
        var max = field(settings(64, 64, 60, 5, 32, 16, 8, 1, 4, 8), 9, patch(0.5, 0), patch(5.5, 0), patch(30, 20));
        for (int t = 0; t < 60; t++) {
            max.tick();
            for (Node n : max.nodes()) {
                assertTrue(n.position.finite() && n.previous.finite());
                assertTrue(Double.isFinite(n.age(0.5)) && Double.isFinite(n.width(0)) && Double.isFinite(n.alpha(0.5)));
            }
        }
    }
}
