package dev.sablelightoverhaul;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The smoke density mask remains stable while lifecycle alpha controls transmission. */
class ThermalCoverageContractTest {
    private static final Path INCLUDE =
        Path.of("src/main/resources/assets/sablelightoverhaul/shaders/include/slo_smoke.glsl");

    private static String span(String text) {
        int s = text.indexOf("// Only the big-cannon reserved seed range executes the thermal material.");
        int f = text.indexOf("vec4 slo_thermal_coverage(vec4 smoke)", s);
        int e = text.indexOf("\n}", f) + 2;
        return text.substring(s, e);
    }

    @Test void generatedPhotonCopiesMatchTheCanonicalInclude() throws Exception {
        String canonical = span(Files.readString(INCLUDE)).replace("\r\n", "\n");
        assertTrue(canonical.contains("sloThermalCut"), "the cut travels beside sloThermalHeat");
        for (String name : new String[]{"gbuffers_all_solid.fsh", "gbuffers_all_translucent.fsh"}) {
            // The generated files mix CRLF/LF on purpose; compare on LF-normalised text.
            String copy = span(Files.readString(Path.of("patch/photon/shaders/program/" + name))).replace("\r\n", "\n");
            assertEquals(canonical, copy, name + " drifted from slo_smoke.glsl -- mirror the edit");
        }
    }

    @Test void cutTracksSteadyOpacityWhileLifecycleFadesCoverage() throws Exception {
        String src = Files.readString(INCLUDE);
        assertTrue(src.contains("float steady=clamp(tint.r,0.05,1.0);"),
            "the steady opacity arrives on the vertex red channel");
        assertTrue(src.contains("clamp(tint.a/max(steady,1e-4),0.0,1.0)"),
            "the lifecycle factor is recovered from alpha/steady");
        assertTrue(src.contains("sloThermalCut=0.22*full"),
            "procedural lobes cut at 0.22 of the steady-state full density");
        assertTrue(src.contains("sloThermalCut=(1.0-pow(0.80,steady))"),
            "flipbook cuts the baked coverage tail, not its body");
        assertTrue(src.contains("sloThermalCut=0.42*steady"),
            "the three-quad path cuts each lobe at the same raw density as the cluster");
        assertFalse(src.contains("vec4 slo_thermal_cutout"), "the absolute 0.22 cutout is gone");
        assertFalse(src.contains("if(smoke.a <= sloThermalCut) discard;"),
            "sub-threshold density contributes a soft fringe");
        assertTrue(src.contains("0.94*sloThermalLife*coverage"),
            "lifecycle fades the core and fringe together");
        assertFalse(src.contains("smoothstep(0.12,0.32,smoke.a)"), "the absolute shoulder is gone");
    }

    @Test void coldMaskAdvectionFreezes() throws Exception {
        String src = Files.readString(INCLUDE);
        assertTrue(src.contains("float ma=min(age,1.0);"), "advection uses the frozen age");
        assertFalse(src.contains("shift+age*.17"), "the flow field must not churn after cooling");
        assertFalse(src.contains("vec2(ma*.23,-ma*.16)") == false, "coarse noise uses the frozen age");
    }

    @Test void cutNumbersLandOnTheDistributionTail() {
        // Default steady opacity: thermalSmoke.opacity 0.8 x big plume initial 0.6.
        double steady = 0.48;
        // Flipbook: alpha = 1-(1-d)^steady, cut = 1-0.8^steady -> d-cut = 0.20.
        double cut = 1.0 - Math.pow(0.80, steady);
        double dCut = 1.0 - Math.pow(1.0 - cut, 1.0 / steady);
        assertEquals(0.20, dCut, 1e-9);
        // Procedural: cut = 0.22 * full; a lone lobe-1 then needs raw density ~0.42, and lobes
        // 2/3 can pass on their own (their steady ceilings 0.44*0.48 and 0.36*0.48 exceed the
        // per-lobe cut share) instead of never reaching the old absolute 0.22.
        double full = 1.0 - (1.0 - 0.58 * steady) * (1.0 - 0.44 * steady) * (1.0 - 0.36 * steady);
        double lobeCut = 0.22 * full;
        assertEquals(0.42, lobeCut / (0.58 * steady), 0.02);
        assertTrue(0.44 * steady > lobeCut, "lobe 2 can pass the cut on its own");
        assertTrue(0.36 * steady > lobeCut, "lobe 3 can pass the cut on its own");
    }

    @Test void javaSideSendsTheSteadyOpacity() throws Exception {
        String mesh = Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/MuzzleSmokeMesh.java"));
        assertTrue(mesh.contains("float alpha,float steady,float breakup,int light"),
            "both thermal draws take the steady opacity");
        assertTrue(mesh.contains("steady*weight,breakup,1-densityDetail/3,alpha*weight"),
            "the three-quad path bakes the lobe weight into both channels");
        String particle = Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/BigSmokeParticle.java"));
        assertTrue(particle.contains("a,opacity,breakup(partial),detail,GasVertexData.smokeLight"),
            "BigSmokeParticle passes its configured steady opacity");
    }
}
