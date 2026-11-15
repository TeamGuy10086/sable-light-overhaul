package dev.sablelightoverhaul.munitions;

import dev.sablelightoverhaul.SableLightOverhaul;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import rbasamoyai.createbigcannons.index.CBCBlocks;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.FuzedProjectileBlockItem;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.config.MunitionPropertiesHandler;

/** CBC-compatible ammunition, with its own block entity validity set and datapack properties. */
public final class FlareMunitions {
 private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,SableLightOverhaul.ID);
 private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,SableLightOverhaul.ID);
 private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,SableLightOverhaul.ID);
 private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,SableLightOverhaul.ID);
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,SableLightOverhaul.ID);
 private static final DeferredRegister<DataComponentType<?>> COMPONENTS=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,SableLightOverhaul.ID);
 public static final DeferredHolder<DataComponentType<?>,DataComponentType<FlareSettings>> SETTINGS=COMPONENTS.register("flare_settings",()->DataComponentType.<FlareSettings>builder().persistent(FlareSettings.CODEC).networkSynchronized(FlareSettings.STREAM_CODEC).build());
 public static final DeferredHolder<DataComponentType<?>,DataComponentType<Integer>> ALTITUDE=COMPONENTS.register("fuze_altitude",()->DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT).build());
 public static final DeferredHolder<EntityType<?>,EntityType<IlluminationShellProjectile>> SHELL_ENTITY=ENTITIES.register("illumination_shell",()->{
  var type=EntityType.Builder.<IlluminationShellProjectile>of(IlluminationShellProjectile::new,MobCategory.MISC)
    .sized(.8F,.8F).fireImmune().clientTrackingRange(16).updateInterval(1).setShouldReceiveVelocityUpdates(false)
    .build(SableLightOverhaul.ID+":illumination_shell");
  MunitionPropertiesHandler.registerProjectileHandler(type,CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE);
  return type;
 });
 public static final DeferredHolder<EntityType<?>,EntityType<SignalShellProjectile>> SIGNAL_ENTITY=ENTITIES.register("signal_shell",()->{
  var type=EntityType.Builder.<SignalShellProjectile>of(SignalShellProjectile::new,MobCategory.MISC)
    .sized(.8F,.8F).fireImmune().clientTrackingRange(16).updateInterval(1).setShouldReceiveVelocityUpdates(false)
    .build(SableLightOverhaul.ID+":signal_shell");
  MunitionPropertiesHandler.registerProjectileHandler(type,CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE);
  return type;
 });
 public static final DeferredHolder<Block,SignalShellBlock> SIGNAL=BLOCKS.register("signal_shell",()->new SignalShellBlock(BlockBehaviour.Properties.ofFullCopy(CBCBlocks.HE_SHELL.get())));
 public static final DeferredHolder<Item,FuzedProjectileBlockItem> SIGNAL_ITEM=ITEMS.register("signal_shell",()->new FuzedProjectileBlockItem(SIGNAL.get(),new Item.Properties().component(SETTINGS.get(),FlareSettings.DEFAULT)));
 public static final DeferredHolder<Block,IlluminationShellBlock> SHELL=BLOCKS.register("illumination_shell",()->new IlluminationShellBlock(BlockBehaviour.Properties.ofFullCopy(CBCBlocks.HE_SHELL.get())));
 public static final DeferredHolder<Item,FuzedProjectileBlockItem> SHELL_ITEM=ITEMS.register("illumination_shell",()->new FuzedProjectileBlockItem(SHELL.get(),new Item.Properties().component(SETTINGS.get(),FlareSettings.DEFAULT)));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FuzedBlockEntity>> SHELL_BLOCK_ENTITY=BLOCK_ENTITIES.register("illumination_shell",()->BlockEntityType.Builder.of(FlareMunitions::shellBlockEntity,SHELL.get(),SIGNAL.get()).build(null));
 private static FuzedBlockEntity shellBlockEntity(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return new IlluminationShellBlockEntity(SHELL_BLOCK_ENTITY.get(),pos,state);}
 public static final DeferredHolder<Item,AltitudeFuzeItem> ALTITUDE_FUZE=ITEMS.register("altitude_fuze",()->new AltitudeFuzeItem(new Item.Properties().component(ALTITUDE.get(),AltitudeTrigger.DEFAULT)));
 public static final DeferredHolder<MenuType<?>,MenuType<AltitudeFuzeMenu>> ALTITUDE_MENU=MENUS.register("altitude_fuze",()->IMenuTypeExtension.create(AltitudeFuzeMenu::client));
 private FlareMunitions(){}
 public static void register(IEventBus bus){
  COMPONENTS.register(bus);ENTITIES.register(bus);BLOCKS.register(bus);ITEMS.register(bus);BLOCK_ENTITIES.register(bus);MENUS.register(bus);
  bus.addListener(FlareMunitions::creative);
 }
 private static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){
  if(event.getTabKey()==CreativeModeTabs.COMBAT || event.getTabKey().location().toString().equals("createbigcannons:munitions")){
   event.accept(SHELL_ITEM.get());event.accept(SIGNAL_ITEM.get());event.accept(ALTITUDE_FUZE.get());
  }
 }
}
