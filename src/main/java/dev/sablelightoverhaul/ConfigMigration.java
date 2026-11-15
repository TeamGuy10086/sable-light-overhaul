package dev.sablelightoverhaul;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves an instance's existing config onto the current section names, on the way in.
 *
 * <p>Why this exists: NeoForge cannot rename a key or a section. Renaming one therefore looks, from the
 * outside, exactly like "the value you tuned disappeared and the default came back", and a silent change is
 * the one thing this mod does not do. The owner's own instance is the distribution's default -- their tuned
 * file <em>is</em> what ships -- so dropping a value is not a small thing.
 *
 * <p>This class is a pure function over lines on purpose. Running it needs a game; checking that two old
 * sections merge into one current section and that every value travels does not, and that check is the
 * reason to trust it.
 *
 * <p>Only section names move. Keys keep their names, and the file that ends up on disk is NeoForge's own:
 * it rewrites the file from the spec after loading, comments included, so this only has to carry values.
 * (A comment cannot be localized in NeoForge -- it is a literal string in the file -- which is why the
 * comments in every file of this mod are written in one language.)
 *
 * <p>Merging is the reason the output is built as a map rather than edited in place: {@code ap} and
 * {@code apSpark} both become {@code apSparks}, and a TOML file may not repeat a header.
 */
public final class ConfigMigration {
    /**
     * The client's moved section paths.
     *
     * <p>These are the names the owner called out as unusable: {@code aerial} held the trail and
     * {@code trails} held the shell lights, so the two names were each the other's subject.
     */
    private static final Map<String, String> CLIENT = Map.ofEntries(
            Map.entry("trails", "tracerLight"),
            Map.entry("aerial", "trail"),
            Map.entry("ap", "apSparks"),
            Map.entry("apSpark", "apSparks"),
            Map.entry("particles.visuals.autocannon", "particles.muzzle.autocannon.flame"),
            // Both sides at once. The prefix rule makes a child follow its parent, so renaming this one side
            // alone would drag the server's experimental.vehicleDust with it, into a name the spec does not
            // define, and seven tuned vehicle dust values would read as unknown keys and revert.
            Map.entry("experimental", "haze"),
            // These three were never "the big cannon's": push() splits dots and one missing pop left them
            // under muzzle.bigCannon. They are atmospheric/particle/diagnostic sections, and the paths now say so.
            Map.entry("muzzle.bigCannon.clouds", "clouds"),
            Map.entry("muzzle.bigCannon.particles", "particles"),
            Map.entry("muzzle.bigCannon.debug", "debug"),
            // The detonation wave had a second "shockwave." written into its own keys, and the beam block was
            // pushed while the shockwave section was still open. Neither was ever a child of the other thing.
            Map.entry("shockwave.shockwave", "shockwave"),
            Map.entry("shockwave.beam", "spotlights.beam"));

    /**
     * The server's moved section paths, which are not the client's.
     *
     * <p>A single table was wrong, and the crash of a half-migrated file is not what showed it: the server's
     * {@code trails} is the tracer FEED and the client's is the shell lights, so one table renamed the feed
     * to the lights' name and left its two values filed under a section the spec does not define. The names
     * meant different things in the two files, which is the same complaint that started this -- one name, two
     * subjects -- and the fix is the same: the feed gets its own name, and the tables stop being shared.
     *
     * <p>{@code tracerLight} to {@code tracerFeed} repairs files the shared table already renamed, so an
     * instance that has been through the broken version ends up right rather than merely no worse.
     */
    private static final Map<String, String> SERVER = Map.of(
            "trails", "tracerFeed",
            "tracerLight", "tracerFeed",
            "experimental", "haze",
            // The vehicle dust experiment and the muzzle haze came out of one section: the experiment keeps
            // its own two keys, and the haze keys move to the section the client half already uses.
            "haze.vehicleDust.enabled", "hullDust.enabled",
            "haze.vehicleDust.radius", "hullDust.radius",
            "haze.vehicleDust", "haze",
            // The server's send-distance block had a capital P and no relation to the client's particles.
            "Particle", "particles");

    private ConfigMigration() {}

    /**
     * The current name of a section, children included: {@code trails.bigCannon} is as much a moved name as
     * {@code trails} is, and enumerating every child would be a list that goes stale the moment one is added.
     */
    private static String currentName(String section, Map<String, String> moved) {
        String direct = moved.get(section);
        if (direct != null) return direct;
        for (var entry : moved.entrySet()) {
            String from = entry.getKey() + ".";
            if (section.startsWith(from)) return entry.getValue() + section.substring(entry.getKey().length());
        }
        return section;
    }

    /** The client's names. */
    public static boolean neededClient(List<String> lines) {
        return needed(lines, CLIENT) || !lines.equals(migrateLightingMode(lines));
    }
    public static List<String> migrateClient(List<String> lines) {
        return migrateLightingMode(needed(lines, CLIENT) ? migrate(lines, CLIENT) : lines);
    }

    private static List<String> migrateLightingMode(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        boolean lighting = false;
        var mode = java.util.regex.Pattern.compile(
                "(?i)^(\\s*mode\\s*=\\s*)[\"'](Legacy|Compatible|None)[\"'](\\s*(?:#.*)?)$");
        for (String line : lines) {
            String section = sectionOf(line);
            if (section != null) lighting = section.equals("lighting");
            var match = mode.matcher(line);
            if (lighting && match.matches()) {
                String replacement = match.group(2).equalsIgnoreCase("None") ? "Off" : "Advanced";
                line = match.group(1) + "\"" + replacement + "\"" + match.group(3);
            }
            result.add(line);
        }
        return result;
    }
    /** The server's names, which are deliberately not the client's. */
    public static boolean neededServer(List<String> lines) { return needed(lines, SERVER); }
    public static List<String> migrateServer(List<String> lines) { return migrate(lines, SERVER); }
    /** True when this file still has anything written under a name that has since moved. */
    public static boolean needed(List<String> lines, Map<String, String> moved) {
        for (String line : lines) {
            String section = sectionOf(line);
            if (section != null && !currentName(section, moved).equals(section)) return true;
        }
        return false;
    }

    /**
     * The same settings under the current names: every comment and every value travels with its key, the
     * sections come out in the order they went in, and each target section appears exactly once.
     */
    public static List<String> migrate(List<String> lines, Map<String, String> moved) {
        var order = new ArrayList<String>();
        var bodies = new LinkedHashMap<String, List<String>>();
        String current = null;
        for (String line : lines) {
            String section = sectionOf(line);
            if (section != null) {
                current = currentName(section, moved);
                if (!bodies.containsKey(current)) {
                    bodies.put(current, new ArrayList<>());
                    order.add(current);
                }
                continue;
            }
            // Anything above the first header is the file's own preamble, which NeoForge writes again.
            if (current != null) bodies.get(current).add(line);
        }
        var out = new ArrayList<String>();
        for (String section : order) {
            out.add("[" + section + "]");
            var body = bodies.get(section);
            // Keep the section's own trailing blank line out of the middle of the next one.
            while (!body.isEmpty() && body.get(body.size() - 1).isBlank()) body.remove(body.size() - 1);
            out.addAll(body);
            out.add("");
        }
        return out;
    }

    /** Rewrites one config file if it needs it. Silent and harmless when the file is absent or current. */
    public static void migrateFile(String fileName) {
        boolean server = fileName.contains("server");
        var path = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(fileName);
        if (!Files.isRegularFile(path)) return;
        try {
            var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (server ? !neededServer(lines) : !neededClient(lines)) return;
            Files.write(path, server ? migrateServer(lines) : migrateClient(lines), StandardCharsets.UTF_8);
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: migrated {} to the current config",
                    fileName);
        } catch (IOException | RuntimeException failure) {
            // A config that cannot be migrated is left exactly as it was: NeoForge will load it with the
            // defaults for the moved sections, which is visible and recoverable, unlike a half-written file.
            com.mojang.logging.LogUtils.getLogger().error(
                    "Sable Light Overhaul: could not migrate {}, leaving it untouched", fileName, failure);
        }
    }

    private static String sectionOf(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < 3 || trimmed.charAt(0) != '[') return null;
        int end = trimmed.indexOf(']');
        return end < 2 ? null : trimmed.substring(1, end).trim();
    }
}
