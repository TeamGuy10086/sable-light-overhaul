package dev.sablelightoverhaul.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The values that travel between a control and a {@link Source}.
 *
 * <p>These exist because of one field bug: a picker over whole numbers handed back a {@code Choice}, the
 * toml writer read it as a plain number (zero), and every pick saved 0 -- so "occlusion 2 to 1" snapped to
 * 0 and could never be set back. The conversion is one line and it is the whole difference between a
 * working picker and a dead one, so it is pinned here.
 */
class FieldValueTest {
    @Test
    void aWholeNumberChoiceCarriesTheNumberItsRowShows() {
        assertEquals(1.0, FieldValue.numeric(new FieldValue.Choice(1, "1")));
        assertEquals(2.0, FieldValue.numeric(new FieldValue.Choice(2, "2")));
        assertEquals(0.0, FieldValue.numeric(new FieldValue.Choice(0, "0")));
        // Not the index: row 2 may well show the number 7.
        assertEquals(7.0, FieldValue.numeric(new FieldValue.Choice(2, "7")));
    }

    @Test
    void aNamedChoiceFallsBackToItsIndex() {
        assertEquals(2.0, FieldValue.numeric(new FieldValue.Choice(2, "High")));
    }

    @Test
    void aPlainNumberIsItself() {
        assertEquals(0.6, FieldValue.numeric(new FieldValue.Num(0.6)));
        assertEquals(0.0, FieldValue.num(new FieldValue.Text("nope")));
    }

    @Test
    void aToggleReadsAsABoolean() {
        assertTrue(FieldValue.flag(new FieldValue.Bool(true)));
        assertEquals("开", FieldValue.display(
                new FieldSpec("k", "g", "L", "", FieldSpec.Kind.TOGGLE, 0, 1, 1, java.util.List.of(), false, ""),
                new FieldValue.Bool(true)));
    }

    /** Dragging a slider across its own value must not produce a write. */
    @Test
    void equalsMeansNoChange() {
        FieldValue before = new FieldValue.Num(0.4);
        assertNull(FieldValue.changed(before, new FieldValue.Num(0.4)));
        assertEquals(new FieldValue.Num(0.5), FieldValue.changed(before, new FieldValue.Num(0.5)));
        assertNull(FieldValue.changed(before, null));
    }
}
