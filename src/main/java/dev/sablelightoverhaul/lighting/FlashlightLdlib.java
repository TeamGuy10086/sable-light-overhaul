package dev.sablelightoverhaul.lighting;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.sablelightoverhaul.FlashlightBeam;
import dev.sablelightoverhaul.FlashlightEditPayload;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The flashlight's page, drawn with LDLib2 exactly like the lamps'.
 *
 * <p>The one difference from the spotlight's page is what the settings belong to: a flashlight is
 * carried, so its values live on the player and not on a block in the world. Nothing is therefore
 * written into or read out of the menu data — the holder is rebuilt on the client from the player
 * opening it, and a menu that carried a position would be one that could point at another player.
 *
 * <p>Every edit is still one field in one message, and the server is still the only side that stores
 * anything: it clamps the value, keeps it in that player's persistent data and pushes it back to that
 * client alone. The page can hence show a number without ever being able to keep one, and a player who
 * is not an operator gets nothing back.
 *
 * <p>The two groups are the two lights the item actually draws — the small light carried around the
 * player and the cone thrown down the gaze — because a single list of seven numbers would not say which
 * of them make the beam and which make the glow at your feet.
 */
public final class FlashlightLdlib {
 private static final float WIDTH=242,ROW=11,GAP=2,LABEL=112,CONTROL=118;
 /** Rows are grouped, and each group is introduced by its own heading. */
 private static final String[][] GROUPS={
   {"", "pointRadius","pointIntensity","color","rgb"},
   {"", "range","width","falloff","decay","intensity","volumeMode"},
 };
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,SableLightOverhaul.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("flashlight",FlashlightLdlib::menuType);
 private FlashlightLdlib(){}
 /** No position is written or read: the holder is the player who opened the page, on both sides. */
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player)));
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /** Open the page for one player. Always true: unlike a lamp, there is nothing that could have gone. */
 public static boolean open(Player player){
  player.openMenu(new Holder(player));
  return true;
 }
 record Holder(Player player) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable("item.sablelightoverhaul.flashlight");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(UI.of(new UIElement()),owner);}
  /**
   * Always valid: the settings are read from the player looking at them, so this holder cannot go stale
   * the way a block-keyed one can when the lamp is broken. The write side is gated on the operator check
   * in {@link FlashlightSettings#setNumber}, which is where that decision belongs.
   */
  @Override public boolean isStillValid(Player owner){return true;}
 }
 private static UI page(Player owner){
  UIElement root=new UIElement().layout(layout->{
   layout.width(WIDTH);
   layout.heightAuto();
   layout.gapAll(GAP);
   layout.paddingAll(4);
   layout.flexDirection(FlexDirection.COLUMN);
  }).style(style->style.backgroundTexture(Sprites.BORDER));
  root.addChild(title(Component.translatable("item.sablelightoverhaul.flashlight"),WIDTH-8));
  root.addChild(hint(Component.translatable("sablelightoverhaul.flashlight.pageHint"),WIDTH-8));
  for(int group=0;group<GROUPS.length;group++){
   root.addChild(section(Component.translatable("sablelightoverhaul.flashlight.group."+group),WIDTH-8));
   for(int i=1;i<GROUPS[group].length;i++)root.addChild(row(owner,GROUPS[group][i]));
  }
  return UI.of(root);
 }
 private static UIElement row(Player owner,String field){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChildren(label(Component.translatable("sablelightoverhaul.flashlight.field."+field),LABEL),control(owner,field));
  return row;
 }
 /** The control a field deserves: a picker where a number has names, a box where it is just a number. */
 private static UIElement control(Player owner,String field){
  return switch(field){
   case "color"->picker(owner,field,palette());
   case "rgb"->rgbField(owner);
   case "range"->picker(owner,field,ranges());
   // The same five the spotlights offer, because it is the same table: this is the cone's cross-section.
   case "volumeMode"->picker(owner,field,List.of("0 关闭","1 无灯光空间遮挡","2 灯光空间遮挡"));
   case "falloff"->picker(owner,field,List.of(SpotlightBlock.MODES));
   default->number(owner,field);
  };
 }
 private static Selector<String> picker(Player owner,String field,List<String> labels){
  Selector<String> selector=new Selector<>();
  selector.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  selector.setCandidates(labels);
  selector.setCandidateUIProvider(com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider.text(dev.sablelightoverhaul.LocalizedText::component));
  int index=Math.max(0,Math.min(labels.size()-1,current(owner,field)-labelBase(field)));
  selector.setSelected(labels.get(index),false);
  selector.setOnValueChanged(chosen->{
   int picked=labels.indexOf(chosen);
   if(picked<0)return;
   send(owner,field,(double)(picked+labelBase(field)));
  });
  return selector;
 }
 /**
  * The value a picker should sit on.
  *
  * <p>The range is the one field whose number arrives in different units on the two sides: the server
  * stores the step, while the state the client draws the beam from carries the blocks that step means
  * (see {@code HeldLights}), so a number that is exactly one of the distances is read as that distance.
  * A step that happens to equal a distance would be read the same way, but the two are then the same row.
  */
 private static int current(Player owner,String field){
  int value=(int)Math.round(FlashlightSettings.number(owner,field));
  if(!"range".equals(field))return value;
  for(int step=1;step<=LightingCommon.count();step++)if(LightingCommon.distance(step)==value)return step;
  return value;
 }
 /** The value a field's first label stands for: the range's labels are distances, so they start at step one. */
 private static int labelBase(String field){return "range".equals(field)?1:0;}
 /** The palette, by the names this mod already shows for it (透明 / 各染料 / 闪烁). */
 private static List<String> palette(){
  var labels=new ArrayList<String>(16);
  for(int i=0;i<=15;i++)labels.add(SourceColor.name(i));
  return labels;
 }
 /** Every distance step, labelled with the distance it actually means. */
 private static List<String> ranges(){
  var labels=new ArrayList<String>(LightingCommon.count());
  for(int step=1;step<=LightingCommon.count();step++)labels.add(dev.sablelightoverhaul.LocalizedText.component("%s 格",LightingCommon.distance(step)).getString());
  return labels;
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
 /** A group heading: dimmer than a field label, so the page reads as sections rather than one list. */
 private static Label section(Component text,float width){
  Label label=label(text,width);
  label.textStyle(style->style.textColor(0xFFC080).textShadow(false).fontSize(8.0F)
    .adaptiveWidth(false).adaptiveHeight(false).textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER));
  return label;
 }
 /** One line of explanation, because who the settings belong to is not visible from the page itself. */
 private static Label hint(Component text,float width){
  Label label=label(text,width);
  label.textStyle(style->style.textColor(0xA0A0A0).textShadow(false).fontSize(7.0F)
    .adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.HOVER_ROLL).textAlignHorizontal(Horizontal.LEFT));
  label.layout(layout->{
   layout.width(width);
   layout.height(9.0F);
  });
  return label;
 }
 private static TextField rgbField(Player owner){
  TextField input=input();
  int stored=(int)Math.round(FlashlightSettings.number(owner,"color"));
  input.setText(SourceColor.isCustom(stored)?SourceColor.hex(stored):"",false);
  input.setTextResponder(text->{
   if(text==null||text.trim().isEmpty()){send(owner,"color",SourceColor.UNTINTED);return;}
   int packed=SourceColor.parseCustom(text);
   if(packed<0)return;
   send(owner,"color",packed);
  });
  return input;
 }
 private static TextField number(Player owner,String field){
  TextField input=input();
  input.setNumbersOnlyDouble(min(field),max(field));
  input.setText(format(FlashlightSettings.number(owner,field)),false);
  input.setWheelDur(1,0.1F);
  input.setTextResponder(text->{
   try{
    send(owner,field,Double.parseDouble(text));
   }catch(NumberFormatException ignored){
    // Typing is not an edit yet; the field stays red until it parses.
   }
  });
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
 /** The server's own clamps, so a field cannot be typed outside what it would store anyway. */
 private static double min(String field){
  return switch(field){
   case "width"->FlashlightBeam.MIN_WIDTH_DEGREES;
   case "decay"->FlashlightBeam.MIN_DECAY_PERCENT;
   default->0;
  };
 }
 private static double max(String field){
  return switch(field){
   case "width"->FlashlightBeam.MAX_WIDTH_DEGREES;
   case "decay"->FlashlightBeam.MAX_DECAY_PERCENT;
   case "intensity","pointIntensity"->15;
   case "pointRadius"->24;
   default->1;
  };
 }
 private static String format(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,String field,double value){
  if(owner.level().isClientSide)PacketDistributor.sendToServer(new FlashlightEditPayload(field,value));
 }
 // ------------------------------------------------------------------ scoped config adapter
 /**
  * The flashlight's whole page as a {@link Source}, for the shared scoped config window.
  *
  * <p>Client only: the menu's holder is the player who opened the page, on both sides (see
  * {@link Holder}), so the values read here are the ones the server last pushed to this client, and
  * every write is still one validated {@link FlashlightEditPayload} the server clamps and gates on
  * the operator check in {@link FlashlightSettings#setNumber}.
  *
  * <p>The catalog is {@link FlashlightSettings#FIELDS} -- every field the page edits, in the order the
  * old page showed it, split into its two sections -- and each row's control, labels, bounds and
  * offset come from the same helpers {@link #control} uses, so the two pages cannot drift. The player's
  * own on/off switch is not here: that is the item's switch, not a page field.
  */
 public static dev.sablelightoverhaul.client.ui.Source configSource(ModularUIContainerMenu menu){
  var holder=menu.uiHolder instanceof Holder h?h:null;
  var source=new dev.sablelightoverhaul.client.ui.DeviceSource(
    Component.translatable("item.sablelightoverhaul.flashlight").getString());
  if(holder==null)return source;
  var owner=holder.player();
  for(int group=0;group<GROUPS.length;group++){
   String section=section(group);
   var specs=new java.util.ArrayList<dev.sablelightoverhaul.client.ui.FieldSpec>(GROUPS[group].length-1);
   for(int i=1;i<GROUPS[group].length;i++)specs.add(spec(GROUPS[group][i],section));
   // The page is scoped to the flashlight, so the navigation is its own section rather than the whole config.
   source.group(FLASHLIGHT_CATEGORY,section,section,specs);
  }
  for(int group=0;group<GROUPS.length;group++)for(int i=1;i<GROUPS[group].length;i++){
   String field=GROUPS[group][i];
   var spec=spec(field,section(group));
   // A string field: the hex box. Its text is the state the reader shows, and the writer converts it
   // back the way the old box did, including "empty means the palette row above".
   if(isHex(field)){
    source.bind(spec,()->new dev.sablelightoverhaul.client.ui.FieldValue.Text(hexText(owner)),
      value->writeHex(owner,value));continue;
   }
   // A picker: the stored number is an index into the labels, and for the range the labels start at step one.
   if(isPicker(field)){
    source.bind(spec,()->choice(field,current(owner,field)),
      value->writeChoice(owner,field,value));continue;
   }
   source.bind(spec,()->new dev.sablelightoverhaul.client.ui.FieldValue.Num(
      Math.rint(FlashlightSettings.number(owner,field))),
     value->send(owner,field,dev.sablelightoverhaul.client.ui.FieldValue.numeric(value)));
  }
  return source;
 }
 /** Where the scoped page sits in the config taxonomy; the row itself is the page's own grouping. */
 private static final String FLASHLIGHT_CATEGORY=dev.sablelightoverhaul.client.ui.ConfigTaxonomy.of("flashlight.style").category();
 /** One section's name: the heading the old page already gave it. */
 private static String section(int group){
  return Component.translatable("sablelightoverhaul.flashlight.group."+group).getString();
 }
 /** The fields the old page drew as a text box rather than a picker. */
 private static boolean isPicker(String field){return "color".equals(field)||"range".equals(field)||"volumeMode".equals(field)||"falloff".equals(field);}
 /** The one string field the source offers: the hand-typed colour, which is not a number at all. */
 private static boolean isHex(String field){return "rgb".equals(field);}
 /** The labels a picker offers, from the same helpers the old page uses. */
 private static List<String> options(String field){
  return switch(field){
   case "color"->palette();
   case "range"->ranges();
   case "volumeMode"->List.of("0 关闭","1 无灯光空间遮挡","2 灯光空间遮挡");
   case "falloff"->List.of(SpotlightBlock.MODES);
   default->List.of();
  };
 }
 /** One source row, described from the same helpers the old page's control uses. */
 private static dev.sablelightoverhaul.client.ui.FieldSpec spec(String field,String groupLabel){
  String labelKey="sablelightoverhaul.flashlight.field."+field;
  String label=Component.translatable(labelKey).getString();
  if(isHex(field))return new dev.sablelightoverhaul.client.ui.FieldSpec(field,groupLabel,label,"",
    dev.sablelightoverhaul.client.ui.FieldSpec.Kind.TEXT,0,0,0,List.of(),false,labelKey);
  if(isPicker(field)){
   var labels=options(field);
   int base=labelBase(field);
   // The row's numeric range is the range the picker stores (choice + label base), never the label's
   // own 0..n-1 -- that is exactly the offset the old page applied when it sent the picked number.
   return new dev.sablelightoverhaul.client.ui.FieldSpec(field,groupLabel,label,"",
     dev.sablelightoverhaul.client.ui.FieldSpec.Kind.CYCLER,base,base+labels.size()-1,1,labels,false,labelKey);
  }
  // The stored values are whole numbers, so the slider steps by one between the server's own clamps.
  return new dev.sablelightoverhaul.client.ui.FieldSpec(field,groupLabel,label,"",
    dev.sablelightoverhaul.client.ui.FieldSpec.Kind.SLIDER,min(field),max(field),1,List.of(),false,labelKey);
 }
 /** The current picker position, as a {@link FieldValue.Choice}: index and the label it shows. */
 private static dev.sablelightoverhaul.client.ui.FieldValue.Choice choice(String field,double stored){
  var labels=options(field);
  int index=(int)Math.round(stored)-labelBase(field);
  index=Math.max(0,Math.min(labels.size()-1,index));
  return new dev.sablelightoverhaul.client.ui.FieldValue.Choice(index,labels.get(index));
 }
 /**
  * A picker write: the chosen label's position plus the field's offset is the number stored.
  *
  * <p>The offset is the reason a choice is not read as a plain number: the range's first label is step
  * one, so choice 0 means 1, not 0.
  */
 private static void writeChoice(Player owner,String field,dev.sablelightoverhaul.client.ui.FieldValue value){
  if(!(value instanceof dev.sablelightoverhaul.client.ui.FieldValue.Choice choice))return;
  var labels=options(field);
  // The index is authoritative, as the screen documents; the name is a fallback for a picker that
  // reports only the label. Bounds are checked here because this is the side that knows the table.
  int picked=choice.index();
  if(picked<0||picked>=labels.size())picked=labels.indexOf(choice.name());
  if(picked<0)return;
  send(owner,field,(double)(picked+labelBase(field)));
 }
 /** The hex box's current text: a custom colour's digits, and empty when the palette row is in charge. */
 private static String hexText(Player owner){
  int stored=(int)Math.round(FlashlightSettings.number(owner,"color"));
  return SourceColor.isCustom(stored)?SourceColor.hex(stored):"";
 }
 /** A hex box write, converted the way the old box converted it; unparsable text is refused, not sent. */
 private static void writeHex(Player owner,dev.sablelightoverhaul.client.ui.FieldValue value){
  if(!(value instanceof dev.sablelightoverhaul.client.ui.FieldValue.Text text))return;
  String digits=text.text();
  if(digits==null||digits.trim().isEmpty()){send(owner,"color",SourceColor.UNTINTED);return;}
  int packed=SourceColor.parseCustom(digits);
  if(packed<0)return;
  send(owner,"color",packed);
 }
}
