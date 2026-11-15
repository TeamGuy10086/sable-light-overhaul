package dev.sablelightoverhaul.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The name filter for the far block entity pass. It is the one piece of that pass that is pure, so it
 * is the one piece that can be pinned down without a game: everything else is pose maths and drawing.
 */
class SubLevelFarEntitiesTest {
    private static final List<String> DEFAULT = List.of("cannon", "flap");

    /** Create: Big Cannons: the mount, the fixed mount, the carriage. */
    @Test void cannonBlockEntitiesMatch() {
        assertTrue(SubLevelFarEntities.matchesName("cannon_mount", DEFAULT));
        assertTrue(SubLevelFarEntities.matchesName("fixed_cannon_mount", DEFAULT));
        assertTrue(SubLevelFarEntities.matchesName("cannon_carriage", DEFAULT));
        assertTrue(SubLevelFarEntities.matchesName("layered_cannon", DEFAULT));
    }

    /** CompactFlap: matched through the path, not through the namespace "compactflap". */
    @Test void flapBlockEntitiesMatch() {
        assertTrue(SubLevelFarEntities.matchesName("compact_flap_bearing", DEFAULT));
        assertTrue(SubLevelFarEntities.matchesName("compact_swivel_flap", DEFAULT));
        assertTrue(SubLevelFarEntities.matchesName("analog_compact_flap_bearing", DEFAULT));
    }

    /** Everything else on a ship is left to Sable: this pass is deliberately narrow. */
    @Test void unrelatedBlockEntitiesDoNotMatch() {
        assertFalse(SubLevelFarEntities.matchesName("gearbox", DEFAULT));
        assertFalse(SubLevelFarEntities.matchesName("quickfiring_breech", DEFAULT));
        assertFalse(SubLevelFarEntities.matchesName("fluid_tank", DEFAULT));
        assertFalse(SubLevelFarEntities.matchesName("sable_display_link", DEFAULT));
    }

    /** The filter is literal: only the fragments asked for are swept in, nothing near-miss. */
    @Test void theFilterIsLiteral() {
        assertFalse(SubLevelFarEntities.matchesName("breech", DEFAULT));
        assertTrue(SubLevelFarEntities.matchesName("breech_cannon", DEFAULT));
    }

    /** An empty list is the documented way to switch the pass off without touching the switch. */
    @Test void anEmptyListMatchesNothing() {
        assertFalse(SubLevelFarEntities.matchesName("cannon_mount", List.of()));
    }

    /** A blank entry would otherwise match every block entity there is. */
    @Test void blankEntriesAreIgnored() {
        assertFalse(SubLevelFarEntities.matchesName("cannon_mount", List.of("", " ")));
        assertTrue(SubLevelFarEntities.matchesName("cannon_mount", List.of("", "cannon")));
    }
}
