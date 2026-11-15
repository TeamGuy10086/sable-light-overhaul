package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileCatalogue;
import dev.sablelightoverhaul.profiles.ProfileKinds;
import net.minecraft.resources.ResourceLocation;

/**
 * The one place a client-side value is looked up, in the one order that makes the cannon form work.
 *
 * <p>A value can exist at three levels, and the closest one wins:
 *
 * <ol>
 *   <li><b>the cannon's own record</b> -- what the owner pinned on that cannon's page, or edited there;</li>
 *   <li><b>the kind's preset</b> -- the value the server published for that kind of cannon, which is what the
 *       page shows when the cannon has no value of its own;</li>
 *   <li><b>this client's configuration</b> -- the caller's own default, which is what each caller already held
 *       before any of this existed.</li>
 * </ol>
 *
 * <p>The profile-backed pages have resolved their values this way since they were written; everything else that reads a
 * per-cannon key did not, and read this client's configuration directly. That is why a page could write a value,
 * show it, and change nothing: the write landed in the record, and nothing on the client ever asked for it. A
 * page whose values are ignored is worse than no page, so the lookup lives here now and every client-side
 * consumer asks this instead of the configuration directly.
 *
 * <p>Every method is null-tolerant: a shot that resolved no cannon (an emitter the table has never seen) simply
 * falls through to the preset and then to the caller's default, which is what a shot fired before the table
 * arrived should do.
 */
public final class SloProfiles {
    private SloProfiles() {}

    /** The kind's preset for a path, or null when the client has no table or no system covers the path. */
    public static Double preset(EffectApi.WeaponKind kind, String path) {
        if (kind == null) return null;
        // Nothing in here may ever take a shot down with it. This is called from the client's packet handler,
        // where an exception does not lose a value -- it loses the whole flash: no light, no plume following it,
        // no value. A missing table, a config that cannot be read, a catalogue that does not know the kind are
        // all ordinary states on a client, and all of them mean the same thing here: no answer, use the caller's
        // own value. The one exception is deliberate: an interrupted thread still propagates.
        try {
        for (var system : ProfileCatalogue.systemsFor(kind)) {
            if (system.covers(path)) {
                double value = ProfileKeys.preset(system, path);
                // ProfileKeys answers with the built-in default when the table has nothing, which is not the
                // same statement as "the cannon has no value" -- but it is a better answer than none, because
                // the table's default is the value the page itself would show for the row.
                return Double.isFinite(value) ? value : null;
            }
        }
        } catch (RuntimeException noAnswer) {
            return null;
        }
        return null;
    }

    /** Whether this cannon holds a value of its own for a path, which is what the page draws highlighted. */
    public static boolean owns(String cannonId, EffectApi.WeaponKind kind, String path) {
        return cannonId != null && kind != null
                && ProfileCache.storedAllById(cannonId, ProfileKinds.id(kind)).containsKey(path);
    }

    /** A double: the cannon's own value, else the kind's preset, else the caller's default. */
    public static double resolve(String cannonId, EffectApi.WeaponKind kind, String path, double fallback) {
        return resolve(cannonId, kind, path, fallback, 0);
    }

    /**
     * The same, with a floor of {@code minimum} applied to whichever value wins.
     *
     * <p>The floor is applied after the choice rather than to the configuration alone, so a pinned cannon value
     * outside the range the configuration would allow cannot smuggle an impossible number past the clamp the
     * caller relies on.
     */
    public static double resolve(String cannonId, EffectApi.WeaponKind kind, String path, double fallback,
                                 double minimum) {
        Double value = null;
        try {
            if (cannonId != null && kind != null) {
                value = ProfileCache.storedAllById(cannonId, ProfileKinds.id(kind)).get(path);
            }
        } catch (RuntimeException noRecord) {
            value = null;
        }
        if (value == null) value = preset(kind, path);
        if (value == null) value = fallback;
        return value < minimum ? minimum : value;
    }

    /** An int, rounded the way a whole-number field is: the nearest value, then the floor. */
    public static int resolveInt(String cannonId, EffectApi.WeaponKind kind, String path, int fallback,
                                 int minimum) {
        double value = resolve(cannonId, kind, path, fallback, minimum);
        return (int) Math.max(minimum, Math.round(value));
    }

    /** The identity a page and a shot agree on, for a caller that has the mount but not the table entry. */
    public static String cannonId(ResourceLocation dimension, long packedMountPos) {
        return ProfileCache.cannonAt(dimension, packedMountPos).orElse(null);
    }
}
