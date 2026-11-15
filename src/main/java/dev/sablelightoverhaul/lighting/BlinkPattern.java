package dev.sablelightoverhaul.lighting;

/**
 * A lamp's blink pattern: comma separated on/off durations in ticks, alternating, starting with on.
 *
 * <p>{@code 4,4,4,28} is on four, off four, on four, off twenty-eight -- two seconds end to end, which is the
 * sort of cadence a signal light wants. The client runs the pattern; nothing here schedules anything.
 */
public final class BlinkPattern {
    public static final int MAX_SEGMENTS = 16;
    public static final int MAX_TICKS = 1200;

    private final int[] ticks;

    private BlinkPattern(int[] ticks) { this.ticks = ticks; }

    public int[] ticks() { return ticks.clone(); }

    public int total() {
        int sum = 0;
        for (int t : ticks) sum += t;
        return sum;
    }

    /** Whether the lamp is on at a moment in the cycle, counted from tick zero. */
    public boolean litAt(long phase) {
        if (ticks.length == 0) return true;
        int total = total();
        if (total <= 0) return true;
        int t = (int) Math.floorMod(phase, (long) total);
        int at = 0;
        for (int i = 0; i < ticks.length; i++) {
            at += ticks[i];
            if (t < at) return (i & 1) == 0;
        }
        return true;
    }

    /**
     * Validates and normalises the text the page sent.
     *
     * @returns the trimmed pattern, or an empty string for a steady lamp
     * @throws IllegalArgumentException with a sentence a player can act on
     */
    public static String parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return "";
        String[] parts = trimmed.split(",", -1);
        if (parts.length > MAX_SEGMENTS) throw dev.sablelightoverhaul.LocalizedText.error("最多 %s 段", MAX_SEGMENTS);
        StringBuilder out = new StringBuilder();
        int sum = 0;
        for (String part : parts) {
            String piece = part.trim();
            if (piece.isEmpty()) throw dev.sablelightoverhaul.LocalizedText.error("有空的一段，格式如 4,4,4,28");
            int value;
            try {
                value = Integer.parseInt(piece);
            } catch (NumberFormatException bad) {
                throw dev.sablelightoverhaul.LocalizedText.error("「%s」不是整数，格式如 4,4,4,28", piece);
            }
            if (value < 1 || value > MAX_TICKS) throw dev.sablelightoverhaul.LocalizedText.error("每段要在 1~%s tick 之间", MAX_TICKS);
            sum += value;
            if (out.length() > 0) out.append(',');
            out.append(value);
        }
        if (sum <= 0) throw dev.sablelightoverhaul.LocalizedText.error("总时长必须大于 0");
        return out.toString();
    }

    /** A parsed pattern, or a steady one when the text is empty or malformed. */
    public static BlinkPattern of(String text) {
        try {
            String parsed = parse(text);
            if (parsed.isEmpty()) return new BlinkPattern(new int[0]);
            String[] parts = parsed.split(",");
            int[] ticks = new int[parts.length];
            for (int i = 0; i < parts.length; i++) ticks[i] = Integer.parseInt(parts[i]);
            return new BlinkPattern(ticks);
        } catch (IllegalArgumentException bad) {
            return new BlinkPattern(new int[0]);
        }
    }
}