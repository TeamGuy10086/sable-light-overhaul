package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.BlastPreviewPayload;
import dev.sablelightoverhaul.BlastVisualScale;
import dev.sablelightoverhaul.BigSmokeModel;
import dev.sablelightoverhaul.GasVertexData;
import dev.sablelightoverhaul.InterceptionPayload;
import dev.sablelightoverhaul.InterceptionStyle;
import java.util.ArrayDeque;
import dev.sablelightoverhaul.lighting.ImpactParticleData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The fireball and cold skirt of one ordinary CBC shell burst. */
public final class ImpactExplosionSmoke extends Particle {
    private static final double TAU = 2 * Math.PI;

    private static final float INTERCEPTION_FIREBALL_SCALE = .70f;
    private static final int COLD_SMOKE_TINT = 0x414347;
    private static final int MAX_INTERCEPTION_PARTICLES = 256;
    private static final ArrayDeque<Particle> interceptionParticles = new ArrayDeque<>();
    private static ClientLevel interceptionWorld;
    private final int epoch = ClientFlashes.visualEpoch();
    private final Vec3 origin, travel, outward;
    private final float radius, opacity, expansion, initialRoll, rollSpeed;
    private final int seed, light;
    private final boolean ground;
    private float aspect;
    private int dustTint;
    private boolean denseDustColumn;
    private int clusterRiseTicks;
    private int luminousTicks;
    private boolean slowHotRoll;
    private boolean frontLoadedRise;
    private int stemPuffs;
    private int nextStemTick;
    private int stemInterval;
    private float pushSpeed = 1;
    private DustColumnMotion dustColumnMotion;
    private int dustTraceSample = -1;
    private float fadeStart = .62f;
    private Vec3 wind = Vec3.ZERO;

    private ImpactExplosionSmoke(ClientLevel level, Vec3 origin, Vec3 travel, Vec3 outward, float radius,
                                 float opacity, float expansion, int ticks, boolean hot,
                                 boolean ground, float rollMultiplier, int light) {
        super(level, origin.x, origin.y, origin.z);
        this.origin = origin;
        this.travel = travel;
        this.outward = outward;
        this.radius = radius;
        this.opacity = opacity;
        this.expansion = expansion;
        this.ground = ground;
        this.light = GasVertexData.smokeLight(light);
        lifetime = ticks;
        hasPhysics = false;
        seed = level.random.nextInt(64);
        initialRoll = (float)(level.random.nextDouble() * TAU);
        rollSpeed = (.12f + .14f * level.random.nextFloat()) * rollMultiplier
            * (level.random.nextBoolean() ? 1 : -1);
        aspect = hot ? 1.16f : ground ? .88f : .96f;
        xo = x;
        yo = y;
        zo = z;
        setBounds(radius);
    }

    public static boolean spawn(Vec3 origin, float power, boolean plume) {
        if (power < BlastPreviewPayload.Impact.MIN_POWER) return false;
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.getConnection().hasChannel(BlastPreviewPayload.Impact.TYPE)) {
            return ImpactExplosionConfig.enabled.get() && MissileSmokeMesh.available(mc.level);
        }
        return spawnGround(origin, power, plume, Vec3.ZERO, 1);
    }

    private static boolean spawnGround(Vec3 origin, float power, boolean plume, Vec3 opening, double dustHeight) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (!ImpactExplosionConfig.enabled.get() || !MissileSmokeMesh.available(level)
            || !Double.isFinite(origin.lengthSqr()) || !Float.isFinite(power) || power <= 0
            || !plume) return false;

        double baseScale = Math.sqrt(Math.clamp(power, .25, 32) / 3.0);
        double dimensionFactor = BlastVisualScale.dimensions(power);
        double scale = baseScale * dimensionFactor;
        double floor = floorBelow(level, origin, Math.min(3.5, 1 + scale));
        boolean ground = plume;
        boolean lateral = opening.lengthSqr() > .5;
        double baseY = Double.isFinite(floor) ? Math.max(floor, origin.y - .7 * scale) : origin.y;
        Vec3 base = new Vec3(origin.x, baseY, origin.z);
        BlockPos lightPos = BlockPos.containing(base.add(0, .65, 0));
        int light = level.hasChunkAt(lightPos) ? LevelRenderer.getLightColor(level, lightPos) : LightTexture.pack(0, 15);
        var random = level.random;
        double density = switch (mc.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> .65;
            case MINIMAL -> .35;
        };

        double dustChance = ImpactExplosionConfig.groundDustColumnChance.get();
        boolean raisedDust = dustChance >= 1 || (dustChance > 0 && random.nextDouble() < dustChance);

        float fireAlpha = ImpactExplosionConfig.fireOpacity.get().floatValue();
        if (fireAlpha > 0) {
            double sizeVariation = .80 + .25 * random.nextDouble();
            double fireSize = 2.8 * baseScale * BlastVisualScale.fireball(power) * ImpactExplosionConfig.fireRadius.get() * sizeVariation;
            int duration = ImpactExplosionConfig.fireTicks.get();
            int coldDuration = ImpactExplosionConfig.fireSmokeTicks.get();
            int count = scaledCount(ImpactExplosionConfig.fireCount.get(), density);
            double turn = random.nextDouble() * TAU;
            // Keep a little variation within the broad, medium-height silhouette.
            double shape = random.nextDouble();
            double clusterLift = fireSize * (1.16 + .24 * shape) * (lateral ? .76 : 1)
                * ImpactExplosionConfig.groundFireballLift.get();
            double clusterWidth = fireSize * (1.26 - .18 * shape) * (lateral ? 1.18 : 1)
                * ImpactExplosionConfig.groundFireballWidth.get();
            float fireExpansion = ImpactExplosionConfig.groundFireballExpansion.get().floatValue();
            int stemCount = 2 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                double angle = turn + TAU * (i + .35 * random.nextDouble()) / count;
                Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                double offset = i == 0 ? 0 : clusterWidth * (.045 + .035 * random.nextDouble());
                double height = fireSize * (.05 + .03 * random.nextDouble());
                Vec3 center = base.add(outward.scale(offset)).add(0, height, 0);
                double thrust = .90 + .10 * random.nextDouble();
                Vec3 travel = outward.scale(clusterWidth * (.085 + .055 * random.nextDouble()))
                    .add(opening.scale(clusterWidth * .55)).add(0, clusterLift * thrust, 0);
                float radius = (float)(clusterWidth * (.32 + .10 * random.nextDouble()));
                int hotDuration = Math.max(2, (int)Math.round(duration * (.95 + .10 * random.nextDouble())));
                int ticks = hotDuration + Math.max(2, (int)(coldDuration * (.85 + .30 * random.nextDouble())));
                var lobe = new ImpactExplosionSmoke(level, center, travel, outward, radius, fireAlpha,
                    fireExpansion, ticks, true, ground, 1, light);
                lobe.clusterRiseTicks = Math.max(6, duration);
                lobe.luminousTicks = hotDuration;
                lobe.slowHotRoll = ground;
                lobe.frontLoadedRise = ground;
                if (i == 0) {
                    // Only the central lobe releases the stem, sampling the real path as it rises.
                    lobe.stemPuffs = stemCount;
                    lobe.nextStemTick = Math.max(1, (int)Math.round(lobe.clusterRiseTicks * .08));
                    lobe.stemInterval = Math.max(1, (int)Math.round(lobe.clusterRiseTicks * .44 / (stemCount - 1)));
                }
                mc.particleEngine.add(lobe);
            }
        }

        int smokeCount = scaledCount(ImpactExplosionConfig.smokeCount.get(), density);
        float smokeAlpha = ImpactExplosionConfig.smokeOpacity.get().floatValue();
        double angleOffset = random.nextDouble() * TAU;
        int shoulderCount = Math.max(1, (smokeCount + 2) / 3);
        int outerCount = Math.max(0, smokeCount - shoulderCount);
        int fastPhase = ground && outerCount > 0 ? random.nextInt(Math.min(4, outerCount)) : -1;
        double smokeSize = ImpactExplosionConfig.smokeRadius.get();
        float expansion = ImpactExplosionConfig.smokeExpansion.get().floatValue();
        double spread = ImpactExplosionConfig.smokeSpread.get();
        double rise = ImpactExplosionConfig.smokeRise.get();
        float pushSpeed = ImpactExplosionConfig.smokePushSpeed.get().floatValue();
        float fadeStart = ImpactExplosionConfig.smokeFadeStart.get().floatValue();
        for (int i = 0; smokeAlpha > 0 && i < smokeCount; i++) {
            // The inner shoulders fill the gaps as the faster ground skirt moves out.
            boolean shoulder = i < shoulderCount;
            int count = shoulder ? shoulderCount : smokeCount - shoulderCount;
            int index = shoulder ? i : i - shoulderCount;
            // Interleave faster puffs with the regular skirt so the front still has gaps and depth.
            boolean fast = !shoulder && index % 4 == fastPhase;
            double angle = angleOffset + TAU * ((index + .8 * random.nextDouble()) / count + (shoulder ? .07 : 0));
            Vec3 outward = groundSpread(angle, opening);
            double variation = random.nextDouble();
            float radius = (float)(scale * smokeSize * (shoulder ? 2.7 + variation : 2.05 + 1.05 * variation));
            double birthRadius = scale * (shoulder ? .2 + .75 * random.nextDouble() : .8 + .75 * random.nextDouble());
            double distance = scale * spread * (shoulder ? 1.0 + 1.25 * random.nextDouble() : 2.9 + 2.6 * random.nextDouble());
            double lift = scale * rise * (shoulder ? 1.45 : ground ? .55 : 1.0) * (.6 + random.nextDouble());
            double height = random.nextDouble();
            double birthY = radius * (ground ? (shoulder ? .35 + .29 * height : .32 + .25 * height)
                : height - (shoulder ? .30 : .52));
            double localFloor = ground ? floorBelow(level, base.add(outward.scale(birthRadius)).add(0, .55, 0), 1.35) : Double.NaN;
            double groundOffset = Double.isFinite(localFloor) ? Math.max(0, localFloor - base.y) : 0;
            Vec3 center = base.add(outward.scale(birthRadius)).add(0, birthY + groundOffset, 0);
            int ticks = Math.max(2, (int)(ImpactExplosionConfig.smokeTicks.get() * (.82 + .34 * random.nextDouble())));
            Vec3 travel = outward.scale(distance).add(0, lift, 0);
            var puff = new ImpactExplosionSmoke(level, center, travel, outward, radius, smokeAlpha,
                expansion * (fast ? .48f : 1), ticks, false, ground, fast ? 1.65f : 1, light);
            puff.pushSpeed = pushSpeed * (fast ? 1.60f : 1);
            puff.fadeStart = fadeStart;
            mc.particleEngine.add(puff);
        }

        Vec3 burst = base.add(0, ground ? .42 * scale : 0, 0);
        ExplosionTrailMesh.spawnImpact(base.add(0, .25 * scale, 0), power, ground,
            scaledCount(ImpactExplosionConfig.trailCount.get(), density * .5), opening);
        throwSparks(level, burst, scale, dimensionFactor, density, false);
        throwDebris(level, burst, scale, dimensionFactor, density, BlastPreviewPayload.Kind.GROUND, Vec3.ZERO);
        if (raisedDust) {
            int jets = scaledCount(ImpactExplosionConfig.buriedColumnCount.get(), density);
            Vec3 vent = opening.scale(.45).add(0, 1, 0).normalize();
            spawnDustCurtain(level, base, vent, scale, jets, 0, DustCurtain.GROUND, dustHeight);
        }
        return true;
    }

    static void receivePreview(BlastPreviewPayload payload) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !level.dimension().location().equals(payload.dimension())
            || !ImpactExplosionConfig.enabled.get() || !ClientFlashes.effectsEnabled()
            || !MissileSmokeMesh.available(level)) return;

        var origin = payload.origin();
        float power = payload.power();
        double dustHeight = ImpactExplosionConfig.dustHeightFromDestruction.get() ? payload.dustHeightFactor() : 1;
        if (payload.kind() == BlastPreviewPayload.Kind.GROUND) {
            spawnGround(origin, power, true, new Vec3(payload.direction().x, 0, payload.direction().z).normalize(), dustHeight);
            return;
        }
        double density = switch (mc.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> .65;
            case MINIMAL -> .35;
        };
        double baseScale = Math.sqrt(Math.clamp(power, .25, 48) / 3.0);
        double dimensionFactor = BlastVisualScale.dimensions(power);
        double scale = baseScale * dimensionFactor;
        double fireScale = baseScale * BlastVisualScale.fireball(power);
        if (payload.kind() == BlastPreviewPayload.Kind.BURIED) {
            spawnBuried(level, origin, payload.direction(), scale, fireScale, dimensionFactor, density,
                payload.dustColor(), payload.shallowVent(), dustHeight);
            return;
        }
        boolean hull = payload.kind() == BlastPreviewPayload.Kind.HULL;
        boolean interception = payload.kind() == BlastPreviewPayload.Kind.INTERCEPTION;
        boolean airburst = payload.kind() == BlastPreviewPayload.Kind.AIR;
        Vec3 axis = hull ? payload.direction().normalize() : Vec3.ZERO;
        var random = level.random;
        int light = level.hasChunkAt(BlockPos.containing(origin))
            ? LevelRenderer.getLightColor(level, BlockPos.containing(origin)) : LightTexture.pack(0, 15);
        double size = fireScale * ImpactExplosionConfig.fireRadius.get() * (interception ? .72 * INTERCEPTION_FIREBALL_SCALE : 1);
        if (airburst) size *= .67;
        int count = scaledCount(ImpactExplosionConfig.fireCount.get(), density);
        float opacity = ImpactExplosionConfig.fireOpacity.get().floatValue();
        int hotTicks = ImpactExplosionConfig.fireTicks.get();
        int smokeTicks = ImpactExplosionConfig.fireSmokeTicks.get();
        for (int i = 0; opacity > 0 && i < count; i++) {
            Vec3 direction = sphereDirection(random, axis);
            Vec3 birth = origin.add(direction.scale(i == 0 ? 0 : size * (.08 + .18 * random.nextDouble())));
            Vec3 travel = direction.scale(size * (.9 + .55 * random.nextDouble()));
            int cold = Math.max(2, (int)(smokeTicks * (hull ? .28 : interception ? .45 : .70)));
            var lobe = new ImpactExplosionSmoke(level, birth, travel, direction, (float)(size * (1 + .25 * random.nextDouble())),
                opacity, .96f, hotTicks + cold, true, false, 1, light);
            lobe.clusterRiseTicks = Math.max(6, hotTicks);
            lobe.luminousTicks = hotTicks;
            mc.particleEngine.add(lobe);
        }

        if (interception) {
            var detail = InterceptionStyle.Detail.at(mc.gameRenderer.getMainCamera().getPosition().distanceTo(origin));
            if (detail != null) throwMissileFragments(level, origin, payload.direction(), 1, .5f, 0x776B63, detail);
        } else {
            throwDebris(level, origin, scale, dimensionFactor, density, payload.kind(), axis);
        }
        if (airburst) {
            throwSparks(level, origin, scale, dimensionFactor, density, true);
        }
    }

    static void receiveInterception(InterceptionPayload payload) {
        receiveInterception(payload, 0);
    }

    static void receiveInterception(InterceptionPayload payload, float fragmentDiameter) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !level.dimension().location().equals(payload.dimension())
            || !Double.isFinite(payload.center().lengthSqr()) || !Double.isFinite(payload.incomingMotion().lengthSqr())
            || !Float.isFinite(payload.power()) || payload.power() < 0) return;
        InterceptionAudio.offer(level, payload);

        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(payload.center());
        var detail = InterceptionStyle.Detail.at(distance);
        if (detail == null || !ImpactExplosionConfig.enabled.get() || !ClientFlashes.effectsEnabled()) return;
        if (interceptionWorld != level) {
            interceptionParticles.clear();
            interceptionWorld = level;
        }
        interceptionParticles.removeIf(particle -> !particle.isAlive());
        var style = InterceptionStyle.of(payload.missileType(), payload.warheadKind(), payload.power());
        var origin = payload.center();
        var random = level.random;
        boolean near = detail == InterceptionStyle.Detail.NEAR;
        var pos = BlockPos.containing(origin);
        int light = near && level.hasChunkAt(pos) ? LevelRenderer.getLightColor(level, pos) : LightTexture.pack(5, 15);
        throwMissileFragments(level, origin, payload.incomingMotion(), style.hullScale(), fragmentDiameter, style.metalColor(), detail);

        if (!MissileSmokeMesh.available(level)) return;
        float alpha = ImpactExplosionConfig.fireOpacity.get().floatValue();
        if (style.explosive() && alpha > 0) {
            int hot = Math.min(detail.hot, ImpactExplosionConfig.fireCount.get());
            float radius = style.fireScale() * (near ? .83f : 1.08f) * INTERCEPTION_FIREBALL_SCALE
                * ImpactExplosionConfig.fireRadius.get().floatValue();
            int duration = ImpactExplosionConfig.fireTicks.get();
            int coldTicks = Math.max(4, ImpactExplosionConfig.fireSmokeTicks.get() / (near ? 2 : 3));
            for (int i = 0; i < hot; i++) {
                Vec3 radial = sphereDirection(random, Vec3.ZERO);
                var puff = new ImpactExplosionSmoke(level, origin.add(radial.scale(radius * .12)),
                    radial.scale(radius), radial, radius, alpha, .96f, duration + coldTicks, true, false, 1, light);
                puff.clusterRiseTicks = Math.max(6, duration);
                puff.luminousTicks = duration;
                addInterceptionParticle(mc, puff);
            }
        }

        float smokeAlpha = ImpactExplosionConfig.smokeOpacity.get().floatValue();
        int cold = Math.min(detail.cold, ImpactExplosionConfig.smokeCount.get());
        float radius = style.hullScale() * ImpactExplosionConfig.smokeRadius.get().floatValue() * (near ? .92f : 1.4f);
        int duration = Math.max(6, ImpactExplosionConfig.smokeTicks.get() / 2);
        for (int i = 0; smokeAlpha > 0 && i < cold; i++) {
            Vec3 radial = sphereDirection(random, Vec3.ZERO);
            Vec3 travel = interceptionVelocity(payload.incomingMotion(), radial, .46, .75)
                .scale(1.5 + random.nextDouble());
            var puff = new ImpactExplosionSmoke(level, origin.add(radial.scale(radius * .2)), travel,
                radial, radius, smokeAlpha * .82f, .95f, duration, false, false, .8f, light);
            addInterceptionParticle(mc, puff);
        }
    }

    private static void addInterceptionParticle(Minecraft mc, Particle particle) {
        if (particle == null) return;
        if (interceptionParticles.size() >= MAX_INTERCEPTION_PARTICLES) interceptionParticles.removeFirst().remove();
        interceptionParticles.addLast(particle);
        mc.particleEngine.add(particle);
    }

    private static void spawnBuried(ClientLevel level, Vec3 origin, Vec3 direction,
                                    double scale, double fireScale, double dimensionFactor, double density,
                                    int color, boolean shallowVent, double dustHeight) {
        boolean vented = direction.lengthSqr() > .5;
        if (vented) {
            int jets = scaledCount(ImpactExplosionConfig.buriedColumnCount.get(), density);
            spawnDustCurtain(level, origin, direction, scale, jets, color, DustCurtain.BURIED, dustHeight);
            if (shallowVent) spawnVentFireball(level, origin, direction, fireScale, density);
            throwDebris(level, origin, scale, dimensionFactor, density, BlastPreviewPayload.Kind.BURIED, direction);
        }

        // The initial burst is placed above the old surface. Client block updates can arrive after its packet.
        var mc = Minecraft.getInstance();
        var random = level.random;
        int baseCount = ImpactExplosionConfig.smokeCount.get();
        int count = scaledCount(vented ? (baseCount * 5 + 1) / 2 : (baseCount + 1) / 2, density);
        float opacity = ImpactExplosionConfig.smokeOpacity.get().floatValue() * (vented ? 1 : .76f);
        int light = explosionLight(level, origin);
        double turn = random.nextDouble() * TAU;
        for (int i = 0; opacity > 0 && i < count; i++) {
            double angle = turn + TAU * (i + random.nextDouble()) / count;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            double width = scale * ImpactExplosionConfig.smokeRadius.get() * 1.68;
            float radius = (float)(width * (.75 + .45 * random.nextDouble()));
            Vec3 birth = origin.add(radial.scale(scale * (.10 + .38 * random.nextDouble())));
            double floor = floorBelow(level, birth.add(0, .6, 0), 2.4);
            if (Double.isFinite(floor)) birth = new Vec3(birth.x, floor, birth.z);
            birth = birth.add(0, radius * (.40 + .18 * random.nextDouble()) + .10, 0);
            double reach = scale * ImpactExplosionConfig.smokeSpread.get() * 1.40;
            double lift = scale * ImpactExplosionConfig.smokeRise.get() * .58;
            Vec3 travel = radial.scale(reach * (.55 + random.nextDouble()))
                .add(0, lift * (.65 + .55 * random.nextDouble()), 0);
            int duration = Math.max(6, (int)(ImpactExplosionConfig.smokeTicks.get() * (vented ? .76 : .48)
                * (.85 + .3 * random.nextDouble())));
            var puff = new ImpactExplosionSmoke(level, birth, travel, radial, radius, opacity,
                ImpactExplosionConfig.smokeExpansion.get().floatValue() * .75f, duration, false, true, 1.4f, light);
            puff.aspect = .91f;
            puff.dustTint = mixedDustTint(random, color);
            puff.pushSpeed = ImpactExplosionConfig.smokePushSpeed.get().floatValue();
            puff.fadeStart = ImpactExplosionConfig.smokeFadeStart.get().floatValue();
            mc.particleEngine.add(puff);
        }
    }

    private static void spawnVentFireball(ClientLevel level, Vec3 origin, Vec3 direction, double scale, double density) {
        float opacity = ImpactExplosionConfig.fireOpacity.get().floatValue();
        if (opacity <= 0) return;
        int lobes = scaledCount(ImpactExplosionConfig.fireCount.get(), density * .80);
        int hotTicks = Math.max(2, ImpactExplosionConfig.fireTicks.get() * 2 / 3);
        int light = explosionLight(level, origin);
        double size = scale * ImpactExplosionConfig.fireRadius.get() * .60;
        var random = level.random;
        var engine = Minecraft.getInstance().particleEngine;
        for (int i = 0; i < lobes; i++) {
            double angle = TAU * (i + random.nextDouble()) / lobes;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 root = origin.add(direction.scale(.18 * size)).add(radial.scale(.30 * size));
            Vec3 drift = radial.scale(.38 * size).add(direction.x * .42 * size, 1.15 * size, direction.z * .42 * size);
            var puff = new ImpactExplosionSmoke(level, root, drift, radial,
                (float)(size * (.72 + .25 * random.nextDouble())), opacity, .55f,
                hotTicks + Math.max(8, ImpactExplosionConfig.fireSmokeTicks.get() * 3 / 4),
                true, false, 1, light);
            puff.clusterRiseTicks = Math.max(4, hotTicks);
            puff.luminousTicks = hotTicks;
            puff.frontLoadedRise = true;
            if (i == 0) {
                puff.stemPuffs = 2;
                puff.nextStemTick = hotTicks / 2;
                puff.stemInterval = Math.max(3, hotTicks / 2);
            }
            engine.add(puff);
        }
    }

    private static int mixedDustTint(RandomSource random, int color) {
        // Vary the density visually while keeping the dust in the same charcoal-grey palette.
        int shade = 47 + random.nextInt(30);
        int red = shade;
        int green = shade + 2;
        int blue = shade + 6;
        int rgb = color & 0xFFFFFF;
        if (rgb != 0) {
            red = (9 * red + (rgb >> 16 & 255)) / 10;
            green = (9 * green + (rgb >> 8 & 255)) / 10;
            blue = (9 * blue + (rgb & 255)) / 10;
        }
        return red << 16 | green << 8 | blue;
    }

    private enum DustCurtain { BURIED, GROUND }

    private static void spawnDustCurtain(ClientLevel level, Vec3 origin, Vec3 direction, double scale, int count,
                                         int color, DustCurtain curtain, double dustHeight) {
        float opacity = ImpactExplosionConfig.buriedColumnOpacity.get().floatValue();
        if (count <= 0 || opacity <= 0 || dustHeight <= 0) return;
        var random = level.random;
        var engine = Minecraft.getInstance().particleEngine;
        int light = explosionLight(level, origin);
        Vec3 axis = direction.add(0, .55, 0).normalize();
        Vec3 side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 across = axis.cross(side);
        double rootOffset = curtain == DustCurtain.GROUND ? ImpactExplosionConfig.dustColumnGroundOffset.get() : 0;
        double angle = switch (curtain) {
            case BURIED -> ImpactExplosionConfig.dustColumnBuriedAngle.get();
            case GROUND -> ImpactExplosionConfig.dustColumnGroundAngle.get();
        };
        double heightScale = scale * ImpactExplosionConfig.buriedColumnHeight.get();
        double width = ImpactExplosionConfig.buriedColumnWidth.get();
        double spread = scale * ImpactExplosionConfig.buriedColumnSpread.get();
        double bend = ImpactExplosionConfig.dustColumnBend.get();
        double speed = ImpactExplosionConfig.buriedColumnSpeed.get();
        int life = ImpactExplosionConfig.buriedColumnTicks.get();
        int puffs = ImpactExplosionConfig.dustColumnPuffs.get();
        double baseWidth = ImpactExplosionConfig.dustColumnBaseWidth.get();
        double tipWidth = ImpactExplosionConfig.dustColumnTipWidth.get();
        float expansion = ImpactExplosionConfig.dustColumnExpansion.get().floatValue();
        float spin = ImpactExplosionConfig.dustColumnSpin.get().floatValue();
        float fadeStart = ImpactExplosionConfig.dustColumnFadeStart.get().floatValue();
        for (int branch = 0; branch < count; branch++) {
            double yaw = TAU * (branch + random.nextDouble()) / count;
            Vec3 outward = side.scale(Math.cos(yaw)).add(across.scale(Math.sin(yaw)));
            double jetAngle = Math.toRadians(Math.min(85, angle * (.75 + .5 * random.nextDouble())));
            Vec3 jet = axis.scale(Math.cos(jetAngle)).add(outward.scale(Math.sin(jetAngle)));
            Vec3 branchOrigin = origin.add(new Vec3(outward.x, 0, outward.z).normalize().scale(rootOffset * scale));
            double height = heightScale * (3.0 + 2.6 * random.nextDouble());
            double spacing = height / puffs;
            int columnLife = (int)Math.round(life * (.82 + .18 * random.nextDouble()));
            int stagger = random.nextInt(2);
            for (int i = 0; i < puffs; i++) {
                double fraction = (i + 1.0) / puffs;
                Vec3 root = branchOrigin.add(direction.scale(.38))
                    .add(outward.scale(spread * (.10 + .16 * random.nextDouble())));
                Vec3 launch = jet.scale(height * fraction)
                    .add(outward.scale(spread * (.07 + bend * fraction * fraction)));
                launch = new Vec3(launch.x, launch.y * dustHeight, launch.z);
                Vec3 horizontal = new Vec3(jet.x, 0, jet.z);
                double upright = smooth(.76, .98, Math.abs(direction.y));
                double exitRise = Math.copySign((.10 + .35 * upright)
                    * (.63 + .30 * fraction + .14 * random.nextDouble()), jet.y);
                Vec3 drift = horizontal.scale(.42).add(outward.x * .065, exitRise * dustHeight, outward.z * .065);
                var puff = new ImpactExplosionSmoke(level, root, Vec3.ZERO, outward,
                    (float)(width * spacing * (baseWidth + (tipWidth - baseWidth) * fraction)
                        * (.86 + .26 * random.nextDouble())), opacity, expansion,
                    Math.max(6, columnLife - 6 * i / puffs), false, false, spin, light);
                puff.age = -Math.min(2, (6 * i / puffs + stagger) / 2);
                puff.dustColumnMotion = new DustColumnMotion(launch, drift,
                    Math.clamp(4.8 / speed, .4, 19.2));
                puff.aspect = 1.0f;
                puff.dustTint = mixedDustTint(random, color);
                puff.denseDustColumn = true;
                if (branch == 0 && (i == 0 || i == puffs - 1) && ExtraConfig.EFFECTS_DEBUG.get()) puff.dustTraceSample = i;
                puff.fadeStart = fadeStart;
                engine.add(puff);
            }
        }
    }

    // Time and velocities are expressed in game ticks. Both phases share the same handoff velocity.
    record DustColumnMotion(Vec3 launch, Vec3 exitVelocity, double launchTicks) {
        private static final double GRAVITY = 1.0 / (20 * 20);

        double rollSeconds(double ticks, int lifetime) {
            double driftTicks = Math.max(0, ticks - launchTicks);
            // Slow acceleration through the handoff, then let the angular speed decay.
            double rampTicks = 8.0;
            double ramp = Math.min(driftTicks, rampTicks);
            double decay = 1.05 / lifetime;
            double afterRamp = -Math.expm1(-decay * (driftTicks - ramp)) / decay;
            return (.5 * ramp * ramp / rampTicks + afterRamp) / 20;
        }

        Vec3 displacement(double ticks) {
            if (ticks <= launchTicks) {
                double t = ticks / launchTicks;
                double impulse = 1 - Math.pow(1 - t, 3.6);
                return launch.scale(impulse).add(exitVelocity.scale(launchTicks * t * t * (t - 1)));
            }
            double driftTicks = ticks - launchTicks;
            double forwardDrag = .095;
            double verticalDrag = .060;
            double forward = -Math.expm1(-forwardDrag * driftTicks) / forwardDrag;
            double vertical = -Math.expm1(-verticalDrag * driftTicks) / verticalDrag;
            double terminal = GRAVITY / verticalDrag;
            return launch.add(exitVelocity.x * forward,
                (exitVelocity.y + terminal) * vertical - terminal * driftTicks,
                exitVelocity.z * forward);
        }
    }

    static Vec3 groundSpread(double angle, Vec3 opening) {
        var radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        return radial.add(opening.scale(1.35)).normalize();
    }

    private static Vec3 sphereDirection(RandomSource random, Vec3 axis) {
        double y = random.nextDouble() * 2 - 1;
        double angle = random.nextDouble() * TAU;
        double width = Math.sqrt(1 - y * y);
        var direction = new Vec3(width * Math.cos(angle), y, width * Math.sin(angle));
        return axis.lengthSqr() > .5 ? axis.add(direction.scale(.68)).normalize() : direction;
    }

    private static int explosionLight(ClientLevel level, Vec3 origin) {
        BlockPos pos = BlockPos.containing(origin);
        return level.hasChunkAt(pos) ? LevelRenderer.getLightColor(level, pos) : LightTexture.pack(0, 15);
    }

    private static int scaledCount(int count, double density) {
        return (int)Math.ceil(count * density);
    }

    private static double floorBelow(ClientLevel level, Vec3 from, double distance) {
        if (!level.hasChunkAt(BlockPos.containing(from))) return Double.NaN;
        var hit = level.clip(new ClipContext(from, from.add(0, -distance, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
        return hit.getType() != HitResult.Type.MISS && hit.getDirection() == net.minecraft.core.Direction.UP
            ? hit.getLocation().y : Double.NaN;
    }

    private static void throwSparks(ClientLevel level, Vec3 origin, double scale, double dimensionFactor, double density, boolean airburst) {
        var random = level.random;
        int count = scaledCount(ImpactExplosionConfig.sparkCount.get(), density) * (airburst ? 2 : 1);
        double speed = .8 * scale * ImpactExplosionConfig.sparkSpeed.get();
        double size = .23 * Math.sqrt(scale * dimensionFactor) * ImpactExplosionConfig.sparkWidth.get();
        int ticks = ImpactExplosionConfig.sparkTicks.get();
        if (airburst) ticks = (ticks * 3 + 1) / 2;
        for (int i = 0; i < count; i++) {
            Vec3 direction = airburst ? sphereDirection(random, Vec3.ZERO)
                : radialDirection(random, .08 + .78 * random.nextDouble());
            Vec3 velocity = direction.scale(speed * (.55 + .9 * random.nextDouble()));
            Vec3 pos = origin.add(direction.scale(.18 * scale));
            var data = new ImpactParticleData((float)(size * (.65 + .7 * random.nextDouble())),
                i % 3 == 0 ? 0xFFF2BE : 0xFFAE56, ticks, 0, ImpactParticleData.SHELL_SPARK, 0);
            Minecraft.getInstance().particleEngine.createParticle(data, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static void throwDebris(ClientLevel level, Vec3 origin, double scale, double dimensionFactor, double density,
                                    BlastPreviewPayload.Kind kind, Vec3 axis) {
        var random = level.random;
        double amount = switch (kind) {
            case AIR -> .5;
            case BURIED -> .65;
            default -> 1;
        };
        int count = scaledCount(ImpactExplosionConfig.debrisCount.get(), density * amount);
        double size = Math.sqrt(scale * dimensionFactor) * ImpactExplosionConfig.debrisSize.get();
        double blastSpeed = .75 * scale * ImpactExplosionConfig.debrisSpeed.get();
        int lifetime = ImpactExplosionConfig.debrisTicks.get();
        for (int i = 0; i < count; i++) {
            double mass = i == 0 ? .72 + .28 * random.nextDouble() : Math.pow(random.nextDouble(), 1.65);
            var shape = fragmentShape(kind, mass);
            double lift = Math.tan(Math.toRadians(30 + 32 * mass + 15 * random.nextDouble()));
            Vec3 direction = switch (kind) {
                case GROUND -> radialDirection(random, lift);
                case BURIED -> sphereDirection(random, axis.add(0, .7, 0).normalize());
                case HULL -> sphereDirection(random, axis);
                case AIR, INTERCEPTION -> sphereDirection(random, Vec3.ZERO);
            };
            double variation = .90 + .20 * random.nextDouble();
            Vec3 velocity = direction.scale(blastSpeed * shape.speed * variation);
            Vec3 birth = origin.add(direction.scale(.18 * scale));
            if (kind == BlastPreviewPayload.Kind.GROUND || kind == BlastPreviewPayload.Kind.BURIED)
                birth = birth.add(0, .16, 0);
            int tint = i % 3 == 0 ? 0x80736A : 0x50433B;
            int ticks = Math.max(2, (int)Math.round(lifetime * (.78 + .68 * mass) * variation));
            int mode = kind == BlastPreviewPayload.Kind.AIR ? ImpactParticleData.AIR_DEBRIS : ImpactParticleData.SHELL_DEBRIS;
            var chip = new ImpactParticleData((float)(size * shape.diameter * .48), tint, ticks, 0, mode, 0);
            Minecraft.getInstance().particleEngine.createParticle(chip, birth.x, birth.y, birth.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static void throwMissileFragments(ClientLevel level, Vec3 origin, Vec3 incoming, double bodyScale,
                                             float fragmentDiameter, int tint, InterceptionStyle.Detail detail) {
        var sprites = ImpactParticle.fragmentSprites();
        if (sprites == null || ImpactExplosionConfig.debrisCount.get() == 0) return;
        var mc = Minecraft.getInstance();
        if (interceptionWorld != level) {
            interceptionParticles.clear();
            interceptionWorld = level;
        }
        interceptionParticles.removeIf(particle -> !particle.isAlive());
        var random = level.random;
        boolean near = detail == InterceptionStyle.Detail.NEAR;
        double maximumDiameter = missileFragmentMaximum(bodyScale, fragmentDiameter);
        double speed = ImpactExplosionConfig.debrisSpeed.get();
        int burnDuration = ImpactExplosionConfig.debrisTicks.get();
        int burning = random.nextInt(4);
        int unlit = 4 + random.nextInt(5);
        if (!near) {
            burning = Math.min(burning, 1);
            unlit = Math.min(unlit, detail.fragments);
        }
        for (int i = 0; i < burning + unlit; i++) {
            boolean lit = i < burning;
            double mass = lit ? .68 + .32 * random.nextDouble() : .28 * random.nextDouble();
            var shape = fragmentShape(BlastPreviewPayload.Kind.INTERCEPTION, mass);
            Vec3 velocity = interceptionVelocity(incoming, sphereDirection(random, Vec3.ZERO), speed, shape.speed());
            float diameter = (float)(maximumDiameter * shape.diameter()
                / fragmentShape(BlastPreviewPayload.Kind.INTERCEPTION, 1).diameter());
            int ticks = Math.clamp((int)(burnDuration * (1.15 + .43 * mass)), 8, 36);
            addInterceptionParticle(mc, new BurningMissileFragment(level, origin, velocity, diameter, tint,
                ticks, near, lit, sprites));
        }
    }

    static double missileFragmentMaximum(double bodyScale, float suppliedDiameter) {
        if (Float.isFinite(suppliedDiameter) && suppliedDiameter > 0) return Math.clamp(suppliedDiameter, .05f, 4);
        return Math.clamp(bodyScale * .38, .16, .60);
    }

    static Vec3 interceptionVelocity(Vec3 incoming, Vec3 scatter, double speed, double variant) {
        double magnitude = incoming.length();
        if (magnitude < 1e-5) return scatter.scale(Math.min(.36, .18 * speed * variant));
        Vec3 heading = incoming.scale(1 / magnitude);
        double along = scatter.dot(heading);
        Vec3 across = scatter.subtract(heading.scale(along));
        // Compress high speeds for readability, while limiting separation to a small part of forward travel.
        double forward = Math.min(9, -8 * Math.expm1(-magnitude * .066) * speed);
        forward *= (.65 + .30 * variant) * (1 + .08 * along);
        double lateral = Math.min(.56, forward * .19);
        return heading.scale(forward).add(across.scale(lateral));
    }

    record FragmentShape(double diameter, double speed) {}

    static FragmentShape fragmentShape(BlastPreviewPayload.Kind kind, double mass) {
        double max = switch (kind) {
            case GROUND -> .56;
            case BURIED -> .40;
            case AIR -> .39;
            case HULL -> .54;
            case INTERCEPTION -> .78;
        };
        double min = kind == BlastPreviewPayload.Kind.INTERCEPTION ? .11 : .10;
        return new FragmentShape(min + mass * (max - min), 1.45 - .90 * mass);
    }

    private static Vec3 radialDirection(RandomSource random, double lift) {
        double angle = random.nextDouble() * TAU;
        return new Vec3(Math.cos(angle), lift, Math.sin(angle)).normalize();
    }

    @Override
    public void tick() {
        if (++age >= lifetime || epoch != ClientFlashes.visualEpoch() || !ClientFlashes.effectsEnabled()) {
            remove();
            return;
        }
        if (age < 0) return;
        if (denseDustColumn && BigSmokeConfig.SOFT_EDGE_CULL.get()) {
            double fade = 1.0 - smooth(fadeStart, 1.0, (double)age / lifetime);
            // Matches the shader's upper bound for an entirely covered texel.
            if (opacity * fade * 1.4 <= BigSmokeConfig.SOFT_EDGE_THRESHOLD.get()) {
                remove();
                return;
            }
        }
        xo = x;
        yo = y;
        zo = z;
        double progress = (double)age / lifetime;
        var gust = ParticleWind.step();
        wind = wind.add(gust.x(), gust.y(), gust.z());
        if (dustColumnMotion != null) {
            Vec3 center = origin.add(dustColumnMotion.displacement(age)).add(wind);
            setPos(center.x, center.y, center.z);
            setBounds(size(progress));
            if (dustTraceSample >= 0 && (age <= 16 || age % 5 == 0)) traceDustColumn(center, progress);
            return;
        }
        double seconds = age / 20.0;
        double pushTime = pushSpeed * seconds;
        // The initial shove hands off to a slower drift without an abrupt stop.
        double riseTime = (double)age / Math.max(1, clusterRiseTicks);
        double radial = clusterRiseTicks > 0 ? -Math.expm1(-1.45 * riseTime)
            : -.74 * Math.expm1(-3 * pushTime) - .26 * Math.expm1(-.65 * pushTime);
        double liftTime = ground ? Math.max(0, seconds - .12) : seconds;
        double verticalTimeScale = 1;
        if (frontLoadedRise && riseTime < 1) {
            // Advance the early lift while matching both height and speed at the end of the rise.
            double remaining = 1 - riseTime;
            double riseBias = ground ? ImpactExplosionConfig.groundFireballRiseBias.get() : .8;
            verticalTimeScale += riseBias * remaining * remaining;
        }
        double rise = clusterRiseTicks > 0 ? -.87 * Math.expm1(-1.75 * riseTime * verticalTimeScale)
            - .13 * Math.expm1(-.8 * seconds * verticalTimeScale)
            : -Math.expm1(-.98 * liftTime);
        Vec3 center = origin.add(travel.x * radial, travel.y * rise, travel.z * radial).add(wind);
        if (clusterRiseTicks > 0) {
            double curl = radius * .17 * Math.sin(2.1 * riseTime) * Math.exp(-.55 * riseTime);
            center = center.add(-outward.z * curl, 0, outward.x * curl);
        }
        setPos(center.x, center.y, center.z);
        setBounds(size(progress));
        if (stemPuffs > 0 && age >= nextStemTick) {
            releaseStem(center);
            stemPuffs--;
            nextStemTick += stemInterval;
        }
    }

    private void traceDustColumn(Vec3 center, double progress) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double depth = center.subtract(camera.getPosition()).dot(new Vec3(camera.getLookVector()));
        double fade = 1.0 - smooth(fadeStart, 1.0, progress);
        com.mojang.logging.LogUtils.getLogger().info(
            "SLO dust trace launch={} sample={} age={}/{} origin={} center={} depth={} radius={} maxAlpha={} refraction={}",
            System.identityHashCode(this), dustTraceSample, age, lifetime, origin, center, depth,
            size(progress), Math.min(1.0, opacity * fade * 1.4), ExtraConfig.SHOCKWAVE_REFRACT.get());
    }

    private void releaseStem(Vec3 center) {
        double angle = level.random.nextDouble() * TAU;
        Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        // Keep the ground fireball connected to the low smoke after the initial lift.
        Vec3 anchor = ground ? origin.lerp(center, .52) : center;
        Vec3 position = anchor.add(direction.scale(radius * .12));
        Vec3 drift = direction.scale(radius * .24).add(0, travel.y * .16, 0);
        var puff = new ImpactExplosionSmoke(level, position, drift, direction, radius * (ground ? .94f : .74f),
            opacity * .78f, .72f, Math.max(8, lifetime - age), false, ground, 1, light);
        Minecraft.getInstance().particleEngine.add(puff);
    }

    private float size(double progress) {
        double growth = clusterRiseTicks > 0 ? 1 - Math.pow(1 - Math.sqrt(progress), 2) : 1 - Math.pow(1 - progress, 2);
        double hotGrowth = 1;
        if (ground && luminousTicks > 0) {
            double growTicks = luminousTicks * ImpactExplosionConfig.groundFireballGrowTime.get();
            double hotProgress = Math.clamp(progress * lifetime / growTicks, 0, 1);
            double birthSize = ImpactExplosionConfig.groundFireballBirthSize.get();
            hotGrowth = birthSize + (1 - birthSize) * (1 - (1 - hotProgress) * (1 - hotProgress));
        }
        return (float)(radius * (1 + expansion * growth) * hotGrowth);
    }

    private void setBounds(float size) {
        setBoundingBox(new AABB(x - size, y - size, z - size, x + size, y + size, z + size));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return MuzzleParticleLayer.TYPE;
    }

    @Override
    public void render(VertexConsumer out, Camera camera, float partial) {
        if (age < 0 || !MuzzleParticleLayer.ribbonReady() || epoch != ClientFlashes.visualEpoch()) return;
        if (SmokeResolution.capture(this)) return;
        double elapsed = age + partial;
        double life = Math.clamp(elapsed / lifetime, 0, 1);
        double coldLife = Math.clamp((elapsed - luminousTicks) / (lifetime - luminousTicks), 0, 1);
        float size = size(life);
        float fadeProgress = smooth(fadeStart, 1, coldLife);
        float fade = 1 - fadeProgress;
        // Integrate the signed spin once from age, so moving the camera cannot change its direction.
        double rollSeconds = dustColumnMotion == null
            ? -Math.expm1(-1.05 * life) * lifetime / (1.05 * 20)
            : dustColumnMotion.rollSeconds(elapsed, lifetime);
        if (dustColumnMotion == null && slowHotRoll) {
            double hotLife = Math.min(life, (double)luminousTicks / lifetime);
            double hotRoll = -Math.expm1(-1.05 * hotLife) * lifetime / (1.05 * 20);
            rollSeconds -= .76 * hotRoll;
        }
        float tilt = initialRoll + rollSpeed * (float)rollSeconds;
        if (luminousTicks == 0) {
            int tint = dustTint != 0 ? dustTint : COLD_SMOKE_TINT;
            int atlasIndex = seed;
            if (denseDustColumn) {
                int frame = Math.min(15, (int)(Math.sqrt(life) * 16));
                atlasIndex = frame * 4 + (seed & 3);
            }
            MuzzleSmokeMesh.drawCachedPuff(out, camera, x, y, z, xo, yo, zo, partial, size, size * aspect,
                tilt, atlasIndex, denseDustColumn, (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
                (tint & 255) / 255f, opacity * fade, light, true, ground && dustTint == 0);
            return;
        }
        float breakup = (float) BigSmokeModel.breakup(fadeProgress);
        float detail = breakup * 1.75f;
        if (ground) {
            // Start contour erosion at the cooling handoff. Interior thinning still follows the fade.
            double coolingSpread = .44 + .42 * Math.min(1.0, coldLife / .45);
            breakup = Math.max(breakup, (float)BigSmokeModel.breakup(coolingSpread));
        }
        double materialAge = BigSmokeModel.impactMaterialAge(elapsed, luminousTicks, coldLife);
        int encoded = BigSmokeModel.encodedSeed(seed, materialAge);
        MuzzleSmokeMesh.drawThermalCluster(out, camera, x, y, z, xo, yo, zo, partial, size,
            size * aspect, tilt, encoded, opacity * fade, opacity,
            breakup, detail, light, ground);
    }

    private static float smooth(double from, double to, double value) {
        double t = Math.clamp((value - from) / (to - from), 0, 1);
        return (float)(t * t * (3 - 2 * t));
    }
}
