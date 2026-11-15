package dev.sablelightoverhaul.lighting;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The name filter for the far entity pass. Tracking itself is a server decision and cannot be exercised
 * without a game, so this pins down the one piece that is pure: which registry paths are swept in.
 */
class FarEntitiesTest {
    private static final List<String> DEFAULT = List.of("cannon", "flap", "pitch_contraption", "munitions_contraption");

    /** CBC names the assembled cannon neither after a cannon nor after a flap: this is the whole reason
     *  the default list carries "pitch_contraption", and it is the entity measured in game. */
    @Test void theAssembledCannonMatchesThroughItsOwnName() {
        assertTrue(FarEntities.matchesName("pitch_contraption", DEFAULT));
    }

    /** CBC Modern Warfare's only entity of its own: also carries neither fragment. */
    @Test void modernWarfareMunitionsContraptionMatches() {
        assertTrue(FarEntities.matchesName("munitions_contraption", DEFAULT));
    }

    /** CBC's carriage is matched by fragment. */
    @Test void carriagesMatch() {
        assertTrue(FarEntities.matchesName("cannon_carriage", DEFAULT));
    }

    /** CompactFlap is matched through the path rather than the namespace "compactflap". */
    @Test void flapsMatch() {
        assertTrue(FarEntities.matchesName("compact_flap_bearing", DEFAULT));
        assertTrue(FarEntities.matchesName("analog_compact_flap_bearing", DEFAULT));
    }

    /** Everything else keeps the vanilla view distance: this pass is deliberately narrow. */
    @Test void ordinaryEntitiesDoNotMatch() {
        assertFalse(FarEntities.matchesName("player", DEFAULT));
        assertFalse(FarEntities.matchesName("cow", DEFAULT));
        assertFalse(FarEntities.matchesName("tnt", DEFAULT));
        assertFalse(FarEntities.matchesName("contraption", DEFAULT));
        assertFalse(FarEntities.matchesName("item", DEFAULT));
    }

    /** Only the path is consulted, so a namespace fragment cannot drag a whole mod's entities in. */
    @Test void theNamespaceIsNotConsulted() {
        assertFalse(FarEntities.matchesName("pitch_contraption", List.of("createbigcannons")));
        assertFalse(FarEntities.matchesName("gearbox", List.of("createbigcannons")));
    }

    /** The filter is literal: only the fragments asked for are swept in, nothing near-miss. */
    @Test void theFilterIsLiteral() {
        assertFalse(FarEntities.matchesName("breech", DEFAULT));
        assertTrue(FarEntities.matchesName("breech_cannon", DEFAULT));
    }

    /** An empty list is the documented way to switch the pass off without touching the switch. */
    @Test void anEmptyListMatchesNothing() {
        assertFalse(FarEntities.matchesName("pitch_contraption", List.of()));
    }

    /** A blank entry would otherwise match every entity there is. */
    @Test void blankEntriesAreIgnored() {
        assertFalse(FarEntities.matchesName("pitch_contraption", List.of("", " ")));
        assertTrue(FarEntities.matchesName("pitch_contraption", List.of("", "contraption")));
    }
}
