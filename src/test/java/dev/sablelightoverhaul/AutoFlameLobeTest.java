package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.MuzzleClientConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The autocannon experiment is a shader-only modulation of the existing gas body, not a second particle
 * system. These checks keep that promise reviewable without a running game: the config is bounded, strength
 * zero is a real switch, and the Photon copy is regenerated from the same fragment rather than drifting.
 */
class AutoFlameLobeTest {
    @Test void defaultsAreBoundedAndStrengthZeroIsTheLiveOffSwitch() {
        for(var value:java.util.List.of(MuzzleClientConfig.AUTO_LOBE_COUNT,
                MuzzleClientConfig.AUTO_LOBE_STRENGTH,MuzzleClientConfig.AUTO_LOBE_TWIST,
                MuzzleClientConfig.AUTO_LOBE_LENGTH_SPREAD,MuzzleClientConfig.AUTO_LOBE_SHARPNESS,
                MuzzleClientConfig.AUTO_CORE_FOCUS,MuzzleClientConfig.AUTO_EDGE_SOFTNESS))
            assertTrue(value.getSpec().test(value.getDefault()));
        assertTrue(MuzzleClientConfig.AUTO_LOBE_STRENGTH.getSpec().test(0.0),
            "accepted tuning must not remove the zero-strength off switch");
    }

    @Test void shaderModulatesTheExistingVolumeAndLeavesANeutralPath() throws Exception {
        String gas = Files.readString(Path.of(
                "src/main/resources/assets/sablelightoverhaul/shaders/include/slo_gas.glsl"));
        assertTrue(gas.contains("uniform vec4 sloAutoGas;"));
        assertTrue(gas.contains("uniform vec4 sloAutoGas2;"));
        assertTrue(gas.contains("float slo_gas_lobe("));
        assertTrue(gas.contains("float petal = slo_gas_lobe(vec3(x, dy, dz), x, age, off, c, f);"));
        // At strength 0 every added factor is exactly 1 and tailReach is exactly reach.
        assertTrue(gas.contains("float tailReach = reach * (1.0 - 0.65 * strength"));
        assertTrue(gas.contains("W*=1.0+0.45*strength*(petal-0.30);"));
        assertTrue(gas.contains("float edge0=mix(0.45,mix(0.55,0.28,clamp(sloAutoGas2.z,0.0,1.0)),strength);"));
        assertTrue(gas.contains("env *= mix(1.0, 0.35 + 0.65 * petal, strength * 0.90);"));
        assertTrue(gas.contains("coreR*=mix(1.0,0.72,clamp(sloAutoGas2.y,0.0,1.0)*strength);"));
        assertTrue(gas.contains("core *= mix(1.0, 0.62 + 0.38 * petal, strength * 0.55);"));

        String auto = Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/AutoMuzzleParticle.java"));
        assertFalse(auto.contains("lobe"), "lobes must not add a second particle or draw call");
        String layer = Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/MuzzleParticleLayer.java"));
        assertTrue(layer.contains("sloAutoGas"));
        assertTrue(layer.contains("sloAutoGas2"));
    }

    @Test void photonProgramsAreRegeneratedFromTheSameField() throws Exception {
        for (String name : new String[] {"gbuffers_all_translucent.fsh", "gbuffers_all_solid.fsh"}) {
            String fsh = Files.readString(Path.of("patch/photon/shaders/program/" + name));
            assertTrue(fsh.contains("uniform vec4 sloAutoGas;"), name);
            assertTrue(fsh.contains("uniform vec4 sloAutoGas2;"), name);
            assertTrue(fsh.contains("float slo_gas_lobe("), name);
            assertTrue(fsh.contains("float petal = slo_gas_lobe(vec3(x, dy, dz), x, age, off, c, f);"), name);
        }
        String bridge = Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/IrisMuzzleBridge.java"));
        assertTrue(bridge.contains("\"sloAutoGas\""));
        assertTrue(bridge.contains("\"sloAutoGas2\""));
    }
}
