package dev.sablelightoverhaul.client.ui;

import java.util.List;

/**
 * What a row looks like, and nothing else.
 *
 * <p><b>No data, no storage.</b> A spec says "this is a bounded number between 0.25 and 2, labelled 体积光亮度, and
 * changing it needs a restart". It does not know whether the value lives in a toml file, in a block entity on the
 * server, or in a test harness. That is what lets one screen drive both this mod's config files and its block pages.
 *
 * <p><b>Immutable.</b> The catalog is built once and reused; the value travels separately as a {@link FieldValue}.
 */
public record FieldSpec(String key, String group, String label, String tooltip, Kind kind,
                        double min, double max, double step, List<String> options, boolean needsRestart,
                        String labelKey) {
    /** The shape of the value, which is also the widget that edits it. */
    public enum Kind { TOGGLE, SLIDER, CYCLER, TEXT, COLOR }

    public FieldSpec {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    /** A slider snaps to whole steps when the value is an integer, and to the spec's step otherwise. */
    public double snap(double v) {
        double c = clamp(v);
        if (step <= 0.0) return c;
        return step >= 1.0 ? Math.rint(c) : Math.rint(c / step) * step;
    }

    public double fraction(double v) {
        double span = max - min;
        // The result is a 0..1 position, not a value: clamping it to [min, max] made every range whose
        // minimum was above zero sit at the far right (1.0) regardless of the value.
        return span <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, (v - min) / span));
    }

    public double fromFraction(double f) {
        return snap(min + (max - min) * Math.max(0.0, Math.min(1.0, f)));
    }
}
