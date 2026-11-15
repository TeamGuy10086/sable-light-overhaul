package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Which cannons a player has edited since opening the page, so the announcement can wait until they close it.
 *
 * <p>Broadcasting each keystroke would be both noisy and pointless: nobody is watching another player's page
 * while they drag a number, and a row that changes value five times in two seconds is five messages that say
 * nothing the last one does not. The values themselves are stored as they are typed -- the page is a form, and
 * a form that lost an edit because the player closed the window would be worse than a late announcement -- so
 * this only holds what has to be *said*, not what has to be *kept*.
 *
 * <p>Keyed by player, because two players can have the same cannon's page open at once and each should get one
 * announcement for their own edits.
 */
public final class ProfileSessions {
    private ProfileSessions() {}

    /** One cannon and one kind: what a single announcement covers. */
    public record Touched(EffectApi.SourceKey key, ResourceLocation kind) {}

    private static final Map<UUID, Map<String, Touched>> TOUCHED = new LinkedHashMap<>();

    private static String idOf(EffectApi.SourceKey key, ResourceLocation kind) {
        return key.dimension().location() + "|" + key.ship() + "|" + key.localPos().asLong() + "|" + kind;
    }

    /** Records that this player changed something about this cannon's values for this kind. */
    public static synchronized void touch(UUID player, EffectApi.SourceKey key, ResourceLocation kind) {
        if (player == null || key == null || kind == null) return;
        if (TOUCHED.size() > 256) TOUCHED.clear();   // a session table is not worth growing without bound
        TOUCHED.computeIfAbsent(player, id -> new LinkedHashMap<>())
                .put(idOf(key, kind), new Touched(key, kind));
    }

    /** Takes everything this player touched, and forgets it. The announcement happens once, on close. */
    public static synchronized Map<String, Touched> drain(UUID player) {
        if (player == null) return Map.of();
        var taken = TOUCHED.remove(player);
        return taken == null ? Map.of() : Map.copyOf(taken);
    }

    /** Forget a player entirely: a disconnect leaves nothing worth announcing. */
    public static synchronized void forget(UUID player) {
        if (player != null) TOUCHED.remove(player);
    }
}
