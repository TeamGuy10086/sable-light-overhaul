package dev.sablelightoverhaul.api;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The whole public surface of this mod's weapon effects, in one place, for review.
 *
 * <p>This file exists so the <em>shape</em> can be read, argued about and renamed before any behaviour moves:
 * renaming a field now costs nothing, renaming it after other mods depend on it costs a compatibility break.
 *
 * <p>Wiring happens one event at a time, and a registration is only allowed to succeed once its event is
 * actually dispatched -- a registration that quietly never fires would be worse than an exception, because an
 * author would see no effect and no error. Wired so far: {@link #onFire}, {@link #onImpact},
 * {@link #onTerrainChanged}, {@link #onBlockHit}, {@link #onTrackedHost}. Left throwing: the three
 * registries.
 *
 * <p>Four rules this file is built on:
 * <ol>
 *   <li>Events fire at the moment something happens, never per particle per frame.</li>
 *   <li>Server events carry authoritative facts; client events are where anything is drawn. A spec travels
 *       with the fact, so a listener never has to invent its own networking.</li>
 *   <li>A missing field means "inherit", exactly like the per instance settings this mod already has.</li>
 *   <li>External code depends on this package only. Everything else may be refactored freely.</li>
 * </ol>
 *
 * <p>The specification is split by <em>mechanism</em>, not by ownership, because ownership turned out to be
 * ambiguous: particles this mod spawns may be vanilla's or another mod's types, and particles it only decorates
 * belong to whoever spawned them. So:
 * <ul>
 *   <li>{@code spawnParticles} — particles this mod calls spawn for, whatever their type.</li>
 *   <li>{@code referencedParticles} / {@code referencedEntities} — hosts somebody else created, which this mod
 *       may select and bind things to.</li>
 * </ul>
 *
 * <p>And a spec entry is described on three independent axes:
 * <ul>
 *   <li><b>anchor</b> — where something is spawned (a world point, a physics body's surface, a muzzle, an
 *       impact centre). This is the axis that was missing until it was pointed out.</li>
 *   <li><b>selector</b> — which existing hosts to pick, only meaningful for {@code referenced*}.</li>
 *   <li><b>binding</b> — what to attach to those hosts (a light, a trail, a tracer).</li>
 * </ul>
 */
public final class EffectApi {
    private EffectApi() {}

    /** Consumers should refuse to work with a version they do not know. */
    public static final int VERSION = 1;

    // ------------------------------------------------------------------------------------------------
    // Registration. The ones that are not wired yet throw on purpose: a silently ignored registration
    // would be the worst possible outcome, because an author would see no effect and no error.
    // ------------------------------------------------------------------------------------------------

    /** Wired: the four CBC weapons dispatch this at the moment they build their muzzle plume. */
    public static void onFire(int priority, Listener<FireEvent> listener) {
        dev.sablelightoverhaul.effects.EffectBus.onFire(priority, listener);
    }

    /** Wired: one explosion, classified and measured once by {@code ImpactClassifier}. */
    public static void onImpact(int priority, Listener<ImpactEvent> listener) {
        dev.sablelightoverhaul.effects.EffectBus.onImpact(priority, listener);
    }

    /** Wired: dispatched once per tick per side, by whoever is tracking hosts on that side. */
    public static void onTrackedHost(int priority, Listener<TrackedHostEvent> listener) {
        dev.sablelightoverhaul.effects.EffectBus.onTrackedHost(priority, listener);
    }

    /** Wired: dispatched on the client, where CBC plays the per block hit effect and this mod draws sparks. */
    public static void onBlockHit(int priority, Listener<BlockHitEvent> listener) {
        dev.sablelightoverhaul.effects.EffectBus.onBlockHit(priority, listener);
    }

    /** Wired: dispatched for every explosion, before any of this mod's effect switches are consulted. */
    public static void onTerrainChanged(int priority, Listener<TerrainEvent> listener) {
        dev.sablelightoverhaul.effects.EffectBus.onTerrainChanged(priority, listener);
    }

    /** Not wired yet -- the client still derives the light shape from the payload's kind. Shapes can be chosen. */
    public static void registerLightPreset(ResourceLocation id, LightPreset preset) {
        throw notWired();
    }

    /** Not wired yet -- the ring still reads its mixture straight from the client config. Tier table and counts. */
    public static void registerRingPreset(ResourceLocation id, RingPreset preset) {
        throw notWired();
    }

    /** Not wired yet -- there is no profile editor to ask. Which blocks can carry a per instance profile. */
    public static void registerProfileHost(Predicate<BlockState> host) {
        throw notWired();
    }

    public interface Listener<T> {
        void accept(T event);
    }

    private static UnsupportedOperationException notWired() {
        return new UnsupportedOperationException(
                "sablelightoverhaul effect API is declared but not implemented yet (VERSION " + VERSION + ")");
    }

    // ------------------------------------------------------------------------------------------------
    // Identity.
    // ------------------------------------------------------------------------------------------------

    /**
     * Stable identity of a weapon: the physics body it belongs to plus a position in that body's local frame.
     *
     * <p>Not an entity id: a contraption is rebuilt when a ship moves, and anything keyed by the entity would
     * be lost. State that has to survive a rebuild keys on this.
     */
    public record SourceKey(net.minecraft.resources.ResourceKey<Level> dimension, @Nullable java.util.UUID ship,
                            BlockPos localPos, ResourceLocation typeId, @Nullable String customName) {
        // The same three parts the owner specified, in the order they resolve:
        //   A  dimension + localPos -- where the cannon is, in the body's own frame when there is a body;
        //   B  ship -- the body the CANNON is in, taken from the muzzle position. Not the hull: a turret is
        //      a sub-body and moves relative to the hull, so keying on the hull would file two cannons that
        //      move relative to each other as one. Null means there is no body and localPos is world space;
        //   C  typeId + customName -- what kind of cannon it is, used only to check that a registered form
        //      fits this cannon. Not an identity.
            /**
             * A stable name for this cannon, for the tables that are shared with clients.
             *
             * <p>Built from the three parts the comments above call identity -- the dimension, the body, and the
             * position in the body's own frame -- and from nothing else. The type and the custom name are not
             * identity, and a name that included them would stop matching the moment a cannon was renamed.
             *
             * <p>Both sides build it by calling this, so a table written on the server and read on a client
             * cannot drift apart.
             */
            public String stableId() {
                return dimension.location() + "|" + ship + "|" + localPos.asLong();
            }
    }

    // ------------------------------------------------------------------------------------------------
    // Events.
    // ------------------------------------------------------------------------------------------------

    /**
     * Somebody fired. Dispatched where the weapon builds its own muzzle plume, which is the first point that
     * knows who fired, from where, in which direction, and how big the charge was.
     *
     * <p>Coordinates are <b>world space</b>, already transformed out of the contraption's own frame, because a
     * listener that draws or lights something cannot use contraption coordinates. {@code size} and {@code power}
     * are the two independent strength figures the shot already carries: {@code size} is the plume size and
     * {@code power} is the charge; both are the values this mod sends on the wire today.
     *
     * <p>{@code level} is here because this event is server side and a listener that acts has to act on a level;
     * deriving it from {@code owner} would make an optional field load bearing.
     *
     * <p>{@code source} is what the weapon is mounted on and {@code owner} is what fired it. For the four CBC
     * weapons those are the same contraption entity; they part company once a weapon can be held.
     */
    public record FireEvent(
            Level level,
            Object source,
            @Nullable SourceKey key,
            @Nullable Entity owner,
            Vec3 origin,
            Vec3 direction,
            WeaponKind kind,
            float size,
            float power,
            long flashToken,
            Spec spec,
            Cancellation cancellation) {}

    /**
     * Something exploded. {@code affectedBlocks} is the number of blocks the blast touched, which is what this
     * mod fits its effect size to — it is NOT a damage figure, and blast resistant blocks lower it by stopping
     * the blast's own rays.
     *
     * <p>{@code direction} is null for every explosion this mod produces today: a charge has no aim, and the
     * field is here for sources that do have one. {@code reach} is measured, not modelled -- the farthest
     * block the blast walked through in each of the eight octants, so it is the shape the blast actually had.
     *
     * <p>{@code yield} is the charge ({@code Explosion.radius()}), already passed through the fallback that
     * gives an explosion without one a floor value; {@code solidity} is how enclosed the charge was, derived
     * from the two. A listener that wants to size something should use these rather than the raw radius.
     */
    public record ImpactEvent(
            Level level,
            Vec3 center,
            @Nullable Vec3 direction,
            float yield,
            float solidity,
            float[] reach,
            int affectedBlocks,
            HitClass hitClass,
            int tier,
            @Nullable Entity source,
            @Nullable SourceKey key,
            Spec spec,
            Cancellation cancellation) {}

    /**
     * Hosts offered for decoration. Particles, entities and projectiles all arrive here.
     *
     * <p><b>Only hosts this mod did not create.</b> The offer exists for somebody else's things -- CBC's
     * plume smoke, CBC's shells, another mod's projectiles or particles -- because their owner is the one who
     * has to say they exist. What this mod spawns itself is not offered: a dust ring, a muzzle flash or one
     * of our own dust particles is ours to create and ours to manage, and a mod that wants to hang something
     * on the shot or the blast has the impact and fire events for that. An offer of our own particles would
     * also be circular: the offer would exist to let somebody decorate an effect we only make because we
     * decided to.
     *
     * <p>This is the offer: whoever spawns or tracks a host says so, once per tick per side, and anybody may
     * attach something to what was offered. It is also the only event that carries its {@code side} on the
     * event itself, because it is the one event that is produced on both sides -- the server offers what it
     * tracks, the client offers what it can see, and in single player both reach the same bus. A listener
     * must therefore look at {@code side} (or at {@code level().isClientSide()}) <em>and</em> at
     * {@code hostKind} before acting: the two producers this mod already has offer projectiles and particles
     * through the same event.
     *
     * <p>{@code group} says which continuing offer these hosts are: the same value for the same group of
     * hosts across ticks, and 0 when the offerer does not distinguish groups. It is the offerer's own id --
     * an opaque handle for correlating one tick's offer with the next, nothing more. It is not an entity id,
     * and it is deliberately <b>not</b> anything about what a listener attaches: a light that follows a
     * plume must not be the reason the plume can be offered at all, or the tail is wagging the dog, and a
     * group of hosts whose light does not exist could never be offered.
     *
     * <p>How long an attachment lives is the attachment's business, and it is said there: see
     * {@link ReferencedTarget#lifetimeTicks()} and {@link ReferencedTarget#followHost()}.
     *
     * <p>{@code selectors} is what this offer is meant to satisfy: the wanted hosts, as the producer
     * understood them. An empty list means "announcing hosts", which is what this mod's own offers do -- they
     * offer what they track for the other side to decide about, not on somebody's request.
     *
     * <p>Known gap, stated rather than faked: the {@link Selector} vocabulary cannot express "every host
     * offered" -- {@code Take(0, ...)} means none of them -- so this mod's own offers carry an empty
     * {@code referencedEntities} specification and its listeners act on all hosts. A producer that wants to
     * point at a subset has to wait for the vocabulary to grow a case for it.
     */
    public record TrackedHostEvent(
            Level level,
            HostKind hostKind,
            List<Host> hosts,
            List<Selector> selectors,
            Side side,
            long group,
            Spec spec,
            Cancellation cancellation) {}

    /** A projectile hit a block. The direction is in world space: the sender converts it before dispatch. */
    public record BlockHitEvent(
            Level level,
            Vec3 pos,
            Vec3 direction,
            boolean ricochet,
            BlockState blockState,
            EntityType<?> projectileType,
            @Nullable SourceKey key,
            Spec spec,
            Cancellation cancellation) {}

    /**
     * Terrain changed. Deliberately independent of {@link ImpactEvent}: turning effects off must not stop it.
     *
     * <p>{@code blocks} is the list the explosion is about to act on, in the game's own words: it is the live
     * list, valid for the duration of the dispatch only. Copy it if you keep it. It is here rather than a
     * radius because the far-LOD refresh needs to know <em>which columns</em> moved, not how far away they
     * were, and a radius would make it guess.
     */
    public record TerrainEvent(Level level, Vec3 center, double radius, List<BlockPos> blocks, Cause cause) {}

    /**
     * One offered host: enough to identify it and to place something on it, nothing more.
     *
     * <p>{@code previous} and {@code current} are the two ends of this tick's step, in world space:
     * anything drawn along the step interpolates between them, which is the whole reason both are here.
     * {@code motion} is the host's own velocity, which is not the same thing as the step -- a host on a
     * moving physics body has a step that includes the body -- and a trail whose frames are thrown
     * backwards along the host's own motion cannot be reproduced without it. A particle has no body, so for
     * a particle host the motion is its step.
     *
     * <p>{@code size} and {@code heat} are how a host takes part in an aggregate: {@code size} is its own
     * extent in blocks and {@code heat} is the weight it carries there, 0..1. A host that does not
     * participate -- every projectile, so far -- reports zero for both, and a binding that sums an aggregate
     * skips hosts whose heat is not positive. {@code id} is 0 for a host the offerer cannot identify, which
     * is the case for particles: Minecraft gives them no id.
     */
    public record Host(long id, Vec3 previous, Vec3 current, Vec3 motion, double size, double heat,
                       boolean tracer) {}

    public enum Side { CLIENT, SERVER }

    public enum HostKind { PARTICLE, ENTITY, PROJECTILE }

    public enum WeaponKind { CANNON, TANK_CANNON, AUTOCANNON, TWIN_AUTOCANNON }

    /**
     * How an explosion was classified, computed by this mod so every consumer agrees.
     *
     * <p>An explosion only ever produces the first three. {@code BLOCK} and {@code ENTITY} are reserved for
     * impacts that are not explosions, so that a listener can read one enum whichever event it is handling.
     */
    public enum HitClass { AIR, GROUND, BURIED, BLOCK, ENTITY }

    public enum Cause { EXPLOSION, PROJECTILE, OTHER }

    /**
     * Event level cancel switch, carried by the event itself.
     *
     * <p>This has to be mutable, and that is not an accident: a listener's whole job is to change what happens
     * next, and an immutable record holding a boolean could never be cancelled by anyone. Every event carries
     * a fresh switch, so cancelling one explosion never touches another; a listener calls
     * {@link #cancel(Section)} or {@link #cancelAll()} and the dispatcher stops the appropriate listeners.
     *
     * <p>Granularity is per spec section, not all or nothing, and one section deeper for a named child -- so a
     * listener can replace the light and leave the dust alone.
     */
    public static final class Cancellation {
        private boolean all;
        private final java.util.Set<Section> sections = java.util.EnumSet.noneOf(Section.class);
        private final java.util.Set<String> children = new java.util.HashSet<>();
        private @Nullable java.util.Set<java.util.UUID> muzzleSmokeRecipients;

        /** Opt in before dispatching FireEvent. Callers keep their own fallback for unhandled viewers. */
        public void requestMuzzleSmokeTakeover() {
            if (muzzleSmokeRecipients == null) muzzleSmokeRecipients = new java.util.HashSet<>();
        }

        public boolean muzzleSmokeTakeoverRequested() { return muzzleSmokeRecipients != null; }

        /** Query after dispatch, separately for each viewer. This does not cover sound, light or blast. */
        public boolean muzzleSmokeHandled(java.util.UUID viewer) {
            return muzzleSmokeRecipients != null && muzzleSmokeRecipients.contains(viewer);
        }

        /** Called by the smoke provider after it delivers its replacement. */
        public void acknowledgeMuzzleSmoke(java.util.UUID viewer) {
            if (muzzleSmokeRecipients != null) muzzleSmokeRecipients.add(viewer);
        }

        /** A fresh, uncancelled switch. Every event needs its own; never share one between events. */
        public static Cancellation none() {
            return new Cancellation();
        }

        /** Stand down entirely: no listener after this one runs at all. */
        public void cancelAll() {
            all = true;
        }

        /** Stand down one section, leaving the rest of the event to the other listeners. */
        public void cancel(Section section) {
            if (section == null) cancelAll();
            else sections.add(section);
        }

        /** Stand down one named child of one section, e.g. the ring but not the sphere. */
        public void cancel(Section section, ResourceLocation child) {
            if (section == null || child == null) cancelAll();
            else children.add(key(section, child));
        }

        /** Whether the whole event has been cancelled. */
        public boolean cancelled() {
            return all;
        }

        /**
         * Whether a listener that does {@code what} has to stand down.
         *
         * <p>A whole event cancellation blocks every section; a section cancellation blocks only that one, so
         * one listener replacing the light does not stop another from adding dust. Passing {@code null} asks
         * the other question -- "is the whole event cancelled" -- which is what the dispatcher checks between
         * listeners.
         */
        public boolean blocks(@Nullable Section what) {
            if (all) return true;
            return what != null && sections.contains(what);
        }

        /** The same question one level down: is this named child of this section switched off? */
        public boolean blocks(Section section, ResourceLocation child) {
            if (blocks(section)) return true;
            return section != null && child != null && children.contains(key(section, child));
        }

        /** For a debug line, so "nothing happened" can be told from "somebody cancelled it". */
        public String describe() {
            if (all) return "all";
            if (sections.isEmpty() && children.isEmpty()) return "none";
            var parts = new java.util.ArrayList<String>();
            for (Section section : sections) parts.add(section.name());
            parts.addAll(children);
            return String.join(",", parts);
        }

        private static String key(Section section, ResourceLocation child) {
            return section.name() + "/" + child;
        }
    }

    public enum Section { LIGHT, SPAWN_PARTICLES, REFERENCED_PARTICLES, REFERENCED_ENTITIES, CUSTOM }

    // ------------------------------------------------------------------------------------------------
    // Specification.
    // ------------------------------------------------------------------------------------------------

    /**
     * What a weapon wants. Every field is optional; a missing one inherits.
     *
     * <p>Produced today from code (this mod's own defaults, or a per instance profile resolved from
     * {@link SourceKey}); later from data files, feeding the very same records.
     */
    public record Spec(
            @Nullable LightRef light,
            Map<ResourceLocation, SpawnParticle> spawnParticles,
            Map<ResourceLocation, ReferencedTarget> referencedParticles,
            Map<ResourceLocation, ReferencedTarget> referencedEntities,
            Map<ResourceLocation, Object> custom) {}

    /**
     * Which light to use, and how much of it.
     *
     * <p>{@code scale} multiplies the strength the preset derives from the event; 1.0 is "exactly what the
     * preset says" and is what this mod's own defaults ask for. It is a brightness multiplier only -- the
     * shape and reach of the light are the preset's, and stay in code where they can still be tuned.
     */
    public record LightRef(ResourceLocation preset, double scale, @Nullable LightBudget budget) {}

    /**
     * Lights are a capped GPU resource, so they need a quota or one listener can starve the rest.
     *
     * <p>Honoured by class today, which is the one thing a client can see without a wire field: the muzzle
     * flash, the explosion, a shell in flight and the player's own torch are HIGH and are served first when
     * the pool is oversubscribed, lamps and other players' lights are NORMAL, and decoration is LOW. A light
     * added by another mod arrives through one of those classes until the priority can travel with a light.
     * {@code maxLights} is a declaration with the same limitation: it cannot be enforced against a light that
     * never carried it.
     */
    public record LightBudget(int maxLights, Priority priority) {}

    /** Weakest first: the rank a class is served in is this enum's order, pinned by a test. */
    public enum Priority { LOW, NORMAL, HIGH }

    /** A particle this mod spawns. */
    public record SpawnParticle(Anchor anchor, ParticleSpec particle, double rate, int lifetimeTicks) {}

    /**
     * A host somebody else spawned, selected and decorated.
     *
     * <p>{@code lifetimeTicks} is how long the attachment lasts, and 0 means "as long as the hosts are
     * offered": the attachment stops when the offer for its group stops coming. That is where "the light
     * dies with the plume" belongs -- on the thing we attach, not on the hosts we attach it to.
     * {@code followHost} says whether the attachment moves with the host or stays where it was made.
     */
    public record ReferencedTarget(Selector selector, Binding binding, int lifetimeTicks, boolean followHost) {}

    /**
     * Where something is spawned. The axis that used to be implicit, which is why the existing call sites all
     * look different from each other.
     */
    public record Anchor(AnchorKind kind, Vec3 point, @Nullable ResourceLocation region, double margin,
                         int maxPoints) {}

    public enum AnchorKind {
        /** A fixed world point (from the event that triggered the effect). */
        EVENT_POINT,
        /** Sampled over the surface of a physics body: hull dust, engine smoke. */
        BODY_SURFACE,
        /** A named attachment on a physics body. */
        BODY_ATTACH,
        /** Somewhere on an entity or projectile. */
        HOST,
        /** The muzzle of the weapon that fired. */
        FIRE_PLUME,
        /** The centre of an impact. */
        IMPACT_CENTRE,
        /** The point where a projectile met a block. */
        BLOCK_HIT
    }

    public record ParticleSpec(ResourceLocation type, double size, double speed, double spread,
                               @Nullable Integer color, @Nullable Integer lifetimeTicks) {}

    /** Which existing hosts to pick. Only meaningful for {@code referenced*}. */
    public record Selector(List<ResourceLocation> types, boolean unclaimed, Vec3 around, double radius,
                           int spawnedWithinTicks, Take take) {}

    public record Take(int count, Pick pick, boolean claim) {}

    /** Deliberately small for the first version; the vocabulary grows by adding values, not by changing shape. */
    public enum Pick { NEAREST, RANDOM, HOT_CENTROID }

    /** What to attach to the picked hosts. */
    public record Binding(BindingKind kind, @Nullable ResourceLocation preset, Map<String, Object> params) {}

    public enum BindingKind { POINT_LIGHT, HOT_CENTROID_LIGHT, TRAIL, TRACER }

    // ------------------------------------------------------------------------------------------------
    // Presets: chosen by id, defined in code, extensible.
    // ------------------------------------------------------------------------------------------------

    /**
     * A light, by name only.
     *
     * <p>Deliberately opaque: the real thing carries a dozen shape and curve parameters (front, rear, width,
     * HEIGHT, cutoff, core, middle, tail, minimum, decay, reference, cap, duration, hold), and keeping them in
     * code is the whole point -- exposing them would freeze geometry this mod still wants to tune. Consumers
     * choose an id; this mod decides what that id looks like.
     */
    public record LightPreset(ResourceLocation id) {}

    /** Tier table and count formula of the dust ring. Kept in code for the same reason. */
    public record RingPreset(ResourceLocation id, List<Tier> tiers) {}

    public record Tier(int minBlocks, int maxBlocks, double speedBase, double speedFactor, double countFactor,
                       int maxCount, double vyRange, double speedVariance, int totalTicks,
                       int countMultiplier) {}
}
