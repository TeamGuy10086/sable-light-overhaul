package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.MissileGlowPolicy;
import dev.sablelightoverhaul.api.ClientMissileEffectsApi;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector4f;

/** Render-thread adapter: reuse the patched packs' occluded flare pass, never create a light. */
final class PackMissileGlows {
    private static final Vector4f[] POSITIONS = vectors(), COLORS = vectors();
    private static List<ClientMissileEffectsApi.Glow> snapshot = List.of();
    private static ClientLevel owner;
    private static boolean prepared;

    private PackMissileGlows() {}

    private static Vector4f[] vectors() {
        var result = new Vector4f[MissileGlowPolicy.PACK_SLOTS];
        for (int i = 0; i < result.length; i++) result[i] = new Vector4f();
        return result;
    }

    static void invalidate() {
        prepared = false;
    }

    private static ClientMissileEffectsApi.Glow missile(int slot) {
        var level = Minecraft.getInstance().level;
        if (level == null || !ClientFlashes.effectsEnabled()) {
            snapshot = List.of(); owner = null; prepared = false;
            return null;
        }
        if (!prepared || owner != level) {
            snapshot = ClientMissileEffectsApi.collect(level);
            owner = level;
            prepared = true;
        }
        int index = slot - DebugFlares.reservedGlowSlots();
        return index >= 0 && index < snapshot.size() ? snapshot.get(index) : null;
    }

    static Vector4f position(int slot) {
        if (slot < DebugFlares.reservedGlowSlots()) return DebugFlares.position(slot);
        var out = POSITIONS[slot];
        var glow = missile(slot);
        if (glow == null) return out.zero();
        var eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return out.set((float)(glow.position().x - eye.x), (float)(glow.position().y - eye.y),
                (float)(glow.position().z - eye.z), glow.radius());
    }

    static int count() {
        missile(DebugFlares.reservedGlowSlots());
        return Math.min(MissileGlowPolicy.PACK_SLOTS, DebugFlares.reservedGlowSlots() + snapshot.size());
    }

    static int missileCount() {
        return Math.max(0, count() - Math.min(MissileGlowPolicy.PACK_SLOTS, DebugFlares.reservedGlowSlots()));
    }

    static Vector4f color(int slot) {
        if (slot < DebugFlares.reservedGlowSlots()) return DebugFlares.color(slot);
        var out = COLORS[slot];
        var glow = missile(slot);
        if (glow == null) return out.zero();
        return out.set(((glow.rgb() >> 16) & 255) / 255f, ((glow.rgb() >> 8) & 255) / 255f,
                (glow.rgb() & 255) / 255f, glow.brightness());
    }
}
