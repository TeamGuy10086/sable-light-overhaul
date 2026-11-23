package dev.sablelightoverhaul.client;

import dev.engine_room.flywheel.api.backend.BackendManager;
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
import java.util.ArrayList;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** Shares a small, immutable set of opaque models across all live shell fragments. */
final class FlywheelDebris implements Effect {
    private static final int MAX_INSTANCES = 4096;
    private static final ArrayList<FlywheelDebris> ACTIVE = new ArrayList<>();
    private static final int[][] FACES = {
        {0, 2, 1}, {0, 3, 2}, {0, 4, 3}, {0, 1, 4},
        {5, 1, 2}, {5, 2, 3}, {5, 3, 4}, {5, 4, 1}
    };
    private static final Model[] MODELS = makeModels();
    private static final Model SPARK = new SingleMeshModel(new SparkMesh(),
        SimpleMaterial.builderOf(Materials.SOLID_UNSHADED_BLOCK)
            .texture(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", "textures/particle/volume_white.png"))
            .backfaceCulling(false).useLight(false).mipmap(false).build());

    private final ClientLevel level;
    private final ImpactParticle fragment;
    private final Model model;
    private final int color;
    private final boolean spark;
    private final VisualManager<Effect> manager;
    private volatile ImpactParticle.DebrisFrame frame;

    private FlywheelDebris(ClientLevel level, ImpactParticle fragment, int color, boolean spark, VisualManager<Effect> manager) {
        this.level = level;
        this.fragment = fragment;
        this.color = color;
        this.manager = manager;
        this.spark = spark;
        this.model = spark ? SPARK : MODELS[level.random.nextInt(MODELS.length)];
        this.frame = fragment.debrisFrame();
    }

    static boolean submit(ClientLevel level, ImpactParticle fragment, int color, boolean spark) {
        if (ACTIVE.size() >= MAX_INSTANCES || !BackendManager.isBackendOn()) return false;
        var visualization = VisualizationManager.get(level);
        if (visualization == null) return false;
        var effect = new FlywheelDebris(level, fragment, color, spark, visualization.effects());
        effect.manager.queueAdd(effect);
        ACTIVE.add(effect);
        return true;
    }

    static void tick(ClientTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var visualization = mc.level != null && BackendManager.isBackendOn() ? VisualizationManager.get(mc.level) : null;
        var currentManager = visualization == null ? null : visualization.effects();
        for (var it = ACTIVE.iterator(); it.hasNext();) {
            var effect = it.next();
            if (effect.level == mc.level && effect.manager == currentManager) {
                if (!mc.isPaused()) {
                    effect.fragment.tick();
                    if (effect.fragment.isAlive()) effect.frame = effect.fragment.debrisFrame();
                }
                if (effect.fragment.isAlive()) continue;
            } else if (effect.level == mc.level && effect.fragment.isAlive()) {
                mc.particleEngine.add(effect.fragment);
            }
            effect.manager.queueRemove(effect);
            it.remove();
        }
    }

    @Override public ClientLevel level() { return level; }

    @Override public EffectVisual<?> visualize(VisualizationContext context, float partialTick) {
        return new Visual(context, partialTick);
    }

    private final class Visual implements EffectVisual<FlywheelDebris>, SimpleDynamicVisual {
        private final VisualizationContext context;
        private final TransformedInstance instance;
        private final Matrix4f sparkPose = new Matrix4f();
        private final Vector3f line = new Vector3f();
        private final Vector3f side = new Vector3f();
        private final Vector3f normal = new Vector3f();

        Visual(VisualizationContext context, float partialTick) {
            this.context = context;
            instance = context.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
            instance.color(color >> 16 & 255, color >> 8 & 255, color & 255, 255);
            update(partialTick);
        }

        @Override public void beginFrame(DynamicVisual.Context frameContext) {
            update(frameContext.partialTick());
        }

        @Override public void update(float partial) {
            var state = frame;
            var origin = context.renderOrigin();
            double x = Mth.lerp(partial, state.previousX(), state.x());
            double y = Mth.lerp(partial, state.previousY(), state.y());
            double z = Mth.lerp(partial, state.previousZ(), state.z());
            float rx = (float)(x - origin.getX());
            float ry = (float)(y - origin.getY());
            float rz = (float)(z - origin.getZ());
            if (spark) {
                line.set(state.trailingDirection().toVector3f());
                if (line.lengthSquared() < .5f) {
                    instance.setVisible(false);
                    return;
                }
                var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                var eye = camera.getPosition();
                side.set((float)(eye.x-x), (float)(eye.y-y), (float)(eye.z-z)).cross(line);
                if (side.lengthSquared() < 1e-6f) side.set(0, 1, 0).rotate(camera.rotation()).cross(line);
                if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0).rotate(camera.rotation()).cross(line);
                side.normalize();
                line.cross(side, normal);
                line.mul(state.lineLength());
                side.mul(state.lineHalfWidth());
                sparkPose.set(line.x, line.y, line.z, 0, side.x, side.y, side.z, 0,
                    normal.x, normal.y, normal.z, 0, rx, ry, rz, 1);
                instance.setTransform(sparkPose);
                instance.setVisible(true);
            } else {
                float angle = Mth.lerp(partial, state.previousRoll(), state.roll());
                instance.setIdentityTransform().translate(rx, ry, rz)
                    .rotateY(angle * .79f).rotateX(angle).rotateZ(angle * .41f)
                    .scale(state.size(), state.size(), state.size());
            }
            instance.light(state.light());
            instance.setChanged();
        }

        @Override public void delete() { instance.delete(); }
    }

    private static Model[] makeModels() {
        var material = SimpleMaterial.builderOf(Materials.SOLID_BLOCK)
            .texture(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul", "textures/particle/volume_white.png"))
            .mipmap(false).build();
        var random = new Random(0x534c4f);
        var models = new Model[8];
        for (int i = 0; i < models.length; i++) {
            var corners = new Vector3f[] {
                new Vector3f(.54f + random.nextFloat() * .15f, .05f, -.12f),
                new Vector3f(.08f, .17f + random.nextFloat() * .18f, .05f),
                new Vector3f(-.13f, -.04f, .23f + random.nextFloat() * .18f),
                new Vector3f(-.08f, -.14f - random.nextFloat() * .12f, -.03f),
                new Vector3f(.17f, .06f, -.27f - random.nextFloat() * .16f),
                new Vector3f(-.45f - random.nextFloat() * .22f, .04f, .13f)
            };
            models[i] = new SingleMeshModel(new FragmentMesh(corners), material);
        }
        return models;
    }

    /** A unit strip. Per-instance axes stretch it from the head along the velocity. */
    private static final class SparkMesh implements QuadMesh {
        private static final Vector4fc BOUNDS = new Vector4f(.5f, 0, 0, Mth.sqrt(1.25f));

        @Override public int vertexCount() { return 4; }
        @Override public Vector4fc boundingSphere() { return BOUNDS; }
        @Override public void write(MutableVertexList output) {
            for (int i = 0; i < 4; i++) {
                output.x(i, i == 1 || i == 2 ? 1 : 0);
                output.y(i, i < 2 ? -1 : 1);
                output.z(i, 0);
                output.normalX(i, 0); output.normalY(i, 0); output.normalZ(i, 1);
                output.r(i, 1); output.g(i, 1); output.b(i, 1); output.a(i, 1);
                output.u(i, .5f); output.v(i, .5f);
                output.light(i, 0); output.overlay(i, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    private static final class FragmentMesh implements QuadMesh {
        private final Vector3f[] vertices = new Vector3f[FACES.length * 4];
        private final Vector3f[] normals = new Vector3f[FACES.length];
        private final Vector4fc bounds;

        FragmentMesh(Vector3f[] corners) {
            float radiusSquared = 0;
            for (int face = 0; face < FACES.length; face++) {
                var indices = FACES[face];
                var a = corners[indices[0]];
                var b = corners[indices[1]];
                var c = corners[indices[2]];
                var normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
                if (normal.dot(new Vector3f(a).add(b).add(c)) < 0) {
                    var swap = b;
                    b = c;
                    c = swap;
                    normal.negate();
                }
                normals[face] = normal;
                var quad = new Vector3f[] {a, b, c, c};
                for (int corner = 0; corner < 4; corner++) {
                    var vertex = quad[corner];
                    vertices[face * 4 + corner] = vertex;
                    radiusSquared = Math.max(radiusSquared, vertex.lengthSquared());
                }
            }
            bounds = new Vector4f(0, 0, 0, Mth.sqrt(radiusSquared));
        }

        @Override public int vertexCount() { return vertices.length; }
        @Override public Vector4fc boundingSphere() { return bounds; }

        @Override public void write(MutableVertexList output) {
            for (int i = 0; i < vertices.length; i++) {
                var vertex = vertices[i];
                var normal = normals[i / 4];
                output.x(i, vertex.x); output.y(i, vertex.y); output.z(i, vertex.z);
                output.normalX(i, normal.x); output.normalY(i, normal.y); output.normalZ(i, normal.z);
                output.r(i, 1); output.g(i, 1); output.b(i, 1); output.a(i, 1);
                output.u(i, 0.5f); output.v(i, 0.5f);
                output.light(i, 0); output.overlay(i, OverlayTexture.NO_OVERLAY);
            }
        }
    }
}
