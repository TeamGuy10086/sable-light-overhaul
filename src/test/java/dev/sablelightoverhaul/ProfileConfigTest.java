package dev.sablelightoverhaul;
import dev.sablelightoverhaul.client.MuzzleClientConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProfileConfigTest {
    @Test void independentProfilesGenerateValidConfigAndPreserveUserDefaults() throws Exception {
        var c=CommentedConfig.inMemory(); var s=MuzzleClientConfig.SPEC;s.correct(c);
        assertTrue(s.isCorrect(c));
        // Exact accepted values are checked against the instance snapshot by AcceptedDefaultsTest.
        // This test checks that changing one independent profile does not alter its neighbours.
        Double smokeRange=c.get("lightProfile.bigCannon.smoke.shape.forwardRange");
        Double blastCore=c.get("lightProfile.bigCannon.blast.spatial.coreFraction");
        c.set("lightProfile.bigCannon.blast.shape.forwardRange",40.0);
        c.set("lightProfile.autocannon.spatial.coreFraction",.1);
        assertEquals(smokeRange,(Double)c.get("lightProfile.bigCannon.smoke.shape.forwardRange"));
        assertEquals(blastCore,(Double)c.get("lightProfile.bigCannon.blast.spatial.coreFraction"));
        assertTrue(s.isCorrect(c));
        c=CommentedConfig.inMemory();s.correct(c);
        Files.writeString(Path.of("build/generated-default.toml"),new TomlWriter().writeToString(c));
        var server=CommentedConfig.inMemory();dev.sablelightoverhaul.lighting.LightingCommon.SPEC.correct(server);
        Files.writeString(Path.of("build/generated-server-default.toml"),new TomlWriter().writeToString(server));
    }
}
