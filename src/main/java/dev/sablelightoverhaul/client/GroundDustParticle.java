package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.AutocannonHazeModel;
import dev.sablelightoverhaul.GasVertexData;
import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import dev.sablelightoverhaul.lighting.ParticleFade;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The dust cloud: this mod's control program over a smoke sprite.
 *
 * <p>Motion is integrated here per axis rather than by vanilla physics, because dust is not smoke:
 * the horizontal axes get a heavy damping so a cloud stops almost where it was stirred up and a
 * trail is made of many clouds left behind, while the vertical axis only ever sees gravity. There is
 * no lift term at all, so anything that leaves a hull sinks.
 *
 * <p><b>It does not grow.</b> The sprite used to be vanilla's {@code big_smoke_0..11}, and that
 * sequence swells from a smudge to its largest frame at index 4 before thinning away again, so playing
 * it from the start inflated every cloud even though its quad never changed. The frames are now this
 * mod's own copies — {@code sablelightoverhaul:dust_0..7}, laid out in {@code particles/ground_dust.json}
 * — and they begin at the largest frame and only go forward from there. The puff is therefore born at
 * full size and dissolves; the lifetime is untouched.
 *
 * <p>Measured on the originals (covered pixels / bounding box): frame 4 is the largest at 107 px and
 * 12x12, falling to 86, 72, 47, 36, 21, 12 and finally 4 px at frame 11, which is where dust_0..7
 * come from.
 *
 * <p>One camera-facing quad is drawn. The flat, world-horizontal quad that used to be drawn alongside
 * it (behind a {@code dust.groundPlane} switch) was never visible from a normal viewing angle, and its
 * switch is gone with it.
 */
public final class GroundDustParticle extends WindborneParticle {
    private static final ResourceLocation SOFT_HAZE_SPRITE =
        ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", "soft_smoke");
    private static SpriteSet sharedSprites;
    private net.minecraft.world.phys.Vec3 sweepNormal;
    private net.minecraft.world.phys.Vec3 sweepVelocity;
    /** Haze only: the horizontal direction this puff spreads along, and this frame's light gain. */
    private net.minecraft.world.phys.Vec3 hazeOutward;
    private float brightness = 1.0F;
    /** Haze motion, handed in with the shot: how it moves is behaviour, and behaviour is the server's. */
    private double hazeDrift, hazeSink, hazeClearance;
    private float hazeLightGain = 1.0F;
    private int sweepDelay;
    private long hazeSource;
    private boolean softMuzzleHaze;
    private final dev.sablelightoverhaul.HazeMass hazeMass;
    private static long maintenanceTick = Long.MIN_VALUE;
    private static int maintenanceEpoch = -1, maintenanceLimit = -1;
    private static long hazeCreated, hazeMerged, hazeDropped, lastReportTick = Long.MIN_VALUE, lastReportedChanges;

    /** Admission is checked for every child, not just once before a burst that can overshoot the quota. */
    public static Particle muzzleHaze(ClientLevel level, net.minecraft.world.phys.Vec3 at,
            net.minecraft.world.phys.Vec3 velocity, GroundDustParticleData data,
            double drift, double sink, double clearance, float lightGain, long source,
            net.minecraft.world.phys.Vec3 muzzle, double radius, int localMax, boolean softMuzzleHaze) {
        tickHaze();
        if (sharedSprites == null || !ClientFlashes.effectsEnabled() || ExtraConfig.VEHICLE_HAZE_MAX.get() <= 0) return null;
        GroundDustParticle nearest = null;
        double best = Double.MAX_VALUE;
        int local = 0;
        for (var p : HAZE) {
            if (p.level != level || p.hazeSource != source || !p.isAlive()) continue;
            double d = p.getPos().distanceToSqr(muzzle);
            if (d > radius * radius) continue;
            local++;
            if (p.softMuzzleHaze != softMuzzleHaze || p.lifetime != data.lifetime() || Math.abs(p.baseAlpha - data.alpha()) > 1e-5
                    || Math.abs(p.birthSize - data.size()) > 1e-5 || Math.abs(p.hazeDrift - drift) > 1e-6
                    || Math.abs(p.hazeSink - sink) > 1e-6 || Math.abs(p.hazeClearance - clearance) > 1e-6
                    || Math.abs(p.hazeLightGain - lightGain) > 1e-6) continue;
            double score = p.getPos().distanceToSqr(at);
            if (score < best) { best = score; nearest = p; }
        }
        boolean full = (localMax > 0 && local >= localMax) || HAZE.size() >= ExtraConfig.VEHICLE_HAZE_MAX.get();
        if (full) {
            if (nearest != null) { nearest.hazeMass.add(level.getGameTime()); hazeMerged++; }
            else hazeDropped++; // Never transfer smoke between unrelated emitters to hide a global budget overflow.
            return null;
        }
        var created = (GroundDustParticle) programmed(level, at, velocity, null, 0, data, drift, sink, clearance, lightGain);
        if (created != null) {
            created.hazeSource = source;
            created.softMuzzleHaze = softMuzzleHaze;
            hazeCreated++;
        }
        return created;
    }
    public static Particle programmed(ClientLevel level, net.minecraft.world.phys.Vec3 at,
            net.minecraft.world.phys.Vec3 velocity, net.minecraft.world.phys.Vec3 normal, int delay, GroundDustParticleData data) {
        return programmed(level, at, velocity, normal, delay, data, 0.0, 0.0, 0.0, 1.0F);
    }
    /**
     * The same, carrying the motion the shot arrived with.
     *
     * <p>Dust passes zeros because it has no haze motion; haze passes what the server sent. The values live on
     * the particle because drift, sink and clearance are consulted every tick, long after the packet that
     * carried them is gone.
     */
    public static Particle programmed(ClientLevel level, net.minecraft.world.phys.Vec3 at,
            net.minecraft.world.phys.Vec3 velocity, net.minecraft.world.phys.Vec3 normal, int delay,
            GroundDustParticleData data, double drift, double sink, double clearance, float lightGain) {
        if (sharedSprites == null || !ClientFlashes.effectsEnabled()) return null;
        tickHaze();
        if (data.haze() && HAZE.size() >= ExtraConfig.VEHICLE_HAZE_MAX.get()) return null;
        var p = new GroundDustParticle(level, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, data, sharedSprites);
        p.sweepNormal = normal;
        p.sweepVelocity = velocity;
        p.sweepDelay = delay;
        p.hazeDrift = drift;
        p.hazeSink = sink;
        p.hazeClearance = clearance;
        p.hazeLightGain = lightGain;
        return p;
    }

    /**
     * A shot shoves every puff near the muzzle horizontally away from it. Client side, no server state.
     *
     * @param axis the barrel's direction, so the puffs behind the muzzle can be pushed harder or softer than
     *             the ones in front -- see {@link dev.sablelightoverhaul.CannonDustMotion#REAR_PUSH}
     */
    public static void pushHaze(net.minecraft.world.phys.Vec3 muzzle, net.minecraft.world.phys.Vec3 axis,
            double radius, double impulse) {
        tickHaze();
        for (var p : HAZE) {
            var push = dev.sablelightoverhaul.CannonDustMotion.gustHorizontal(
                new net.minecraft.world.phys.Vec3(p.x, p.y, p.z), muzzle, axis, radius, impulse);
            if (push.lengthSqr() <= 0) continue;
            var velocity = dev.sablelightoverhaul.CannonDustMotion.cap(
                new net.minecraft.world.phys.Vec3(p.xd, p.yd, p.zd).add(push), .22);
            p.setParticleSpeed(velocity.x, velocity.y, velocity.z);
            // Keep drifting along the new direction, otherwise the outward easing would pull the puff
            // straight back to where the push found it.
            double flat = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (flat > 1e-5) p.hazeOutward = new net.minecraft.world.phys.Vec3(velocity.x / flat, 0, velocity.z / flat);
        }
    }
    private static final java.util.Set<GroundDustParticle> HAZE = new java.util.LinkedHashSet<>();
    /** How many haze puffs sit within this radius of a point. Squared distances only: no roots, no allocation,
  * and the caller is the client, which owns these particles in the first place. */
 public static int hazeNear(double x,double y,double z,double radius){
  double limit=radius*radius;
  int found=0;
  for(var puff:HAZE){
   double dx=puff.x-x, dy=puff.y-y, dz=puff.z-z;
   if(dx*dx+dy*dy+dz*dz<=limit)found++;
  }
  return found;
 } public static void clearHaze() {
        MuzzleVisualProbe.clear();
        for (var particle : java.util.List.copyOf(HAZE)) particle.remove();
        HAZE.clear();
        maintenanceTick = lastReportTick = Long.MIN_VALUE;
        maintenanceEpoch = -1;
        hazeCreated = hazeMerged = hazeDropped = lastReportedChanges = 0;
    }
    public static void tickHaze() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        int epoch = ClientFlashes.visualEpoch(), limit = ExtraConfig.VEHICLE_HAZE_MAX.get();
        if (maintenanceTick == now && maintenanceEpoch == epoch && maintenanceLimit == limit) return;
        maintenanceTick = now; maintenanceEpoch = epoch; maintenanceLimit = limit;
        var iterator = HAZE.iterator();
        while (iterator.hasNext()) {
            var p = iterator.next();
            if (!p.isAlive() || p.epoch != epoch || p.level != level || !p.hazeMass.alive(now)) {
                iterator.remove(); p.remove();
            }
        }
        while (HAZE.size() > limit) { HAZE.iterator().next().remove(); hazeDropped++; }
        long changes = hazeCreated + hazeMerged + hazeDropped;
        if (changes != lastReportedChanges && (lastReportTick == Long.MIN_VALUE || now < lastReportTick || now-lastReportTick >= 20)) {
            lastReportTick = now; lastReportedChanges = changes;
            com.mojang.logging.LogUtils.getLogger().info(
                    "SLO muzzle haze: carriers={} created={} merged={} dropped={} layer=no-depth-write (cumulative)",
                    HAZE.size(), hazeCreated, hazeMerged, hazeDropped);
        }
    }
    private final long bornTick;
    private final boolean haze;
    private final float phase;
    private final int epoch = ClientFlashes.visualEpoch();
    /** The frames have to be kept: the cloud steps through them as it dissolves. */
    private final SpriteSet sprites;
    private final float birthSize;
    private final float stretch;
    /** The sprite's own turn, in radians; zero leaves the texture axis-aligned. */
    private float rotation;
    /** The shrink that keeps a turned rectangle inside the frame it came from. */
    private float rotationFit = 1.0F;
    private float rotationCos = 1.0F, rotationSin = 0.0F;
    private final float damping;
    private final float pull;
    private final float baseAlpha;
    /** The fraction of the life spent fading out; see {@link ParticleFade}. */
    private final float fade;
    private GroundDustParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                               GroundDustParticleData data, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.haze = data.haze();
        this.bornTick = level.getGameTime();
        this.phase = random.nextFloat() * (float)(2 * Math.PI);
        // Dust is drawn with its sprite turned about its own centre by a random angle, which is what stops a
        // field of puffs from looking like one shape repeated. The turn is in the texture coordinates, not in
        // the billboard, so the stretch and the size are untouched; the coordinates are shrunk by the factor
        // that lets a turned rectangle fit inside an axis-aligned one, so no corner samples a neighbouring
        // frame in the atlas. It is spread over +/- the configured number of degrees.
        double spread = 0;
        try {
            spread = ExtraConfig.GROUND_DUST_ROTATION.get();
        } catch (RuntimeException notLoadedYet) {
            spread = 0;
        }
        this.rotation = (float) Math.toRadians(spread * (random.nextFloat() * 2.0F - 1.0F));
        float turnCos = (float) Math.abs(Math.cos(this.rotation));
        float turnSin = (float) Math.abs(Math.sin(this.rotation));
        this.rotationFit = 1.0F / Math.max(0.15F, turnCos + turnSin);
        this.rotationCos = (float) Math.cos(this.rotation);
        this.rotationSin = (float) Math.sin(this.rotation);
        if (Math.abs(this.rotation) < 1.0e-4) this.rotation = 0;
        if (haze) HAZE.add(this);
        this.birthSize = Math.max(0.1F, data.size());
        // Dust is a patch, not a balloon: all of its presence comes from how big it is born.
        this.stretch = Math.min(Math.max(data.stretch(), 1.0F), 6.0F);
        this.damping = Math.min(Math.max(data.damping(), 0.1F), 1.0F);
        // Downward acceleration is applied by this class, so vanilla's own gravity stays off.
        this.pull = Math.max(data.gravity(), 0.0F);
        this.baseAlpha = Math.min(Math.max(data.alpha(), 0.02F), 1.0F);
        this.fade = Math.min(Math.max(data.fade(), 0.02F), 1.0F);
        this.lifetime = Math.max(2, data.lifetime());
        this.hazeMass = haze ? new dev.sablelightoverhaul.HazeMass(this.lifetime, this.baseAlpha) : null;
        if (haze) this.hazeMass.add(level.getGameTime());
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = true;
        this.quadSize = birthSize;
        int rgb = BlockDebrisColor.resolve(level,data.materialState(),data.materialPos(),data.color());
        this.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        this.setAlpha(haze ? 0 : baseAlpha);
        this.setParticleSpeed(dx, dy, dz);
        // Haze spreads outwards from where it was born. The birth velocity is already radial, so the
        // direction it is integrated along is captured once here instead of being recomputed every tick.
        if (haze) {
            double flat = Math.sqrt(dx * dx + dz * dz);
            this.hazeOutward = flat < 1e-6
                ? new net.minecraft.world.phys.Vec3(random.nextDouble() - .5, 0, random.nextDouble() - .5).normalize()
                : new net.minecraft.world.phys.Vec3(dx / flat, 0, dz / flat);
        }
        // Starts on the largest frame and only ever goes forward from there, so nothing inflates.
        this.setSprite(sprites.get(haze ? 0 : dustFrame(0), this.lifetime));
    }
    @Override public void remove() { super.remove(); HAZE.remove(this); }
    @Override public void tick() {
        // A new visual epoch means a new world or a mode change; old clouds must not survive it.
        if (!ClientFlashes.effectsEnabled() || epoch != ClientFlashes.visualEpoch()) { remove(); return; }
        if (haze && ExtraConfig.VEHICLE_HAZE_MAX.get() == 0) { remove(); return; }
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        if (sweepNormal != null && age < sweepDelay + 6) {
            // The stagger exists so a cluster sweeps open instead of moving as one slab. Before a puff's turn
            // comes it keeps the velocity it was born with, rather than being held still: holding it looked
            // like the dust only started moving after a pause, and a puff that is born with speed should use it.
            int sweepAge = age - sweepDelay;
            if (sweepAge >= 0) {
                var v = sweepVelocity.scale(Math.sin((sweepAge + 1) * Math.PI / 14)).add(sweepNormal.scale(.025));
                this.setParticleSpeed(v.x, v.y, v.z);
            }
        }
        if (haze) {
            // Haze is not dust: its motion is the client's own business and deliberately ignores the
            // emitter's damping and gravity. It eases outwards along the direction it was born with
            // until it reaches the drift cap, wobbles a little, and sinks slowly under its own setting,
            // so it spreads where you can see it and then stops growing instead of piling up at the muzzle.
            double flat = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
            double drift = this.hazeDrift;
            if (drift > flat && this.hazeOutward != null) {
                double add = (drift - flat) * .05;
                this.xd += this.hazeOutward.x * add;
                this.zd += this.hazeOutward.z * add;
            }
            this.xd += Math.sin(phase + age * .075) * .0005;
            this.zd += Math.cos(phase + age * .06) * .0005;
            this.xd *= .995;
            this.zd *= .995;
            // A sinking puff is optional: the default is that thin haze never sinks at all, so it only
            // spreads sideways and fades, which is what muzzle haze actually does. Turning sink on keeps
            // it above the ground (see keepClearance).
            double sink = this.hazeSink;
            this.yd = sink > 0 ? this.yd - sink : 0;
        } else {
            this.xd *= damping;
            this.zd *= damping;
            this.yd -= 0.04 * this.pull;
        }
        this.move(this.xd, this.yd, this.zd);
        if (haze && this.hazeSink > 0) keepClearance();
        this.age++;
        float progress = (float) this.age / this.lifetime;
        // Duration, not size: only the opacity and the sprite frame change over its life. The frames are
        // ours now (see the class comment) and run from the largest one onwards, so stepping through them
        // dissolves a cloud and never grows it. How much of the life the fade takes is the page's `fade`
        // field (0.4 was hard-coded here before it was configurable), and the haze keeps its own envelope.
        this.setAlpha(haze
                ? hazeMass.alpha(level.getGameTime(), 0)
                : ParticleFade.alpha(progress, baseAlpha, this.fade));
        // The haze keeps a broad silhouette; opacity, rather than a shrinking sprite, dissipates it.
        if (!softMuzzleHaze) {
            this.setSprite(sprites.get(haze ? Math.min(hazeMass.spriteAge(level.getGameTime()) / 5, this.lifetime / 3) : dustFrame(this.age), this.lifetime));
        }
        if (haze ? !hazeMass.alive(level.getGameTime()) : this.age >= this.lifetime) remove();
    }
    /**
     * Which sprite frame dust shows at this age.
     *
     * <p>An empty frame list means the old behaviour: one frame per tick, forwards, stopping on the last one.
     * A list means play exactly those frames, each for the configured number of ticks, looping. Frames are
     * clamped to the particle's own count so a config cannot ask for a frame that is not there.
     */
    private int dustFrame(int age) {
        var frames = ExtraConfig.GROUND_DUST_FRAMES.get();
        if (frames.isEmpty()) return Math.min(age, this.lifetime - 1);
        int per = Math.max(1, ExtraConfig.GROUND_DUST_FRAME_TICKS.get());
        int chosen = ((Number) frames.get((age / per) % frames.size())).intValue();
        return Math.min(Math.max(chosen, 0), this.lifetime - 1);
    }
    @Override public ParticleRenderType getRenderType() {
        return softMuzzleHaze ? MuzzleParticleLayer.TYPE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    /**
     * Keeps a puff that is allowed to sink at least the configured distance above the block under it.
     * The quad is several blocks across while the particle's own bounding box is a point, so without
     * this the cloud's centre settles right on the surface and half of it ends up inside the ground.
     * Only ever called when sinking is enabled; the scan is a handful of block reads and stops at the
     * first solid one.
     */
    private void keepClearance() {
        double clearance = this.hazeClearance;
        var from = net.minecraft.core.BlockPos.containing(x, y, z);
        for (int i = 0; i <= 4; i++) {
            var at = from.below(i);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()) continue;
            double floor = at.getY() + 1 + clearance;
            if (y < floor) { this.y = floor; this.yd = 0; }
            return;
        }
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(float partialTick) {
        return haze ? new net.minecraft.world.phys.AABB(x, y, z, x, y, z).inflate(birthSize * stretch) : super.getRenderBoundingBox(partialTick);
    }
    /** Dust is one quad; haze uses three low-opacity overlapping lobes. */
    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (!ClientFlashes.effectsEnabled() || epoch != ClientFlashes.visualEpoch()) return;
        if (softMuzzleHaze) {
            renderSoftHaze(buffer, camera, partialTick);
            return;
        }
        var cameraPos = camera.getPosition();
        float ox = (float) (net.minecraft.util.Mth.lerp(partialTick, xo, x) - cameraPos.x);
        float oy = (float) (net.minecraft.util.Mth.lerp(partialTick, yo, y) - cameraPos.y);
        float oz = (float) (net.minecraft.util.Mth.lerp(partialTick, zo, z) - cameraPos.z);
        int light = this.getLightColor(partialTick);
        // Translucent particles are drawn after the GPU light pass has already run, so that pass cannot
        // brighten them: ask the same published light list what is falling on this puff and lift its
        // colour by that much. Dust keeps its plain colour; only haze is a thing a light falls on.
        this.brightness = haze
            ? 1.0F + (float)Math.min(1.0, GpuLightRenderer.sampleLight(x, y, z) / 15.0) * this.hazeLightGain
            : 1.0F;
        float half = this.getQuadSize(partialTick) * 0.5F;
        float halfWidth = half * stretch;
        Quaternionf rotation = new Quaternionf(camera.rotation());
        if (haze) rotation.rotateZ(0.12F * (float)Math.sin(phase + (age + partialTick) * .015));
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
        if (haze) {
            float opacity = hazeMass.alpha(level.getGameTime(), partialTick);
            float layerAlpha = dev.sablelightoverhaul.HazeEnvelope.layerAlpha(opacity, 3);
            for (int lobe = 0; lobe < 3; lobe++) {
                float dx = (lobe - 1) * halfWidth * .4F;
                float dy = (float)Math.sin(phase + lobe * 2.1) * half * .18F;
                quad(buffer, ox + right.x() * dx + up.x() * dy, oy + right.y() * dx + up.y() * dy,
                    oz + right.z() * dx + up.z() * dy, right, up, halfWidth * .72F, half * .85F, light, layerAlpha);
            }
        } else quad(buffer, ox, oy, oz, right, up, halfWidth, half, light, alpha);
    }

    private void renderSoftHaze(VertexConsumer out, Camera camera, float partial) {
        float halfHeight = birthSize * .5f * AutocannonHazeModel.growth(age + partial, lifetime);
        float halfWidth = halfHeight * stretch;
        var eye = camera.getPosition();
        double distance = Math.sqrt(eye.distanceToSqr(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z)));
        float opacity = AutocannonHazeModel.opacity(hazeMass.alpha(level.getGameTime(), partial), baseAlpha)
            * AutocannonHazeModel.cameraFade(distance, halfHeight);
        if (opacity <= .002f) return;

        float gain = 1f + (float)Math.min(1, GpuLightRenderer.sampleLight(x, y, z) / 15) * hazeLightGain;
        float red = Math.min(1, rCol * .76f * gain);
        float green = Math.min(1, gCol * .75f * gain);
        float blue = Math.min(1, bCol * .73f * gain);
        int light = getLightColor(partial);
        if (MuzzleParticleLayer.proceduralSmoke()) {
            int seed = (int)(phase * (64 / (Math.PI * 2)));
            MuzzleSmokeMesh.drawCachedPuff(out, camera, x, y, z, xo, yo, zo, partial, halfWidth, halfHeight, 0,
                seed, red, green, blue, opacity, GasVertexData.smokeLight(light));
        } else {
            var atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            var soft = atlas.getSprite(SOFT_HAZE_SPRITE);
            MuzzleSmokeMesh.drawAtlasBillboard(out, camera, x, y, z, xo, yo, zo, partial, halfWidth, halfHeight,
                soft.getU0(), soft.getU1(), soft.getV0(), soft.getV1(), red, green, blue, opacity, light);
        }
    }
    private void quad(VertexConsumer buffer, float ox, float oy, float oz, Vector3f right, Vector3f up,
                      float halfWidth, float halfHeight, int light, float alpha) {
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        // Only dust turns: haze's three lobes are placed deliberately and a rotation would fight that.
        if (!haze && rotation != 0) {
            float cu = (u0 + u1) * 0.5F, cv = (v0 + v1) * 0.5F;
            float hu = (u1 - u0) * 0.5F * rotationFit, hv = (v1 - v0) * 0.5F * rotationFit;
            float c = rotationCos, s = rotationSin;
            // The four corners of the sprite rectangle, turned about its centre, in the quad's own corner order:
            // (-1,-1), (1,-1), (1,1), (-1,1).
            vertex(buffer, ox, oy, oz, right, up, -halfWidth, -halfHeight,
                    cu + (-hu * c + hv * s), cv + (-hu * s - hv * c), light, alpha);
            vertex(buffer, ox, oy, oz, right, up, halfWidth, -halfHeight,
                    cu + (hu * c + hv * s), cv + (hu * s - hv * c), light, alpha);
            vertex(buffer, ox, oy, oz, right, up, halfWidth, halfHeight,
                    cu + (hu * c - hv * s), cv + (hu * s + hv * c), light, alpha);
            vertex(buffer, ox, oy, oz, right, up, -halfWidth, halfHeight,
                    cu + (-hu * c - hv * s), cv + (-hu * s + hv * c), light, alpha);
            return;
        }
        vertex(buffer, ox, oy, oz, right, up, -halfWidth, -halfHeight, u0, v1, light, alpha);
        vertex(buffer, ox, oy, oz, right, up, halfWidth, -halfHeight, u1, v1, light, alpha);
        vertex(buffer, ox, oy, oz, right, up, halfWidth, halfHeight, u1, v0, light, alpha);
        vertex(buffer, ox, oy, oz, right, up, -halfWidth, halfHeight, u0, v0, light, alpha);
    }
    private void vertex(VertexConsumer buffer, float ox, float oy, float oz, Vector3f right, Vector3f up,
                        float dx, float dy, float u, float v, int light, float alpha) {
        buffer.addVertex(ox + right.x() * dx + up.x() * dy, oy + right.y() * dx + up.y() * dy, oz + right.z() * dx + up.z() * dy)
                .setUv(u, v)
                .setColor(Math.min(1.0F, rCol * brightness), Math.min(1.0F, gCol * brightness), Math.min(1.0F, bCol * brightness), alpha)
                .setLight(light);
    }
    public static final class Provider implements ParticleProvider<GroundDustParticleData> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; sharedSprites = sprites; HullDustParticle.sprites(sprites); }
        @Override public Particle createParticle(GroundDustParticleData data, ClientLevel level,
                double x, double y, double z, double dx, double dy, double dz) {
            if (data.haze() && !ClientFlashes.effectsEnabled()) return null;
            HAZE.removeIf(p -> p.epoch != ClientFlashes.visualEpoch() || p.level != level || !p.isAlive());
            if (data.haze() && HAZE.size() >= ExtraConfig.VEHICLE_HAZE_MAX.get()) return null;
            return new GroundDustParticle(level, x, y, z, dx, dy, dz, data, sprites);
        }
    }
}
