package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.profiles.ProfileAudit;
import dev.sablelightoverhaul.profiles.ProfileStore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The sweep's view of a real server level.
 *
 * <p>It lives beside the rest of the Sable bridge on purpose: reading a plot coordinate is Sable's business,
 * and keeping the two together means the internal bridge does not have to be made public for a cosmetic
 * preference to be checked.
 *
 * <p>Three questions with three different answers, and keeping them apart is the whole job. Whether the body
 * still exists decides that a mounted cannon is gone, and only that removes an entry. Whether the chunks are
 * loaded decides nothing on its own -- a ship out of loaded chunks is a ship nobody can see, not a ship that
 * was taken apart. And where the block is now is a plot coordinate, which is where its type can actually be
 * read, while where it is <em>seen</em> is a rendered position and that is the only one worth measuring a
 * player's distance against.
 */
public final class ServerProfileView implements ProfileAudit.View {
    private final ServerLevel level;

    public ServerProfileView(ServerLevel level) {
        this.level = level;
    }

    /**
     * Where this cannon's block is, or null when the body it was in is gone.
     *
     * <p>Null means gone and nothing else. A position in an unloaded chunk is returned like any other, and
     * {@link #isLoaded} is what says so -- that is the distinction the audit relies on to leave a player's
     * settings alone when their ship is merely far away.
     */
    @Override
    public BlockPos resolve(ProfileStore.CannonId id) {
        if (id == null) return null;
        if (!level.dimension().equals(id.dimension())) return null;
        if (id.ship() == null) return id.localPos();
        if (!SableAnchors.bodyExists(level, id.ship())) return null;
        var origin = SableAnchors.plotOrigin(level, id.ship());
        return origin == null ? null : id.localPos().offset(origin.getX(), 0, origin.getZ());
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return pos != null && level.isLoaded(pos);
    }

    @Override
    public ResourceLocation typeAt(BlockPos pos) {
        if (pos == null) return null;
        var state = level.getBlockState(pos);
        if (state.isAir()) return null;
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

}