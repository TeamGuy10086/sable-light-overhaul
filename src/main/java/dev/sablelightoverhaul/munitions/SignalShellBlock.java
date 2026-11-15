package dev.sablelightoverhaul.munitions;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
/** Reuses all validated CBC item/block/contraption payload transfers. */
public final class SignalShellBlock extends IlluminationShellBlock {
 private static final MapCodec<SignalShellBlock> CODEC=simpleCodec(SignalShellBlock::new);
 public SignalShellBlock(BlockBehaviour.Properties properties){super(properties);}
 @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
 @Override public EntityType<? extends IlluminationShellProjectile> getAssociatedEntityType(){return FlareMunitions.SIGNAL_ENTITY.get();}
}
