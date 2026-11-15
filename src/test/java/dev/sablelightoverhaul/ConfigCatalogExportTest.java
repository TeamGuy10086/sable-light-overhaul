package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.Source;
import dev.sablelightoverhaul.client.ui.TomlSource;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exports both config specs as a tab-separated catalogue, for the config map and for checking the UI layer.
 *
 * <p>This is the "the catalogue can generate the documentation table" half of T23: the hand-written tables in
 * the docs went stale twice, and the only durable fix is to let the spec say what the keys are. The file lands
 * in {@code build/reports}, which is ignored, so it is a report rather than a source of truth.
 */
class ConfigCatalogExportTest {
    @Test
    void exportBothSides() throws IOException {
        Path out = Path.of("build", "reports", "config-catalog.tsv");
        Files.createDirectories(out.getParent());
        StringBuilder text = new StringBuilder("side\tcategory\tsection\ttopic\tkind\tkey\tlabel\ttooltip\n");
        int rows = 0;
        for (var side : List.of(
                new Object[] {"client", MuzzleClientConfig.SPEC},
                new Object[] {"server", LightingCommon.SPEC})) {
            Source source = new TomlSource((net.neoforged.neoforge.common.ModConfigSpec) side[1], Set.of());
            for (Source.Group group : source.groups()) {
                for (FieldSpec field : group.fields()) {
                    text.append(side[0]).append('\t')
                        .append(group.category()).append('\t')
                        .append(group.section()).append('\t')
                        .append(group.topic()).append('\t')
                        .append(field.kind()).append('\t')
                        .append(field.key()).append('\t')
                        .append(oneLine(field.label())).append('\t')
                        .append(oneLine(field.tooltip())).append('\n');
                    rows++;
                }
            }
        }
        Files.writeString(out, text.toString(), StandardCharsets.UTF_8);
        System.out.println("config catalogue: " + rows + " rows -> " + out.toAbsolutePath());
        org.junit.jupiter.api.Assertions.assertTrue(rows > 300, "both sides together must be more than 300 rows");
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
