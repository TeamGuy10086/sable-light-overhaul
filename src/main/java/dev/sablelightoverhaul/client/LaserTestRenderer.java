package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.lighting.LaserTestBlock;
import dev.sablelightoverhaul.lighting.LaserTestBlockEntity;
import dev.sablelightoverhaul.lighting.SourceAnchor;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;

/** The block entity discovers emitters; the beam itself belongs to the translucent particle batch. */
final class LaserTestRenderer implements BlockEntityRenderer<LaserTestBlockEntity> {
    static final int VOLUME_SLOTS = 4;
    private static final java.util.List<WeakReference<Beam>> volumes =
        new java.util.ArrayList<>(java.util.Collections.nCopies(VOLUME_SLOTS, null));

    static org.joml.Vector4f volumeUniform(int slot, boolean origin) {
        var reference = volumes.get(slot);
        var beam = reference == null ? null : reference.get();
        if (beam == null || !beam.volumeActive()) return new org.joml.Vector4f(0f);
        Vec3 value = origin ? beam.volumeStart.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())
                            : beam.volumeAxis;
        return new org.joml.Vector4f((float)value.x, (float)value.y, (float)value.z, origin ? (float)beam.volumeLength : 4f);
    }

    static org.joml.Vector4f extinctionUniform() {
        var result = new org.joml.Vector4f();
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        for (int i = 0; i < VOLUME_SLOTS; i++) {
            var ref = volumes.get(i);
            var beam = ref == null ? null : ref.get();
            if (beam != null) result.setComponent(i, (float)(1 - beam.energy(partial)));
        }
        return result;
    }

    static org.joml.Vector4f flowUniform(int slot) {
        var ref = volumes.get(slot);
        var beam = ref == null ? null : ref.get();
        if (beam == null || !beam.volumeActive()) return new org.joml.Vector4f(0f);
        long seed = beam.block.getBlockPos().asLong();
        double time = animationTick();
        return new org.joml.Vector4f((float)LaserBeamMesh.phase(time, seed, .85),
            (float)LaserBeamMesh.phase(time, seed + 1, 1.71),
            (float)LaserBeamMesh.phase(time, seed + 2, .010),
            (float)LaserBeamMesh.phase(time, seed + 3, .003));
    }

    private static final long CLOCK_ORIGIN = System.nanoTime();

    private static double animationTick() {
        return (System.nanoTime() - CLOCK_ORIGIN) / 50_000_000.0;
    }

    private static final double RANGE = 96;
    private static final ResourceLocation WHITE =
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "volume_white");
    private final WeakHashMap<LaserTestBlockEntity, WeakReference<Beam>> active = new WeakHashMap<>();

    LaserTestRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public boolean shouldRenderOffScreen(LaserTestBlockEntity block) { return true; }
    @Override public int getViewDistance() { return 160; }

    @Override public void render(LaserTestBlockEntity block, float partial, PoseStack stack,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (!(block.getLevel() instanceof ClientLevel level) || !powered(block)) return;
        var reference = active.get(block);
        var existing = reference == null ? null : reference.get();
        if (existing == null || !existing.isAlive()) {
            var beam = new Beam(level, block);
            active.put(block, new WeakReference<>(beam));
            Minecraft.getInstance().particleEngine.add(beam);
        }
    }

    private static boolean powered(LaserTestBlockEntity block) {
        var state = block.getBlockState();
        return !block.isRemoved() && state.getBlock() instanceof LaserTestBlock
            && state.getValue(BlockStateProperties.POWERED);
    }

    private record Trace(long tick, Direction facing, double fraction, PlasmaImpact.Contact contact) {}
    record Material(float u, float v, int light, double gain) {
        Material(float u, float v, int light) { this(u, v, light, 1); }
        Material scaled(double factor) { return factor == 1 ? this : new Material(u, v, light, gain * factor); }
        void vertex(VertexConsumer out, Vec3 p, int color, int alpha) {
            out.addVertex((float)p.x, (float)p.y, (float)p.z)
                .setColor((int)(((color >> 16) & 255) * gain), (int)(((color >> 8) & 255) * gain),
                        (int)((color & 255) * gain), (int)(alpha * gain))
                .setUv(u, v).setLight(light);
        }
    }

    private static final class Beam extends Particle {
        private final LaserTestBlockEntity block;
        private Trace trace;
        private final PlasmaImpact impact = new PlasmaImpact();
        private int offTicks = -1;

        private double energy(float partial) {
            return offTicks < 0 ? 1 : Math.clamp(1 - (offTicks + partial) / 7.0, 0, 1);
        }
        private Vec3 volumeStart, volumeAxis;
        private double volumeLength;
        private long volumeTick;

        private boolean volumeActive() {
            return volumeStart != null && Minecraft.getInstance().level == level && isAlive() && offTicks < 7
                && ClientFlashes.inPackLighting() && level.getGameTime() - volumeTick <= 1 && volumeLength > .001;
        }

        private void publishVolume(long tick) {
            volumeTick = tick;
            int available = -1;
            for (int i = 0; i < volumes.size(); i++) {
                var ref = volumes.get(i);
                var other = ref == null ? null : ref.get();
                if (other == this) return;
                if (available < 0 && (other == null || !other.volumeActive())) available = i;
            }
            if (available >= 0) volumes.set(available, new WeakReference<>(this));
        }

        Beam(ClientLevel level, LaserTestBlockEntity block) {
            super(level, block.getBlockPos().getX(), block.getBlockPos().getY(), block.getBlockPos().getZ());
            this.block = block;
            hasPhysics = false;
            updateGeometry(0);
        }

        @Override public ParticleRenderType getRenderType() { return MuzzleParticleLayer.TYPE; }

        @Override public AABB getRenderBoundingBox(float partial) {
            return getBoundingBox();
        }

        @Override public void tick() {
            if (block.getLevel() != level || level.getBlockEntity(block.getBlockPos()) != block) {
                remove();
                return;
            }
            offTicks = powered(block) ? -1 : offTicks + 1;
            if (offTicks >= 12) { remove(); return; }
            boolean geometry = offTicks < 7 && updateGeometry(0);
            impact.tick(level, geometry && offTicks < 0 ? trace.contact : null, level.getGameTime(), random);
        }

        private boolean updateGeometry(float partial) {
            var state = block.getBlockState();
            var pos = block.getBlockPos();
            Direction facing = state.getValue(LaserTestBlock.FACING);
            Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 rawStart = Vec3.atCenterOf(pos).add(axis.scale(.501));
            var anchor = SourceAnchor.capture(level, pos);
            long tick = level.getGameTime();
            if (trace == null || (offTicks < 0 && (trace.tick != tick || trace.facing != facing))) {
                trace = trace(level, anchor, rawStart, axis, facing, tick);
            }
            Vec3 start = rawStart;
            if (anchor.ship() != null) {
                var frame = anchor.pointAt(level, rawStart, partial);
                if (frame == null) return false;
                start = new Vec3(frame[0], frame[1], frame[2]);
                axis = new Vec3(new Quaternionf(frame[3], frame[4], frame[5], frame[6])
                    .transform(axis.toVector3f())).normalize();
            }
            Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (start.distanceToSqr(eye) > 200 * 200) {
                remove();
                return false;
            }

            double length = RANGE * trace.fraction;
            if (trace.contact != null) {
                var target = trace.contact.frame(level, partial);
                if (target != null) {
                    Vec3 delta = target.point().subtract(start);
                    length = delta.length();
                    if (length > .001) axis = delta.scale(1 / length);
                }
            }
            // Flerovium tests a sphere at the particle position with radius max(bbWidth, bbHeight)/2.
            // Keep that sphere conservative even when the midpoint is behind the camera.
            float diameter = (float)(length + 2 * 5.2);
            setSize(diameter, diameter);
            setPos(start.x + axis.x * length * .5, start.y + axis.y * length * .5, start.z + axis.z * length * .5);
            setBoundingBox(new AABB(start, start.add(axis.scale(length))).inflate(5.2));
            volumeStart = start;
            volumeAxis = axis;
            volumeLength = length;
            if (ClientFlashes.inPackLighting()) {
                publishVolume(tick);
            }
            return true;
        }

        @Override public void render(VertexConsumer out, Camera camera, float partial) {
            if (block.getLevel() != level || (offTicks < 7 && !updateGeometry(partial)) || volumeStart == null) return;
            Vec3 start = volumeStart.subtract(camera.getPosition());
            Vec3 axis = volumeAxis;
            double length = volumeLength;
            var pos = block.getBlockPos();
            var atlas = (TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            var sprite = atlas.getSprite(WHITE);
            var material = new Material((sprite.getU0() + sprite.getU1()) * .5f,
                (sprite.getV0() + sprite.getV1()) * .5f, LightTexture.FULL_BRIGHT);
            double time = animationTick();
            if (length > .001 && energy(partial) > 0) {
                int light = ClientFlashes.inPackLighting()
                    ? dev.sablelightoverhaul.GasVertexData.plasmaLight(LightTexture.FULL_BRIGHT) : LightTexture.FULL_BRIGHT;
                var plasma = new Material(material.u, material.v, light);
                LaserBeamMesh.draw(out, plasma, start, axis, length, time, pos.asLong(), trace.contact != null, energy(partial));
            }
            impact.render(level, out, material, camera.getPosition(), partial, level.getGameTime());
        }
    }

    private static Trace trace(ClientLevel level, SourceAnchor anchor, Vec3 start, Vec3 axis,
                               Direction facing, long tick) {
        Vec3 end = start.add(axis.scale(RANGE));
        if (anchor.ship() != null) {
            start = anchor.world(level, anchor.pos(), start);
            end = anchor.world(level, anchor.pos(), end);
            if (start == null || end == null) return new Trace(tick, facing, 0, null);
        }
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return new Trace(tick, facing, 1, null);
        Vec3 contact = hit.getLocation();
        SourceAnchor hitBody = SourceAnchor.capture(level, hit.getBlockPos());
        Vec3 incoming = end.subtract(start).normalize();
        if (hitBody.ship() != null) {
            float[] q = hitBody.rotation(level);
            if (q == null) return new Trace(tick, facing, 1, null);
            incoming = new Vec3(new Quaternionf(q[0], q[1], q[2], q[3]).normalize().conjugate().transform(incoming.toVector3f()));
        }
        var surface = PlasmaImpact.Contact.of(hitBody, contact, Vec3.atLowerCornerOf(hit.getDirection().getNormal()), incoming);
        if (hitBody.ship() != null) contact = hitBody.world(level, hit.getBlockPos(), contact);
        double fullLength = start.distanceTo(end);
        double fraction = contact == null || fullLength < 1e-6 ? 0 : Math.clamp(start.distanceTo(contact) / fullLength, 0, 1);
        return new Trace(tick, facing, fraction, contact != null ? surface : null);
    }
}
