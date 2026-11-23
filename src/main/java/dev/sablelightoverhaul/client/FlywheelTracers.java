package dev.sablelightoverhaul.client;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.sablelightoverhaul.TracerPrism;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

final class FlywheelTracers implements Effect {
    private static final Model PRISM = new SingleMeshModel(new PrismMesh(),
        SimpleMaterial.builderOf(Materials.SOLID_UNSHADED_BLOCK)
            .texture(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", "textures/particle/volume_white.png"))
            .useLight(false).mipmap(false).build());
    private static FlywheelTracers active;

    private final ClientLevel level;
    private final VisualManager<Effect> manager;
    private volatile boolean ready;
    private volatile List<DrawSegment> frame = List.of();

    private record DrawSegment(Vec3 from, Vec3 to, double halfWidth) {}

    static void prepareFrame(Camera camera, float partial, double focal) {
        var effect = active;
        if (effect == null || !VisualizationManager.supportsVisualization(effect.level)) return;
        // Game state and the exposure clock stay on the render thread. Flywheel workers read the snapshot.
        var segments = new ArrayList<DrawSegment>();
        ProjectileStreaks.drawInstanced(effect.level, camera, partial, focal,
            (from, to, width) -> segments.add(new DrawSegment(from, to, width)));
        effect.frame = segments;
    }

    private FlywheelTracers(ClientLevel level, VisualManager<Effect> manager) {
        this.level = level;
        this.manager = manager;
    }

    static void updateLevel(ClientLevel level, boolean needed) {
        var visualization = level != null && needed ? VisualizationManager.get(level) : null;
        var manager = visualization == null ? null : visualization.effects();
        if (active != null && (active.level != level || active.manager != manager)) {
            active.manager.queueRemove(active);
            active = null;
        }
        if (active == null && manager != null) {
            active = new FlywheelTracers(level, manager);
            manager.queueAdd(active);
        }
    }

    static boolean drawing(ClientLevel level) {
        return active != null && active.level == level && active.ready
            && VisualizationManager.supportsVisualization(level);
    }

    @Override public ClientLevel level() { return level; }

    @Override public EffectVisual<?> visualize(VisualizationContext context, float partialTick) {
        return new Visual(context);
    }

    private final class Visual implements EffectVisual<FlywheelTracers>, SimpleDynamicVisual {
        private final VisualizationContext context;
        private final Instancer<TransformedInstance> instancer;
        private final ArrayList<TransformedInstance> segments = new ArrayList<>();
        private final Matrix4f pose = new Matrix4f();
        private int used;

        Visual(VisualizationContext context) {
            this.context = context;
            this.instancer = context.instancerProvider().instancer(InstanceTypes.TRANSFORMED, PRISM);
        }

        @Override public void beginFrame(DynamicVisual.Context frameContext) {
            int previouslyUsed = used;
            used = 0;
            for (DrawSegment segment : frame) draw(segment.from, segment.to, segment.halfWidth);
            for (int i = used; i < previouslyUsed; i++) {
                segments.get(i).setVisible(false);
            }
            ready = true;
        }

        private void draw(Vec3 from, Vec3 to, double width) {
            if (used == segments.size()) {
                var instance = instancer.createInstance();
                instance.color(255, 255, 255, 255);
                instance.light(LightTexture.FULL_BRIGHT);
                segments.add(instance);
            }
            var instance = segments.get(used++);
            Vec3 span = to.subtract(from);
            Vec3 axis = span.normalize();
            Vec3 side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0))
                .normalize().scale(width);
            Vec3 up = axis.cross(side);
            var origin = context.renderOrigin();
            pose.set((float)span.x, (float)span.y, (float)span.z, 0,
                (float)side.x, (float)side.y, (float)side.z, 0,
                (float)up.x, (float)up.y, (float)up.z, 0,
                (float)(from.x - origin.getX()), (float)(from.y - origin.getY()), (float)(from.z - origin.getZ()), 1);
            instance.setTransform(pose);
            instance.setVisible(true);
            instance.setChanged();
        }

        @Override public void update(float partialTick) {}

        @Override public void delete() {
            ready = false;
            for (var instance : segments) instance.delete();
            segments.clear();
        }
    }

    private static final class PrismMesh implements QuadMesh {
        private final ArrayList<Vec3> points = new ArrayList<>(56);
        private final ArrayList<Boolean> coreFaces = new ArrayList<>(14);
        private final Vector4fc bounds = new Vector4f(.5f, 0, 0, 1.5f);

        PrismMesh() {
            // TracerPrism's X-axis basis is Z then -Y. Remap it to local Y/Z.
            var prism = new ArrayList<Vec3>(24);
            TracerPrism.emit(Vec3.ZERO, new Vec3(1, 0, 0), 1,
                p -> prism.add(new Vec3(p.x, p.z, -p.y)));
            for (int i = 0; i < prism.size(); i += 4) {
                Vec3 a = prism.get(i), b = prism.get(i + 1), c = prism.get(i + 2), d = prism.get(i + 3);
                if (a.x == b.x && a.x == c.x) {
                    addQuad(a, b, c, d, true);
                } else {
                    // Orient the first edge across the width before baking the amber edges.
                    if (a.x != b.x) {
                        Vec3 first = a;
                        a = b; b = c; c = d; d = first;
                    }
                    Vec3 left = a.lerp(b, .15), right = a.lerp(b, .85);
                    Vec3 farLeft = d.lerp(c, .15), farRight = d.lerp(c, .85);
                    addQuad(a, left, farLeft, d, false);
                    addQuad(left, right, farRight, farLeft, true);
                    addQuad(right, b, c, farRight, false);
                }
            }
        }

        private void addQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean core) {
            points.add(a); points.add(b); points.add(c); points.add(d);
            coreFaces.add(core);
        }

        @Override public int vertexCount() { return points.size(); }
        @Override public Vector4fc boundingSphere() { return bounds; }

        @Override public void write(MutableVertexList output) {
            for (int i = 0; i < points.size(); i++) {
                int face = i / 4 * 4;
                Vec3 p = points.get(i);
                Vec3 a = points.get(face);
                Vec3 normal = points.get(face + 1).subtract(a).cross(points.get(face + 2).subtract(a)).normalize();
                output.x(i, (float)p.x); output.y(i, (float)p.y); output.z(i, (float)p.z);
                output.normalX(i, (float)normal.x); output.normalY(i, (float)normal.y); output.normalZ(i, (float)normal.z);
                output.r(i, 1); output.g(i, (coreFaces.get(i / 4) ? 216 : 80) / 255f);
                output.b(i, 0); output.a(i, 1);
                output.u(i, .5f); output.v(i, .5f);
                output.light(i, 0); output.overlay(i, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
