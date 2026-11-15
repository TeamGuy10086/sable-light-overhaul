package dev.sablelightoverhaul;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one thing a config reorganisation must not do is lose a value, and this is where that is checked
 * without a game: real-shaped fragments of the owner's own file, moved onto the current names.
 *
 * <p>The output puts one blank line after each section, so these compare against the normalised form rather
 * than the input's own spacing. What matters is that every value and every comment is still there, and that
 * no header is repeated.
 */
class ConfigMigrationTest {
    @Test void retiredLightingModesMigrateOnlyInTheLightingSection() {
        for (String retired : List.of("Legacy", "Compatible", "None")) {
            var input = List.of("[lighting]", "mode = \"" + retired + "\" # user setting",
                    "[unrelated]", "mode = 'Legacy'");
            var expected = retired.equals("None") ? "Off" : "Advanced";
            assertTrue(ConfigMigration.neededClient(input));
            var output = ConfigMigration.migrateClient(input);
            assertEquals(List.of("[lighting]", "mode = \"" + expected + "\" # user setting",
                    "[unrelated]", "mode = 'Legacy'"), output);
            assertFalse(ConfigMigration.neededClient(output));
        }
    }

    /**
     * The detonation wave's keys carried a second "shockwave." of their own, the beam block was opened while
     * the shockwave section was still on the stack, and the server's send distances were a capital-P section
     * of their own. Each goes to the name that describes it.
     */
    @Test void theWaveBeamAndServerParticlesGetTheirOwnNames() {
        var client = ConfigMigration.migrateClient(List.of(
                "[shockwave]",
                "enabled = true",
                "[shockwave.shockwave]",
                "thickness = 0.5",
                "initialRatio = 1.0",
                "[shockwave.beam]",
                "volume = 0.25",
                "occlusion = 2",
                ""));
        assertEquals(List.of(
                "[shockwave]",
                "enabled = true",
                "thickness = 0.5",
                "initialRatio = 1.0",
                "",
                "[spotlights.beam]",
                "volume = 0.25",
                "occlusion = 2",
                ""), client);

        var server = ConfigMigration.migrateServer(List.of(
                "[Particle]",
                "muzzleCull = 1024",
                "shockwaveRange = 1024.0",
                ""));
        assertEquals(List.of(
                "[particles]",
                "muzzleCull = 1024",
                "shockwaveRange = 1024.0",
                ""), server);
    }

    /**
     * Three sections were never the big cannon's: a {@code push("muzzle.bigCannon.plumeAlpha")} that popped
     * once instead of three times left them nested under it. The values (and the big cannon's own plume keys,
     * which must stay put) travel to their own names.
     */
    @Test void theBigCannonsStowawaysMoveToTheRoot() {
        var out = ConfigMigration.migrateClient(List.of(
                "[muzzle.bigCannon.plumeAlpha]",
                "initial = 0.4",
                "[muzzle.bigCannon.clouds]",
                "nearCull = 6.0",
                "fogScale = 0.35",
                // The real file already has a root [particles] section (the muzzle particle groups), so the
                // stowaway body has to merge into it rather than emit a second header.
                "[particles]",
                "trackedPerShot = 8",
                "[muzzle.bigCannon.particles]",
                "depthSort = true",
                "[muzzle.bigCannon.debug]",
                "effects = false",
                ""));
        assertEquals(List.of(
                "[muzzle.bigCannon.plumeAlpha]",
                "initial = 0.4",
                "",
                "[clouds]",
                "nearCull = 6.0",
                "fogScale = 0.35",
                "",
                "[particles]",
                "trackedPerShot = 8",
                "depthSort = true",
                "",
                "[debug]",
                "effects = false",
                ""), out);
    }

    @Test void theTwoSectionsThatWereNamedAfterEachOtherSwapNames() {
        var out = ConfigMigration.migrateClient(List.of(
                "[aerial]",
                "# the trail",
                "enabled = true",
                "drift = 0.06",
                "",
                "[trails]",
                "coreFraction = 0.15",
                "[trails.bigCannon]",
                "radius = 12.0",
                ""));
        assertEquals(List.of(
                "[trail]",
                "# the trail",
                "enabled = true",
                "drift = 0.06",
                "",
                "[tracerLight]",
                "coreFraction = 0.15",
                "",
                "[tracerLight.bigCannon]",
                "radius = 12.0",
                ""), out);
    }

    @Test void twoOldSectionsBecomeOneWithoutRepeatingItsHeader() {
        // [ap] and [apSpark] are far apart in the real file, so merging by editing headers in place would
        // leave the second one's keys under whatever section happened to precede it.
        var out = ConfigMigration.migrateClient(List.of(
                "[ap]",
                "streakWidth = 0.30",
                "[other]",
                "keep = 1",
                "[apSpark]",
                "enabled = true",
                "[apSpark.hit]",
                "count = 30",
                "[apSpark.ricochet]",
                "chips = 6",
                ""));
        assertEquals(List.of(
                "[apSparks]",
                "streakWidth = 0.30",
                "enabled = true",
                "",
                "[other]",
                "keep = 1",
                "",
                "[apSparks.hit]",
                "count = 30",
                "",
                "[apSparks.ricochet]",
                "chips = 6",
                ""), out);
    }

    @Test void aFileThatIsAlreadyCurrentIsNotTouched() {
        var current = List.of("[trail]", "enabled = true", "[tracerLight]", "coreFraction = 0.15", "");
        assertFalse(ConfigMigration.neededClient(current), "nothing in it has moved");
    }

    @Test void theVisualsSectionJoinsTheMuzzleHierarchy() {
        var out = ConfigMigration.migrateClient(List.of(
                "[particles.visuals.autocannon]",
                "sizeMultiplier = 9.0",
                ""));
        assertEquals(List.of("[particles.muzzle.autocannon.flame]", "sizeMultiplier = 9.0", ""), out);
    }

    /**
     * The server's tables are not the client's, and this is the case that proves it matters: the server's
     * trails is the tracer FEED while the client's is the shell lights, so one shared table renamed the feed
     * to the lights' name and left its two values under a section the spec does not define.
     */
    @Test void theServerFeedIsNotTheClientShellLights() {
        var server = List.of("[trails]", "feedEnabled = true", "feedRange = 512", "");
        assertEquals(List.of("[tracerFeed]", "feedEnabled = true", "feedRange = 512", ""),
                ConfigMigration.migrateServer(server));
        // A file the shared table already renamed ends up right rather than merely no worse.
        var renamed = List.of("[tracerLight]", "feedEnabled = true", "feedRange = 512", "");
        assertEquals(List.of("[tracerFeed]", "feedEnabled = true", "feedRange = 512", ""),
                ConfigMigration.migrateServer(renamed));
        // And the client's own tracerLight, which is the shell lights, is left exactly where it is.
        var client = List.of("[tracerLight]", "coreFraction = 0.15", "");
        assertEquals(client, ConfigMigration.migrateClient(client));
        // The feed section's child, had one existed, would follow it; the point is that the tables differ.
        assertTrue(ConfigMigration.neededServer(List.of("[trails]", "")));
        assertFalse(ConfigMigration.neededClient(List.of("[tracerFeed]", "")));
    }}
