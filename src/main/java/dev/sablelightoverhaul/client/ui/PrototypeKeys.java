package dev.sablelightoverhaul.client.ui;

import java.util.Set;

/**
 * The headless tests' row set: one key of every shape, all of them real.
 *
 * <p>Reorganising two hundred keys into pages is a design decision, not a code one, and doing it before the widget
 * layer has been used once would mean doing it twice. Clearing this set shows every key; until then the screen is
 * limited to rows that prove each control works against a real value.
 *
 * <p>volume / glow / width / shadowRange are bounded numbers (sliders), debug / mode are choices
 * (cyclers: debug is an int 0..1, not a boolean, which is why it is not the toggle), colors is a string (text),
 * frameTicks is an integer with a wide range, shadows.longRange is the one real boolean (toggle), and
 * apSparks.color is the 24-bit colour (hex box). One row of every kind, so a control that cannot work is
 * visible on the first open instead of on the hundredth key.
 */
public final class PrototypeKeys {
    public static final Set<String> ONLY = Set.of(
            "beam.volume", "beam.glow", "beam.shadowRange", "beam.shadowBudget", "beam.width",
            "beam.debug", "lighting.mode", "disco.colors",
            "particles.groundDust.frameTicks", "shadows.longRange", "apSparks.color");

    private PrototypeKeys() {}
}