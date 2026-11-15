package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.client.ui.ConfigTaxonomy;
import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.Source;
import dev.sablelightoverhaul.client.ui.TomlSource;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Headless check of the config catalog: which keys it finds, what it calls them and how it would draw them.
 *
 * <p>The screen itself cannot be tested without a game, but everything that decides <em>what</em> it shows can: the
 * walk over the spec, the label split, the kind mapping and the snapshot. That is where the prototype's first
 * screen was wrong -- a missing label and a missing row are both decided here, not in the widget code -- and a
 * failing test is a second faster than a relaunch.
 */
class ConfigCatalogTest {
    @Test void legacyNestedTankLightKeysAreShownOnTankPage() {
        for(String leaf:List.of("time.durationTicks","time.peakHoldTicks","time.decayPerTick","shape.forwardRange")) {
            var part=ConfigTaxonomy.of("lightProfile.bigCannon.tankCannon."+leaf);
            org.junit.jupiter.api.Assertions.assertEquals("坦克炮",part.section());
            org.junit.jupiter.api.Assertions.assertEquals("光曲线（范围/时间/缩放）",part.topic());
        }
        org.junit.jupiter.api.Assertions.assertEquals("大炮",ConfigTaxonomy.of("lightProfile.bigCannon.blast.time.durationTicks").section());
    }

    @Test
    void everyPrototypeKeyIsFoundAndLabelled() {
        Source source = new TomlSource(MuzzleClientConfig.SPEC, dev.sablelightoverhaul.client.ui.PrototypeKeys.ONLY);
        List<Source.Group> groups = source.groups();
        var values = source.read();
        StringBuilder report = new StringBuilder("\nconfig catalog\n");
        int rows = 0;
        for (Source.Group group : groups) {
            report.append("  [").append(group.category()).append(" / ").append(group.section()).append("]\n");
            for (FieldSpec f : group.fields()) {
                rows++;
                report.append(String.format("    %-40s %-10s %-22s %s%n",
                        f.key(), f.kind(), "|" + f.label() + "|",
                        dev.sablelightoverhaul.client.ui.FieldValue.display(f, values.get(f.key()))));
            }
        }
        report.append("  rows=").append(rows).append(" groups=").append(groups.size()).append('\n');
        System.out.println(report);

        org.junit.jupiter.api.Assertions.assertEquals(
                dev.sablelightoverhaul.client.ui.PrototypeKeys.ONLY.size(), rows,
                "every key in the test space must become exactly one row:\n" + report);
        for (Source.Group group : groups) {
            for (FieldSpec f : group.fields()) {
                org.junit.jupiter.api.Assertions.assertFalse(f.label().isBlank(), "blank label for " + f.key());
                org.junit.jupiter.api.Assertions.assertNotNull(values.get(f.key()), "no value for " + f.key());
            }
        }
    }

    /**
     * The regression that made every picker snap back to its first option lived in the value conversion,
     * which is now {@code FieldValue.numeric} and is tested in that package. This one keeps the catalogue
     * itself honest: the prototype space must contain a real boolean row (the toggle) and a whole-number
     * cycler (the picker), or those two controls have no way to be exercised at all.
     */
    @Test
    void theTestSpaceExercisesEveryKindThatNeedsARealKey() {
        Source source = new TomlSource(MuzzleClientConfig.SPEC, dev.sablelightoverhaul.client.ui.PrototypeKeys.ONLY);
        var kinds = source.groups().stream()
                .flatMap(group -> group.fields().stream())
                .map(FieldSpec::kind)
                .collect(java.util.stream.Collectors.toSet());
        for (FieldSpec.Kind kind : FieldSpec.Kind.values()) {
            org.junit.jupiter.api.Assertions.assertTrue(kinds.contains(kind),
                    "the prototype keys must include one " + kind + " row; found " + kinds);
        }
    }

    /** Every key must land in one of the ten categories the map promises, with a section and a topic. */
    @Test
    void everyCatalogKeyFallsIntoOneOfTheTenCategories() {
        var categories = new java.util.LinkedHashSet<String>();
        int clientRows = 0;
        int serverRows = 0;
        for (var entry : List.of(
                Map.entry("client", MuzzleClientConfig.SPEC),
                Map.entry("server", LightingCommon.SPEC))) {
            Source source = new TomlSource((net.neoforged.neoforge.common.ModConfigSpec) entry.getValue(),
                    java.util.Set.of());
            var groups = source.groups();
            int rows = 0;
            for (Source.Group group : groups) {
                rows += group.fields().size();
                org.junit.jupiter.api.Assertions.assertFalse(group.category().equals("其它"),
                        entry.getKey() + " key matched no taxonomy rule: " + group.fields().getFirst().key());
                org.junit.jupiter.api.Assertions.assertFalse(group.section().isBlank(), "blank section for " + group);
                org.junit.jupiter.api.Assertions.assertFalse(group.topic().isBlank(), "blank topic for " + group);
                org.junit.jupiter.api.Assertions.assertTrue(
                        ConfigTaxonomy.CATEGORIES.contains(group.category()),
                        "unknown category " + group.category());
                categories.add(group.category());
            }
            if (entry.getKey().equals("client")) clientRows = rows; else serverRows = rows;
        }
        org.junit.jupiter.api.Assertions.assertEquals(ConfigTaxonomy.CATEGORIES.size() - 1, categories.size(),
                "both specs together must cover all ten categories, found " + categories);
        org.junit.jupiter.api.Assertions.assertEquals(334, clientRows,
                "the client catalogue changed size; update CONFIG-MAP and this number together");
        org.junit.jupiter.api.Assertions.assertEquals(178, serverRows,
                "the server catalogue changed size; update CONFIG-MAP and this number together");
    }

    /** A wide whole-number range is a count, so its slider steps by 1 and spans the real range, not the default. */
    @Test
    void countLikeRangesHaveAUsefulStepAndTheirRealMaximum() {
        Source source = new TomlSource(MuzzleClientConfig.SPEC, java.util.Set.of());
        var byKey = source.groups().stream()
                .flatMap(group -> group.fields().stream())
                .collect(java.util.stream.Collectors.toMap(FieldSpec::key, field -> field));
        FieldSpec duration = byKey.get("particles.muzzle.bigCannon.durationTicks");
        org.junit.jupiter.api.Assertions.assertNotNull(duration, "durationTicks missing from the catalogue");
        org.junit.jupiter.api.Assertions.assertEquals(1200.0, duration.max(), 0.0001);
        org.junit.jupiter.api.Assertions.assertEquals(1.0, duration.step(), 0.0001);
        org.junit.jupiter.api.Assertions.assertEquals((30.0 - 1.0) / (1200.0 - 1.0), duration.fraction(30.0), 0.0001);
        FieldSpec spark = byKey.get("apSparks.hit.lifetime");
        org.junit.jupiter.api.Assertions.assertEquals(1.0, spark.step(), 0.0001);
    }

    /** List values are edited as text; rendering them as a fraction of 0..1 was a type error. */
    @Test
    void listValuesAreTextRowsNotSliders() {
        Source client = new TomlSource(MuzzleClientConfig.SPEC, java.util.Set.of());
        var kinds = client.groups().stream()
                .flatMap(group -> group.fields().stream())
                .collect(java.util.stream.Collectors.toMap(FieldSpec::key, FieldSpec::kind));
        for (String key : java.util.List.of(
                "apSparks.bigShells.names", "shockwave.ringCore", "shockwave.ringGroups",
                "particles.groundDust.frames", "subLevels.farBlockEntityNames")) {
            org.junit.jupiter.api.Assertions.assertEquals(FieldSpec.Kind.TEXT, kinds.get(key),
                    key + " must be a text row");
        }
        Source server = new TomlSource(LightingCommon.SPEC, java.util.Set.of());
        var serverKinds = server.groups().stream()
                .flatMap(group -> group.fields().stream())
                .collect(java.util.stream.Collectors.toMap(FieldSpec::key, FieldSpec::kind));
        for (String key : java.util.List.of("farEntities.names", "explosions.yieldTiers",
                "staticSources.colors.overrides")) {
            org.junit.jupiter.api.Assertions.assertEquals(FieldSpec.Kind.TEXT, serverKinds.get(key),
                    key + " must be a text row");
        }
    }

    /**
     * The paths the UI shows are the TOML paths, so a section that is silently nested under another is a
     * row in the wrong place (or two rows with the same label). These are the moves of 0.1.472–0.1.474:
     * the three stowaways at the root, both cannons' plume keys as real client keys, and the detonation
     * wave's own prefix / the beam block's wrong parent.
     */
    @Test
    void theMovedSectionsAndTheNewPlumeKeysAreWhereTheySay() {
        Source source = new TomlSource(MuzzleClientConfig.SPEC, java.util.Set.of());
        var keys = source.groups().stream()
                .flatMap(group -> group.fields().stream())
                .map(FieldSpec::key)
                .collect(java.util.stream.Collectors.toSet());
        for (String key : java.util.List.of(
                "clouds.nearCull", "clouds.fogScale", "clouds.enabled",
                "particles.depthSort", "particles.occlusionBudget",
                "debug.effects", "debug.trails",
                "muzzle.tankCannon.plumeAlpha.initial",
                "muzzle.tankCannon.plumeAlpha.fadeFrom",
                "muzzle.bigCannon.plumeAlpha.initial",
                "shockwave.thickness", "shockwave.initialRatio", "shockwave.compressionSpeed",
                "spotlights.beam.volume", "spotlights.beam.shadowBudget")) {
            org.junit.jupiter.api.Assertions.assertTrue(keys.contains(key), "missing moved key " + key);
        }
        for (String old : java.util.List.of(
                "muzzle.bigCannon.clouds.nearCull",
                "muzzle.bigCannon.particles.depthSort",
                "muzzle.bigCannon.debug.effects",
                "shockwave.shockwave.thickness",
                "shockwave.beam.volume", "spotlights.beam.occlusion")) {
            org.junit.jupiter.api.Assertions.assertFalse(keys.contains(old), "stale path still defined: " + old);
        }

        Source server = new TomlSource(LightingCommon.SPEC, java.util.Set.of());
        var serverKeys = server.groups().stream()
                .flatMap(group -> group.fields().stream())
                .map(FieldSpec::key)
                .collect(java.util.stream.Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertTrue(serverKeys.contains("particles.muzzleCull"),
                "the server send-distance block must be particles.*");
        org.junit.jupiter.api.Assertions.assertFalse(serverKeys.contains("Particle.muzzleCull"),
                "stale capital-P path still defined");
    }
}
