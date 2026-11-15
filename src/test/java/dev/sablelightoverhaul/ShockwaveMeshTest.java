package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.sablelightoverhaul.client.ShockwaveMesh;
import org.junit.jupiter.api.Test;

/**
 * The two pieces of arithmetic the spherical shockwave is made of, which are the only parts of it that can
 * be checked without a game.
 *
 * <p>The shape is the user's call -- a blast wave is a sphere, and the eight measured octant reaches are
 * deliberately unused here (the particle path reads them for how it spreads its puffs). What is checked is
 * that the sphere projects as a <em>ring</em>: transparent where the view ray goes through it, brightest
 * where the ray grazes it, and gone by the end of its life.
 */
class ShockwaveMeshTest {
    @Test void forcedMatrixWindowCannotEscapeThroughIrisDeferredEntityBuffers() throws Exception {
        var code=new org.objectweb.asm.tree.ClassNode();
        try(var input=ShockwaveMesh.class.getResourceAsStream("ShockwaveMesh.class")){
            assertNotNull(input);new org.objectweb.asm.ClassReader(input).accept(code,0);
        }
        var render=code.methods.stream().filter(m->m.name.equals("onRenderLevelStage")).findFirst().orElseThrow();
        boolean immediate=false,restoresProjection=false;
        for(var instruction:render.instructions){
            if(!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call))continue;
            assertFalse(call.name.equals("bufferSource")||call.name.equals("endBatch"),
                "Iris endBatch(RenderType) is a no-op even when the shaderpack is off");
            if(call.owner.equals("net/minecraft/client/renderer/RenderType")&&call.name.equals("draw"))immediate=true;
            if(call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&call.name.equals("setProjectionMatrix"))restoresProjection=true;
        }
        assertTrue(immediate,"submit mesh before restoring the identity model-view");
        assertTrue(restoresProjection,"draw uses an explicit event projection");
    }

    @Test void theLimbIsBrightAndTheFacingSurfaceIsInvisible() {
        assertTrue(ShockwaveMesh.limbAlpha(0.0, 1.0F, 1.0F, 2.5) > 0.5F, "a grazing ray is the bright part of the ring");
        assertEquals(0.0F, ShockwaveMesh.limbAlpha(1.0F, 1.0F, 1.0F, 2.5), 1.0E-6, "looking straight through is nothing");
        assertEquals(0.0F, ShockwaveMesh.limbAlpha(-1.0F, 1.0F, 1.0F, 2.5), 1.0E-6, "and so is the far side, facing away");
    }

    /** The absolute value is what makes the far half of the sphere light up at the same silhouette. */
    @Test void theNearAndFarHalvesAreSymmetric() {
        for (int i = 0; i <= 10; i++) {
            double facing = i / 10.0;
            assertEquals(ShockwaveMesh.limbAlpha(facing, 1.0F, 1.0F, 2.5), ShockwaveMesh.limbAlpha(-facing, 1.0F, 1.0F, 2.5), 1.0E-6,
                    "silhouette at " + facing);
        }
    }

    @Test void brightnessOnlyFallsOffFromTheLimb() {
        float previous = Float.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            float alpha = ShockwaveMesh.limbAlpha(i / 20.0, 1.0F, 1.0F, 2.5);
            assertTrue(alpha <= previous + 1.0E-6, "monotone towards the centre: " + alpha);
            assertTrue(alpha >= 0.0F && alpha <= 1.0F, "and a usable alpha: " + alpha);
            previous = alpha;
        }
    }

    @Test void theWaveFadesOutOverItsLifeAndDiesAtTheEnd() {
        assertEquals(1.0F, ShockwaveMesh.lifeFade(0.0), 1.0E-6, "full at birth");
        assertEquals(0.75F, ShockwaveMesh.lifeFade(0.5), 1.0E-6, "still bright at half way");
        assertEquals(0.0F, ShockwaveMesh.lifeFade(1.0), 1.0E-6, "gone at the end");
        // Out-of-range input is clamped rather than extrapolated into negative or blown-out alpha.
        assertEquals(1.0F, ShockwaveMesh.lifeFade(-3.0), 1.0E-6);
        assertEquals(0.0F, ShockwaveMesh.lifeFade(3.0), 1.0E-6);
    }

    /** The two knobs the user asked for: brightness scales it, sharpness decides how thin the ring is. */
    @Test void brightnessScalesTheRingAndSharpnessThinsIt() {
        assertEquals(0.4F, ShockwaveMesh.limbAlpha(0.0, 1.0F, 0.4F, 2.5), 1.0E-6, "brightness is the peak alpha");
        assertEquals(0.0F, ShockwaveMesh.limbAlpha(0.0, 1.0F, 0.0F, 2.5), 1.0E-6, "zero draws nothing");
        float soft = ShockwaveMesh.limbAlpha(0.5, 1.0F, 1.0F, 1.0);
        float hard = ShockwaveMesh.limbAlpha(0.5, 1.0F, 1.0F, 8.0);
        assertTrue(hard < soft, "a sharper ring is dimmer away from the silhouette: " + hard + " vs " + soft);
        assertEquals(1.0F, ShockwaveMesh.limbAlpha(0.0, 1.0F, 1.0F, 8.0), 1.0E-6, "but still full at it");
    }

    /** The band's two radii: the thickness is a share of the radius, and it never inverts or goes negative. */
    @Test void theBandIsAShareOfTheRadiusAndNeverInverts() {
        assertEquals(20.0, ShockwaveMesh.innerRadius(20.0, 0.0), 1.0E-9, "no thickness means no band");
        assertEquals(15.0, ShockwaveMesh.innerRadius(20.0, 0.25), 1.0E-9);
        assertEquals(8.0, ShockwaveMesh.innerRadius(20.0, 0.6), 1.0E-9);
        // Clamped, because a page or a hand-edited config can ask for anything.
        assertEquals(20.0, ShockwaveMesh.innerRadius(20.0, -1.0), 1.0E-9, "negative is no band, not an inverted one");
        assertEquals(8.0, ShockwaveMesh.innerRadius(20.0, 5.0), 1.0E-9, "and the share is capped");
        assertTrue(ShockwaveMesh.innerRadius(0.0, 0.25) >= 0.0);
    }

    /**
     * The mesh's motion, which is the reason the three knobs can be independent: the front must leave at
     * exactly the configured speed and still arrive at the measured reach exactly when the lifetime is up.
     */
    /**
     * The calibration the user set in game: coefficient 1 is the effect they tuned on a power-24 shell,
     * which was 60 blocks. Smaller charges scale down sub-linearly -- power 0.8 -- so a quarter charge still
     * throws a wave about a third of the radius instead of a quarter, which is what the eye expects up close.
     */
    @Test void theSizeFollowsTheChargeAndCoefficientOneIsTheCalibratedLook() {
        assertEquals(60.0, ShockwaveMesh.meshRadius(24.0, 1.0), 1.0E-9, "the calibration point");
        // Sub-linear: about a third of the radius for a quarter of the charge, not a quarter.
        assertEquals(60.0 * Math.pow(4.0 / 24.0, 0.8), ShockwaveMesh.meshRadius(4.0, 1.0), 1.0E-9, "TNT");
        assertEquals(60.0 * Math.pow(6.0 / 24.0, 0.8), ShockwaveMesh.meshRadius(6.0, 1.0), 1.0E-9);
        assertEquals(60.0 * Math.pow(12.0 / 24.0, 0.8), ShockwaveMesh.meshRadius(12.0, 1.0), 1.0E-9);
        assertEquals(60.0 * Math.pow(32.0 / 24.0, 0.8), ShockwaveMesh.meshRadius(32.0, 1.0), 1.0E-9, "the top tier scales up");
        // The coefficient stays strictly proportional: twice the coefficient, twice the radius.
        assertEquals(ShockwaveMesh.meshRadius(24.0, 1.0) * 2.0, ShockwaveMesh.meshRadius(24.0, 2.0), 1.0E-9);
        // Monotonic in the charge, and never below the floor.
        assertTrue(ShockwaveMesh.meshRadius(6.0, 1.0) < ShockwaveMesh.meshRadius(12.0, 1.0));
        assertTrue(ShockwaveMesh.meshRadius(0.0, 1.0) >= 2.0);
        assertTrue(ShockwaveMesh.meshRadius(-5.0, 1.0) >= 2.0);
    }
    /** Size and duration are the two numbers that decide "how big" and "how long", and nothing else. */
    @Test void theFrontCoversExactlyTheConfiguredSizeInExactlyTheConfiguredTime() {
        double size = 120.0, life = 30.0, expansion = 1.0;
        assertEquals(0.0, ShockwaveMesh.radiusAt(size, life, expansion, 0.0), 1.0E-9);
        assertEquals(size, ShockwaveMesh.radiusAt(size, life, expansion, life), 1.0E-9, "arrives on time");
        assertEquals(size * 0.5, ShockwaveMesh.radiusAt(size, life, expansion, life * 0.5), 1.0E-9,
                "expansion 1 is a steady sweep: half the time is half the distance");
        // And it does not sit at the end early: the last tick still moves it.
        assertTrue(ShockwaveMesh.radiusAt(size, life, expansion, life - 0.5) < size);
    }

    @Test void theSizeIsTheRadiusAndDoesNotDependOnAnythingElse() {
        for (double size : new double[] {4.0, 40.0, 120.0, 512.0}) {
            assertEquals(size, ShockwaveMesh.radiusAt(size, 10.0, 1.0, 10.0), 1.0E-9, "size " + size);
            double previous = -1.0;
            for (int i = 0; i <= 20; i++) {
                double r = ShockwaveMesh.radiusAt(size, 10.0, 1.0, i * 0.5);
                assertTrue(r >= previous - 1.0E-9, "monotone: " + r);
                assertTrue(r <= size + 1.0E-9, "never beyond the configured size: " + r);
                previous = r;
            }
        }
    }

    @Test void thePacingDecidesWhenTheDistanceIsCovered() {
        double quarter = 2.5;
        double frontLoaded = ShockwaveMesh.radiusAt(100.0, 10.0, 4.0, quarter);
        double steady = ShockwaveMesh.radiusAt(100.0, 10.0, 1.0, quarter);
        double backLoaded = ShockwaveMesh.radiusAt(100.0, 10.0, 0.3, quarter);
        assertTrue(frontLoaded > steady && steady > backLoaded,
                "4 front-loads, 1 is even, 0.3 back-loads: " + frontLoaded + " / " + steady + " / " + backLoaded);
        for (double expansion : new double[] {0.05, 1.0, 4.0}) {
            assertEquals(100.0, ShockwaveMesh.radiusAt(100.0, 10.0, expansion, 10.0), 1.0E-9,
                    "every pacing still arrives: " + expansion);
        }
    }

    @Test void theMotionSurvivesNonsenseInput() {
        assertEquals(0.0, ShockwaveMesh.radiusAt(40.0, 10.0, 1.0, -5.0), 1.0E-9, "before birth");
        assertEquals(40.0, ShockwaveMesh.radiusAt(40.0, 10.0, 1.0, 99.0), 1.0E-9, "long after the end");
        assertTrue(!Double.isNaN(ShockwaveMesh.radiusAt(0.0, 10.0, 1.0, 5.0)), "a zero size is not a NaN");
        assertTrue(!Double.isNaN(ShockwaveMesh.radiusAt(40.0, 0.0, 1.0, 5.0)), "nor is a zero life");
    }
    @Test void theLifeFadeScalesTheLimbWithoutChangingItsShape() {
        assertEquals(ShockwaveMesh.limbAlpha(0.2, 1.0F, 1.0F, 2.5) * 0.5F, ShockwaveMesh.limbAlpha(0.2, 0.5F, 1.0F, 2.5), 1.0E-6);
        assertEquals(0.0F, ShockwaveMesh.limbAlpha(0.2, 0.0F, 1.0F, 2.5), 1.0E-6, "a dead wave draws nothing");
    }
}
