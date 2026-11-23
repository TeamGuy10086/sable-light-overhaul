package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Envelope invariants for the big-plume model. Counts 4..256 and release 1..40 are the config ranges,
 * so every pair is exercised; heat and coverage must stay independent knobs.
 */
class BigSmokeModelTest {
    private static final double EPS = 1e-9;

    @Test void everySupportedShotReleasesItsExactTotalFrontloaded() {
        for (int total = 4; total <= 256; total++) for (int release = 1; release <= 40; release++) {
            int sum = 0;
            for (int tick = 0; tick < release; tick++) {
                int c = BigSmokeModel.children(total, release, tick);
                assertTrue(c >= 0, "negative children at " + total + "/" + release + "/" + tick);
                if (tick == 0) assertTrue(c >= 1 && c >= total / release, "first tick must emit even when total < release");
                sum += c;
            }
            assertEquals(total, sum, "exact total for " + total + " over " + release);
        }
    }

    @Test void invalidShotRangesReleaseNothing() {
        for (int tick = -2; tick <= 3; tick++) {
            assertEquals(0, BigSmokeModel.children(0, 5, tick));
            assertEquals(0, BigSmokeModel.children(-8, 5, tick));
            assertEquals(0, BigSmokeModel.children(36, 0, tick));
            assertEquals(0, BigSmokeModel.children(36, -5, tick));
        }
        assertEquals(0, BigSmokeModel.children(36, 5, 5), "release is exclusive past the end");
    }

    @Test void frontMovesForwardMonotonicallyAndBrakes() {
        double previous = 0, previousStep = BigSmokeModel.front(0.05);
        for (int i = 1; i <= 200; i++) {
            double f = BigSmokeModel.front(i * 0.05);
            assertTrue(f > previous && f < 1, "front must advance but never reach the asymptote at " + i);
            assertTrue(i == 1 || f - previous < previousStep, "later equal-time steps must be smaller at " + i);
            previousStep = f - previous;
            previous = f;
        }
        assertEquals(0.5, BigSmokeModel.front(Math.log(2) / 1.2), 1e-9);
        assertEquals(0, BigSmokeModel.front(0), EPS);
        assertEquals(0, BigSmokeModel.front(-3), EPS, "negative time is clamped");
    }

    @Test void growthIsFinitePositiveAndStabilizes() {
        double previous = 0;
        for (int i = 0; i <= 1200; i++) {
            double g = BigSmokeModel.growth(i * 0.05);
            assertTrue(Double.isFinite(g) && g > 0, "growth must be finite positive at " + i);
            assertTrue(g >= previous - EPS, "growth never shrinks");
            previous = g;
        }
        double settled = BigSmokeModel.growth(60);
        for (double t = 60; t <= 3600; t += 7.5) assertEquals(settled, BigSmokeModel.growth(t), 1e-6, "growth saturates at " + t);
        assertEquals(0.28, BigSmokeModel.growth(0), EPS);
    }

    @Test void extraRadialMotionBuildsAsVolumeIncreases() {
        assertEquals(0, BigSmokeModel.radialTravel(.18, .8), EPS);
        assertEquals(0, BigSmokeModel.radialTravel(10, 0), EPS);
        double early = BigSmokeModel.radialTravel(.5, .8);
        double middle = BigSmokeModel.radialTravel(1, .8);
        assertTrue(middle - early > early);
        assertEquals(1.35, BigSmokeModel.radialTravel(120, .8), EPS);
    }

    @Test void heatExpiresWhileSmokeStillShows() {
        for (int hotTicks = 1; hotTicks <= 200; hotTicks += 1) {
            assertEquals(1, BigSmokeModel.heat(0, hotTicks), EPS);
            assertEquals(0, BigSmokeModel.heat(hotTicks, hotTicks), EPS, "heat ends exactly at hotTicks");
            assertEquals(0, BigSmokeModel.heat(hotTicks + 500, hotTicks), EPS, "and stays gone");
            assertEquals(0.5, BigSmokeModel.heat(hotTicks / 2.0, hotTicks), EPS);
            int lifetime = 1200;
            for (int age = hotTicks + 1; age < lifetime; age++)
                assertTrue(BigSmokeModel.coverage(age/20.0,lifetime/20.0,BigSmokeModel.expansion(age/20.0,.8),.25) > 0, "alpha still positive at " + age + "/" + hotTicks);
        }
        assertTrue(BigSmokeModel.coverage(1,60,BigSmokeModel.expansion(1,.8),.25) > 0.5, "still largely opaque when the default 20-tick heat has run out");
        assertEquals(1, BigSmokeModel.heat(0, 0), EPS, "a zero window still starts hot instead of dividing by zero");
    }

    @Test void coverageFadesAsCloudExpandsAndFinishesAtLifetime() {
        for(double life:new double[]{.5,8,60}) {
            assertEquals(0,BigSmokeModel.coverage(0,life,1,.25));
            assertEquals(1,BigSmokeModel.coverage(.08,life,1,.25),EPS,"same fade-in despite lifetime");
            double prev=1;
            for(int tick=2;tick<=life*20;tick++) {
                double t=tick/20.0,a=BigSmokeModel.coverage(t,life,BigSmokeModel.expansion(t,.8),.25);
                assertTrue(a>=0&&a<=prev+EPS);prev=a;
            }
            assertEquals(0,BigSmokeModel.coverage(life,life,1,.25));
        }
        assertTrue(BigSmokeModel.coverage(2,8,1,.25)>BigSmokeModel.coverage(2,8,2,.25),"expansion dilutes coverage");
    }
    @Test void earlyCloudBridgesFlashAndColdCloudKeepsExpanding() {
        assertTrue(BigSmokeModel.growth(.2)>.9,"smoke is already broad when the four-tick flash ends");
        assertTrue(BigSmokeModel.growth(4)>BigSmokeModel.growth(1)*1.2,"cold stage must still expand");
        assertEquals(1,BigSmokeModel.expansion(60,0));
        assertEquals(1.8,BigSmokeModel.growth(120),1e-6);
    }
    @Test void interleavedReleaseCoversHeadAndRootWithoutOrderedTrain() {
        assertEquals(.5,BigSmokeModel.station(0));assertEquals(.25,BigSmokeModel.station(1));assertEquals(.75,BigSmokeModel.station(2));
        var seen=new java.util.HashSet<Double>();
        for(int i=0;i<256;i++){double f=BigSmokeModel.station(i);assertTrue(f>0&&f<1);assertTrue(seen.add(f));}
        assertTrue(java.util.stream.IntStream.range(0,4).mapToDouble(BigSmokeModel::station).min().orElseThrow()<.2);
        assertTrue(java.util.stream.IntStream.range(0,4).mapToDouble(BigSmokeModel::station).max().orElseThrow()>.7);
    }
    @Test void flashLinkCanBeDisabledAndNeverOverridesSmokeSizeWithoutBound() {
        assertEquals(4,BigSmokeModel.linkedRadius(4,100,0));
        assertEquals(4,BigSmokeModel.linkedRadius(4,1,1));
        assertEquals(12,BigSmokeModel.linkedRadius(4,100,1));
        assertEquals(6,BigSmokeModel.linkedRadius(4,8,.5));
    }

    @Test void chargeScaleIsMonotonicPastTheRetiredOnePointEightCap(){
        double ref=4.0,previous=BigSmokeModel.chargeScale(0.1,ref,.5,6.0);
        for(double power=0.2;power<=4096;power*=1.05){
            double s=BigSmokeModel.chargeScale(power,ref,.5,6.0);
            assertTrue(s>=previous-EPS,"charge scale never shrinks at power "+power);previous=s;
        }
        assertEquals(1.8,BigSmokeModel.chargeScale(4*1.8*1.8,ref,.5,1.8),EPS,"the old cap is just a maximum now");
        assertTrue(BigSmokeModel.chargeScale(2*2*2*2*ref,ref,.5,6.0)>1.8,"1.8 is no longer the ceiling");
        assertEquals(6.0,BigSmokeModel.chargeScale(1e9,ref,.5,6.0),EPS,"only chargeMaxScale stops growth");
    }
    @Test void chargeScaleIsOneAtTheReferenceAndZeroExponentIgnoresCharge(){
        assertEquals(1.0,BigSmokeModel.chargeScale(4,4,.5,6),EPS);
        assertEquals(.1,BigSmokeModel.chargeScale(.01,4,.5,6),EPS,"a tiny power clamps instead of dividing to zero");
        for(double power:new double[]{.01,.5,4,64,4096})for(double maximum:new double[]{.1,1.8,16})
            assertEquals(Math.min(1.0,maximum),BigSmokeModel.chargeScale(power,4,0,maximum),EPS,"exponent 0 disables charge sizing");
        assertEquals(2.0,BigSmokeModel.chargeScale(16,4,.5,6),EPS,"exponent .5 is a square root");
        assertEquals(Math.cbrt(16),BigSmokeModel.chargeScale(16,4,2/3.0,6),1e-6,"exponent passes through as given");
    }
    @Test void chargeScaleStopsAtTheMaximumAndNeverDropsBelowOneTenth(){
        for(double maximum:new double[]{.1,1.8,3,6,16}){
            assertEquals(maximum,BigSmokeModel.chargeScale(1e6,4,.5,maximum),EPS,"maximum "+maximum);
            assertTrue(BigSmokeModel.chargeScale(1e6,4,.5,maximum)>=BigSmokeModel.chargeScale(1e5,4,.5,maximum)-EPS);
        }
        assertEquals(.1,BigSmokeModel.chargeScale(4,1e6,.5,6),EPS,"small charges floor at a tenth");
        assertEquals(6.0,BigSmokeModel.chargeScale(1e9,4,.5,6),EPS);
        assertTrue(BigSmokeModel.chargeScale(1e9,4,.5,6)<=16.0,"default maximum 6 keeps the authored cap");
    }

    @Test void impactSmokeMovesThroughTheSharedHotAndColdAtlasFrames() {
        assertEquals(0, BigSmokeModel.impactMaterialAge(0, 40, 0));
        assertTrue(BigSmokeModel.impactMaterialAge(20, 40, 0) < .5);
        assertTrue(BigSmokeModel.impactMaterialAge(39, 40, 0) < 1);
        assertEquals(1, BigSmokeModel.impactMaterialAge(40, 40, 0));
        assertEquals(1, BigSmokeModel.impactMaterialAge(0, 0, 0));
        assertEquals(4.5, BigSmokeModel.impactMaterialAge(72, 40, .5));
        assertEquals(8, BigSmokeModel.impactMaterialAge(104, 40, 1));
    }

    @Test void encodedSeedKeepsSixBitIdAndQuantizedThermalFrame() {
        for (int seed = -200; seed <= 200; seed++) assertEquals(seed & 63, (BigSmokeModel.encodedSeed(seed, 0) - 128) & 63);
        assertEquals(128 + 63, BigSmokeModel.encodedSeed(63, 0), "id 63 stays in the field, not the frame");
        assertEquals(0, (BigSmokeModel.encodedSeed(63, 0) - 128) >>> 6, "and never bleeds into the thermal frame");
        assertEquals(128, BigSmokeModel.encodedSeed(64, 0), "id 64 wraps back to 0");
        assertEquals(128, BigSmokeModel.encodedSeed(0, -50), "negative age clamps to frame 0");
        assertEquals(128 + 64 * 255, BigSmokeModel.encodedSeed(0, 1e9), "oversized age clamps to frame 255");
        assertEquals(128, BigSmokeModel.encodedSeed(0, 0.999 / 32), "just under a boundary stays in frame 0");
        assertEquals(128 + 64, BigSmokeModel.encodedSeed(0, 1.0 / 32), "exactly on a boundary steps up");
        assertEquals(128 + 64, BigSmokeModel.encodedSeed(0, 1.999 / 32), "just under the next boundary is still frame 1");
        assertEquals(128 + 128, BigSmokeModel.encodedSeed(0, 2.0 / 32));
        for (int frame = 0; frame < 256; frame++) {
            int encoded = BigSmokeModel.encodedSeed(63, (frame + 0.5) / 32);
            assertEquals(63, (encoded - 128) & 63, "id 63 survives every frame");
            assertEquals(frame, (encoded - 128) >>> 6, "thermal frame survives the id");
        }
    }

    @Test void rootCoolsWhileForwardCloudStaysHotForEveryBirthJitter() {
        for(double jitter:new double[]{.85,1,1.15}){
            double root=BigSmokeModel.thermalTicks(20,.1,.35,1.5,jitter);
            double tip=BigSmokeModel.thermalTicks(20,.9,.35,1.5,jitter);
            assertEquals(0,BigSmokeModel.heat(10,root),EPS);
            assertTrue(BigSmokeModel.heat(10,tip)>.5);
            assertEquals(0,BigSmokeModel.heat(40,tip),EPS,"heat always ends; smoke coverage remains independent");
        }
    }
    @Test void equalThermalScalesDisableAxialGradientAndMinimumStaysFinite() {
        for(double p:new double[]{-1,0,.25,.5,1,2}){
            assertEquals(20,BigSmokeModel.thermalTicks(20,p,1,1,1),EPS);
            assertEquals(1,BigSmokeModel.thermalTicks(1,p,.05,.05,.85),EPS);
        }
        assertEquals(1600,BigSmokeModel.thermalTicks(200,1,.05,8,1),EPS);
    }
}
