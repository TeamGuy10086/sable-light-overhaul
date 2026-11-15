package dev.sablelightoverhaul.client.ui;

/**
 * A value on its way through the screen, with no opinion about where it came from.
 *
 * <p><b>The NO_CHANGE rule.</b> A control does not "apply" anything: it converts a click into a candidate value and
 * {@link #changed} answers {@code null} when that value equals the one already shown. A {@code null} return is the
 * screen's licence to do nothing at all -- no write, no file save, no packet. Without it, dragging a slider across
 * its own current position would rewrite the config dozens of times a second.
 */
public sealed interface FieldValue {
    record Bool(boolean on) implements FieldValue {}

    record Num(double value) implements FieldValue {}

    record Text(String text) implements FieldValue {}

    /** {@code index} is authoritative; {@code name} is what the option list said, for an enum round-trip. */
    record Choice(int index, String name) implements FieldValue {}

    /** {@code null} means "unchanged", which is not the same thing as "same value written again". */
    static FieldValue changed(FieldValue before, FieldValue after) {
        if (after == null) return null;
        return after.equals(before) ? null : after;
    }

    /** The text a control shows. Formatting lives here because it depends only on the spec and the value. */
    static String display(FieldSpec spec, FieldValue value) {
        if (value == null) return "";
        return switch (spec.kind()) {
            case TOGGLE -> value instanceof Bool b && b.on() ? "开" : "关";
            case COLOR -> String.format("%06X", (int) num(value) & 0xFFFFFF);
            case CYCLER -> value instanceof Choice c ? c.name() : "";
            case SLIDER -> spec.step() >= 1.0 ? String.valueOf((int) Math.rint(num(value)))
                    : String.format("%.2f", num(value));
            case TEXT -> value instanceof Text t ? t.text() : "";
        };
    }

    static double num(FieldValue value) {
        return value instanceof Num n ? n.value() : 0.0;
    }

    /**
     * The number a stored key should receive.
     *
     * <p>A picker hands back a {@link Choice}, and its <em>name</em> is the value the row showed ("0".."2"
     * for a whole-number cycler). Reading the index instead is what made a picker write 0 whatever was
     * picked, and treating a choice as a plain number (which is 0) is the same bug one layer down. The
     * index is only the fallback for a named option that is not a number; such a key has to be an enum and
     * is written by its own writer, never here.
     */
    static double numeric(FieldValue value) {
        if (value instanceof Choice choice) {
            try {
                return Double.parseDouble(choice.name());
            } catch (NumberFormatException notANumber) {
                return choice.index();
            }
        }
        return num(value);
    }

    static boolean flag(FieldValue value) {
        return value instanceof Bool b && b.on();
    }
}
