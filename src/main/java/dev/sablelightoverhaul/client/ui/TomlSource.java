package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.client.HostileSmokeConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The toml binding: reads a {@link ModConfigSpec} into rows, writes edits back and saves the file.
 *
 * <p><b>No table.</b> Everything a row needs is already in the spec -- {@code getPath()} for the group and the key,
 * {@code getSpec().getComment()} for the label and the tooltip, {@code getRange()} for the bounds,
 * {@code getDefault()} for the reset value and {@code restartType()} for the marker. So this class contains no list
 * of keys, and a key that is added, renamed or re-ranged cannot leave the screen behind.
 *
 * <p><b>All the file access is here.</b> The screen does not know what a {@code ConfigValue} is; it asks this
 * binding for values and hands values back. Swapping this class for one backed by a server page is the whole of
 * what it takes to show a block's settings in the same screen.
 */
public final class TomlSource implements Source {
    private final ModConfigSpec spec;
    /** Empty means every key; otherwise only these dotted paths, which is the prototype's test space. */
    private final Set<String> only;
    private final boolean hostileInstalled;
    private final Map<String, ModConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
    private List<Group> groups;
    /** Writes are batched: a slider drag would otherwise rewrite the file on every pixel. */
    private boolean dirty;

    public TomlSource(ModConfigSpec spec, Set<String> only) {
        this(spec, only, net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded("sablehostile"));
    }

    TomlSource(ModConfigSpec spec, Set<String> only, boolean hostileInstalled) {
        this.spec = spec;
        this.hostileInstalled = hostileInstalled;
        this.only = only == null ? Set.of() : Set.copyOf(only);
    }

    @Override
    public List<Group> groups() {
        if (groups == null) build();
        return groups;
    }

    @Override
    public Map<String, FieldValue> read() {
        if (groups == null) build();
        Map<String, FieldValue> out = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> e : values.entrySet()) {
            FieldSpec field = byKey.get(e.getKey());
            if (field == null) continue;
            out.put(e.getKey(), snapshot(field, e.getValue()));
        }
        return out;
    }

    @Override
    public void write(FieldSpec field, FieldValue value) {
        ModConfigSpec.ConfigValue<?> target = values.get(field.key());
        if (target == null) return;
        try {
            store(target, value, field);
            dirty = true;
        } catch (RuntimeException | LinkageError ex) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not write config key {} = {}", field.key(), value, ex);
        }
    }

    @Override
    public void reset(FieldSpec field) {
        ModConfigSpec.ConfigValue<?> target = values.get(field.key());
        if (target == null) return;
        try {
            store(target, snapshot(field, target, true), field);
            dirty = true;
        } catch (RuntimeException | LinkageError ex) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not reset config key {}", field.key(), ex);
        }
    }

    @Override
    public void flush() {
        if (!dirty) return;
        dirty = false;
        save();
    }

    private void save() {
        try {
            spec.save();
        } catch (RuntimeException | LinkageError ignored) {
            // A config that is not loaded yet keeps the value in memory; the next save carries it.
        }
    }

    // ------------------------------------------------------------------ building

    private final Map<String, FieldSpec> byKey = new LinkedHashMap<>();

    private void build() {
        List<Row> rows = new ArrayList<>();
        collect(spec, spec.getValues(), new ArrayList<>(), rows);
        rows.sort(java.util.Comparator
                .comparingInt((Row row) -> ConfigTaxonomy.rank(row.part().category()))
                .thenComparingInt(row -> ConfigTaxonomy.sectionRank(row.part().category(), row.part().section())));
        Map<String, List<FieldSpec>> grouped = new LinkedHashMap<>();
        Map<String, ConfigTaxonomy.Part> parts = new LinkedHashMap<>();
        for (Row row : rows) {
            String key = row.part().category() + "\u0000" + row.part().section() + "\u0000" + row.part().topic();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row.field());
            parts.put(key, row.part());
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<FieldSpec>> e : grouped.entrySet()) {
            ConfigTaxonomy.Part part = parts.get(e.getKey());
            out.add(new Group(part.category(), part.section(), part.topic(), List.copyOf(e.getValue())));
        }
        groups = List.copyOf(out);
    }

    private record Row(ConfigTaxonomy.Part part, FieldSpec field) {}

    private void collect(ModConfigSpec spec, com.electronwill.nightconfig.core.UnmodifiableConfig config,
                         List<String> path, List<Row> rows) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            Object v = entry.getValue();
            path.add(entry.getKey());
            try {
                if (v instanceof com.electronwill.nightconfig.core.Config nested) {
                    collect(spec, nested, path, rows);
                } else if (v instanceof ModConfigSpec.ConfigValue<?> value) {
                    String dotted = String.join(".", path);
                    if (!hostileInstalled && (dotted.startsWith(HostileSmokeConfig.PREFIX) || dotted.startsWith("navalAudio."))) continue;
                    // The old constrained hull path is still registered for config compatibility, but it no longer
                    // computes anything: its keys get no row and no value entry, so the screen never offers them.
                    if (dev.sablelightoverhaul.RetiredHullSettings.contains(dotted) || dotted.equals("spotlights.beam.occlusion") || dotted.equals("particles.muzzle.autocannon.tracerTrailStart") || dotted.equals("particles.muzzle.tankCannon.tracerTrailStart")) continue;
                    // Matched by tail, not by the whole path: this mod's keys are grouped with push(), so the
                    // path that matters for a name (beam.volume) sits under a section that has nothing to do with
                    // it (shockwave.beam.volume). The test space names what the row IS, not where it happens to live.
                    if (!only.isEmpty() && !wanted(dotted)) continue;
                    ConfigTaxonomy.Part part = ConfigTaxonomy.of(dotted);
                    FieldSpec field = describe(part.section(), dotted, value);
                    rows.add(new Row(part, field));
                    byKey.put(dotted, field);
                    values.put(dotted, value);
                }
            } finally {
                path.remove(path.size() - 1);
            }
        }
    }

    /** One field by dotted key, for callers that apply an edit without walking the groups. */
    public FieldSpec spec(String key) {
        if (groups == null) build();
        return byKey.get(key);
    }

    private boolean wanted(String dotted) {
        for (String tail : only) {
            if (dotted.equals(tail) || dotted.endsWith("." + tail)) return true;
        }
        return false;
    }
    private FieldSpec describe(String group, String key, ModConfigSpec.ConfigValue<?> value) {
        String comment = "";
        double min = 0.0;
        double max = 1.0;
        double step = 0.01;
        boolean restart = false;
        try {
            var valueSpec = value.getSpec();
            if (valueSpec != null) {
                if (valueSpec.getComment() != null) comment = valueSpec.getComment();
                restart = valueSpec.restartType() != ModConfigSpec.RestartType.NONE;
                var range = valueSpec.getRange();
                if (range != null) {
                    min = ((Number) range.getMin()).doubleValue();
                    max = ((Number) range.getMax()).doubleValue();
                    boolean whole = min == Math.rint(min) && max == Math.rint(max);
                    if (value instanceof ModConfigSpec.IntValue || value instanceof ModConfigSpec.LongValue) {
                        step = 1.0;
                    } else if (whole && max - min >= 50.0) {
                        // A double with a wide whole-number range is a count (ticks, blocks); 1 is the useful step.
                        step = 1.0;
                    } else {
                        step = Math.max((max - min) / 200.0, 0.001);
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A key without a range is not a fault; it becomes a text row below.
        }
        FieldSpec.Kind kind;
        List<String> options = List.of();
        if (value instanceof ModConfigSpec.BooleanValue) {
            kind = FieldSpec.Kind.TOGGLE;
        } else if (value instanceof ModConfigSpec.EnumValue<?> e) {
            kind = FieldSpec.Kind.CYCLER;
            List<String> names = new ArrayList<>();
            for (Object constant : e.getDefault().getClass().getEnumConstants()) {
                names.add(((Enum<?>) constant).name());
            }
            options = List.copyOf(names);
            min = 0;
            max = Math.max(0, options.size() - 1);
            step = 1;
        } else if (value.getDefault() instanceof List<?>) {
            // A list is text, not a fraction: rendering it as a slider was a type error in the catalogue.
            kind = FieldSpec.Kind.TEXT;
        } else if (value.getDefault() instanceof String) {
            kind = FieldSpec.Kind.TEXT;
        } else if (value instanceof ModConfigSpec.IntValue && step >= 1.0 && max - min <= 8.0) {
            // A handful of whole numbers is a choice, not a slider: occlusion 0/1/2 and debug 0/1 are picked, not
            // dragged, and a cycler states what the values are instead of showing a fraction of a bar.
            kind = FieldSpec.Kind.CYCLER;
            List<String> names = new ArrayList<>();
            for (int i = (int) min; i <= (int) max; i++) names.add(String.valueOf(i));
            options = List.copyOf(names);
        } else if (value instanceof ModConfigSpec.IntValue && max >= 0xFFFF) {
            // A 24-bit colour is an int with a 0..0xFFFFFF range; a slider over sixteen million steps is useless.
            String lower = key.toLowerCase(Locale.ROOT);
            kind = lower.contains("color") || lower.contains("rgb") ? FieldSpec.Kind.COLOR : FieldSpec.Kind.SLIDER;
        } else {
            kind = FieldSpec.Kind.SLIDER;
        }
        String[] split = splitLabel(comment);
        String label = split[0].isBlank() ? humanise(key) : split[0];
        return new FieldSpec(key, group, label, split[1].isBlank() ? comment : split[1], kind,
                min, max, step, options, restart, "sablelightoverhaul.config.key." + labelPath(key));
    }

    private static String labelPath(String key) {
        if (!key.startsWith(HostileSmokeConfig.PREFIX)) return key;
        String field = key.substring(key.lastIndexOf('.') + 1);
        if (field.equals("enabled") || field.equals("lobesPerShot") || field.equals("color") || field.equals("densityDetail")) {
            return HostileSmokeConfig.PREFIX + field;
        }
        if (!ConfigTaxonomy.hostileWeapon(key).thin()) return "particles.muzzle.bigCannon.thermalSmoke." + field;
        return "haze." + switch (field) {
            case "hazeSpread", "hazeDrift", "hazeSink", "hazeClearance", "hazePush", "hazePushRadius" -> "muzzle" + field.substring(4);
            case "hazeLightGain" -> "lightGain";
            default -> field;
        };
    }

    /**
     * Turns one stored value into the screen's own type.
     *
     * <p>The boxed type has to be read from the {@code ConfigValue}'s class rather than guessed from the range: a
     * {@code ConfigValue<Integer>} handed a {@code Double} is corrected back on the next load, which reads to the
     * player as "it did not stick".
     */
    private static FieldValue snapshot(FieldSpec field, ModConfigSpec.ConfigValue<?> value) {
        return snapshot(field, value, false);
    }

    private static FieldValue snapshot(FieldSpec field, ModConfigSpec.ConfigValue<?> value, boolean useDefault) {
        Object raw;
        try {
            raw = useDefault ? value.getDefault() : value.get();
        } catch (RuntimeException | LinkageError ex) {
            raw = value.getDefault();
        }
        if (field.kind() == FieldSpec.Kind.CYCLER) {
            String name = String.valueOf(raw);
            int index = field.options().indexOf(name);
            return new FieldValue.Choice(index < 0 ? 0 : index, name);
        }
        if (field.kind() == FieldSpec.Kind.TOGGLE) {
            return new FieldValue.Bool(raw instanceof Boolean b && b);
        }
        if (field.kind() == FieldSpec.Kind.TEXT) {
            if (raw instanceof List<?> list) {
                StringBuilder joined = new StringBuilder();
                for (Object element : list) {
                    if (joined.length() > 0) joined.append(", ");
                    joined.append(element);
                }
                return new FieldValue.Text(joined.toString());
            }
            return new FieldValue.Text(raw == null ? "" : String.valueOf(raw));
        }
        return new FieldValue.Num(raw instanceof Number n ? n.doubleValue() : field.min());
    }

    private static void store(ModConfigSpec.ConfigValue<?> target, FieldValue value, FieldSpec field) {
        if (target instanceof ModConfigSpec.IntValue ints) {
            ints.set((int) Math.rint(FieldValue.numeric(value)));
        } else if (target instanceof ModConfigSpec.DoubleValue doubles) {
            doubles.set(FieldValue.numeric(value));
        } else if (target instanceof ModConfigSpec.LongValue longs) {
            longs.set((long) Math.rint(FieldValue.numeric(value)));
        } else if (target instanceof ModConfigSpec.BooleanValue bools) {
            bools.set(FieldValue.flag(value));
        } else if (target instanceof ModConfigSpec.EnumValue<?> enums) {
            if (value instanceof FieldValue.Choice choice) storeEnum(enums, choice);
        } else if (value instanceof FieldValue.Text text && target.getDefault() instanceof List<?> defaults) {
            @SuppressWarnings("unchecked")
            ModConfigSpec.ConfigValue<List<?>> listValue = (ModConfigSpec.ConfigValue<List<?>>) target;
            listValue.set(parseList(text.text(), defaults));
        } else if (value instanceof FieldValue.Text text) {
            @SuppressWarnings("unchecked")
            ModConfigSpec.ConfigValue<Object> any = (ModConfigSpec.ConfigValue<Object>) target;
            any.set(text.text());
        } else {
            throw new IllegalArgumentException("no writer for " + field.key() + " of kind " + field.kind());
        }
    }

    /**
     * The text box holds a list as comma-separated values. Numbers stay numbers when the default is numeric
     * (or when the list was empty and every token parses), because a list of ints handed strings is rejected
     * on the next load.
     */
    private static List<?> parseList(String text, List<?> defaults) {
        List<String> tokens = new ArrayList<>();
        for (String part : text.split("[,\\n;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) tokens.add(trimmed);
        }
        boolean numeric = !defaults.isEmpty()
                ? defaults.stream().allMatch(element -> element instanceof Number)
                : tokens.stream().allMatch(token -> token.matches("-?\\d+"));
        if (numeric) {
            try {
                List<Integer> numbers = new ArrayList<>(tokens.size());
                for (String token : tokens) numbers.add(Integer.parseInt(token));
                return numbers;
            } catch (NumberFormatException ignored) {
                // Fall through to strings: a list with one non-number is a string list.
            }
        }
        return List.copyOf(tokens);
    }

    private static void storeEnum(ModConfigSpec.EnumValue<?> raw, FieldValue.Choice choice) {
        Object[] constants = raw.getDefault().getClass().getEnumConstants();
        if (choice.index() < 0 || choice.index() >= constants.length) return;
        setEnumConstant(raw, constants[choice.index()]);
    }

    private static <T extends Enum<T>> void setEnumConstant(ModConfigSpec.EnumValue<?> raw, Object constant) {
        @SuppressWarnings("unchecked")
        ModConfigSpec.EnumValue<T> typed = (ModConfigSpec.EnumValue<T>) raw;
        typed.set((T) constant);
    }

    /**
     * Splits a comment into its bold lead-in and the rest.
     *
     * <p>The convention in this mod's newer keys is {@code **标签**，说明…}, so the bold part becomes the row's label
     * and the remainder its tooltip. Older keys have no bold part and fall back to their name plus the whole comment.
     */
    private static String[] splitLabel(String comment) {
        if (comment == null || comment.isBlank()) return new String[] {"", ""};
        String c = comment.trim();
        if (c.startsWith("**")) {
            int end = c.indexOf("**", 2);
            if (end > 2) {
                String label = c.substring(2, end).trim();
                String rest = c.substring(end + 2).replaceFirst("^[，,、:：\\s]+", "").trim();
                return new String[] {label, rest};
            }
        }
        return new String[] {"", c};
    }

    /** {@code shadowBudget} -> {@code Shadow budget}, a fallback that at least reads like words. */
    private static String humanise(String key) {
        String name = key.substring(key.lastIndexOf('.') + 1);
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (i > 0 && Character.isUpperCase(ch) && !Character.isUpperCase(name.charAt(i - 1))) out.append(' ');
            out.append(i == 0 ? Character.toUpperCase(ch) : ch);
        }
        return out.toString();
    }
}
