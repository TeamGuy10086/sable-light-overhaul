package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.api.EffectApi;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The kinds of shot a form can be written for.
 *
 * <p>A cannon's form is filed under the kind of shot it fires, not under the kind of block it is. The first
 * attempt filed it under the mount block's registry name and had the page guess the rest from that name, which
 * guessed wrong immediately: the owner's tank cannon was shown the big cannon's numbers because the guess
 * looked for "autocannon" in the block name and the block is not called that.
 *
 * <p>The shot knows its own kind -- it is on the fire event -- so nothing has to be guessed at either end. The
 * page lists every kind, and a shot reads the one that matches it; a shot that finds nothing keeps the
 * client's own configuration and says so once, which is the owner's rule and a better one than a silent
 * nearest guess.
 */
public final class ProfileKinds {
    private ProfileKinds() {}

    /** The id a kind's values are filed under. A kind id, deliberately not a block id. */
    public static ResourceLocation id(EffectApi.WeaponKind kind) {
        return ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,
                kind.name().toLowerCase(Locale.ROOT));
    }

    /** Which kind an id names, for the server, which is handed ids rather than kinds by a client. */
    public static Optional<EffectApi.WeaponKind> of(ResourceLocation id) {
        if (id == null) return Optional.empty();
        for (EffectApi.WeaponKind kind : EffectApi.WeaponKind.values()) {
            if (id(kind).equals(id)) return Optional.of(kind);
        }
        return Optional.empty();
    }

    /** Every kind, in the order the enum declares them, which is the order the page shows them in. */
    public static List<EffectApi.WeaponKind> all() {
        return List.of(EffectApi.WeaponKind.values());
    }

    /** What a player calls it. */
    public static String label(EffectApi.WeaponKind kind) {
        return switch (kind) {
            case CANNON -> "巨炮";
            case TANK_CANNON -> "坦克炮";
            case AUTOCANNON -> "自动炮";
            case TWIN_AUTOCANNON -> "双管自动炮";
        };
    }

    /**
     * Which of the client's light profiles this kind's numbers come from, by name.
     *
     * <p>The client picks between three profiles when it draws: the giant cannon uses the blast profile while
     * the flash is held and the smoke profile after, and everything else uses the autocannon profile. A form
     * cannot tell those two phases apart -- the phase is the client's own animation state and never reaches the
     * server -- so the giant cannon is represented by the smoke profile, which is the phase that lasts.
     */
    public static String profileName(EffectApi.WeaponKind kind) {
        return kind == EffectApi.WeaponKind.CANNON ? "bigCannon.smoke" : "autocannon";
    }
}
