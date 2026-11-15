package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sablelightoverhaul.HostileShellPayload;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;

/** Draws only the shell body where vanilla has no visible copy. The normal SLO feed owns tracer effects. */
public final class HostileShellClient {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static List<HostileShellPayload.Shell> shells = List.of();
    private static final IntOpenHashSet measuredIds = new IntOpenHashSet();
    private static long serverTick = Long.MIN_VALUE, receivedAt;
    private static MethodHandle renderer;
    private static boolean resolved;
    private static MultiBufferSource.BufferSource buffers;

    private HostileShellClient() {}

    static void receive(HostileShellPayload packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (world.get() != level) {
            world = new WeakReference<>(level);
            shells = List.of();
            serverTick = Long.MIN_VALUE;
        }
        if (packet.tick() <= serverTick) return;
        serverTick = packet.tick();
        receivedAt = ProjectileStreaks.visualTick();
        measuredIds.clear();
        for (var shell : shells) measuredIds.add(shell.id());
        shells = packet.shells();
        for (var shell : shells) {
            measuredIds.add(shell.id());
            HostileMathShells.nativeSeen(level, shell.uuid(), shell.model());
        }
    }

    static void nativeJoined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel level
                && event.getEntity() instanceof AbstractBigCannonProjectile shell) {
            var model = HostileShellPayload.Model.forType(BuiltInRegistries.ENTITY_TYPE.getKey(shell.getType()).toString());
            if (model != null) HostileMathShells.nativeSeen(level, shell.getUUID(), model);
        }
    }

    static boolean measured(ClientLevel level, long id) {
        return id == (int) id && world.get() == level && ProjectileStreaks.visualTick() - receivedAt <= 5
            && measuredIds.contains((int) id);
    }

    public static boolean replacesMathPlaceholder(String type) {
        return HostileShellPayload.Model.forType(type) != null && HostileMathShells.negotiated() && resolveRenderer();
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        long elapsed = ProjectileStreaks.visualTick() - receivedAt;
        var nativeShells = world.get() == level && elapsed >= 0 && elapsed <= 3 ? shells : List.<HostileShellPayload.Shell>of();
        var mathShells = HostileMathShells.tracks(level);
        if (nativeShells.isEmpty() && !mathShells.iterator().hasNext()) return;
        var poses = event.getPoseStack();
        if (poses == null || !resolveRenderer()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var view = new View(level, event, poses, event.getCamera().getPosition(), event.getCamera().getLookVector(),
            mc.gameRenderer.getDepthFar() * .9,
            Math.abs(event.getProjectionMatrix().m11()) * mc.getWindow().getHeight() * .5);
        if (view.projectionLimit <= 0) return;

        boolean drawn = false;
        for (var track : mathShells) {
            drawn |= draw(view, track.shell(), track.position(ProjectileStreaks.visualTick(), partial), (int) track.streak());
        }
        for (var shell : nativeShells) {
            if (shell.terminal() || HostileMathShells.claims(level, shell.uuid()) || HealthbarFlightBridge.owns(shell.uuid())) continue;
            var nativeEntity = level.getEntity(shell.id());
            if (nativeEntity != null && nativeEntity.getUUID().equals(shell.uuid()) && !nativeEntity.isRemoved()
                    && (level.isOutsideBuildHeight(nativeEntity.blockPosition().getY())
                        || mc.levelRenderer.isSectionCompiled(nativeEntity.blockPosition()))
                    && mc.getEntityRenderDispatcher().shouldRender(nativeEntity, event.getFrustum(), view.eye.x, view.eye.y, view.eye.z)) continue;
            drawn |= draw(view, shell, shell.previous().lerp(shell.current(), elapsed == 0 ? partial : 1), shell.id());
        }
        if (drawn) buffers.endBatch();
    }

    private record View(ClientLevel level, RenderLevelStageEvent event, PoseStack poses, Vec3 eye,
                        org.joml.Vector3fc look, double projectionLimit, double focalPixels) {}

    private static boolean draw(View view, HostileShellPayload.Shell shell, Vec3 position, int occlusionId) {
        if (renderer == null) return false;
        Vec3 direction = shell.current().subtract(shell.previous()).normalize();
        if (direction.lengthSqr() < .5) {
            var heading = HealthbarFlightBridge.heading(shell.uuid());
            if (heading != null) direction = heading.normalize();
        }
        if (direction.lengthSqr() < .5) return false;
        Vec3 offset = position.subtract(view.eye);
        if (offset.x * view.look.x() + offset.y * view.look.y() + offset.z * view.look.z() <= 0) return false;
        double distance = offset.length();
        double scale = Math.min(1, view.projectionLimit / distance);
        Vec3 projected = view.eye.add(offset.scale(scale));
        if (!view.event.getFrustum().isVisible(new AABB(projected, projected).inflate(1))
                || VoxyAccess.blocked(view.eye, position, occlusionId)) return false;

        var block = BlockPos.containing(position);
        int light = shell.tracer() ? LightTexture.FULL_BRIGHT
            : view.level.hasChunkAt(block) ? LevelRenderer.getLightColor(view.level, block)
            : LightTexture.pack(0, view.level.dimensionType().hasSkyLight() ? 15 : 0);
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(8192));
        view.poses.pushPose();
        try {
            view.poses.translate(projected.x - view.eye.x, projected.y - view.eye.y, projected.z - view.eye.z);
            // Keep the far proxy rasterizable. The native copy retains its model scale.
            double depth = offset.x * view.look.x() + offset.y * view.look.y() + offset.z * view.look.z();
            float modelScale = (float) (scale * Math.max(1, 5.0 * depth / Math.max(1, view.focalPixels)));
            view.poses.scale(modelScale, modelScale, modelScale);
            return (boolean) renderer.invokeExact(shell.model().id(), direction, view.poses, (MultiBufferSource) buffers, light);
        } catch (Throwable error) {
            com.mojang.logging.LogUtils.getLogger().warn("Sable Hostile shell rendering failed", error);
            renderer = null;
            return false;
        } finally {
            view.poses.popPose();
        }
    }

    private static boolean resolveRenderer() {
        if (!resolved) {
            resolved = true;
            try {
                renderer = MethodHandles.publicLookup().findStatic(Class.forName("dev.sablehostile.client.NavalShellRenderer"),
                    "renderShell", MethodType.methodType(boolean.class, ResourceLocation.class, Vec3.class,
                        PoseStack.class, MultiBufferSource.class, int.class));
            } catch (ReflectiveOperationException | LinkageError error) {
                com.mojang.logging.LogUtils.getLogger().warn("Sable Hostile far shell renderer is unavailable", error);
            }
        }
        return renderer != null;
    }
}
