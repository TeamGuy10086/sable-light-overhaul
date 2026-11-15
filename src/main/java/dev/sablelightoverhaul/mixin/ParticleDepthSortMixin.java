package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.ExtraConfig;
import dev.sablelightoverhaul.client.GameClock;
import dev.sablelightoverhaul.client.ParticleDepthSort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import dev.sablelightoverhaul.client.VoxyAccess;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws every particle layer far to near.
 *
 * <p>Vanilla walks each layer in spawn order and never sorts, so on a translucent layer a faint quad
 * spawned early claims the depth and culls whatever appears behind it later -- see
 * {@link ParticleDepthSort} for the whole argument, and
 * {@code research/CBC-CANNON-SMOKE-2026-10-05.zh-CN.md} §5 for what it looked like in game (the tank
 * cannon's haze hiding every other particle).
 *
 * <p>The injection is at the head of the five-argument {@code render}, which the three-argument one
 * delegates to, so both entry points are covered by one hook. Layers the caller is not drawing this
 * pass are skipped -- Iris calls this method twice per frame to split particles around the translucent
 * chunk layer, and sorting a layer that is about to be filtered out is wasted work.
 *
 * <p>Only layers that call themselves translucent are sorted. The opaque ones do not care: they neither
 * blend nor depend on the order, and they are the ones that get big (block breaking).
 *
 * <p><b>One side effect worth knowing</b>: the per-layer queue holds at most 16384 particles and evicts
 * from its head when full. That head was "the oldest" and is now "the farthest", so under a flood the
 * cap drops distant particles instead of old ones. Nothing else about the cap changes.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleDepthSortMixin {
    @Shadow @Final private Map<ParticleRenderType, Queue<Particle>> particles;

    @Unique private final dev.sablelightoverhaul.TransientParticleBatch<ParticleRenderType,Particle> slo$merged=new dev.sablelightoverhaul.TransientParticleBatch<>();
    @Unique private final dev.sablelightoverhaul.TransientParticleBatch<ParticleRenderType,Particle> slo$waveMerged=new dev.sablelightoverhaul.TransientParticleBatch<>();

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V")
    private void slo$drawCompatibleTogether(LightTexture light,Camera camera,float partial,Frustum frustum,
            Predicate<ParticleRenderType> predicate,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){
        var ordinary=ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        var smoke=dev.sablelightoverhaul.client.MuzzleParticleLayer.TYPE;
        boolean owns=false;
        if(ExtraConfig.PARTICLE_DEPTH_SORT.get()&&dev.sablelightoverhaul.client.ClientFlashes.inPackLighting()
                &&predicate.test(ordinary)&&predicate.test(smoke))
            owns=slo$merged.begin(particles,ordinary,smoke,ExtraConfig.PARTICLE_SORT_MAX.get());
        boolean waveOwns=false;
        var wave=dev.sablelightoverhaul.client.ShockwaveParticle.RENDER_TYPE;
        if(ExtraConfig.PARTICLE_DEPTH_SORT.get()&&dev.sablelightoverhaul.client.ClientFlashes.inPackLighting()
                &&predicate.test(wave)&&predicate.test(smoke))
            waveOwns=slo$waveMerged.begin(particles,wave,smoke,ExtraConfig.PARTICLE_SORT_MAX.get());
        try{original.call(light,camera,partial,frustum,predicate);}
        finally{
            // RETURN injectors do not run on exceptions. Restore parked references before releasing scratch.
            slo$restoreCulled();
            if(waveOwns)slo$waveMerged.close();
            if(owns)slo$merged.close();
        }
    }
    @Unique private void slo$restoreCulled(){
        for(int i=0;i<slo$parkedParticles.size();i++)slo$parkedQueues.get(i).add(slo$parkedParticles.get(i));
        slo$parkedParticles.clear();slo$parkedQueues.clear();
    }

    /** Scratch, reused every frame: this runs on the render thread only. */
    @Unique private double[] slo$xs = new double[0];
    @Unique private double[] slo$ys = new double[0];
    @Unique private double[] slo$zs = new double[0];
    @Unique private long[] slo$packed = new long[0];
    @Unique private int[] slo$order = new int[0];
    @Unique private final List<Particle> slo$snapshot = new ArrayList<>();
    @Unique private final List<Particle> slo$visible = new ArrayList<>();
    @Unique private final List<Particle> slo$previousOrder = new ArrayList<>();
    @Unique private final java.util.Map<Particle, Integer> slo$index = new java.util.IdentityHashMap<>();
    /** Particles held out of the draw for this frame only, with the queue they must go back into. */
    @Unique private final List<Queue<Particle>> slo$parkedQueues = new ArrayList<>();
    @Unique private final List<Particle> slo$parkedParticles = new ArrayList<>();

    /**
     * Put the parked particles back the moment the draw is over.
     *
     * <p>This is what makes the cull non-destructive. The engine ticks these very queues, so a particle left
     * out across a tick would stop ageing and never return; held out only for the draw call and restored
     * here, it lives normally and is drawn again as soon as the camera or the smoke itself clears the ridge.
     */
    @Inject(
        method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
        at = @At("RETURN"),
        require = 1
    )
    private void slo$restoreParked(LightTexture lightTexture, Camera camera, float partialTick,
                                   Frustum frustum, Predicate<ParticleRenderType> renderTypePredicate,
                                   CallbackInfo callback) {
        if (slo$parkedParticles.isEmpty()) return;
        for (int i = 0; i < slo$parkedParticles.size(); i++) {
            slo$parkedQueues.get(i).add(slo$parkedParticles.get(i));
        }
        slo$parkedParticles.clear();
        slo$parkedQueues.clear();
    }

    @Inject(
        method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
        at = @At("HEAD"),
        require = 1
    )
    private void slo$sortByDepth(LightTexture lightTexture, Camera camera, float partialTick,
                                 Frustum frustum, Predicate<ParticleRenderType> renderTypePredicate,
                                 CallbackInfo callback) {
        if (!ExtraConfig.PARTICLE_DEPTH_SORT.get()) return;
        // Iris calls this twice a frame and there are more frames than ticks: the occlusion budget has to
        // be handed out per pass, or it is spent long before the frame it was meant for.
        VoxyAccess.beginFrame();
        // Sorting is n log n per drawn translucent layer and Iris calls this twice a frame, so the cap is
        // what keeps the cost bounded on the frames where a layer has been flooded. A layer that big is
        // unreadable anyway, and skipping it also restores vanilla's eviction order for that layer (the
        // captions in the config say why that alone does not save anybody's smoke).
        int cap = ExtraConfig.PARTICLE_SORT_MAX.get();
        boolean debug = ExtraConfig.on(ExtraConfig.PARTICLE_SORT_DEBUG);
        long started = debug ? System.nanoTime() : 0L;
        int layers = 0, sorted = 0, skipped = 0, largest = 0;
        Vec3 eye = camera.getPosition();
        for (Map.Entry<ParticleRenderType, Queue<Particle>> entry : particles.entrySet()) {
            ParticleRenderType type = entry.getKey();
            if (type == ParticleRenderType.NO_RENDER || !type.isTranslucent()) continue;
            if (!renderTypePredicate.test(type)) continue;
            Queue<Particle> queue = entry.getValue();
            int count = queue.size();
            if (count > largest) largest = count;
            if (count < 2) continue;
            layers++;
            if (cap > 0 && count > cap) {
                skipped++;
                // Sorting this layer is skipped because it is flooded -- but the cull must not be. A barrage
                // makes a layer far larger than the cap, and leaving it untouched is what let whole clouds
                // reappear the instant a shell landed: not one ray was cast for it. The layer keeps its
                // original order here and only loses what cannot be seen.
                if (ExtraConfig.OCCLUSION_ENABLED.get()) {
                    slo$snapshot.clear();
                    for (Particle particle : queue) {
                        ParticlePositionAccessor at = (ParticlePositionAccessor) (Object) particle;
                        if (VoxyAccess.blocked(eye, new Vec3(at.slo$x(), at.slo$y(), at.slo$z()), System.identityHashCode(particle))) {
                            slo$parkedQueues.add(queue);
                            slo$parkedParticles.add(particle);
                        } else {
                            slo$snapshot.add(particle);
                        }
                    }
                    queue.clear();
                    queue.addAll(slo$snapshot);
                }
                continue;
            }

            if (slo$xs.length < count) {
                int capacity = Math.max(count, slo$xs.length * 2);
                slo$xs = new double[capacity];
                slo$ys = new double[capacity];
                slo$zs = new double[capacity];
                slo$packed = new long[capacity];
                slo$order = new int[capacity];
            }
            slo$snapshot.clear();
            slo$snapshot.addAll(queue);
            // Cull first, sort second. A particle that will not be drawn has no business taking part in the
            // sort, and on a barrage the cull removes a large part of the layer -- this is where the saving is.
            net.minecraft.client.Camera slo$camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            net.minecraft.world.phys.Vec3 slo$eye = slo$camera.getPosition();
            net.minecraft.world.phys.Vec3 slo$look = new net.minecraft.world.phys.Vec3(slo$camera.getLookVector());
            slo$visible.clear();
            int slo$kept = 0;
            for (int slo$i = 0; slo$i < count; slo$i++) {
                Particle slo$particle = slo$snapshot.get(slo$i);
                // The position the particle is drawn at, near enough: the interpolated one is between
                // `xo` and `x`, and for a quad a fraction of a block apart the difference cannot change
                // which of two particles is in front.
                ParticlePositionAccessor slo$at = (ParticlePositionAccessor) (Object) slo$particle;
                double slo$px = net.minecraft.util.Mth.lerp(partialTick,slo$at.slo$previousX(),slo$at.slo$x());
                double slo$py = net.minecraft.util.Mth.lerp(partialTick,slo$at.slo$previousY(),slo$at.slo$y());
                double slo$pz = net.minecraft.util.Mth.lerp(partialTick,slo$at.slo$previousZ(),slo$at.slo$z());
                if (slo$particle instanceof dev.sablelightoverhaul.client.ProjectileStreaks.Streak streak) {
                    var centre=streak.sortingCentre(partialTick);
                    slo$px=centre.x; slo$py=centre.y; slo$pz=centre.z;
                } else if (slo$particle instanceof dev.sablelightoverhaul.client.AutoMuzzleParticle flame) {
                    var centre = flame.sortingCentre();
                    slo$px=centre.x; slo$py=centre.y; slo$pz=centre.z;
                } else if (slo$particle instanceof dev.sablelightoverhaul.client.FireballParticle flame) {
                    var centre=flame.sortingCentre();
                    slo$px=centre.x; slo$py=centre.y; slo$pz=centre.z;
                }
                // LOD occlusion must NOT be done by leaving a particle out here: this queue is the engine set of
                // live particles, so a missing one stops ticking and never comes back. The cull is applied
                // around the draw call only and put back before the next tick -- see the parked list below.
                if (VoxyAccess.blocked(slo$eye, new net.minecraft.world.phys.Vec3(slo$px, slo$py, slo$pz), System.identityHashCode(slo$particle))) {
                    slo$parkedQueues.add(queue);
                    slo$parkedParticles.add(slo$particle);
                    continue;
                }
                slo$xs[slo$kept] = slo$px;
                slo$ys[slo$kept] = slo$py;
                slo$zs[slo$kept] = slo$pz;
                if (type == dev.sablelightoverhaul.client.MuzzleParticleLayer.TYPE) {
                    // Sort this batch by eye-space depth; radial distance misorders wide off-axis smoke.
                    double depth = (slo$px-slo$eye.x)*slo$look.x+(slo$py-slo$eye.y)*slo$look.y+(slo$pz-slo$eye.z)*slo$look.z;
                    slo$xs[slo$kept] = slo$eye.x + Math.max(0,depth);
                    slo$ys[slo$kept] = slo$eye.y;
                    slo$zs[slo$kept] = slo$eye.z;
                }
                slo$visible.add(slo$particle);
                slo$kept++;
            }
            // Start from the previous frame's order when it is still usable. Between two frames the order barely
            // changes -- a camera moves a fraction of a block and particles drift -- and a primitive array sort
            // cannot use that, so it repeats the whole n log n every frame. The seeded pass gives up (and the
            // full sort runs) whenever the old order is useless, which is what the shift cap detects.
            int[] order;
            int slo$cap = Math.max(0, ExtraConfig.PARTICLE_SORT_MAX_SHIFTS.get()) * Math.max(1, slo$kept);
            if (!slo$previousOrder.isEmpty() && slo$cap > 0) {
                slo$index.clear();
                for (int slo$i = 0; slo$i < slo$kept; slo$i++) slo$index.put(slo$visible.get(slo$i), slo$i);
                int slo$at = 0;
                for (int slo$i = 0; slo$i < slo$previousOrder.size() && slo$at < slo$kept; slo$i++) {
                    Integer slo$now = slo$index.remove(slo$previousOrder.get(slo$i));
                    if (slo$now != null) slo$order[slo$at++] = slo$now;
                }
                // Whatever is left is new this frame: it has no place in the old order yet, so it goes on the end
                // and the seeded pass will move it where it belongs (or give up and let the full sort do it).
                for (Integer slo$now : slo$index.values()) {
                    if (slo$at < slo$kept) slo$order[slo$at++] = slo$now;
                }
                boolean slo$done = ParticleDepthSort.farToNearSeeded(
                    slo$xs, slo$ys, slo$zs, slo$kept, slo$eye.x, slo$eye.y, slo$eye.z, slo$packed, slo$order, slo$cap);
                order = slo$done ? slo$order : ParticleDepthSort.farToNear(
                    slo$xs, slo$ys, slo$zs, slo$kept, slo$eye.x, slo$eye.y, slo$eye.z, slo$packed, slo$order);
            } else {
                order = ParticleDepthSort.farToNear(
                    slo$xs, slo$ys, slo$zs, slo$kept, slo$eye.x, slo$eye.y, slo$eye.z, slo$packed, slo$order);
            }
            slo$previousOrder.clear();
            for (int slo$i = 0; slo$i < slo$kept; slo$i++) slo$previousOrder.add(slo$visible.get(order[slo$i]));
            // Rebuild the queue in that order. It is emptied first, so re-adding cannot evict anything:
            // the cap only ever drops a particle on an add that would exceed it.
            queue.clear();
            for (int slo$i = 0; slo$i < slo$kept; slo$i++) {
                queue.add(slo$visible.get(order[slo$i]));
            }
            sorted += slo$kept;
        }
        if (debug) slo$report(sorted, layers, skipped, largest, System.nanoTime() - started);
    }

    /**
     * One line a second, so "it feels laggy" can be read as a number.
     *
     * <p>Both the counts and the milliseconds are for one {@code render} call: Iris calls it twice per
     * frame, so a steady state reads about twice the per-frame work.
     */
    @Unique private static void slo$report(int sorted, int layers, int skipped, int largest, long nanos) {
        slo$sortedParticles += sorted;
        slo$sortedLayers += layers;
        slo$skippedLayers += skipped;
        slo$nanos += nanos;
        if (largest > slo$largestLayer) slo$largestLayer = largest;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        if (!GameClock.due(now, slo$lastReport, 20)) return;
        slo$lastReport = now;
        if (slo$sortedLayers == 0 && slo$skippedLayers == 0 && slo$largestLayer == 0) return;
        com.mojang.logging.LogUtils.getLogger().info(
            "Sable Light Overhaul particles: sorted {} particle(s) across {} layer(s) in {} ms; {} layer(s) over the {} cap left in vanilla order; largest layer this second = {}",
            slo$sortedParticles, slo$sortedLayers, String.format(java.util.Locale.ROOT, "%.2f", slo$nanos / 1.0E6),
            slo$skippedLayers, ExtraConfig.PARTICLE_SORT_MAX.get(), slo$largestLayer);
        slo$sortedParticles = 0;
        slo$sortedLayers = 0;
        slo$skippedLayers = 0;
        slo$nanos = 0L;
        slo$largestLayer = 0;
    }

    @Unique private static int slo$sortedParticles = 0;
    @Unique private static int slo$sortedLayers = 0;
    @Unique private static int slo$skippedLayers = 0;
    @Unique private static int slo$largestLayer = 0;
    @Unique private static long slo$nanos = 0L;
    @Unique private static long slo$lastReport = Long.MIN_VALUE;
}
