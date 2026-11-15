package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.HostileSmokeWeapon;
import dev.sablelightoverhaul.ServerFlashes;
import dev.sablelightoverhaul.ShockwavePayload;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.lighting.LightingCommon;
import dev.sablelightoverhaul.voxy.VoxyLodNudger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * This mod's own effects, expressed as listeners.
 *
 * <p>The point of the exercise is that "our muzzle flash" and "a third party's muzzle flash" are the same kind
 * of thing: registered at {@link EffectBus#DEFAULT_PRIORITY}, running after anyone who wants to run first, and
 * cancellable section by section. This class is therefore the only place left that knows how this mod draws an
 * event, and it is deliberately thin -- it does not measure anything, it does not look at the world, it just
 * forwards the event to the code that already did the drawing.
 *
 * <p>Registration order matters at equal priority -- the bus keeps it -- so the light is registered before the
 * shockwave, which is the order the two were sent in when one method did both.
 *
 * <p>Registration is guarded and idempotent: installing twice would draw everything twice.
 */
public final class DefaultListeners {
    private DefaultListeners() {}

    private static boolean installed;

    /** Called once, from the mod constructor, on both sides. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        EffectApi.onFire(EffectBus.DEFAULT_PRIORITY, DefaultListeners::muzzleFlash);
        EffectApi.onFire(EffectBus.DEFAULT_PRIORITY, HostileMuzzleSmoke::send);
        EffectApi.onImpact(EffectBus.DEFAULT_PRIORITY, DefaultListeners::blastLight);
        EffectApi.onImpact(EffectBus.DEFAULT_PRIORITY, DefaultListeners::shockwave);
        // Terrain is not an effect, so it has no switch of its own beyond the LOD feature's: it is
        // registered whatever the explosion light is set to.
        EffectApi.onTerrainChanged(EffectBus.DEFAULT_PRIORITY, DefaultListeners::lodRefresh);
    }

    /**
     * The muzzle flash: one light, sent to every player within range of the muzzle.
     *
     * <p>Everything the flash needs is in the event -- where, which way, how big, and how strong -- so this is
     * a straight call. What it does <em>not</em> do is decide whether the mod is enabled: the server side switch
     * still stands in front of the tank cannon's whole effect group, and the client still decides for itself
     * whether to draw an incoming flash.
     */
    /** Set once: one line that says what the event calls the shooter, and whether its mount was captured. */
        private static boolean LOGGED_SHOT_SOURCE;
    /** Set once: which of the four steps in mountKeyFor gave up. */
    private static boolean LOGGED_KEY_STEPS;
    /** Set once: whether the mount table learned anything, which is what a client needs published. */
    private static boolean LOGGED_MOUNT_NEWS;
        private static void muzzleFlash(EffectApi.FireEvent event) {
        if (event.cancellation().blocks(EffectApi.Section.LIGHT)) return;
        Entity owner = event.owner();
        // The mount is the cannon's owner in every sense that matters: it assembled the cannon, it has a definite
            // coordinate wherever it stands, and it is the identity a form page files the cannon's values under.
            long mountPos = Long.MIN_VALUE;
            if (event.source() instanceof rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity contraption
                    && contraption.getController() instanceof rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption.Block block
                    && event.level() instanceof net.minecraft.server.level.ServerLevel server) {
                var mount = block.getControllerBlockPos();
                mountPos = mount.asLong();
                var mountKey = mountKeyFor(server, event, event.key());
                if (mountKey == null) {
                    com.mojang.logging.LogUtils.getLogger().info(
                            "Sable Light Overhaul: shot had a mount at {} but no cannon key", mount);
                } else {
                    boolean news = dev.sablelightoverhaul.lighting.MountIndex.register(
                            server.dimension().location(), mount, mountKey);
                    // News: this mount was not in the table, so the clients are given it now instead of at the
                    // next opportunity. Once per mount, and the alternative is a few shots aimed by preset.
                    //
                    // The publish hangs off the *first* register call. It used to be decided by a second call,
                    // and a second call can only ever answer false -- the mount is in the table by then -- so
                    // nothing was ever sent and every client lookup missed.
                    if (news) {
                        for (var player : server.players()) dev.sablelightoverhaul.lighting.ProfileSync.send(player);
                    }
                    if (!LOGGED_MOUNT_NEWS) {
                        LOGGED_MOUNT_NEWS = true;
                        com.mojang.logging.LogUtils.getLogger().info(
                                "Sable Light Overhaul: mount table {} for {} ({})",
                                news ? "learned a new mount" : "already knew the mount", mount, mountKey.stableId());
                    }
                }
            }
            if (!LOGGED_SHOT_SOURCE) {
                // The one thing that could not be worked out from the reading: what the event calls the shooter.
                // If the mount is not captured here, the client can never name the cannon, and this says why.
                LOGGED_SHOT_SOURCE = true;
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul: shot source is {} mount captured={}",
                        event.source() == null ? "null" : event.source().getClass().getName(),
                        mountPos != Long.MIN_VALUE);
            }
            var key = event.key();
            boolean hostile = key != null && key.typeId().getNamespace().equals("sablehostile");
            int emitter = hostile ? key.stableId().hashCode() | Integer.MIN_VALUE : owner == null ? 0 : owner.getId();
            boolean halfSizeFireball = hostile && key.typeId().getPath().equals(HostileSmokeWeapon.TWIN_76.path());
            ServerFlashes.emitSized(event.level(), event.origin(), event.direction(), flashKind(event.kind()),
                emitter, event.origin(), event.power(), event.size(), profileFor(event), mountPos,
                !HostileMuzzleSmoke.requested(event), halfSizeFireball);
    }

    /**
     * The explosion light: two stacked lights, whose brightness and size follow what the blast measured
     * rather than a constant per tier -- an airburst is bright over the radius it actually filled, a buried
     * shot is dimmer over the reach it actually managed.
     *
     * <p>The light has its own switch, separate from the shockwave's. They were one switch until the two
     * costs were looked at: a light is cheap, while one payload becomes a great many particles on every
     * client that receives it, so "no light" and "no shockwave" are different amounts of saving and deserve
     * to be asked separately.
     */
    private static void blastLight(EffectApi.ImpactEvent event) {
        // An impact is dispatched on the side the blast happened on, and the client dispatches its own from
        // the payload it received: in single player both reach this one bus, so every listener says which
        // side it is for. This one draws for the server, because that is where the flash is measured.
        if (event.level().isClientSide()) return;
        if (event.cancellation().blocks(EffectApi.Section.LIGHT)) return;
        if (!LightingCommon.EXPL_LIGHT_ENABLED.get()) return;
        if (!ImpactClassifier.worthDrawing(event)) return;
        // The specification travels with the event, so a per weapon override arrives here as a brightness
        // multiplier; our own default is 1.0 and this is a no-op for it.
        double scale = event.spec() == null || event.spec().light() == null ? 1.0 : event.spec().light().scale();
        double span = Math.max(1.0, ShockwavePayload.reachMean(event.reach()));
        double exposure = 1.0 - event.solidity();
        double charge = LightingCommon.EXPL_CHARGE.get()
                * (event.yield() / ShockwavePayload.DEFAULT_YIELD) * (0.35 + 0.65 * exposure) * scale;
        double size = span * LightingCommon.EXPL_SIZE_SCALE.get();
        int emitter = event.source() == null ? -1 : event.source().getId();
        Vec3 center = event.center();
        // Two lights stacked up the column: one at the centre reads as a point, while a blast also lights
        // the ground it goes off on and the smoke rising above it.
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        ServerFlashes.emitSized(event.level(), center, up, 0, emitter, center, (float) charge, (float) size);
        Vec3 above = center.add(0.0, 2.5, 0.0);
        ServerFlashes.emitSized(event.level(), above, up, 0, emitter, above,
                (float) (charge * 0.6), (float) (size * 0.6));
    }

    /**
     * The shockwave: not drawn here, because the ring of dust and the sphere are built on the client from what
     * the server measured. So the server's job is to tell the players who could see it, and the client's job
     * is to decide which of the two shapes to draw -- that choice is a rendering preference of each player and
     * cannot be made here.
     *
     * <p>One consequence worth stating: because this is a single message, a listener that cancels one named
     * child of {@code SPAWN_PARTICLES} -- the ring but not the sphere -- cannot be honoured from this side.
     * Cancelling the whole section is.
     */
    private static void shockwave(EffectApi.ImpactEvent event) {
        if (event.level().isClientSide()) return;
        if (event.cancellation().blocks(EffectApi.Section.SPAWN_PARTICLES)) return;
        if (!LightingCommon.EXPL_SHOCKWAVE_ENABLED.get()) return;
        if (!ImpactClassifier.worthDrawing(event)) return;
        if (!(event.level() instanceof ServerLevel level)) return;
        int emitter = event.source() == null ? -1 : event.source().getId();
        ShockwavePayload payload = new ShockwavePayload(level.dimension().location(),
                event.center().x, event.center().y, event.center().z, event.yield(), event.solidity(),
                event.tier(), event.reach(), event.affectedBlocks(),
                ShockwavePayload.toBlastClass(event.hitClass()), emitter);
        double range = LightingCommon.SHOCKWAVE_RANGE.get();
        for (var player : level.players()) {
            if (player.distanceToSqr(event.center()) > range * range) continue;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * The far LOD refresh. It is a listener rather than a statement inside the explosion handler because it is
     * the one part of an explosion that is not an effect: it has to run with the effects switched off, and its
     * threshold is about how much terrain moved, not about how bright anything is.
     */
    private static void lodRefresh(EffectApi.TerrainEvent event) {
        if (event.level().isClientSide()) return;
        if (!(event.level() instanceof ServerLevel level)) return;
        if (event.blocks().size() < LightingCommon.VOXY_MIN_BLOCKS.get()) return;
        VoxyLodNudger.queue(level, event.center(), event.blocks());
    }

    /**
     * The wire carries an int kind, not the enum, and that int is what the client picks the light shape from:
     * 0 is the big cannon, 1 the autocannon family, 2 the tank cannon. This is the only place the two
     * vocabularies meet, so a new weapon kind has to be added here as well as to the enum.
     */
    public static int flashKind(EffectApi.WeaponKind kind) {
        return switch (kind) {
            case CANNON -> 0;
            case AUTOCANNON -> 1;
      case TWIN_AUTOCANNON -> 3;
            case TANK_CANNON -> 2;
        };
    }

    /**
     * The form this shot's cannon is configured with, or null.
     *
     * <p>Read here because this is the last place that has the event: the source key is what identifies the
     * cannon, and the flash carries the answer onwards. Every failure is silent by design -- no store, no
     * entry, a value that is not a number -- because an unconfigured cannon is the normal case and a shot must
     * never be lost to a configuration problem.
     *
     * <p>Absent numbers are NaN rather than the local default, and that is deliberate: only the client knows
     * what its own configuration says, so a form that sets one value must leave the others exactly as the
     * client would have had them, not substitute a number the server made up.
     */
    private static float[] profileFor(EffectApi.FireEvent event) {
        try {
            // Off by default while the configuration itself is being checked: with it off every shot behaves
            // exactly as the config files say and no stored form is consulted. Read directly, not through
            // LightingCommon.on, which ORs its argument with the diagnostics master switch.
            if (!dev.sablelightoverhaul.lighting.LightingCommon.PROFILES_ENABLED.get()) {
                return null;
            }
            if (!(event.level() instanceof ServerLevel server)) return null;
            // The key is its own field. This used to pattern-match the event's source, which is the thing the
            // weapon is mounted on -- an entity -- so the test never matched once and no form was ever applied.
            var shot = event.key();
            if (shot == null) return null;
            // The page wrote under the MOUNT's identity: the mount's own position inside its body. The shot's
            // key is the muzzle's, and a muzzle is not a mount, so the mount is found the same way the wrench
            // finds it -- same frame rule, same ten steps.
            var mountKey = mountKeyFor(server, event, shot);
            if (mountKey == null) return null;
            // The form is filed under the kind of shot this is, which the event carries, so nothing is guessed.
            var kindId = dev.sablelightoverhaul.profiles.ProfileKinds.id(event.kind());
            var values = dev.sablelightoverhaul.profiles.ProfileData.get(server).store()
                    .resolve(mountKey, kindId);
            if (values.isEmpty()) {
                // The owner's rule: keep the client's own configuration, and say so once.
                remind(server, event, mountKey, kindId);
                return null;
            }
            var names = dev.sablelightoverhaul.FlashPayload.PROFILE_SLOTS_ARE.split(", ");
            var slots = new float[dev.sablelightoverhaul.FlashPayload.PROFILE_SLOTS];
            java.util.Arrays.fill(slots, Float.NaN);
            for (int slot = 0; slot < slots.length && slot < names.length; slot++) {
                Double value = values.get().get(names[slot]);
                if (value != null) slots[slot] = value.floatValue();
            }
            return slots;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    /**
    /** Cannons already told that a kind has no form, so a reminder is said once per cannon and kind. */
    private static final java.util.Set<String> REMINDED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Says once that this cannon has no form for the kind of shot it just fired.
     *
     * <p>Silent when the cannon has never been configured at all, because then there is nothing to remind
     * anyone about -- an unconfigured cannon firing with its defaults is the ordinary case. It also names the
     * kinds the cannon does have, since the reason a shot finds nothing is almost always that the form was
     * written for a different kind.
     */
    private static void remind(ServerLevel server, EffectApi.FireEvent event, EffectApi.SourceKey key,
                               net.minecraft.resources.ResourceLocation kindId) {
        try {
            var all = dev.sablelightoverhaul.profiles.ProfileData.get(server).store().allFor(key);
            if (all.isEmpty() || all.containsKey(kindId)) return;
            if (REMINDED.size() > 1024) REMINDED.clear();
            if (!REMINDED.add(key.dimension() + "|" + key.ship() + "|" + key.localPos() + "|" + kindId)) return;
            var player = controlling(server, event);
            if (player == null) return;
            var names = net.minecraft.network.chat.Component.empty();
            for (var id : all.keySet()) {
                var known = dev.sablelightoverhaul.profiles.ProfileKinds.of(id).orElse(null);
                if (known == null) continue;
                if (!names.getString().isEmpty()) names.append(net.minecraft.network.chat.Component.literal("、"));
                names.append(net.minecraft.network.chat.Component.translatable(
                        "sablelightoverhaul.profile.kind." + known.name()));
            }
            var which = dev.sablelightoverhaul.profiles.ProfileKinds.of(kindId).orElse(null);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "sablelightoverhaul.profile.reminder",
                    which == null ? net.minecraft.network.chat.Component.literal("?")
                            : net.minecraft.network.chat.Component.translatable(
                                    "sablelightoverhaul.profile.kind." + which.name()),
                    names.getString().isEmpty()
                            ? net.minecraft.network.chat.Component.translatable("sablelightoverhaul.profile.reminder.none")
                            : names), false);
        } catch (RuntimeException ignored) {
            // A reminder is never worth an exception in the middle of a shot.
        }
    }

    /**
     * Who to tell: the player who fired, the one controlling what they fired from, or whoever is nearest.
     *
     * <p>Asked by reflection because the method that answers it lives on Create's contraption base class, and
     * this project compiles against CBC and Sable but not Create. The class is there at runtime, so the call
     * works; a project that cannot see a type should not have to name it. When even that fails the nearest
     * player within a few dozen blocks is told, which is who a cannon's crew is in practice.
     */
    private static net.minecraft.world.entity.player.Player controlling(ServerLevel server,
                                                                       EffectApi.FireEvent event) {
        if (event.owner() instanceof net.minecraft.world.entity.player.Player player) return player;
        if (event.owner() != null) {
            try {
                var answer = event.owner().getClass().getMethod("getControllingPlayer").invoke(event.owner());
                if (answer instanceof net.minecraft.world.entity.player.Player player) return player;
            } catch (ReflectiveOperationException | RuntimeException notThisContraption) {
                // Not a contraption, or a build without that method: fall through to the nearest player.
            }
        }
        var where = event.origin();
        return server.getNearestPlayer(where.x, where.y, where.z, 48.0, false);
    }

    /**
     * The identity the page would have written under, found from where the shot came out.
     *
     * <p>Null when there is no mount within reach, which is the ordinary case for a shot from something this
     * mod does not know about and simply means the client keeps its own configuration.
     */
    private static EffectApi.SourceKey mountKeyFor(ServerLevel server, EffectApi.FireEvent event,
                                                   EffectApi.SourceKey shot) {
        // The mount is the cannon's controller block -- the same coordinate the flash handler captures and the
        // same identity a form is filed under. This used to search for a mount block near the muzzle, and for a
        // cannon on a Sable body that search comes back empty: the frame loses the body, so the key was dropped
        // and every shot was filed under no cannon at all.
        if (!(event.source() instanceof rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity contraption)) return null;
        if (!(contraption.getController() instanceof rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption.Block block)) return null;
        var mount = block.getControllerBlockPos();
        if (mount == null) return null;
        var blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(server.getBlockState(mount).getBlock());
        // blockId is a label, not part of the identity: stableId is dimension|body|position. Returning null
        // over a missing registry key is what silently killed every mount key this mod ever built.
        // The mount's position in its own body, which is what the page stored. Capturing it again is what
        // makes a shot and a click land on the same identity rather than on two nearby ones.
// Sable's sub-levels sit at a fixed, very distant coordinate and are projected to the player, so the world
// position of a mount block is a projection and not an identity. The captured anchor -- the body plus the
// position inside it -- is the identity, which is why the page has always used it.
        var mountKey = new EffectApi.SourceKey(shot.dimension(), null, mount, blockId, shot.customName());
        if (!LOGGED_KEY_STEPS) {
            LOGGED_KEY_STEPS = true;
            // The mount block's own position, and the identity built from it. A Sable body lives at a fixed,
            // very distant coordinate -- the log shows x around twenty million -- and what looks like the ship
            // moving is a projection of that body to the player, which is why this position is the stable one.
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: mount key probe -- mount {} | block {} | stableId {}",
                    mount, blockId, mountKey.stableId());
        }
        return mountKey;
    }}
