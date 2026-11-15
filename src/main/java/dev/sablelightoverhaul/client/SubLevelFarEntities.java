package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Draws block entities inside Sable physical bodies that Sable's own pass leaves out at distance.
 *
 * <p>Sable renders a physical body from its own retained section meshes, so a ship stays visible far
 * beyond the client's view distance (that is what SSRD / {@code sub_level_tracking_range} buy). Its
 * block entities, however, come from {@code SectionRenderDispatcher.RenderSection.getCompiled()
 * .getRenderableBlockEntities()}, and a section that is not compiled — anywhere past the client's
 * chunk view distance — contributes an empty list. The geometry is therefore still drawn while the
 * cannon barrels and control surfaces on it are not: at view distance 3 (48 blocks) a warship is a
 * hull with nothing on its decks.
 *
 * <p>This pass fills that gap without touching Sable, CBC or compactflap. It walks the same plot
 * chunks Sable does, picks the block entities whose registry path matches the configured names, and
 * draws the ones Sable is <em>not</em> already drawing — the difference is taken from
 * {@link SubLevelRenderData#isSectionCompiled}, so a compiled section is left alone and this whole
 * class becomes a no-op on the day Sable (or SSRD) starts enumerating those block entities itself.
 *
 * <p>The per-block-entity pose is the pack's own: the sub-level's render pose maps plot space to
 * world space, so the block corner is transformed by it, the camera is expressed in plot space
 * through its inverse (Sable does the same thing for its own pass, and without it the distance test
 * compares a plot coordinate against a world camera), and the ship's orientation is applied to the
 * pose stack so a barrel leaning with its turret stays leaning.
 */
public final class SubLevelFarEntities {
    /** How far out we are willing to look for physical bodies, in blocks. Generous on purpose: the
     *  range a body is actually drawn at belongs to Sable/SSRD, not to us. */
    private static final double QUERY_RADIUS = 4096.0;
    /** Slack added around a block entity when testing the view frustum, in blocks. A cannon's render
     *  bounding box is not always larger than its block, so this is what keeps a barrel that hangs
     *  over the screen edge from popping. */
    private static final double FRUSTUM_SLACK = 2.0;

    private static final Matrix4d POSE = new Matrix4d();
    private static final Matrix4d INVERSE = new Matrix4d();
    private static final Matrix4f ROTATION = new Matrix4f();
    private static final Vector3d CORNER = new Vector3d();
    private static final Vector3d PLOT_CAMERA = new Vector3d();

    private SubLevelFarEntities() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (!net.neoforged.fml.ModList.get().isLoaded("sable")) return;
        if (!ExtraConfig.SUBLEVEL_FAR.get()) return;
        List<? extends String> names = ExtraConfig.SUBLEVEL_FAR_NAMES.get();
        if (names.isEmpty()) return;
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        // Sable installs this on the dispatcher so its own pass can hand the distance test a camera
        // that lives in the same coordinate space as the block entities it is testing.
        BlockEntityRenderDispatcherExtension extension =
                dispatcher instanceof BlockEntityRenderDispatcherExtension e ? e : null;
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        AABB query = new AABB(camera.x - QUERY_RADIUS, camera.y - QUERY_RADIUS, camera.z - QUERY_RADIUS,
                camera.x + QUERY_RADIUS, camera.y + QUERY_RADIUS, camera.z + QUERY_RADIUS);
        // Inside the client's view distance Sable draws these itself; past it, its section-based
        // enumeration comes up empty. The split is by that distance rather than by whether a section
        // reports itself compiled: SSRD keeps sub-level sections alive well past the view distance, so
        // "compiled" says nothing about whether Sable's own pass is going to draw the block entity.
        double near = (mc.options.getEffectiveRenderDistance() * 16.0) + 16.0;
        int ships = 0, chunks = 0, matched = 0, insideView = 0, offscreen = 0, drawn = 0;
        boolean debug = ExtraConfig.on(ExtraConfig.SUBLEVEL_DEBUG);
        StringBuilder detail = debug ? new StringBuilder() : null;
        for (dev.ryanhcode.sable.sublevel.SubLevel ship : container.queryIntersecting(new BoundingBox3d(query))) {
            if (!(ship instanceof ClientSubLevel client)) continue;
            if (client.isRemoved()) continue;
            var plot = client.getPlot();
            if (plot == null) continue;
            var loaded = plot.getLoadedChunks();
            if (loaded.isEmpty()) continue;
            ships++;
            chunks += loaded.size();

            var pose = client.renderPose(partialTick);
            if (pose == null) continue;
            pose.bakeIntoMatrix(POSE);
            if (!POSE.isFinite() || Math.abs(POSE.determinant()) < 1.0E-12) continue;

            ROTATION.set(POSE);
            ROTATION.setTranslation(0.0F, 0.0F, 0.0F);
            INVERSE.set(POSE).invert();
            PLOT_CAMERA.set(camera.x, camera.y, camera.z);
            INVERSE.transformPosition(PLOT_CAMERA);
            if (extension != null) {
                extension.sable$setCameraPosition(new Vec3(PLOT_CAMERA.x, PLOT_CAMERA.y, PLOT_CAMERA.z));
            }
            try {
                for (PlotChunkHolder holder : loaded) {
                    LevelChunk chunk = holder.getChunk();
                    if (chunk == null) continue;
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (!matches(blockEntity, names)) continue;
                        matched++;
                        BlockPos pos = blockEntity.getBlockPos();
                        POSE.transformPosition(pos.getX(), pos.getY(), pos.getZ(), CORNER);
                        double dx = CORNER.x - camera.x;
                        double dz = CORNER.z - camera.z;
                        double flat = Math.sqrt(dx * dx + dz * dz);
                        // Which block entities match, how far the pose puts them, and what we did with
                        // them: without this the counters below cannot tell "drawn somewhere wrong"
                        // apart from "drawn right but not the thing the player is looking at".
                        if (detail != null && detail.length() < 700) {
                            detail.append(' ').append(name(blockEntity)).append('@').append((int) flat);
                        }
                        // Horizontal, the same way the chunk view distance culls: a ship overhead is
                        // still inside it.
                        if (flat < near) {
                            insideView++;
                            if (detail != null && detail.length() < 700) detail.append("(near)");
                            continue;
                        }
                        if (!event.getFrustum().isVisible(new AABB(
                                CORNER.x - FRUSTUM_SLACK, CORNER.y - FRUSTUM_SLACK, CORNER.z - FRUSTUM_SLACK,
                                CORNER.x + 1.0 + FRUSTUM_SLACK, CORNER.y + 1.0 + FRUSTUM_SLACK,
                                CORNER.z + 1.0 + FRUSTUM_SLACK))) {
                            offscreen++;
                            continue;
                        }
                        poseStack.pushPose();
                        poseStack.translate(dx, CORNER.y - camera.y, dz);
                        poseStack.mulPose(ROTATION);
                        dispatcher.render(blockEntity, partialTick, poseStack, buffers);
                        poseStack.popPose();
                        drawn++;
                    }
                }
            } finally {
                if (extension != null) extension.sable$setCameraPosition(null);
            }
        }
        if (drawn > 0) buffers.endBatch();
        if (debug) debug(level, camera, ships, chunks, matched, insideView, offscreen, drawn, near, detail);
    }

    /** The block entity's registry path, for the debug line. */
    static String name(BlockEntity blockEntity) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id == null ? "?" : id.getPath();
    }

    private static long lastDebugLog;
    private static int debugFrames;

    /**
     * One line a second, the same spirit as the trail and shockwave counters.
     *
     * <p>The entity half of the line is the one that matters for cannons: an assembled cannon is not a
     * block entity at all but a contraption entity ({@code createbigcannons:pitch_contraption} and
     * friends) whose blocks live in its Contraption NBT, and those entities live in the sub-level's
     * plot chunks. So if the cannon vanishes at a distance while the hull does not, the first question
     * is whether the contraption entity is still on the client at all, or still there and simply not
     * drawn. {@code contraptions=N farthest=} answers that; {@code drawn=} answers the block entity
     * half.
     */
    private static void debug(net.minecraft.client.multiplayer.ClientLevel level, Vec3 camera,
                              int ships, int chunks, int matched, int insideView, int offscreen,
                              int drawn, double nearBlocks, StringBuilder detail) {
        debugFrames++;
        long now = System.currentTimeMillis();
        if (now - lastDebugLog < 1000L) return;
        lastDebugLog = now;
        int entities = 0;
        int contraptions = 0;
        double farthest = 0.0;
        StringBuilder kinds = new StringBuilder();
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
            entities++;
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null) continue;
            if (id.getPath().contains("contraption")) contraptions++;
            if (kinds.length() < 300) {
                kinds.append(' ').append(id.getPath()).append('@')
                        .append((int) Math.sqrt(entity.distanceToSqr(camera.x, camera.y, camera.z)));
            }
            farthest = Math.max(farthest, Math.sqrt(entity.distanceToSqr(camera.x, camera.y, camera.z)));
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "sable far BEs: frames={} ships={} chunks={} matched={} insideView({} blocks)={} offscreen={} drawn={}"
                        + " | entities={} contraptions={} farthest={}",
                debugFrames, ships, chunks, matched, (int) nearBlocks, insideView, offscreen, drawn,
                entities, contraptions, (int) farthest);
        if (detail != null) {
            com.mojang.logging.LogUtils.getLogger().info("sable far detail: matched:{} | entities:{}", detail, kinds);
        }
        debugFrames = 0;
    }

    /** Whether a block entity's registry path carries one of the configured name fragments. */
    static boolean matches(BlockEntity blockEntity, List<? extends String> names) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id != null && matchesName(id.getPath(), names);
    }

    /**
     * The filter itself, on the registry path only: {@code createbigcannons:cannon_mount} matches
     * {@code cannon}, and {@code compactflap:compact_flap_bearing} matches {@code flap} because of its
     * path rather than its namespace -- matching the namespace instead would drag in every block
     * entity of a mod whose id happens to contain the fragment.
     */
    static boolean matchesName(String path, List<? extends String> names) {
        for (String name : names) {
            if (name != null && !name.isEmpty() && path.contains(name)) return true;
        }
        return false;
    }
}
