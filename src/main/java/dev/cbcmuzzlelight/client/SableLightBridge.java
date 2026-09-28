package dev.cbcmuzzlelight.client;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Loaded only when Sable is present. No blocks or chunks are created. */
final class SableLightBridge {
    private static boolean failed;
    private SableLightBridge() {}

    static Map<Long, Integer> project(ClientLevel level, Map<Long, Integer> world) {
        Map<Long, Integer> result = new HashMap<>();
        if (failed || world.isEmpty()) return result;
        try {
            var container = SubLevelContainer.getContainer(level);
            if (container == null) return result;
            // Deterministic selection prevents budget overflow from randomly flickering sources.
            for (var entry : world.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                var source = BlockPos.of(entry.getKey());
                var box = new BoundingBox3d(source).expand(entry.getValue() + 1);
                var targets = new java.util.ArrayList<dev.ryanhcode.sable.sublevel.SubLevel>();
                container.queryIntersecting(box).forEach(targets::add);
                targets.sort(java.util.Comparator.comparing(s -> s.getUniqueId()));
                for (var ship : targets) {
                    if (ship.isRemoved()) continue;
                    Vec3 local = ship.logicalPose().transformPositionInverse(Vec3.atCenterOf(source));
                    if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) continue;
                    var pos = BlockPos.containing(local);
                    var plot = ship.getPlot();
                    var chunkPos = new ChunkPos(pos);
                    if (level.isOutsideBuildHeight(pos) || !plot.contains(chunkPos)
                            || plot.getChunk(plot.toLocal(chunkPos)) == null || !level.hasChunkAt(pos)) continue;
                    result.merge(pos.asLong(), entry.getValue(), Math::max);
                    if (result.size() >= MuzzleClientConfig.SABLE_MAX_SOURCES.get()) return result;
                }
            }
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            result.clear();
            LogUtils.getLogger().error("CBC Muzzle Light: Sable bridge disabled for this session; world lighting remains active", error);
        }
        return result;
    }
}
