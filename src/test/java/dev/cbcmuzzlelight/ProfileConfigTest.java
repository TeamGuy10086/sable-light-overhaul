package dev.cbcmuzzlelight;
import dev.cbcmuzzlelight.client.MuzzleClientConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProfileConfigTest {
    @Test void independentProfilesGenerateValidConfigAndPreserveUserDefaults() throws Exception {
        var c=CommentedConfig.inMemory(); var s=MuzzleClientConfig.SPEC;s.correct(c);
        assertTrue(s.isCorrect(c));
        assertEquals(9.0,(Double)c.get("visuals.autocannon.sizeMultiplier"));
        assertEquals(2.0,(Double)c.get("visuals.autocannon.flameWidthScale"));
        assertEquals(32.0,(Double)c.get("advanced.autocannon.shape.forwardRange"));
        assertEquals(25.0,(Double)c.get("advanced.bigCannon.blast.shape.forwardRange"));
        assertEquals(25.0,(Double)c.get("advanced.bigCannon.smoke.shape.forwardRange"));
        c.set("advanced.bigCannon.blast.shape.forwardRange",40.0);
        c.set("advanced.autocannon.spatial.coreFraction",.1);
        assertEquals(25.0,(Double)c.get("advanced.bigCannon.smoke.shape.forwardRange"));
        assertEquals(.25,(Double)c.get("advanced.bigCannon.blast.spatial.coreFraction"));
        assertTrue(s.isCorrect(c));
        c=CommentedConfig.inMemory();s.correct(c);
        Files.writeString(Path.of("build/generated-default.toml"),new TomlWriter().writeToString(c));
        var server=CommentedConfig.inMemory();dev.cbcmuzzlelight.lighting.LightingCommon.SPEC.correct(server);
        Files.writeString(Path.of("build/generated-server-default.toml"),new TomlWriter().writeToString(server));
    }
}
