package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.client.ClientChannels;
import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The generator configuration page, drawn with LDLib2 — the toolkit Synaxis builds its pages from,
 * so this looks and behaves like the pages a player already knows.
 *
 * <p>This class is loaded only when LDLib2 is present ({@link GeneratorUi} and
 * {@link LightingBlocks#register} keep the reference inside a mod-loaded check), which is what keeps
 * the page an optional feature instead of a hard dependency.
 *
 * <p>The page is a pure view: every widget reads the block entity once and each edit is sent as one
 * validated {@link GeneratorEditPayload}. Nothing here uses the toolkit's own value binding, whose
 * ids are assigned by widget-tree order and would desync silently if the two sides ever built a
 * different tree.
 */
public final class GeneratorLdlib {
 private static final float WIDTH=242,ROW=11,GAP=2,LABEL=112,CONTROL=118;
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,SableLightOverhaul.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("generator",GeneratorLdlib::menuType);
 private GeneratorLdlib(){}
 /** Built through a method so the menu's own holder can be read without a self-reference. */
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player,data.readBlockPos())));
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /**
  * Open the page for one generator.
  * @returns false when the block entity is gone, so the caller can fall back.
  */
 public static boolean open(Player player,BlockPos pos){
  if (player instanceof net.minecraft.server.level.ServerPlayer remote && !remote.connection.hasChannel(dev.sablelightoverhaul.lighting.GeneratorEditPayload.TYPE)) return false;
  if(!(player.level().getBlockEntity(pos) instanceof GeneratorBlockEntity))return false;
  player.openMenu(new Holder(player,pos),buffer->buffer.writeBlockPos(pos));
  return true;
 }
 /** Menu provider and page holder in one, so the server menu and the client page agree. */
 record Holder(Player player,BlockPos pos) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable(generator().kind().labelKey());}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(UI.of(new UIElement()),owner);}
  @Override public boolean isStillValid(Player owner){return generator()!=null;}
  private GeneratorBlockEntity generator(){
   return player.level().getBlockEntity(pos) instanceof GeneratorBlockEntity generator?generator:null;
  }
 }
 /** One page: a title and one row per editable field. */
 private static UI page(GeneratorBlockEntity generator,Player owner){
  UIElement root=new UIElement().layout(layout->{
   layout.width(WIDTH);
   layout.heightAuto();
   layout.gapAll(GAP);
   layout.paddingAll(4);
   layout.flexDirection(FlexDirection.COLUMN);
  }).style(style->style.backgroundTexture(Sprites.BORDER));
  root.addChild(title(Component.translatable(generator.kind().labelKey()),WIDTH-8));
  for(var field:GeneratorFields.of(generator.kind()))root.addChild(row(generator,field,owner));
  root.addChild(bar(generator,owner));
  return UI.of(root);
 }
 private static UIElement row(GeneratorBlockEntity generator,GeneratorFields.Field field,Player owner){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChildren(label(Component.translatable(field.labelKey()),LABEL),control(generator,field,owner));
  return row;
 }
 private static UIElement control(GeneratorBlockEntity generator,GeneratorFields.Field field,Player owner){
  return switch(field.type()){
   case TOGGLE->toggle(generator,field,owner);
   case NUMBER->number(generator,field,owner);
   case TEXT->text(generator,field,owner);
  };
 }
 private static Label label(Component text,float width){
  Label label=new Label();
  label.setText(text);
  label.textStyle(style->style.textColor(0xFFFFFF).textShadow(false).fontSize(8.0F)
    .adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.HOVER_ROLL)
    .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
  label.layout(layout->{
   layout.width(width);
   layout.height(ROW);
   layout.flexShrink(0.0F);
  });
  return label;
 }
 private static Label title(Component text,float width){
  Label label=label(text,width);
  label.textStyle(style->style.textColor(0xFFFFFF).textShadow(false).fontSize(8.0F)
    .adaptiveWidth(false).adaptiveHeight(false).textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
  label.layout(layout->{
   layout.width(width);
   layout.height(12.0F);
  });
  return label;
 }
 private static Toggle toggle(GeneratorBlockEntity generator,GeneratorFields.Field field,Player owner){
  Toggle toggle=new Toggle();
  toggle.noText();
  toggle.toggleStyle(style->style.unmarkTexture(Sprites.RECT_DARK).markTexture(Sprites.RECT_LIGHT));
  toggle.setValue(generator.flag(field.id()),false);
  toggle.setOnToggleChanged(value->send(owner,GeneratorEditPayload.of(generator.getBlockPos(),field.id(),value)));
  toggle.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  return toggle;
 }
 private static TextField number(GeneratorBlockEntity generator,GeneratorFields.Field field,Player owner){
  TextField input=input();
  input.setNumbersOnlyDouble(field.min(),field.max());
  input.setText(format(generator.number(field.id())),false);
  input.setWheelDur(1,0.1F);
  input.setTextResponder(text->{
   try {
    send(owner,GeneratorEditPayload.of(generator.getBlockPos(),field.id(),Double.parseDouble(text)));
   } catch(NumberFormatException ignored){
    // Typing is not an edit yet; the field stays red until it parses.
   }
  });
  return input;
 }
 private static TextField text(GeneratorBlockEntity generator,GeneratorFields.Field field,Player owner){
  TextField input=input();
  input.setAnyString();
  input.setText(generator.text(field.id()),false);
  input.setTextValidator(value->ParticleSpec.valid(generator.getLevel(),value));
  input.textFieldStyle(style->style.errorColor(0xFFFF5555));
  input.setTextResponder(value->send(owner,GeneratorEditPayload.of(generator.getBlockPos(),field.id(),value)));
  return input;
 }
 private static TextField input(){
  TextField input=new TextField();
  input.style(style->style.backgroundTexture(Sprites.RECT_DARK));
  input.textFieldStyle(style->style.textColor(0xFFFFFF).cursorColor(0xFFFFFF).textShadow(false).fontSize(8.0F));
  input.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
   layout.paddingHorizontal(2.0F);
   layout.paddingVertical(1.0F);
  });
  return input;
 }
 private static String format(double value){
  return String.format(java.util.Locale.ROOT,"%.2f",value);
 }
 /**
  * Copy this generator's settings out, or paste a set back in.
  *
  * <p>The export reads each field the way its own widget does: a toggle as true or false, a number through the
  * same format the field shows, a text field as the text it holds. One format serves cannons and generators
  * alike because every value is text on the wire -- a generator has a particle specification in it, which is
  * text, so a format of numbers could not have carried it.
  *
  * <p>The import sends what it read and lets the server refuse individual values, rather than filtering here:
  * the page does not own the schema, and a value this generator cannot use is refused by the same validated
  * write a single edit goes through. The line below reports what was read, which is what a player wants to
  * know before it lands.
  */
 private static UIElement bar(GeneratorBlockEntity generator,Player owner){
  UIElement bar=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  bar.addChildren(
    button(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.profile.export").getString(),
      ()->export(generator)),
    button(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.profile.import").getString(),
      ()->importFromClipboard(generator)));
  return bar;
 }
 /** Write this generator's settings to the clipboard, in the format an import reads back. */
 private static void export(GeneratorBlockEntity generator){
  var values=new java.util.TreeMap<String,String>();
  for(var field:GeneratorFields.of(generator.kind()))values.put(field.id(),valueOf(generator,field));
  var text=dev.sablelightoverhaul.profiles.ProfileText.writeText(
    new dev.sablelightoverhaul.profiles.ProfileText.TextExport("generator",modVersion(),
      java.util.Map.of(generator.kind().name().toLowerCase(java.util.Locale.ROOT),values)));
  net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
  tell(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.generator.exported",values.size()));
 }
 /**
  * Send the clipboard's settings to this generator, one validated write per value on the server.
  *
  * <p>The same payload the old page sent: the paste is not filtered here, because the page does not own
  * the schema and a value this generator cannot use has to be refused by the same validated write a
  * single edit goes through.
  */
 private static void importFromClipboard(GeneratorBlockEntity generator){
  var parsed=dev.sablelightoverhaul.profiles.ProfileText.readText(
    net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
  if(parsed.isEmpty()){ tell(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.profile.nothingToRead")); return; }
  var export=parsed.get();
  var values=new java.util.LinkedHashMap<String,String>();
  export.byType().values().forEach(values::putAll);
  if(values.isEmpty()){ tell(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.generator.nothingUsable")); return; }
  ClientChannels.sendToServer(new dev.sablelightoverhaul.GeneratorImportPayload(generator.getBlockPos(),values));
  tell(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.generator.imported",values.size(),export.modVersion(),export.unknownLines().size()));
 }
 /** One field, read the way its own widget reads it. */
 private static String valueOf(GeneratorBlockEntity generator,GeneratorFields.Field field){
  return switch(field.type()){
   case TOGGLE->generator.flag(field.id())?"true":"false";
   case NUMBER->format(generator.number(field.id()));
   case TEXT->generator.text(field.id());
  };
 }
 private static UIElement button(String label,Runnable action){
  var button=new com.lowdragmc.lowdraglib2.gui.ui.elements.Button();
  button.setText(label);
  button.layout(layout->{
   layout.width(96.0F);
   layout.height(12.0F);
  });
  button.setOnClick(event->action.run());
  return button;
 }
 /** One line in chat, which is where a page says what it just did. */
 private static void tell(net.minecraft.network.chat.Component message){
  var player=net.minecraft.client.Minecraft.getInstance().player;
  if(player!=null)player.displayClientMessage(Component.literal("Sable Light Overhaul: ").append(message),false);
 }
 /** The build that wrote an export, so an import from an older one can be recognised as older. */
 private static String modVersion(){
  return net.neoforged.fml.ModList.get().getModContainerById(SableLightOverhaul.ID)
    .map(container->container.getModInfo().getVersion().toString())
    .orElse("unknown");
 }
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,GeneratorEditPayload payload){
  if(owner.level().isClientSide)ClientChannels.sendToServer(payload);
 }
 // ------------------------------------------------------------------ scoped config adapter
 /**
  * This generator's whole page as a {@link Source}, for the shared scoped config window.
  *
  * <p>Client only: the menu's holder is the client's own copy of this block, which is what the page
  * reads today, and every write is still one validated {@link GeneratorEditPayload} to the server.
  * The catalog is {@link GeneratorFields#of}: the table already names every editable value, its
  * widget kind and its bounds, so a row cannot be missed when a field is added.
  *
  * <p>The row's key is the field id, because that is what the payload carries.
  */
 public static dev.sablelightoverhaul.client.ui.Source configSource(ModularUIContainerMenu menu){
  var holder=menu.uiHolder instanceof Holder h?h:null;
  var generator=holder==null?null:holder.generator();
  var source=new dev.sablelightoverhaul.client.ui.DeviceSource(
    generator==null?"":Component.translatable(generator.kind().labelKey()).getString());
  if(generator==null)return source;
  for(var group:generatorGroups(generator.kind())){
   var specs=new java.util.ArrayList<dev.sablelightoverhaul.client.ui.FieldSpec>(group.fields().size());
   var part=dev.sablelightoverhaul.client.ui.ConfigTaxonomy.of(group.taxonomyKey());
   String section=Component.translatable(sectionKey(group.taxonomyKey())).getString();
   for(var field:group.fields())specs.add(spec(field,section));
   source.group(part.category(),part.section(),part.topic(),specs);
  }
  for(var field:GeneratorFields.of(generator.kind())){
   var spec=spec(field,Component.translatable(sectionKey(taxonomyGroup(field))).getString());
   switch(field.type()){
    case TOGGLE->source.bind(spec,()->new dev.sablelightoverhaul.client.ui.FieldValue.Bool(generator.flag(field.id())),
      value->send(holder.player(),GeneratorEditPayload.of(generator.getBlockPos(),field.id(),
        dev.sablelightoverhaul.client.ui.FieldValue.flag(value))));
    case NUMBER->source.bind(spec,()->new dev.sablelightoverhaul.client.ui.FieldValue.Num(generator.number(field.id())),
      value->send(holder.player(),GeneratorEditPayload.of(generator.getBlockPos(),field.id(),
        dev.sablelightoverhaul.client.ui.FieldValue.numeric(value))));
    case TEXT->source.bind(spec,()->new dev.sablelightoverhaul.client.ui.FieldValue.Text(generator.text(field.id())),
      value->{ if(ParticleSpec.valid(generator.getLevel(),text(value))) send(holder.player(),GeneratorEditPayload.of(generator.getBlockPos(),field.id(),text(value))); });
   }
  }
  source.action(Component.translatable("sablelightoverhaul.profile.export").getString(),()->export(generator));
  source.action(Component.translatable("sablelightoverhaul.profile.import").getString(),()->importFromClipboard(generator));
  return source;
 }
 /** One source row, from the table row every consumer already reads; the group is the section, not the row's own. */
 private static dev.sablelightoverhaul.client.ui.FieldSpec spec(GeneratorFields.Field field,String groupLabel){
  var kind=switch(field.type()){
   case TOGGLE->dev.sablelightoverhaul.client.ui.FieldSpec.Kind.TOGGLE;
   case NUMBER->dev.sablelightoverhaul.client.ui.FieldSpec.Kind.SLIDER;
   case TEXT->dev.sablelightoverhaul.client.ui.FieldSpec.Kind.TEXT;
  };
  // Whole numbers get a whole-number step, so a slider cannot land on 1.37 particles. The bounds stay
  // the table's own, so the box accepts exactly what the server would store.
  boolean whole=java.util.Set.of("maxPerScan","searchDepth","lifetimeMin","lifetimeMax","lifetime","intervalAtMaxSignal","intervalAtMinSignal","countAtMaxSignal","countAtMinSignal").contains(field.id());
  String label=Component.translatable(field.labelKey()).getString();
  // Text has no range: its box takes whatever the level's particle parser accepts, not a slider's span.
  double min=kind==dev.sablelightoverhaul.client.ui.FieldSpec.Kind.TEXT?0:field.min();
  double max=kind==dev.sablelightoverhaul.client.ui.FieldSpec.Kind.TEXT?0:field.max();
  double step=kind==dev.sablelightoverhaul.client.ui.FieldSpec.Kind.TEXT?0:whole?1.0:0.01;
  return new dev.sablelightoverhaul.client.ui.FieldSpec(field.id(),groupLabel,label,"",kind,
    min,max,step,java.util.List.of(),false,field.labelKey());
 }
 /** The section the window files this row under; the shared fields belong with the shared ones. */
 private static String taxonomyGroup(GeneratorFields.Field field){
  return field.owner()==GeneratorKind.DUST?"generators.dust":field.owner()==GeneratorKind.SMOKE?"generators.smoke":"generators";
 }
 /**
  * The rows in their existing order, split the way the old page implied: the values every kind has,
  * then the values of this kind. The vortex's fields carry no table section of their own, so they
  * stay with the shared block rather than inventing one.
  */
 private static java.util.List<Group> generatorGroups(GeneratorKind kind){
  var shared=new java.util.ArrayList<GeneratorFields.Field>();
  var own=new java.util.ArrayList<GeneratorFields.Field>();
  for(var field:GeneratorFields.of(kind))(field.owner()==null?shared:own).add(field);
  var groups=new java.util.ArrayList<Group>(2);
  groups.add(new Group("generators",shared));
  if(!own.isEmpty())groups.add(new Group(taxonomyGroup(own.get(0)),own));
  return groups;
 }
 private record Group(String taxonomyKey,java.util.List<GeneratorFields.Field> fields){}
 /**
  * The section's name in the player's language. The taxonomy names sections for the config screen's
  * navigation, so a scoped page asks it for the key and lets the lang file say it: {@code generators}
  * becomes {@code sablelightoverhaul.config.section.generators}.
  */
 private static String sectionKey(String taxonomyKey){return "sablelightoverhaul.config.section."+taxonomyKey;}
 /** A text write; anything that is not text is refused rather than coerced. */
 private static String text(dev.sablelightoverhaul.client.ui.FieldValue value){
  return value instanceof dev.sablelightoverhaul.client.ui.FieldValue.Text t?t.text():"";
 }
}
