package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which configuration keys belong to which kind of cannon, grouped into the systems a page shows.
 *
 * <p><b>The keys are derived, not listed.</b> A system names one or more configuration <em>path prefixes</em>,
 * and the actual keys are whatever the spec has under them. Writing the leaf names out by hand would go stale
 * the first time a key is added or renamed, and it would be wrong in a way nobody notices until a page shows
 * a key that no longer exists. The prefixes are short and stable, so they are worth naming; the keys are not.
 *
 * <p><b>Each system belongs to one side, and that is not a detail.</b> The cannon's own effects are configured
 * on the server, so their preset values come from the server spec and the server is the authority. The light
 * profiles and the muzzle particle shapes are configured on the client, so their presets come from each
 * client's own file. A per-cannon override still travels from the server in both cases: the page shows the
 * override when there is one and the preset when there is not, which is exactly the model the fourteen light
 * slots have always used, generalised.
 *
 * <p>Budgets and switches are deliberately absent. They are not per cannon, so they do not belong on a per
 * cannon page and cannot be overridden by one; the page shows them on its own global tab instead.
 */
public final class ProfileCatalogue {
    /** Which configuration file a system's preset values come from. */
    public enum Side { SERVER, CLIENT }

    /**
     * One system, as the page shows it: a heading and the configuration paths underneath it.
     *
     * @param label    what the page calls it
     * @param prefixes configuration paths; a key belongs to the system when it equals one or starts with it
     * @param side     whose configuration the preset values come from
     */
    public record System(String label, List<String> prefixes, Side side) {
        /** Whether this system covers a configuration path. */
        public boolean covers(String path) {
            for (var prefix : prefixes) {
                if (path.equals(prefix) || path.startsWith(prefix + ".")) return true;
            }
            return false;
        }
    }

    private static final List<System> TANK = List.of(
            new System("炮口火球", List.of("muzzle.tankCannon.fireball"), Side.SERVER),
            new System("白烟与炮尾烟", List.of("muzzle.tankCannon.plumeScale", "muzzle.tankCannon.breechSmoke"), Side.SERVER),
            new System("地面扬尘", List.of("muzzle.tankCannon.groundDust"), Side.SERVER),
            new System("车体扬尘", List.of("muzzle.tankCannon.hullDust"), Side.SERVER),
            new System("炮口薄烟", List.of("haze"), Side.SERVER),
            new System("炮口光", List.of("lightProfile.autocannon"), Side.CLIENT),
            // The muzzle plume's opacity is deliberately listed twice, once per side, because there are two
            // sections with this name: the server's is a preset for the kind and is what a page shows when a
            // cannon has no value of its own, while the client's is the one the smoke actually reads -- CBC's
            // own plume particles are drawn on the client. Labelling both is the whole point: an owner who
            // tuned the server copy and saw no change had no way to tell which copy was the live one.
            new System("炮口烟透明度（服务端预设）", List.of("muzzle.tankCannon.plumeAlpha"), Side.SERVER),
            new System("预算与距离", List.of("muzzle.tankCannon.particlesPerShot", "muzzle.tankCannon.cullDistance"), Side.SERVER));

    private static final List<System> AUTOMATIC = List.of(
            new System("炮口光", List.of("lightProfile.autocannon"), Side.CLIENT),
            new System("炮口焰粒子", List.of("particles.muzzle.autocannon"), Side.CLIENT),
            new System("炮口薄烟", List.of("haze"), Side.SERVER));

    private static final List<System> GIANT = List.of(
            new System("炮口闪光", List.of("lightProfile.bigCannon"), Side.CLIENT),
            new System("炮口烟跟随光源", List.of("particles.muzzle.bigCannon"), Side.CLIENT),
            // The client's copy, and the one the smoke reads: CBC draws the big cannon's plume on the client,
            // so this is the section that actually changes what an owner sees. The server keeps a section of
            // the same name as the kind's preset, which is why both are listed with their side spelled out.
            new System("炮口烟透明度（客户端，两种炮共用）", List.of("muzzle.bigCannon.plumeAlpha"), Side.CLIENT));

    private ProfileCatalogue() {}

    /**
     * The systems a kind's page shows, in order.
     *
     * <p>The same system appears under several kinds on purpose: thin smoke is thin smoke, but each kind keeps
     * its own values for it, so each kind's page has to offer it.
     */
    public static List<System> systemsFor(EffectApi.WeaponKind kind) {
        return switch (kind) {
            case TANK_CANNON -> TANK;
            case AUTOCANNON, TWIN_AUTOCANNON -> AUTOMATIC;
            case CANNON -> GIANT;
        };
    }

    /** Every configuration path this system covers, found in a spec's own value map, in the spec's order. */
    public static List<String> keysOf(System system, Map<String, ?> specValues) {
        var keys = new ArrayList<String>();
        if (specValues == null) return keys;
        for (var path : specValues.keySet()) {
            if (system.covers(path)) keys.add(path);
        }
        return keys;
    }

    /**
     * The preset value of every key this system covers, as the configuration currently has it.
     *
     * <p>Numbers are what a form holds, so booleans and integers are carried as doubles: true is 1. Values a
     * form cannot use are skipped rather than guessed at.
     */
    public static Map<String, Double> presetsOf(System system, Map<String, ?> specValues) {
        var presets = new LinkedHashMap<String, Double>();
        if (specValues == null) return presets;
        for (var entry : specValues.entrySet()) {
            if (!system.covers(entry.getKey())) continue;
            Double number = asNumber(entry.getValue());
            if (number != null) presets.put(entry.getKey(), number);
        }
        return presets;
    }

    /** A configuration value as the number a form stores, or null when it is not one a form can hold. */
    public static Double asNumber(Object value) {
        // A spec's own value map hands back the ConfigValue wrappers rather than the values, which is why a
        // table built from it came out empty while 181 paths had been flattened: every entry was an object
        // that is not a Number. Anything a supplier can answer is asked, once, here.
        if (value instanceof java.util.function.Supplier<?> supplier) {
            try {
                value = supplier.get();
            } catch (RuntimeException notLoadedYet) {
                return null;
            }
        }
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean flag) return flag ? 1.0 : 0.0;
        return null;
    }
}
