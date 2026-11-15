package dev.sablelightoverhaul.profiles;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export string is the one thing a player can hand to another player, so what it must survive is being
 * pasted back, being pasted into the wrong page, and coming from an older build that had fewer entries.
 */
class ProfileTextTest {
    private static final String CANNON = "createbigcannons:pitch_contraption";

    private static ProfileText.Export sample() {
        return new ProfileText.Export("cannon", "0.1.168",
                Map.of(CANNON, Map.of("shape.forwardRange", 32.0, "time.durationTicks", 5.0)));
    }

    @Test void whatIsWrittenCanBeReadBack() {
        var back = ProfileText.read(ProfileText.write(sample())).orElseThrow();
        assertEquals("cannon", back.kind());
        assertEquals("0.1.168", back.modVersion(), "the build that wrote it is part of the string");
        assertEquals(32.0, back.byType().get(CANNON).get("shape.forwardRange"));
        assertEquals(5.0, back.byType().get(CANNON).get("time.durationTicks"));
        assertTrue(back.unknownLines().isEmpty());
    }

    @Test void theModVersionIsThereToSayWhichEntriesToExpect() {
        // An export from a build that did not have a key yet: a reader can see that from the version rather
        // than guessing why the entry is missing.
        var older = ProfileText.read("SLO-PROFILE v1 kind=cannon mod=0.1.150\n[" + CANNON + "]\nshape.forwardRange=32.0\n")
                .orElseThrow();
        assertEquals("0.1.150", older.modVersion());
        assertFalse(older.names().contains("time.durationTicks"));
    }

    @Test void somethingThatIsNotOursIsRefusedAndNotThrown() {
        assertTrue(ProfileText.read("hello world").isEmpty());
        assertTrue(ProfileText.read("").isEmpty());
        assertTrue(ProfileText.read(null).isEmpty());
        assertTrue(ProfileText.read("SLO-PROFILE vX kind=cannon").isEmpty());
    }

    @Test void aNewerFormatIsRefusedRatherThanGuessedAt() {
        assertTrue(ProfileText.read("SLO-PROFILE v99 kind=cannon mod=9.9.9\n[" + CANNON + "]\nshape.forwardRange=1\n").isEmpty());
    }

    @Test void aLineThatDoesNotParseIsReportedNotDropped() {
        var read = ProfileText.read("SLO-PROFILE v1 kind=cannon mod=0.1.168\n[" + CANNON + "]\n"
                + "shape.forwardRange=32.0\nthis is not a value\nshape.rearRange=abc\n").orElseThrow();
        assertEquals(2, read.unknownLines().size(), "both the malformed line and the unparsable number");
        assertEquals(32.0, read.byType().get(CANNON).get("shape.forwardRange"), "a good line still lands");
    }

    @Test void twoExportsOfTheSamePageAreIdentical() {
        var shuffled = new ProfileText.Export("cannon", "0.1.168",
                Map.of(CANNON, Map.of("time.durationTicks", 5.0, "shape.forwardRange", 32.0)));
        assertEquals(ProfileText.write(sample()), ProfileText.write(shuffled),
                "order of writing must not depend on map iteration order");
    }

    @Test void everyNameCanBeListedForComparingAgainstWhatThisBuildKnows() {
        assertEquals(List.of("shape.forwardRange", "time.durationTicks"),
                List.copyOf(new java.util.TreeSet<>(sample().names())));
    }
}
