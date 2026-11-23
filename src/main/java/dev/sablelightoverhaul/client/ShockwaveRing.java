package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.BlastVisualScale;

import dev.sablelightoverhaul.ShockwavePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;

/**
 * The KubeJS ring, ported: one expanding disc of three kinds of particle, thrown outward from the blast.
 *
 * <p>The original is a client script that receives a block count with the explosion's position and then
 * bursts a mixture of <b>createbigcannons:shrapnel_smoke</b> (weight 1), <b>minecraft:cloud</b> (2) and
 * <b>minecraft:end_rod</b> (2) outward in the horizontal plane, with a tier table deciding the speed, the
 * count and how much the ring spreads vertically. All of its numbers are here unchanged -- see {@link Tier}
 * -- because the reason this exists is that those numbers look right, and re-deriving them would be a
 * second guess at something already tuned in game.
 *
 * <p>What the mod adds is the part a script could not do well: <b>where the charge went off</b>.
 * {@link ShockwavePayload.BlastClass#AIR} draws nothing at all (a disc of dust hanging in the sky is
 * wrong), {@link ShockwavePayload.BlastClass#BURIED} draws only the body of the ring and not the cloud and
 * spark rings around it, and <a>GROUND</a> draws the lot.
 *
 * <p>Spawning is exactly what the script did -- {@code level.addParticle(..., true, ...)} in the client's
 * own tick, so nothing goes over the network -- and the batches are spread over one or two ticks as the
 * script spread them.
 */
public final class ShockwaveRing {
    private ShockwaveRing() {}

    /**
     * One tier of the original script, its numbers untouched except the lowest floor: the script ignored
     * anything below 550 blocks, which left lighter shells unrecognised, and the mod's own explosion gate is
     * 200, so the first tier starts there.
     */
    public record Tier(int minBlocks, int maxBlocks, double speedBase, double speedFactor, double countFactor,
                int maxCount, double vyRange, double speedVariance, int totalTicks, int countMultiplier) {}

    /** Baked from the owner's config: 0.5, so the tier formulas above stay the script's own numbers. */
    private static final double RING_BASE_SCALE = 0.5;

    public static final Tier[] TIERS = {
            new Tier(200, 2000, 0.3, 0.0008, 0.25, 200, 0.1, 0.1, 1, 1),
            new Tier(2000, 18000, 0.5, 0.00025, 0.125, 300, 0.5, 0.1, 2, 3),
            new Tier(18000, Integer.MAX_VALUE, 0.8, 0.0002725, 0.125, 400, 1.0, 0.4, 2, 6)
    };

    /** The tier a block count falls in, or null when it is below the first one. */
    public static Tier tierFor(int blocks) {
        for (Tier tier : TIERS) if (blocks >= tier.minBlocks() && blocks < tier.maxBlocks()) return tier;
        return null;
    }

    /** How fast the ring's particles leave, in blocks per tick: the script's formula, scale included. */
    public static double speedFor(Tier tier, int blocks, double scale) {
        return (tier.speedBase() + blocks * tier.speedFactor()) * scale;
    }

    /** The same with the config's scale, for the spawner. */
    static double speedFor(Tier tier, int blocks) {
        return speedFor(tier, blocks, ExtraConfig.SHOCKWAVE_RING_SPEED_SCALE.get());
    }

    /** How many particles one burst is made of; the script's own formula, floor included. */
    public static int countFor(Tier tier, int blocks) {
        double count = Math.min(blocks * tier.countFactor(), tier.maxCount()) * tier.countMultiplier();
        return (int) Math.max(count, 10);
    }

    /** One kind of particle with its weight, parsed once per burst from the config. */
    private record Mixture(ParticleOptions options, double weight) {}

    /** One burst, part way through its batches. */
    /** One burst, part way through its batches. Throttle factors are decided once, when it is created. */
    private static final class Burst {
        final double x, y, z, speed, vyRange, speedVariance;
        final int batchSize;
        final List<Mixture> core, ring, clouds, endRods, others;
        final double coreWeight, ringWeight, totalWeight, cloudWeight, endRodWeight;
        final int live;
        final double throttleCloud, throttleEndRod;
        int remaining;
        Burst(double x, double y, double z, double speed, double vyRange, double speedVariance, int batchSize,
              int count, List<Mixture> core, List<Mixture> ring) {
            this.remaining = count;
            this.x = x; this.y = y; this.z = z;
            this.speed = speed; this.vyRange = vyRange; this.speedVariance = speedVariance;
            this.batchSize = batchSize;
            this.core = core; this.ring = ring;
            this.coreWeight = total(core); this.ringWeight = total(ring);
            this.totalWeight = this.coreWeight + this.ringWeight;
            // Only the two vanilla kinds are throttled; anything else the config names is left alone, so a
            // modded particle never silently loses its share.
            this.clouds = new ArrayList<>();
            this.endRods = new ArrayList<>();
            this.others = new ArrayList<>();
            for (Mixture m : ring) {
                if (m.options().getType() == net.minecraft.core.particles.ParticleTypes.CLOUD) clouds.add(m);
                else if (m.options().getType() == net.minecraft.core.particles.ParticleTypes.END_ROD) endRods.add(m);
                else others.add(m);
            }
            this.cloudWeight = total(clouds);
            this.endRodWeight = total(endRods);
            // Read once per explosion, which is the whole point: the throttle slows the approach to the cap,
            // so it only has to be right at the moment the particles are about to be created.
            this.live = liveParticles();
            double horizontal = 0.0;
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null) {
                double dx = x - minecraft.player.getX();
                double dz = z - minecraft.player.getZ();
                horizontal = Math.sqrt(dx * dx + dz * dz);
            }
            this.throttleCloud = cloudFactor(this.live, horizontal);
            this.throttleEndRod = endRodFactor(this.live);
        }
        double ringShare() { return totalWeight <= 0.0 ? 0.0 : ringWeight / totalWeight; }
        double cloudShare() { return ringWeight <= 0.0 ? 0.0 : cloudWeight / ringWeight; }
        double endRodShare() { return ringWeight <= 0.0 ? 0.0 : endRodWeight / ringWeight; }
        private static double total(List<Mixture> list) {
            double sum = 0.0;
            for (Mixture m : list) sum += m.weight;
            return sum;
        }
    }

    /** Every particle the client is carrying right now, across all layers. Read once per explosion. */
    static int liveParticles() {
        net.minecraft.client.particle.ParticleEngine engine =
                net.minecraft.client.Minecraft.getInstance().particleEngine;
        if (engine == null) return 0;
        int total = 0;
        for (java.util.Queue<net.minecraft.client.particle.Particle> queue
                : ((dev.sablelightoverhaul.mixin.ParticleEngineAccessor) engine).slo$particles().values()) {
            total += queue.size();
        }
        return total;
    }

    private static final List<Burst> PENDING = new ArrayList<>();

    // ------------------------------------------------------------------------------------------------
    // Throttling: how many particles a burst may spray, given what the client is already carrying. The
    // thresholds are relative to the engine cap of 16384 per layer; the goal is to slow the approach to it,
    // not to hold a target, and the occlusion cull handles the rest.
    // ------------------------------------------------------------------------------------------------

    static final int THROTTLE_START = 4096;
    static final int THROTTLE_END = 12288;
    static final double CLOUD_NEAR_DISTANCE = 256.0;
    static final double CLOUD_FAR_DISTANCE = 384.0;
    static final double CLOUD_NEAR_RATIO = 0.5;
    static final double CLOUD_FAR_RATIO = 0.25;
    static final int CLOUD_LIFETIME_TICKS = 10;

    /** 0 at the start count, 1 at the end count, and a straight line in between. */
    static double ramp(int live) {
        if (live <= THROTTLE_START) return 0.0;
        if (live >= THROTTLE_END) return 1.0;
        return (live - THROTTLE_START) / (double) (THROTTLE_END - THROTTLE_START);
    }

    /**
     * Cloud's share of its normal count. Five equal steps, interpolated inside each one rather than switched,
     * so a change in the live count moves the density smoothly: cloud 1 -> 3/4, then end rod 1 -> 3/4, then
     * cloud 3/4 -> 1/2, then end rod 3/4 -> 1/2, then cloud 1/2 -> 0. At the far end cloud is gone and end
     * rod keeps half. Distance only ever takes cloud further down, never up.
     */
    static double cloudFactor(int live, double horizontalDistance) {
        double t = ramp(live);
        double byCount;
        if (t <= 0.2) byCount = lerp(1.0, 0.75, t / 0.2);
        else if (t <= 0.4) byCount = 0.75;
        else if (t <= 0.6) byCount = lerp(0.75, 0.5, (t - 0.4) / 0.2);
        else if (t <= 0.8) byCount = 0.5;
        else byCount = lerp(0.5, 0.0, (t - 0.8) / 0.2);
        double byDistance = horizontalDistance > CLOUD_FAR_DISTANCE ? CLOUD_FAR_RATIO
                : (horizontalDistance > CLOUD_NEAR_DISTANCE ? CLOUD_NEAR_RATIO : 1.0);
        return Math.min(byCount, byDistance);
    }

    /** End rod's share: 1 -> 3/4 in the second step, 3/4 -> 1/2 in the fourth, and half from then on. */
    static double endRodFactor(int live) {
        double t = ramp(live);
        if (t <= 0.2) return 1.0;
        if (t <= 0.4) return lerp(1.0, 0.75, (t - 0.2) / 0.2);
        if (t <= 0.6) return 0.75;
        if (t <= 0.8) return lerp(0.75, 0.5, (t - 0.6) / 0.2);
        return 0.5;
    }

    private static double lerp(double from, double to, double at) {
        double clamped = Math.max(0.0, Math.min(1.0, at));
        return from + (to - from) * clamped;
    }

    /** A new explosion: queue its ring, or queue nothing at all when it went off in the air. */
    static void burst(ClientLevel level, dev.sablelightoverhaul.api.EffectApi.ImpactEvent event) {
        if (!ExtraConfig.SHOCKWAVE_RING_ENABLED.get()) {
            if (ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul ring: ringEnabled is false");
            return;
        }
        boolean debug = ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG);
        com.mojang.logging.LogUtils.getLogger().info(
            "Sable Light Overhaul ring recv: blocks={} class={} minBlocks={} core={} groups={}",
            event.affectedBlocks(), event.hitClass(), ExtraConfig.SHOCKWAVE_RING_MIN_BLOCKS.get(),
            ExtraConfig.SHOCKWAVE_RING_CORE.get().size(), ExtraConfig.SHOCKWAVE_RING_GROUPS.get().size());
        if (event.hitClass() == dev.sablelightoverhaul.api.EffectApi.HitClass.AIR) {
            com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul ring: class AIR (buried={} ground={}) -- nothing is drawn for an air burst",
                event.hitClass() == dev.sablelightoverhaul.api.EffectApi.HitClass.BURIED, false);
            return;
        }
        // Any explosion that got this far gets a ring: the reader's rule is big blast big effect, small
        // blast small effect, and the tier formulas already scale with the block count (a hit that broke
        // nothing lands on the smallest tier and throws a handful of puffs rather than nothing).
        int blocks = Math.max(0, event.affectedBlocks());
        Tier tier = tierFor(Math.max(blocks, TIERS[0].minBlocks()));
        if (tier == null) return;
        // A buried charge keeps the body of the ring and drops the cloud and the sparks around it, which is
        // what the script did for a shot that went off underground.
        List<Mixture> core = parse(level, ExtraConfig.SHOCKWAVE_RING_CORE.get());
        List<Mixture> ring = event.hitClass() == dev.sablelightoverhaul.api.EffectApi.HitClass.BURIED
                ? List.of()
                : parse(level, ExtraConfig.SHOCKWAVE_RING_GROUPS.get());
        // The ring's total is the tier formula times this scale. It exists so the density can be tuned
        // without touching the script's tier numbers, and so a setting of zero switches the ring off
        // while leaving everything else in place.
        double scale = Math.max(0.0, ExtraConfig.SHOCKWAVE_RING_COUNT_SCALE.get());
        // The 0.5 that used to sit in the config was baked in here, so the default behaviour is what the
        // owner had been running. The key scales that up or down: 0 switches the ring off, 1.0 is what they
        // had, and the range goes to 4 (the config carries the numbers).
        int scaledCount = (int) Math.round(countFor(tier, blocks) * RING_BASE_SCALE * scale);
        if (scaledCount <= 0) {
            com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul ring: count scale {} makes this blast draw nothing", scale);
            return;
        }
        if (debug) {
            com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul ring parsed: core={} entr(ies) ring={} entr(ies) tier={} count={}",
                core.size(), ring.size(), tierName(tier), scaledCount);
        }
        if (total(core) + total(ring) <= 0.0) {
            if (debug) com.mojang.logging.LogUtils.getLogger().warn(
                "Sable Light Overhaul ring: nothing parsed from ringCore/ringGroups -- particle strings did not decode");
            return;
        }
        int count = scaledCount;
        int perBatch = (int) Math.ceil(count / (double) tier.totalTicks());
        Vec3 center = event.center();
        double dimensionFactor = BlastVisualScale.dimensions(event.yield());
        Burst burst = new Burst(center.x, center.y, center.z, speedFor(tier, blocks) * dimensionFactor,
                tier.vyRange() * dimensionFactor, tier.speedVariance() * dimensionFactor,
                Math.max(1, perBatch), count, core, ring);
        // Spawned here and now rather than queued for the next client tick: the queue was the one link that
        // never ran, and spreading 2400 over two ticks is not worth a second chance at going missing.
        spawn(level, burst);
        if (ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul ring: {} blocks, {} -> {} particles at {}/tick, {} per tick, class {}",
                    blocks, tierName(tier), count, speedFor(tier, blocks) / 1.0, perBatch, event.hitClass());
        }
    }

    private static double total(List<Mixture> list) {
        double sum = 0.0;
        for (Mixture m : list) sum += m.weight;
        return sum;
    }

    private static String tierName(Tier tier) {
        return "tier" + (tier.minBlocks() < 2000 ? 1 : tier.minBlocks() < 18000 ? 2 : 3);
    }

    /** Parses config entries of the form {@code "weight particle-string"}. */
    private static List<Mixture> parse(ClientLevel level, List<? extends String> entries) {
        List<Mixture> parsed = new ArrayList<>();
        for (String entry : entries) {
            String text = entry.trim();
            if (text.isEmpty()) continue;
            double weight = 1.0;
            int space = text.indexOf(' ');
            if (space > 0) {
                try {
                    weight = Double.parseDouble(text.substring(0, space));
                    text = text.substring(space + 1).trim();
                } catch (NumberFormatException ignored) {
                    // No leading weight: the whole entry is the particle string, weight 1.
                }
            }
            if (weight <= 0.0) continue;
            ParticleOptions options = dev.sablelightoverhaul.lighting.ParticleSpec.parse(level, text);
            if (options != null) parsed.add(new Mixture(options, weight));
            else com.mojang.logging.LogUtils.getLogger().warn(
                "Sable Light Overhaul ring: particle string did not decode: {}", text);
        }
        return parsed;
    }

    /** One batch of the oldest burst, once per client tick: the script's own cadence. */
    /** One batch of the oldest burst, once per client tick: the script's own cadence. */
    static void spawn(ClientLevel level, Burst burst) {
        int batch = Math.min(burst.remaining, burst.batchSize);
        // Split the batch by pool first, then multiply each pool's own loop count by its throttle. No per
        // particle dice and no rewriting of the mix ratios: the counts the script asked for stay the counts,
        // they are simply spent on fewer iterations where a pool is being held back.
        int ringBatch = (int) Math.round(batch * burst.ringShare());
        int coreBatch = batch - ringBatch;
        int cloudBatch = (int) Math.round(ringBatch * burst.cloudShare() * burst.throttleCloud);
        int endRodBatch = (int) Math.round(ringBatch * burst.endRodShare() * burst.throttleEndRod);
        int otherBatch = Math.max(0, ringBatch - cloudBatch - endRodBatch);
        if (ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) {
            com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul ring spawn: {} of {} at ({}, {}, {}), speed {} | core {} cloud {} endrod {} other {} | live {} cloud x{} endrod x{}",
                batch, burst.remaining, burst.x, burst.y + 2.5, burst.z, burst.speed,
                coreBatch, cloudBatch, endRodBatch, otherBatch, burst.live,
                String.format(java.util.Locale.ROOT, "%.2f", burst.throttleCloud),
                String.format(java.util.Locale.ROOT, "%.2f", burst.throttleEndRod));
        }
        for (int i = 0; i < coreBatch; i++) spawnOne(level, burst, pickFrom(level, burst.core), false);
        for (int i = 0; i < cloudBatch; i++) spawnOne(level, burst, pickFrom(level, burst.clouds), true);
        for (int i = 0; i < endRodBatch; i++) spawnOne(level, burst, pickFrom(level, burst.endRods), false);
        for (int i = 0; i < otherBatch; i++) spawnOne(level, burst, pickFrom(level, burst.others), false);
        burst.remaining -= batch;
    }

    /** One particle, positioned and thrown exactly as the script did. */
    private static void spawnOne(ClientLevel level, Burst burst, Mixture mixture, boolean shorten) {
        if (mixture == null) return;
        double offset = (level.random.nextDouble() - 0.5) * burst.speedVariance;
        double speed = burst.speed + offset
                + ExtraConfig.SHOCKWAVE_RING_VARIANCE.get() * (level.random.nextDouble() - 0.5) * 2.0;
        double theta = level.random.nextDouble() * 2.0 * Math.PI;
        double vy = (level.random.nextDouble() - 0.5) * burst.vyRange;
        double x = burst.x, y = burst.y + 2.5, z = burst.z;
        double vx = Math.cos(theta) * speed, vz = Math.sin(theta) * speed;
        if (!shorten) {
            level.addParticle(mixture.options(), true, x, y, z, vx, vy, vz);
            return;
        }
        // A cloud goes through the engine so its lifetime can be shortened: past about half a second it is
        // too faint to read as part of the wave and is only spending the layer's budget.
        net.minecraft.client.particle.ParticleEngine engine =
                net.minecraft.client.Minecraft.getInstance().particleEngine;
        if (engine == null) {
            level.addParticle(mixture.options(), true, x, y, z, vx, vy, vz);
            return;
        }
        net.minecraft.client.particle.Particle spawned =
                engine.createParticle(mixture.options(), x, y, z, vx, vy, vz);
        if (spawned != null) {
            ((dev.sablelightoverhaul.mixin.ParticleLifetimeAccessor) (Object) spawned)
                    .slo$setLifetime(CLOUD_LIFETIME_TICKS);
        }
    }

    /** The weighted draw inside one pool: the same arithmetic pick() uses across the whole burst. */
    private static Mixture pickFrom(ClientLevel level, List<Mixture> pool) {
        if (pool == null || pool.isEmpty()) return null;
        double sum = 0.0;
        for (Mixture m : pool) sum += m.weight;
        if (sum <= 0.0) return null;
        double roll = level.random.nextDouble() * sum;
        double cumulative = 0.0;
        for (Mixture m : pool) {
            cumulative += m.weight;
            if (roll <= cumulative) return m;
        }
        return pool.get(pool.size() - 1);
    }

    /** One weighted draw over the body and the ring together, as the script drew it. */
    private static Mixture pick(ClientLevel level, Burst burst) {
        double total = burst.coreWeight + burst.ringWeight;
        if (total <= 0.0) return null;
        boolean fromRing = level.random.nextDouble() * total >= burst.coreWeight;
        List<Mixture> pool = fromRing ? burst.ring : burst.core;
        if (pool.isEmpty()) pool = fromRing ? burst.core : burst.ring;
        double sum = 0.0;
        for (Mixture m : pool) sum += m.weight;
        if (sum <= 0.0) return null;
        double roll = level.random.nextDouble() * sum;
        double cumulative = 0.0;
        for (Mixture m : pool) {
            cumulative += m.weight;
            if (roll <= cumulative) return m;
        }
        return pool.get(pool.size() - 1);
    }

    /** A world change drops whatever was still being spawned into the old one. */
    static void clear() {
        PENDING.clear();
    }

    /** How many bursts are still spawning, for the debug line. */
    static int pending() {
        return PENDING.size();
    }
}
