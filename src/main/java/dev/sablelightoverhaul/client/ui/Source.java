package dev.sablelightoverhaul.client.ui;

import java.util.List;
import java.util.Map;

/**
 * The seam between the screen and whatever actually holds the values.
 *
 * <p><b>Why an interface and not a ConfigValue.</b> The screen has to serve two very different owners: a toml file
 * that lives on this client and is written directly, and a block on a server whose value only changes when a packet
 * comes back. If the screen held a {@code ConfigValue}, the second owner could not exist. So the screen never reads
 * or writes anything itself: it is handed a snapshot, it renders it, and every edit goes back through this
 * interface. A binding decides what a write means -- a file save, a payload, or nothing at all in a test.
 *
 * <p><b>Reads are snapshots.</b> {@link #read()} is called when the screen opens and after a reset, never per frame:
 * a binding is allowed to be expensive (a network round trip, a server query) as long as it is rare.
 */
public interface Source {
    default String title() { return ""; }
    default boolean serverNavigation() { return true; }
    record Action(String label, Runnable run) {}
    default List<Action> actions() { return List.of(); }
    default void selectCategory(String category) {}
    default boolean canReset(FieldSpec spec) { return false; }
    /** The rows to show, in the order they should appear. */
    List<Group> groups();

    /** Current values, keyed by {@link FieldSpec#key()}. A key may be absent; the row then shows its default. */
    Map<String, FieldValue> read();

    /** Persist one value. Implementations validate and refuse rather than trusting the screen. */
    void write(FieldSpec spec, FieldValue value);

    /** Put one value back to its defined default. */
    default void reset(FieldSpec spec) {
        FieldValue def = read().get(spec.key());
        if (def != null) write(spec, def);
    }

    /** Called once after a batch of edits, for a binding that writes to disk lazily. */
    default void flush() {}

    /** Whether this binding will accept a write for the row. Read-only sources override this. */
    default boolean canWrite(FieldSpec spec) {
        return true;
    }

    /** Whether the row can have any effect in the current runtime; the screen hides the rest. */
    default boolean isRelevant(FieldSpec spec) {
        return true;
    }

    /** One titled block of rows, placed in the taxonomy's three levels. */
    record Group(String category, String section, String topic, List<FieldSpec> fields) {}

    /** One page of the navigation: a category and the groups that belong to it. */
    record Page(String category, List<Group> groups) {}
}
