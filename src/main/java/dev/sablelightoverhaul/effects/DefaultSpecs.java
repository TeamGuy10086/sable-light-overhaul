package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.client.ExtraConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds this mod's own defaults as {@link EffectApi.Spec} values.
 *
 * <p>This is the bridge between "what the mod does today" and the event API. It reads the existing config at
 * call time instead of copying numbers, so a spec cannot silently disagree with the setting it came from --
 * which is the whole reason this step exists before any call site moves.
 *
 * <p>Live: {@link #forFire} is built by {@link FireDispatch} for every firing event the four CBC weapons
 * dispatch, and the mod's own muzzle flash reads its light from it. The rest is still built only by callers
 * that do not exist yet, so nothing here can change behaviour on its own.
 *
 * <p>One rule follows from where each of these is built, and it is not optional: <b>a spec built on the
 * server path may not read a client setting.</b> {@link #forFire} and {@link #forImpact} are built while a
 * shot or an explosion is being handled, which happens on the server; {@link #forBlockHit} is built on the
 * client, where the armour piercing sparks are drawn. A client value read from the server path would not be
 * a stale number -- the field is only assigned when the client config is built, so on a dedicated server it
 * is null and reading it throws. That is why the ring and the mesh record no duration and no size here: those
 * belong to the client's rendering settings, and the client already reads them itself.
 *
 * <p>One thing to be clear about: a preset id is a label this mod agrees with itself on. The wire still
 * carries the payload's own int kind, and the client still picks the light shape from that, so a third party
 * cannot yet name a light and get it. Making the id travel is part of the client side of this work.
 */
public final class DefaultSpecs {
    private DefaultSpecs() {}

    public static final ResourceLocation PRESET_LIGHT_AUTO = id("light_auto");
    public static final ResourceLocation PRESET_LIGHT_BLAST = id("light_blast");
    public static final ResourceLocation PRESET_LIGHT_SMOKE = id("light_smoke");
    public static final ResourceLocation PRESET_RING = id("ring_scripted");
    public static final ResourceLocation PRESET_MESH = id("blast_mesh");

    /**
     * The named children of {@code spawnParticles} for an impact.
     *
     * <p>These are the ids a listener cancels one effect by: {@code cancel(SPAWN_PARTICLES, SPAWN_RING)}
     * switches off the dust ring and leaves the sphere, which is why the two are separate entries rather than
     * one "shockwave" blob. The strings are the contract, so they do not change once published.
     */
    public static final ResourceLocation SPAWN_RING = id("ring");
    public static final ResourceLocation SPAWN_MESH = id("mesh");

    /**
     * The two things this mod attaches to a host it did not spawn: the light a shell carries, and the rope
     * behind a tracer. Named here so a listener can cancel one of them without touching the other -- they
     * have always been independent in the code (a shell's light is not its rope) and this is where that
     * independence becomes something another mod can reach.
     */
    public static final ResourceLocation TRACER_LIGHT = id("tracer_light");
    public static final ResourceLocation PROJECTILE_STREAK = id("projectile_streak");
    public static final ResourceLocation TRACER_TRAIL = id("tracer_trail");

    /**
     * The light that follows the heat-weighted centre of somebody else's smoke, and the mean size that
     * centre is given. One id, because the two travel together: a light following a plume without knowing
     * how big the plume is would be a point in the dark.
     */
    public static final ResourceLocation SMOKE_LIGHT = id("smoke_light");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", path);
    }

    /**
     * The light a weapon kind uses on firing, by preset id (the shape itself stays in code).
     *
     * <p>The mapping is not a guess: the client picks the shape from the payload's kind, and every barrel
     * weapon except the big cannon is drawn with the autocannon light. This is written down in two places on
     * the client -- {@code FlashStore.add} calls kinds 1 and 2 "the barrel weapons", and
     * {@code AdvancedLights} treats {@code kind == 0} as the big one -- so the tank cannon belongs with the
     * autocannons here, not with the big cannon it was grouped with before.
     *
     * <p>{@code slo:light_blast} is the big cannon's whole light life, which on the client is two phases in
     * sequence: a blast phase, then a smoke phase that follows the shot's own smoke. One id covers both,
     * because one weapon has one light. {@code slo:light_smoke} names the second phase on its own, for the
     * smoke follower to ask for by itself.
     */
    public static EffectApi.LightRef muzzleLight(EffectApi.WeaponKind kind) {
        ResourceLocation preset = switch (kind) {
            case AUTOCANNON, TWIN_AUTOCANNON, TANK_CANNON -> PRESET_LIGHT_AUTO;
            case CANNON -> PRESET_LIGHT_BLAST;
        };
        // A light is a capped resource: our own defaults ask for HIGH so a third party adding its own lights
        // cannot crowd our muzzle flash out of the pool.
        return new EffectApi.LightRef(preset, 1.0,
                new EffectApi.LightBudget(8, EffectApi.Priority.HIGH));
    }

    /** The blast light: the existing code stacks two, centre plus a little above, both measured from the reach. */
    public static EffectApi.LightRef blastLight() {
        return new EffectApi.LightRef(PRESET_LIGHT_BLAST, 1.0,
                new EffectApi.LightBudget(8, EffectApi.Priority.HIGH));
    }

    /**
     * The dust ring. The preset id names the tier table and count formula; the particle mixture the preset
     * draws from still lives in {@code ringCore} / {@code ringGroups}, and moving that into {@code RingPreset}
     * is a known piece of work rather than something this method pretends to have finished.
     */
    public static EffectApi.SpawnParticle ring() {
        return new EffectApi.SpawnParticle(
                new EffectApi.Anchor(EffectApi.AnchorKind.IMPACT_CENTRE, null, null, 0.0, 0),
                // STILL OPEN: the actual mixture (ringCore / ringGroups) and its weights. Called out as a
                // known gap rather than filled with a guess, so nobody mistakes this for finished.
                null,
                1.0,
                0);
    }

    /** The spherical wave mesh. Same story as {@link #ring}: the client's own settings own its numbers. */
    public static EffectApi.SpawnParticle mesh() {
        return new EffectApi.SpawnParticle(
                new EffectApi.Anchor(EffectApi.AnchorKind.IMPACT_CENTRE, null, null, 0.0, 0),
                // STILL OPEN: layers, thickness and ring sharpness are not in this record yet, and neither is
                // the duration -- the client reads that from its own settings, so there is nothing to copy
                // here that would not go stale.
                null,
                1.0,
                0);
    }

    /**
     * Armour piercing sparks. Two parameter sets exist today, one for a hit and one for a ricochet, and the
     * spec keeps them apart instead of flattening them: a bounced shell should not have to look like a
     * stopped one.
     */
    public static Map<ResourceLocation, EffectApi.SpawnParticle> apSparks() {
        Map<ResourceLocation, EffectApi.SpawnParticle> out = new HashMap<>();
        out.put(id("ap_hit"), spark(false));
        out.put(id("ap_ricochet"), spark(true));
        return out;
    }

    /**
     * A config value that may not exist. These specs are built on both sides -- the client draws them, the server
     * builds them for an explosion -- and the config they come from is a client config, which is null on a dedicated
     * server. Reading it there used to throw inside the tick loop and take the whole server down, so the default is
     * used instead: the server only assembles the spec, the styling is the client's business.
     */
    private static double cfg(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (Throwable absent) {
            return fallback;
        }
    }

    private static int cfg(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (Throwable absent) {
            return fallback;
        }
    }

    private static net.minecraft.network.chat.Component cfgColor(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> value) {
        try {
            return value == null ? null : (net.minecraft.network.chat.Component) value.get();
        } catch (Throwable absent) {
            return null;
        }
    }

    private static EffectApi.SpawnParticle spark(boolean ricochet) {
        double count = ricochet ? cfg(ExtraConfig.AP_RICO_COUNT, 8) : cfg(ExtraConfig.AP_HIT_COUNT, 6);
        double speed = ricochet ? cfg(ExtraConfig.AP_RICO_SPEED, 0.4) : cfg(ExtraConfig.AP_HIT_SPEED, 0.35);
        double spread = ricochet ? cfg(ExtraConfig.AP_RICO_SPREAD, 0.5) : cfg(ExtraConfig.AP_HIT_SPREAD, 0.4);
        double size = ricochet ? cfg(ExtraConfig.AP_RICO_SIZE, 0.12) : cfg(ExtraConfig.AP_HIT_SIZE, 0.1);
        int life = ricochet ? (int) cfg(ExtraConfig.AP_RICO_LIFE, 10.0) : (int) cfg(ExtraConfig.AP_HIT_LIFE, 8.0);
        int colour = cfg(ExtraConfig.AP_COLOR, 0xFFC070);
        return new EffectApi.SpawnParticle(
                new EffectApi.Anchor(EffectApi.AnchorKind.BLOCK_HIT, null, null, 0.0, 0),
                new EffectApi.ParticleSpec(null, size, speed, spread, colour, life),
                (int) count,
                life);
    }
    /** A fully populated spec for the kinds of event this mod already handles. */
    public static EffectApi.Spec forFire(EffectApi.WeaponKind kind) {
        return new EffectApi.Spec(muzzleLight(kind), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static EffectApi.Spec forImpact() {
        Map<ResourceLocation, EffectApi.SpawnParticle> spawn = new HashMap<>();
        spawn.put(SPAWN_RING, ring());
        spawn.put(SPAWN_MESH, mesh());
        return new EffectApi.Spec(blastLight(), spawn, Map.of(), Map.of(), Map.of());
    }

    public static EffectApi.Spec forBlockHit() {
        return new EffectApi.Spec(null, apSparks(), Map.of(), Map.of(), Map.of());
    }

    /**
     * What this mod attaches to hosts somebody else created -- currently the tracer shells it tracks.
     *
     * <p>Deliberately empty of bindings, and the reason is a gap in the vocabulary rather than unfinished
     * work: the two things we attach are named ({@link #TRACER_LIGHT}, {@link #TRACER_TRAIL}) and the code
     * that attaches them is a listener that can be cancelled by those names, but a {@code ReferencedTarget}
     * has to carry a {@link EffectApi.Selector}, and a selector cannot say "every host in this offer" --
     * {@code Take(0, ...)} means none of them. Our own offer is exactly that: no selection, everything
     * announced. Filling this in with an empty selector would read as "pick nothing".
     */
    public static EffectApi.Spec forTrackedHosts() {
        return new EffectApi.Spec(null, Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** Kept for the smoke tracker, which is a referenced-host feature rather than a spawned one. */
    public static List<ResourceLocation> trackedHostTypes() {
        return List.of(id("plume_children"));
    }
}
