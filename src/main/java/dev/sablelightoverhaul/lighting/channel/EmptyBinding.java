package dev.sablelightoverhaul.lighting.channel;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * The link a block has when there is nothing to link to: no AFN, or no board installed.
 *
 * <p>A block entity always holds one of these rather than a nullable field, so no caller has to ask
 * whether a channel exists before asking what it says. Every answer is the harmless one.
 */
public final class EmptyBinding implements ChannelBinding {
 @Override public boolean bound(){return false;}
 @Override public boolean available(){return false;}
 @Override public boolean configured(){return false;}
 @Override public ChannelSignal signal(){return ChannelSignal.NOTHING;}
 @Override public boolean hasValue(){return false;}
 @Override public void tick(ServerLevel level,BlockPos pos){}
 @Override public BindResult bind(ItemStack stack,UUID playerId){return BindResult.NOT_A_BOARD;}
 @Override public void clear(){}
 @Override public void close(){}
 @Override public void save(CompoundTag tag,HolderLookup.Provider registries){}
 @Override public void load(CompoundTag tag,HolderLookup.Provider registries){}
 @Override public String describe(){return "unbound";}
}
