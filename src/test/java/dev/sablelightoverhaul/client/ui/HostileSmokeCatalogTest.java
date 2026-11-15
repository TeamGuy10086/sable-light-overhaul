package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.HostileSmokeWeapon;
import dev.sablelightoverhaul.client.HostileSmokeConfig;
import dev.sablelightoverhaul.client.MuzzleClientConfig;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostileSmokeCatalogTest {
    @Test void optionalCategoryHasOneSectionPerWeaponWithTheCorrectControls() {
        var hidden = new TomlSource(MuzzleClientConfig.SPEC, Set.of(), false);
        assertTrue(hidden.groups().stream().noneMatch(g -> g.category().equals(HostileSmokeConfig.CATEGORY)));
        var shown = new TomlSource(MuzzleClientConfig.SPEC, Set.of(), true);
        var groups = shown.groups().stream().filter(g -> g.category().equals(HostileSmokeConfig.CATEGORY)).toList();
        assertTrue(ConfigTaxonomy.CATEGORIES.contains(HostileSmokeConfig.CATEGORY));
        assertEquals(HostileSmokeWeapon.values().length + 2, groups.size());
        assertEquals(9, groups.stream().filter(g -> g.section().equals("舰炮音效")).mapToInt(g -> g.fields().size()).sum());
        assertNull(hidden.spec("navalAudio.reportsEnabled"));
        assertEquals("sablelightoverhaul.config.key.navalAudio.reportsEnabled", shown.spec("navalAudio.reportsEnabled").labelKey());
        for (var weapon : HostileSmokeWeapon.values()) {
            String prefix = HostileSmokeConfig.PREFIX + weapon.path() + ".";
            assertNotNull(shown.spec(prefix + "enabled"));
            assertNull(shown.spec(prefix + "scale"));
            assertNotNull(shown.spec(prefix + (weapon.thin() ? "hazeCount" : "lobesPerShot")));
            assertNotNull(shown.spec(prefix + (weapon.thin() ? "hazeLifetime" : "hotTicks")));
            if (!weapon.thin()) {
                var detail = shown.spec(prefix + "densityDetail");
                assertNotNull(detail);
                assertEquals("sablelightoverhaul.config.key.sableHostileSmoke.densityDetail", detail.labelKey());
            }
            assertNull(shown.spec(prefix + (weapon.thin() ? "lobesPerShot" : "hazeCount")));
        }
        var values = MuzzleClientConfig.SPEC.getValues();
        assertEquals(8, values.<net.neoforged.neoforge.common.ModConfigSpec.IntValue>get("sableHostileSmoke.twin_76mm.lobesPerShot").getDefault());
        assertEquals(16, values.<net.neoforged.neoforge.common.ModConfigSpec.IntValue>get("sableHostileSmoke.twin_127mm.lobesPerShot").getDefault());
    }
}
