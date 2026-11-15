package dev.sablelightoverhaul.lighting.channel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * One block's link to an AFN channel, as the blocks see it.
 *
 * <p>The interface exists so that no block entity has an AFN type in its fields: with AFN absent, the
 * JVM has no reason to load the implementation and nothing there can fail. The same reason the Sable
 * bridge is kept in its own class.
 *
 * <p>Everything here is server side. A channel is a live object owned by the server thread, and the
 * decision it drives has to reach the clients the same way every other setting does -- through the
 * block entity's own data -- rather than by each client reading a channel of its own.
 */
public interface ChannelBinding {
 /** Whether a frequency board has been installed. */
 boolean bound();
 /** Whether the mod that owns the channels is even present. */
 boolean available();
 /** Whether AFN considers the installed boards a live channel (they can be bound and still dead). */
 boolean configured();
 /** The last thing the channel said. */
 ChannelSignal signal();
 /** Whether a channel value has been seen at all, as opposed to a bare signal. */
 boolean hasValue();
 /** Called once per server tick by the owning block entity, and only while something is bound. */
 void tick(ServerLevel level,BlockPos pos);
 /** Install a held board into the first free slot, if this player may read the channel it names. */
 BindResult bind(ItemStack stack,java.util.UUID playerId);
 void clear();
 /** Release the live channel but keep the stored boards: used when a block entity is removed. */
 void close();
 void save(CompoundTag tag,HolderLookup.Provider registries);
 void load(CompoundTag tag,HolderLookup.Provider registries);
 /** One line for a chat message: what is bound, and whether AFN says it is live. */
 String describe();
 /** What {@link #bind} did, so the caller can say something useful. */
 enum BindResult { NOT_A_BOARD, FIRST_SLOT, SECOND_SLOT, NO_READ_ACCESS }
}
