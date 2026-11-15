package dev.sablelightoverhaul.lighting;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Who may change a placed device.
 *
 * <p>A block's settings live on the server, so the server is where the answer has to be enforced. The owner is
 * recorded on the block entity when it is placed, not inferred from whoever edits first: a machine that two
 * players can reach must not silently belong to the second one. Legacy blocks (placed before this existed) have
 * no owner and stay editable, which is the only migration that does not lock players out of their own builds.
 *
 * <p>The same rule is available on the client for the page's read-only state, but it is only a hint there --
 * {@link #canEdit(Player, Level, BlockPos)} on the server is the one that decides.
 */
public final class BlockOwners {
    /** A block entity that remembers who placed it. */
    public interface Owned {
        UUID owner();

        void setOwner(UUID owner);
    }

    private static final String TAG = "SableOwner";

    private BlockOwners() {}

    /** Operators may always edit; the placer may edit; legacy (ownerless) blocks stay open. */
    public static boolean canEdit(Player player, Level level, BlockPos pos) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        if (level == null || !(level.getBlockEntity(pos) instanceof Owned owned)) return true;
        UUID owner = owned.owner();
        return owner == null || owner.equals(player.getUUID());
    }

    /** Called from a block's placement hook; a dispenser or a fake player leaves the device ownerless. */
    public static void placed(Level level, BlockPos pos, LivingEntity placer) {
        if (!(placer instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (level.getBlockEntity(pos) instanceof Owned owned) {
            owned.setOwner(player.getUUID());
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    public static void save(CompoundTag tag, UUID owner) {
        if (owner != null) tag.putUUID(TAG, owner);
    }

    public static UUID load(CompoundTag tag) {
        return tag.hasUUID(TAG) ? tag.getUUID(TAG) : null;
    }

    /** A block entity that is not ours (or a legacy one) is left alone by callers that only have a position. */
    public static UUID ownerOf(Level level, BlockPos pos) {
        if (level == null) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Owned owned ? owned.owner() : null;
    }
}
