package dev.sablelightoverhaul.lighting;

import java.util.ArrayList;
import java.util.List;

/**
 * The shadow cones of one lamp: the directions it is allowed to cast in.
 *
 * <p>Written as text because that is the only shape that can say "several rectangles of angles" without
 * inventing a UI for it: {@code "{h1,h2,v1,v2};{h1,h2,v1,v2}"}. The braces are optional, whitespace is
 * ignored, and the groups are separated by semicolons.
 *
 * <p>A group is horizontal start, horizontal end, vertical start, vertical end, in degrees. Horizontal
 * runs 0..360 and vertical -90..+90, both growing the way a compass and a horizon do. A group must not be
 * empty ({@code h1 < h2} and {@code v1 < v2}) and the horizontal end may not run past 360: the shader
 * tests the range in one direction only, so a wrapping range is refused here rather than silently
 * meaning something else.
 *
 * <p>Parsing is deliberately loud. A rejected string keeps the lamp exactly as it was, and the reason
 * travels back to the player, because "my shadows are wrong" is a much worse outcome than "group 2 has
 * its end before its start".
 *
 * <p>The result is packed, four bytes to a word, low byte first: horizontal start, horizontal end,
 * vertical start, vertical end. Horizontal uses 0..255 for 0..360 and vertical uses 0..255 for -90..+90,
 * so both land on about 1.4 degrees. A word of zero means "no cone", which is why the count is simply the
 * number of leading non-zero words and the wire format needs no separate count.
 */
public final class Cones {
    /** The most cones one lamp may have: the shader tests four and the data is four words. */
    public static final int MAX = 4;
    /** The packed form of "no cones at all": every direction casts, as it always did. */
    public static final int[] NONE = new int[MAX];

    private Cones() {}

    /** One group's angles, or null when the group is not four integers. */
    private record Angles(int h1, int h2, int v1, int v2) {}

    /**
     * Parse the text into up to {@link #MAX} packed words, zero-padded.
     *
     * @param text the page's own text; null or blank means no cones.
     * @throws IllegalArgumentException with a sentence naming the group and the problem.
     */
    public static int[] parse(String text) {
        if (text == null || text.isBlank()) return NONE.clone();
        List<Angles> groups = new ArrayList<>();
        int index = 0;
        for (String raw : text.split(";", -1)) {
            index++;
            String group = raw.trim();
            if (group.isEmpty()) continue;
            if (group.startsWith("{")) group = group.substring(1);
            if (group.endsWith("}")) group = group.substring(0, group.length() - 1);
            String[] parts = group.split(",", -1);
            if (parts.length != 4) {
                throw dev.sablelightoverhaul.LocalizedText.error("第 %s 组需要 4 个角度（水平起,水平终,垂直起,垂直终），实际 %s 个", index, parts.length);
            }
            int[] value = new int[4];
            for (int i = 0; i < 4; i++) {
                try {
                    value[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException notANumber) {
                    throw dev.sablelightoverhaul.LocalizedText.error("第 %s 组的第 %s 项不是整数：%s", index, i + 1, parts[i].trim());
                }
            }
            Angles angles = new Angles(value[0], value[1], value[2], value[3]);
            if (angles.h1() < 0 || angles.h2() > 360) {
                throw dev.sablelightoverhaul.LocalizedText.error("第 %s 组的水平角必须在 0~360 之间（且不跨 360°）：%s~%s", index, angles.h1(), angles.h2());
            }
            if (angles.h1() >= angles.h2()) {
                throw dev.sablelightoverhaul.LocalizedText.error("第 %s 组的水平终必须大于水平起：%s~%s", index, angles.h1(), angles.h2());
            }
            if (angles.v1() < -90 || angles.v2() > 90) {
                throw dev.sablelightoverhaul.LocalizedText.error("第 %s 组的垂直角必须在 -90~90 之间：%s~%s", index, angles.v1(), angles.v2());
            }
            if (angles.v1() >= angles.v2()) {
                throw dev.sablelightoverhaul.LocalizedText.error("第 %s 组的垂直终必须大于垂直起：%s~%s", index, angles.v1(), angles.v2());
            }
            groups.add(angles);
        }
        if (groups.size() > MAX) {
            throw dev.sablelightoverhaul.LocalizedText.error("最多 %s 个锥，实际 %s 个", MAX, groups.size());
        }
        int[] packed = new int[MAX];
        for (int i = 0; i < groups.size(); i++) {
            Angles angles = groups.get(i);
            packed[i] = pack(angles.h1(), angles.h2(), angles.v1(), angles.v2());
        }
        return packed;
    }

    /**
     * One cone as one word.
     *
     * <p>The two axes quantise differently (0..360 against -90..+90) because each is scaled to fill a
     * byte: 1.4 degrees either way, which is a region to cover rather than a boundary to hit.
     */
    public static int pack(int h1, int h2, int v1, int v2) {
        int a = scale(h1, 0, 360);
        int b = scale(h2, 0, 360);
        int c = scale(v1 + 90, 0, 180);
        int d = scale(v2 + 90, 0, 180);
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    private static int scale(int value, int from, int span) {
        int scaled = (int) Math.round((value - from) * 255.0 / span);
        return Math.max(0, Math.min(255, scaled));
    }

    /** How many words of a packed list are in use: the words are compacted, so it is the leading ones. */
    public static int count(int[] packed) {
        if (packed == null) return 0;
        int count = 0;
        while (count < packed.length && count < MAX && packed[count] != 0) count++;
        return count;
    }

    /** The packed word for a cone whose angles were just parsed, for tests and for the report. */
    public static int h1(int word) { return (word & 255) * 360 / 255; }
    public static int h2(int word) { return ((word >> 8) & 255) * 360 / 255; }
    public static int v1(int word) { return ((word >> 16) & 255) * 180 / 255 - 90; }
    public static int v2(int word) { return ((word >> 24) & 255) * 180 / 255 - 90; }

    /** The packed list as text, for the chat fallback and for a page that has to show a parsed value. */
    public static String describe(int[] packed) {
        int count = count(packed);
        if (count == 0) return "全部方向";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (text.length() > 0) text.append('；');
            text.append('{').append(h1(packed[i])).append(',').append(h2(packed[i])).append(',')
                .append(v1(packed[i])).append(',').append(v2(packed[i])).append('}');
        }
        return text.toString();
    }
}
