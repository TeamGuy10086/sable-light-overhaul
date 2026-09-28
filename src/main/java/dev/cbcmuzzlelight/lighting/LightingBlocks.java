package dev.cbcmuzzlelight.lighting;
import dev.cbcmuzzlelight.MuzzleLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
public final class LightingBlocks {
 public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,MuzzleLight.ID);
 public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,MuzzleLight.ID);
 public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MuzzleLight.ID);
 public static final DeferredHolder<Block,SpotlightBlock> SPOTLIGHT=BLOCKS.register("spotlight",SpotlightBlock::new);
 public static final DeferredHolder<Item,Item> SPOTLIGHT_ITEM=ITEMS.register("spotlight",()->new BlockItem(SPOTLIGHT.get(),new Item.Properties()));
 public static final DeferredHolder<Item,Item> TOOL=ITEMS.register("light_registrar",()->new RegistrarItem(new Item.Properties().stacksTo(1)));
 /** Dust kicked off the ground a moving body drives over. */
 public static final DeferredHolder<Block,GeneratorBlock> DUST_GENERATOR=BLOCKS.register("dust_generator",GeneratorBlock.Dust::new);
 /** Smoke vented into world space, so a moving body leaves it hanging behind. */
 public static final DeferredHolder<Block,GeneratorBlock> SMOKE_GENERATOR=BLOCKS.register("smoke_generator",GeneratorBlock.Smoke::new);
 /** Marks which body is the vehicle, so the recoil needs no guess. One per body cluster. */
 public static final DeferredHolder<Block,MarkerBlock> HULL_MARKER=BLOCKS.register("hull_marker",MarkerBlock.Hull::new);
 /** Marks the body whose surfaces the dust coming off the vehicle sits on. */
 public static final DeferredHolder<Block,MarkerBlock> DUST_MARKER=BLOCKS.register("dust_marker",MarkerBlock.Dust::new);
 public static final DeferredHolder<Item,Item> HULL_MARKER_ITEM=ITEMS.register("hull_marker",()->new BlockItem(HULL_MARKER.get(),new Item.Properties()));
 public static final DeferredHolder<Item,Item> DUST_MARKER_ITEM=ITEMS.register("dust_marker",()->new BlockItem(DUST_MARKER.get(),new Item.Properties()));
 /** Stands in for a light block that was taken over: looks the same, emits nothing. */
 public static final DeferredHolder<Block,LightSourceBlock> LIGHT_SOURCE=BLOCKS.register("light_source",LightSourceBlock::new);
 public static final DeferredHolder<Item,Item> LIGHT_SOURCE_ITEM=ITEMS.register("light_source",()->new BlockItem(LIGHT_SOURCE.get(),new Item.Properties()));
 /** Easter egg: a disco ball that throws spinning coloured cones. Its light is decided client side. */
 public static final DeferredHolder<Block,DiscoBallBlock> DISCO_BALL=BLOCKS.register("disco_ball",DiscoBallBlock::new);
 public static final DeferredHolder<Item,Item> DISCO_BALL_ITEM=ITEMS.register("disco_ball",()->new BlockItem(DISCO_BALL.get(),new Item.Properties()));
 /** Held, it lights the player's own surroundings; its page is operator-only. */
 public static final DeferredHolder<Item,Item> FLASHLIGHT=ITEMS.register("flashlight",()->new dev.cbcmuzzlelight.FlashlightItem(new Item.Properties().stacksTo(1)));
 public static final DeferredHolder<Item,Item> DUST_GENERATOR_ITEM=ITEMS.register("dust_generator",()->new BlockItem(DUST_GENERATOR.get(),new Item.Properties()));
 public static final DeferredHolder<Item,Item> SMOKE_GENERATOR_ITEM=ITEMS.register("smoke_generator",()->new BlockItem(SMOKE_GENERATOR.get(),new Item.Properties()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<GeneratorBlockEntity>> GENERATOR=BLOCK_ENTITIES.register("generator",
  ()->BlockEntityType.Builder.of(GeneratorBlockEntity::new,DUST_GENERATOR.get(),SMOKE_GENERATOR.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<LightSourceBlockEntity>> LIGHT_SOURCE_ENTITY=BLOCK_ENTITIES.register("light_source",
  ()->BlockEntityType.Builder.of(LightSourceBlockEntity::new,LIGHT_SOURCE.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SpotlightBlockEntity>> SPOTLIGHT_ENTITY=BLOCK_ENTITIES.register("spotlight",
  ()->BlockEntityType.Builder.of(SpotlightBlockEntity::new,SPOTLIGHT.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<HullMarkerBlockEntity>> HULL_MARKER_ENTITY=BLOCK_ENTITIES.register("hull_marker",
  ()->BlockEntityType.Builder.of(HullMarkerBlockEntity::new,HULL_MARKER.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<DiscoBallBlockEntity>> DISCO_BALL_ENTITY=BLOCK_ENTITIES.register("disco_ball",
  ()->BlockEntityType.Builder.of(DiscoBallBlockEntity::new,DISCO_BALL.get()).build(null));
 public static void register(IEventBus bus){
  BLOCKS.register(bus);ITEMS.register(bus);BLOCK_ENTITIES.register(bus);bus.addListener(LightingBlocks::tab);
  // The configuration page's menu is only registered when the page toolkit is present.
  if(net.neoforged.fml.ModList.get().isLoaded("ldlib2")){GeneratorLdlib.register(bus);DiscoBallLdlib.register(bus);SpotlightLdlib.register(bus);FlashlightLdlib.register(bus);LightSourceLdlib.register(bus);}
 }
 private static void tab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){
  if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){e.accept(SPOTLIGHT_ITEM.get());e.accept(TOOL.get());e.accept(DUST_GENERATOR_ITEM.get());e.accept(SMOKE_GENERATOR_ITEM.get());e.accept(HULL_MARKER_ITEM.get());e.accept(LIGHT_SOURCE_ITEM.get());e.accept(DISCO_BALL_ITEM.get());e.accept(FLASHLIGHT.get());}
 }
}