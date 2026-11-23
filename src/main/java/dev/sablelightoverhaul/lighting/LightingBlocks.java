package dev.sablelightoverhaul.lighting;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
public final class LightingBlocks {
 public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,SableLightOverhaul.ID);
 public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,SableLightOverhaul.ID);
 public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,SableLightOverhaul.ID);
 public static final DeferredHolder<Block,LaserTestBlock> LASER_TEST=BLOCKS.register("laser_test",LaserTestBlock::new);
 public static final DeferredHolder<Item,Item> LASER_TEST_ITEM=ITEMS.register("laser_test",
  () -> new BlockItem(LASER_TEST.get(),lore(new Item.Properties(),"sablelightoverhaul.laser_test.lore")));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<LaserTestBlockEntity>> LASER_TEST_ENTITY=
  BLOCK_ENTITIES.register("laser_test",() -> BlockEntityType.Builder.of(LaserTestBlockEntity::new,LASER_TEST.get()).build(null));
 public static final DeferredHolder<Block,SpotlightBlock> SPOTLIGHT=BLOCKS.register("spotlight",SpotlightBlock::new);
 public static final DeferredHolder<Item,Item> SPOTLIGHT_ITEM=ITEMS.register("spotlight",
  ()->new BlockItem(SPOTLIGHT.get(),lore(new Item.Properties(),"sablelightoverhaul.spotlight.lore.0","sablelightoverhaul.spotlight.lore.1","sablelightoverhaul.spotlight.lore.2","sablelightoverhaul.spotlight.lore.3","sablelightoverhaul.spotlight.lore.4")));
 public static final DeferredHolder<Item,Item> TOOL=ITEMS.register("light_registrar",
  ()->new RegistrarItem(lore(new Item.Properties().stacksTo(1),"sablelightoverhaul.registrar.lore.0","sablelightoverhaul.registrar.lore.1","sablelightoverhaul.registrar.lore.2")));
 /** Dust kicked off the ground a moving body drives over. */
 public static final DeferredHolder<Block,GeneratorBlock> DUST_GENERATOR=BLOCKS.register("dust_generator",GeneratorBlock.Dust::new);
 /** Smoke vented into world space, so a moving body leaves it hanging behind. */
 public static final DeferredHolder<Block,GeneratorBlock> SMOKE_GENERATOR=BLOCKS.register("smoke_generator",GeneratorBlock.Smoke::new);
 /** Sheds a vortex off the blocks a player registered to it with the wand; see {@link VortexMarks}. */
 public static final DeferredHolder<Block,GeneratorBlock> VORTEX_GENERATOR=BLOCKS.register("vortex_generator",GeneratorBlock.Vortex::new);
 /** The marking wand: registers blocks to the nearest vortex generator, and is the highlight's switch. */
 public static final DeferredHolder<Item,Item> VORTEX_WAND=ITEMS.register("vortex_wand",
  ()->new Item(lore(new Item.Properties().stacksTo(1),"sablelightoverhaul.vortexwand.lore.0","sablelightoverhaul.vortexwand.lore.1","sablelightoverhaul.vortexwand.lore.2")));
 /** Marks the body whose surfaces the dust coming off the vehicle sits on. */
 public static final DeferredHolder<Block,MarkerBlock> DUST_MARKER=BLOCKS.register("dust_marker",MarkerBlock.Dust::new);
 public static final DeferredHolder<Item,Item> DUST_MARKER_ITEM=ITEMS.register("dust_marker",
  ()->new BlockItem(DUST_MARKER.get(),lore(new Item.Properties(),"sablelightoverhaul.dustmarker.lore.0","sablelightoverhaul.dustmarker.lore.1")));
 /** Stands in for a light block that was taken over: looks the same, emits nothing. */
 public static final DeferredHolder<Block,LightSourceBlock> LIGHT_SOURCE=BLOCKS.register("light_source",LightSourceBlock::new);
 public static final DeferredHolder<Item,Item> LIGHT_SOURCE_ITEM=ITEMS.register("light_source",
  ()->new BlockItem(LIGHT_SOURCE.get(),lore(new Item.Properties(),"sablelightoverhaul.lightsource.lore.0","sablelightoverhaul.lightsource.lore.1","sablelightoverhaul.lightsource.lore.2","sablelightoverhaul.lightsource.lore.3")));
 /** Easter egg: a disco ball that throws spinning coloured cones. Its light is decided client side. */
 public static final DeferredHolder<Block,DiscoBallBlock> DISCO_BALL=BLOCKS.register("disco_ball",DiscoBallBlock::new);
 public static final DeferredHolder<Item,Item> DISCO_BALL_ITEM=ITEMS.register("disco_ball",
  ()->new BlockItem(DISCO_BALL.get(),lore(new Item.Properties(),"sablelightoverhaul.disco.lore.0","sablelightoverhaul.disco.lore.1","sablelightoverhaul.disco.lore.2")));
 /** Held, it lights the player's own surroundings; its page is operator-only. */
 public static final DeferredHolder<Item,Item> FLASHLIGHT=ITEMS.register("flashlight",
  ()->new dev.sablelightoverhaul.FlashlightItem(lore(new Item.Properties().stacksTo(1),"sablelightoverhaul.flashlight.lore.0","sablelightoverhaul.flashlight.lore.1","sablelightoverhaul.flashlight.lore.2")));
 /** Opens a cannon's form: a plain right-click asks about the block clicked, a shift click searches. */
 public static final DeferredHolder<Item,Item> BLAST_DEBUG=ITEMS.register("blast_debug",
  ()->new dev.sablelightoverhaul.BlastDebugItem(lore(new Item.Properties().stacksTo(1),
   "sablelightoverhaul.blast_debug.lore.0","sablelightoverhaul.blast_debug.lore.1")));
 public static final DeferredHolder<Item,Item> PROFILE_WRENCH=ITEMS.register("profile_wrench",
   () -> new Item(new Item.Properties().stacksTo(1)));
 public static final DeferredHolder<Item,Item> DUST_GENERATOR_ITEM=ITEMS.register("dust_generator",
  ()->new BlockItem(DUST_GENERATOR.get(),lore(new Item.Properties(),"sablelightoverhaul.dustgenerator.lore.0","sablelightoverhaul.dustgenerator.lore.1")));
 public static final DeferredHolder<Item,Item> SMOKE_GENERATOR_ITEM=ITEMS.register("smoke_generator",
  ()->new BlockItem(SMOKE_GENERATOR.get(),lore(new Item.Properties(),"sablelightoverhaul.smokegenerator.lore.0","sablelightoverhaul.smokegenerator.lore.1")));
 public static final DeferredHolder<Item,Item> VORTEX_GENERATOR_ITEM=ITEMS.register("vortex_generator",
  ()->new BlockItem(VORTEX_GENERATOR.get(),lore(new Item.Properties(),"sablelightoverhaul.vortexgenerator.lore.0","sablelightoverhaul.vortexgenerator.lore.1")));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<GeneratorBlockEntity>> GENERATOR=BLOCK_ENTITIES.register("generator",
  ()->BlockEntityType.Builder.of(GeneratorBlockEntity::new,DUST_GENERATOR.get(),SMOKE_GENERATOR.get(),VORTEX_GENERATOR.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<LightSourceBlockEntity>> LIGHT_SOURCE_ENTITY=BLOCK_ENTITIES.register("light_source",
  ()->BlockEntityType.Builder.of(LightSourceBlockEntity::new,LIGHT_SOURCE.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SpotlightBlockEntity>> SPOTLIGHT_ENTITY=BLOCK_ENTITIES.register("spotlight",
  ()->BlockEntityType.Builder.of(SpotlightBlockEntity::new,SPOTLIGHT.get()).build(null));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<DiscoBallBlockEntity>> DISCO_BALL_ENTITY=BLOCK_ENTITIES.register("disco_ball",
  ()->BlockEntityType.Builder.of(DiscoBallBlockEntity::new,DISCO_BALL.get()).build(null));
 /**
  * Give an item its lore: what it controls and how.
  *
  * <p>Every item this mod adds says, in its tooltip, the gestures it answers to and the settings it
  * owns, so a player who has just picked one up does not have to read a page or a document to find out
  * what it is for. The lines are translation keys rather than literals, so both shipped languages work.
  */
 private static Item.Properties lore(Item.Properties properties,String... keys){
  var lines=new java.util.ArrayList<Component>(keys.length);
  for(String key:keys)lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
  return properties.component(DataComponents.LORE,new ItemLore(lines));
 }
 public static void register(IEventBus bus){
  BLOCKS.register(bus);ITEMS.register(bus);BLOCK_ENTITIES.register(bus);bus.addListener(LightingBlocks::tab);
  // The configuration pages' menus are only registered when the page toolkit is present.
  if(net.neoforged.fml.ModList.get().isLoaded("ldlib2")){GeneratorLdlib.register(bus);DiscoBallLdlib.register(bus);SpotlightLdlib.register(bus);FlashlightLdlib.register(bus);LightSourceLdlib.register(bus);ProfileLdlib.register(bus);}
 }
 private static void tab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){
  // Every item the mod adds is here, markers included: the dust marker used to be registered and
  // unobtainable, which is a strange thing for a block with a page and a model to be.
  if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){e.accept(SPOTLIGHT_ITEM.get());e.accept(TOOL.get());e.accept(DUST_GENERATOR_ITEM.get());e.accept(SMOKE_GENERATOR_ITEM.get());e.accept(VORTEX_GENERATOR_ITEM.get());e.accept(VORTEX_WAND.get());e.accept(DUST_MARKER_ITEM.get());e.accept(LIGHT_SOURCE_ITEM.get());e.accept(DISCO_BALL_ITEM.get());e.accept(FLASHLIGHT.get());e.accept(PROFILE_WRENCH.get());e.accept(BLAST_DEBUG.get());e.accept(LASER_TEST_ITEM.get());}
 }
}
