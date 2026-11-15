package dev.sablelightoverhaul.profiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A page's values as one string, to and from the clipboard.
 *
 * <p>Shared by cannon profiles and by the generators, because both are nothing but a named set of values and
 * neither should grow its own format.
 *
 * <p>The values are text, and that is the primitive here rather than a convenience. A cannon's form is numbers
 * and a generator's form is numbers plus at least one particle specification, which is text -- so a format
 * that could only carry numbers would have needed a second format beside it, and two formats is how two
 * readers end up disagreeing. Every value is text; the numeric view below is a reading of it, not a different
 * file.
 *
 * <p>The header carries two versions and both are load bearing. The first is the format's own, so a reader can
 * refuse a string written by a newer layout instead of guessing at it. The second is the mod's, because the
 * same page gains and loses entries between releases: importing a string from 0.1.150 into 0.1.193 means
 * entries that exist now are absent from it, and a reader that knows which build wrote the string can say so
 * instead of silently leaving them at whatever the form happens to hold.
 *
 * <pre>
 * SLO-PROFILE v1 kind=cannon mod=0.1.193
 * [createbigcannons:pitch_contraption]
 * shape.forwardRange=32.0
 * time.durationTicks=5.0
 * </pre>
 *
 * <p>Reading never throws on content: a line that is not understood is reported in the export's
 * {@code unknownLines} rather than dropped in silence or allowed to abort the import.
 */
public final class ProfileText {
    public static final int FORMAT = 1;
    private static final String MAGIC = "SLO-PROFILE";

    /**
     * One page's worth of values, as they appear in the string.
     *
     * @param kind       what this belongs to, e.g. {@code cannon} or {@code generator}
     * @param modVersion the build that wrote it, so a reader knows what to expect of the entries
     * @param byType     type id to values; for a generator this is the generator kind
     * @param unknownLines lines the reader did not understand, reported rather than ignored
     */
    public record TextExport(String kind, String modVersion, Map<String, Map<String, String>> byType,
                             List<String> unknownLines) {
        public TextExport(String kind, String modVersion, Map<String, Map<String, String>> byType) {
            this(kind, modVersion, byType, List.of());
        }

        /** Every entry name in the export, for a caller that wants to compare against what it knows. */
        public Set<String> names() {
            var all = new TreeSet<String>();
            byType.values().forEach(values -> all.addAll(values.keySet()));
            return java.util.Collections.unmodifiableSet(all);
        }
    }

    /**
     * One cannon form's worth of values, as numbers.
     *
     * <p>A reading of {@link TextExport} rather than a separate format: a value that is not a number cannot be
     * used by a cannon and is reported instead of being turned into something arbitrary.
     */
    public record Export(String kind, String modVersion,
                         Map<String, Map<String, Double>> byType, List<String> unknownLines) {
        public Export(String kind, String modVersion, Map<String, Map<String, Double>> byType) {
            this(kind, modVersion, byType, List.of());
        }

        /** Every entry name in the export, for a caller that wants to compare against what it knows. */
        public Set<String> names() {
            var all = new TreeSet<String>();
            byType.values().forEach(values -> all.addAll(values.keySet()));
            return java.util.Collections.unmodifiableSet(all);
        }
    }

    private ProfileText() {}

    /** The string to put on the clipboard. Values are written sorted so two exports of the same page match. */
    public static String writeText(TextExport export) {
        var out = new StringBuilder();
        out.append(MAGIC).append(" v").append(FORMAT)
           .append(" kind=").append(export.kind())
           .append(" mod=").append(export.modVersion()).append('\n');
        var types = new TreeMap<>(export.byType());
        types.forEach((type, values) -> {
            out.append('[').append(type).append("]\n");
            new TreeMap<>(values).forEach((name, value) ->
                    out.append(name).append('=').append(value == null ? "" : value).append('\n'));
        });
        return out.toString();
    }

    /** The cannon form's string, which is the text one with numbers in it. */
    public static String write(Export export) {
        var byType = new LinkedHashMap<String, Map<String, String>>();
        export.byType().forEach((type, values) -> {
            var text = new LinkedHashMap<String, String>();
            values.forEach((name, value) -> text.put(name, String.valueOf(value)));
            byType.put(type, text);
        });
        return writeText(new TextExport(export.kind(), export.modVersion(), byType, export.unknownLines()));
    }

    /**
     * Reads a string back, keeping every value as the text it was written as.
     *
     * <p>Returns empty when it is not one of ours at all, or when it was written by a newer format; both are
     * cases for a page to report, not exceptions to throw at a player who pasted the wrong thing.
     */
    public static Optional<TextExport> readText(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        var lines = text.replace("\r\n", "\n").split("\n");
        if (lines.length == 0) return Optional.empty();
        var header = lines[0].trim().split("\\s+");
        if (header.length < 2 || !MAGIC.equals(header[0])) return Optional.empty();
        int version;
        try {
            version = Integer.parseInt(header[1].startsWith("v") ? header[1].substring(1) : header[1]);
        } catch (NumberFormatException notOurs) {
            return Optional.empty();
        }
        if (version > FORMAT) return Optional.empty();
        String kind = "unknown";
        String mod = "unknown";
        for (String field : header) {
            int eq = field.indexOf('=');
            if (eq < 0) continue;
            String key = field.substring(0, eq);
            String value = field.substring(eq + 1);
            if ("kind".equals(key)) kind = value;
            if ("mod".equals(key)) mod = value;
        }
        var byType = new LinkedHashMap<String, Map<String, String>>();
        var unknown = new java.util.ArrayList<String>();
        String current = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length() - 1).trim();
                byType.computeIfAbsent(current, key -> new LinkedHashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (current == null || eq <= 0) {
                unknown.add(line);
                continue;
            }
            byType.get(current).put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return Optional.of(new TextExport(kind, mod, byType, List.copyOf(unknown)));
    }

    /** The cannon form's reading of a string: the text one, with each value required to be a number. */
    public static Optional<Export> read(String text) {
        var parsed = readText(text);
        if (parsed.isEmpty()) return Optional.empty();
        var source = parsed.get();
        var byType = new LinkedHashMap<String, Map<String, Double>>();
        var unknown = new java.util.ArrayList<>(source.unknownLines());
        source.byType().forEach((type, values) -> {
            var numbers = new LinkedHashMap<String, Double>();
            values.forEach((name, value) -> {
                try {
                    numbers.put(name, Double.parseDouble(value));
                } catch (NumberFormatException notANumber) {
                    // A cannon cannot use this value, and inventing one would be worse than saying so.
                    unknown.add(name + "=" + value);
                }
            });
            byType.put(type, numbers);
        });
        return Optional.of(new Export(source.kind(), source.modVersion(), byType, List.copyOf(unknown)));
    }
}
