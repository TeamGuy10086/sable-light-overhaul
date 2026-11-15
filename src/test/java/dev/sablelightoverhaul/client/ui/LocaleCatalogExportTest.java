package dev.sablelightoverhaul.client.ui;

import com.google.gson.GsonBuilder;
import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Author-side translation inventory, including optional Sable Hostile fields. */
class LocaleCatalogExportTest {
    @Test void export() throws Exception {
        var groups = new ArrayList<Source.Group>();
        groups.addAll(new TomlSource(MuzzleClientConfig.SPEC, Set.of(), true).groups());
        groups.addAll(new TomlSource(LightingCommon.SPEC, Set.of(), true).groups());
        var path = Path.of("build/reports/locale-catalog.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(groups));
    }
}
