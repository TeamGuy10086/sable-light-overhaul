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
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.client.ui.DeviceSource;
import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.FieldValue;
import dev.sablelightoverhaul.client.ui.Source;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import java.util.ArrayList;
import java.util.List;
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
 * The spotlight's page, drawn with LDLib2 exactly like the disco ball's.
 *
 * <p>Every edit goes into the lamp's own block entity, which clamps the value and syncs to every
 * client: the page can therefore show raw numbers without ever being able to store one, and the edit
 * messages are the same payload the generator and disco pages use, so nothing new crosses the network.
 *
 * <p>The page is grouped by what the setting is for, and the fields where a number means something
 * specific are given the control that says so: a switch for the three on/off settings, and a picker
 * with the names of the modes and of the palette for the rest. A text box holding "2" does not tell
 * anyone that two means manual, or that a range of 7 is not seven blocks.
 */
public final class SpotlightLdlib {
 private static final float WIDTH=242,ROW=11,GAP=2,LABEL=112,CONTROL=118;
 /**
  * Rows are grouped, and each group is introduced by its own heading.
  *
  * <p>The first entry is the group's own note key, or empty for none. It used to be a placeholder, and it
  * is used now because a heading alone cannot say what a sweep is for: "红石扫描" reads like a setting a
  * player should understand from the name, and the two rows under it are numbers with no visible effect
  * until a redstone signal arrives.
  */
 private static final String[][] GROUPS={
   {"", "control","latch","switch","channel"},
   {"", "intensity","color","rgb","width","widthMin","widthMax"},
   {"", "angle","azimuth"},
   {"sablelightoverhaul.spotlight.hint.sweep", "sweepBase","sweepRange"},
   {"", "range","falloff","volumeMode","visible"},
            {"", "offsetX","offsetY","offsetZ"},
 };
 /** The three modes, in the order the block entity stores them. */
 private static final String[] MODES={"0 全红石控制","1 半红石开关","2 手动","3 AFN信道"};
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,SableLightOverhaul.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("spotlight",SpotlightLdlib::menuType);
 private SpotlightLdlib(){}
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player,data.readBlockPos())));
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /** Open the page for one lamp. @returns false when the block entity is gone, so the caller can fall back. */
 public static boolean open(Player player,BlockPos pos){
  if (player instanceof net.minecraft.server.level.ServerPlayer remote && !remote.connection.hasChannel(dev.sablelightoverhaul.lighting.GeneratorEditPayload.TYPE)) return false;
  if(!(player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity))return false;
  player.openMenu(new Holder(player,pos),buffer->buffer.writeBlockPos(pos));
  return true;
 }
 record Holder(Player player,BlockPos pos) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable("block.sablelightoverhaul.spotlight");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(UI.of(new UIElement()),owner);}
  @Override public boolean isStillValid(Player owner){return settings()!=null;}
  private SpotlightBlockEntity settings(){
   return player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity lamp?lamp:null;
  }
 }
 private static UI page(SpotlightBlockEntity lamp,Player owner){
  UIElement root=new UIElement().layout(layout->{
   layout.width(WIDTH);
   layout.heightAuto();
   layout.gapAll(GAP);
   layout.paddingAll(4);
   layout.flexDirection(FlexDirection.COLUMN);
  }).style(style->style.backgroundTexture(Sprites.BORDER));
  root.addChild(title(Component.translatable("block.sablelightoverhaul.spotlight"),WIDTH-8));
  root.addChild(hint(Component.translatable("sablelightoverhaul.spotlight.hint"),WIDTH-8));
  for(int group=0;group<GROUPS.length;group++){
   root.addChild(section(Component.translatable("sablelightoverhaul.spotlight.group."+group),WIDTH-8));
   // The group's own explanation, when it has one: two rows of angles whose effect only appears once a
   // redstone signal arrives are not self-explanatory, and the heading has no room to say so.
   if(!GROUPS[group][0].isEmpty())root.addChild(hint(Component.translatable(GROUPS[group][0]),WIDTH-8));
   for(int i=1;i<GROUPS[group].length;i++)root.addChild(row(lamp,GROUPS[group][i],owner));
  }
  return UI.of(root);
 }
 private static UIElement row(SpotlightBlockEntity lamp,String field,Player owner){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChildren(label(Component.translatable("sablelightoverhaul.spotlight.field."+field),LABEL),control(lamp,field,owner));
  return row;
 }
 /** The control a field deserves: a switch for on/off, a picker where a number has names, a box otherwise. */
 private static UIElement control(SpotlightBlockEntity lamp,String field,Player owner){
  return switch(field){
   case "latch","switch","visible","channel"->toggle(lamp,field,owner);
   case "volumeMode"->picker(lamp,field,owner,List.of("0 关闭","1 无灯光空间遮挡","2 灯光空间遮挡"));
   case "control"->picker(lamp,field,owner,List.of(MODES));
   case "falloff"->picker(lamp,field,owner,dev.sablelightoverhaul.Falloff.choices((int)Math.round(lamp.number("falloff"))));
   case "color"->picker(lamp,field,owner,palette());
   // Six hex digits, and when it parses it wins over the palette row above it. The value travels as the
   // colour field itself: a custom colour is a tagged int, and every int fits a double exactly.
   case "rgb"->rgbField(lamp,owner);
   // A free number of blocks now, not a step: the picker could only offer the ladder in the config.
            case "range"->number(lamp,field,owner);
   default->number(lamp,field,owner);
  };
 }
 private static Switch toggle(SpotlightBlockEntity lamp,String field,Player owner){
  Switch widget=new Switch();
  widget.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  widget.setOn(lamp.number(field)!=0.0,false);
  widget.setOnSwitchChanged(on->send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),field,on?1.0:0.0)));
  return widget;
 }
 private static Selector<String> picker(SpotlightBlockEntity lamp,String field,Player owner,List<String> labels){
  Selector<String> selector=new Selector<>();
  selector.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  selector.setCandidates(labels);
  selector.setCandidateUIProvider(com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider.text(dev.sablelightoverhaul.LocalizedText::component));
  // The range is stored as a step but listed from step one, so its row is the stored value minus one;
  // every other field's stored number is already the row. (An earlier version added the offset on the
  // way in and subtracted it on the way out, which was two off and showed the wrong distance.)
  int offset="range".equals(field)?1:0;
  int value=(int)Math.round(lamp.number(field));
  int row=Math.max(0,Math.min(labels.size()-1,value-offset));
  selector.setSelected(labels.get(row),false);
  selector.setOnValueChanged(chosen->{
   int picked=labels.indexOf(chosen);
   if(picked<0)return;
   send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),field,(double)(picked+offset)));
  });
  return selector;
 }
 /** The palette, by the names this mod already shows for it (透明 / 各染料 / 闪烁). */
 private static List<String> palette(){
  var labels=new ArrayList<String>(16);
  for(int i=0;i<=15;i++)labels.add(SourceColor.name(i));
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
 /** One line of explanation, because the modes are not self-explanatory from their names alone. */
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
 private static TextField rgbField(SpotlightBlockEntity lamp,Player owner){
  TextField input=input();
  int stored=(int)Math.round(lamp.number("color"));
  input.setText(SourceColor.isCustom(stored)?SourceColor.hex(stored):"",false);
  input.setTextResponder(text->{
   if(text==null||text.trim().isEmpty()){
    send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),"color",(double)SourceColor.UNTINTED));
    return;
   }
   int packed=SourceColor.parseCustom(text);
   if(packed<0)return;
   send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),"color",(double)packed));
  });
  return input;
 }
 private static TextField number(SpotlightBlockEntity lamp,String field,Player owner){
  TextField input=input();
  input.setNumbersOnlyDouble(min(field),max(field));
  input.setText(format(lamp.number(field)),false);
  input.setWheelDur(1,0.1F);
  input.setTextResponder(text->{
   try{
    send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),field,Double.parseDouble(text)));
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
 /** The block entity's own clamps, so a field cannot be typed outside what it would store anyway. */
 private static double min(String field){
  return switch(field){
   case "angle","sweepBase","sweepRange"->-90;case "azimuth"->-360;case "widthMin","widthMax"->1;
   case "width"->0;case "range"->1;case "offsetX","offsetY","offsetZ"->-8;
   default->0;
  };
 }
 private static double max(String field){return switch(field){
  case "control"->2;case "intensity","width","color"->15;case "angle","sweepBase","sweepRange"->90;
  case "widthMin","widthMax"->90;
  case "azimuth"->360;case "range"->1024;case "falloff"->5;case "offsetX","offsetY","offsetZ"->8;
  default->1;
 };}
 /** Two decimal places, which is what every other free value on this page already shows. */
 private static String format(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,GeneratorEditPayload payload){
  if(owner.level().isClientSide)ClientChannels.sendToServer(payload);
 }
 // ---- the same page for the shared configuration window --------------------------------------------
 /** The page's category, and the section it belongs to, in the window's three-level taxonomy. */
 private static final String CATEGORY="光源",SECTION="射灯";
 /** Where the hexadecimal box stores its colour. {@link SourceColor} tags a hand-typed colour as one int. */
 private static final String RGB="rgb";
 /** Six hex digits, optional prefix: what the colour box accepts, and what a palette row must never take. */
 private static final java.util.regex.Pattern HEX=java.util.regex.Pattern.compile("(?i)[#]?[0-9a-f]{6}");
 /** Human-readable group names, one per entry of {@link #GROUPS}, in its order. */
 private static final String[] TOPICS={"红石与开关","亮度与光锥","朝向","红石扫描","距离与衰减","位置偏移"};
 /**
  * The lamp's page as a {@link Source}, for the configuration window.
  *
  * <p>Client side only, because the rows read a block entity that has to be in this client's world. The
  * adapter is a view and nothing else: readers read the live block entity, and a write posts the same
  * validated {@link GeneratorEditPayload} the page's own controls post, so the server's clamp is still the
  * only thing that stores a value and nothing here changes locally.
  *
  * <p>{@code rgb} is the palette row's own colour and therefore a number, not the six-digit text the old
  * page's box held: the window hands a number back as a {@link FieldValue.Choice}, and reading it as an
  * index would turn a hand-typed colour into {@link SourceColor#UNTINTED}.
  *
  * @returns the adapter, or {@code null} when this is not the client, the menu is another page's, or the
  *   lamp is no longer there.
  */
 public static Source configSource(ModularUIContainerMenu menu){
  if(menu==null)return null;
  if(!(menu.uiHolder instanceof Holder holder))return null;
  SpotlightBlockEntity lamp=holder.settings();
  if(lamp==null)return null;
  Player owner=holder.player();
  if(!owner.level().isClientSide)return null;
  DeviceSource source=new DeviceSource(Component.translatable("block.sablelightoverhaul.spotlight").getString());
  for(int group=0;group<GROUPS.length;group++){
   List<FieldSpec> fields=new ArrayList<>(GROUPS[group].length-1);
   for(int i=1;i<GROUPS[group].length;i++)fields.add(spec(lamp,GROUPS[group][i],TOPICS[group]));
   source.group(CATEGORY,SECTION,TOPICS[group],fields);
  }
  for(int group=0;group<GROUPS.length;group++){
   for(int i=1;i<GROUPS[group].length;i++){
    String field=GROUPS[group][i];
    source.bind(spec(lamp,field,TOPICS[group]),()->read(lamp,field),value->sendEdit(owner,lamp,field,value));
   }
  }
  return source;
 }
 /**
  * One row's shape: its label is the page's own translation key, and its bounds are the page's own clamps.
  *
  * <p>An empty tooltip stays empty: the window shows one only when there is one, and the group heading
  * already carries the sweep's explanation.
  */
 private static FieldSpec spec(SpotlightBlockEntity lamp,String field,String topic){
  String labelKey="sablelightoverhaul.spotlight.field."+field;
  return switch(field){
   case "latch","switch","visible","channel"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.TOGGLE,0,1,1,List.of(),false,labelKey);
   case "volumeMode"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.CYCLER,0,2,1,List.of("0 关闭","1 无灯光空间遮挡","2 灯光空间遮挡"),false,labelKey);
   case "control"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.CYCLER,0,MODES.length-1,1,List.of(MODES),false,labelKey);
   // The list depends on what is stored, so it is listed with the disabled profile only when the lamp is
   // already on it -- exactly as the old picker does -- and the row's end is that list's end.
   case "falloff"->falloffSpec(lamp,field,topic,labelKey);
   case "color"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.CYCLER,0,15,1,palette(),false,labelKey);
   // A colour, not a palette index: the stored int is tagged, and every int fits a double exactly.
   case RGB->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.COLOR,0,0xFFFFFF,1,List.of(),false,labelKey);
   default->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.SLIDER,min(field),max(field),java.util.Set.of("intensity","width","range").contains(field)?1:0.01,List.of(),false,labelKey);
  };
 }
 private static FieldSpec falloffSpec(SpotlightBlockEntity lamp,String field,String topic,String labelKey){
  List<String> labels=dev.sablelightoverhaul.Falloff.choices((int)Math.round(lamp.number("falloff")));
  return new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.CYCLER,0,labels.size()-1,1,labels,false,labelKey);
 }
 /** What one row shows now, in the shape its spec asks for. {@code rgb} is derived, never stored. */
 private static FieldValue read(SpotlightBlockEntity lamp,String field){
  return switch(field){
   case "latch","switch","visible","channel"->new FieldValue.Bool(lamp.number(field)!=0.0);
   case "control"->choice(lamp,"control",List.of(MODES));
   case "volumeMode"->choice(lamp,"volumeMode",List.of("0 关闭","1 无灯光空间遮挡","2 灯光空间遮挡"));
   case "falloff"->choice(lamp,"falloff",dev.sablelightoverhaul.Falloff.choices((int)Math.round(lamp.number("falloff"))));
   case "color"->choice(lamp,"color",palette());
   case RGB->coloured(lamp.number("color"));
   default->number(lamp,field);
  };
 }
 private static FieldValue number(SpotlightBlockEntity lamp,String field){
  return new FieldValue.Num(lamp.number(field));
 }
 /** The picker with a stored number behind it: the row is the stored value, or zero when it names no row. */
 private static FieldValue choice(SpotlightBlockEntity lamp,String field,List<String> labels){
  int row=(int)Math.round(lamp.number(field));
  if(row<0||row>=labels.size())row=0;
  return new FieldValue.Choice(row,labels.get(row));
 }
 /** The colour box's contents: the hand-typed colour's hex, or empty while a palette row is in charge. */
 private static FieldValue coloured(double stored){
  int index=(int)Math.round(stored);
  return new FieldValue.Num(SourceColor.isCustom(index)?index&0xFFFFFF:0);
 }
 /**
  * The row's colour as one packed number: a hand-typed colour as {@link SourceColor#custom}, a palette
  * row as its index.
  *
  * <p>Two shapes arrive. The window parses a colour row's text as plain {@code RRGGBB} and hands back a
  * {@link FieldValue.Choice} named with it; but a number already at or above {@link SourceColor#CUSTOM}
  * is a tagged colour that came that way, and every smaller one is a palette index, so a choice's index
  * is only the fallback for a name that is neither hex nor a row the picker lists.
  */
 private static int packed(FieldValue value){
  return SourceColor.custom((int)Math.round(FieldValue.num(value)) & 0xFFFFFF);
 }

 /** One edit from the window, through the same validated payload and the same server clamp as the page. */
 private static void sendEdit(Player owner,SpotlightBlockEntity lamp,String field,FieldValue value){
  if("latch".equals(field)||"switch".equals(field)||"visible".equals(field)||"channel".equals(field)){
   send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),field,FieldValue.flag(value)));
   return;
  }
  if("volumeMode".equals(field)||"control".equals(field)||"falloff".equals(field)||"color".equals(field)){
   send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),field,(double)choiceIndex(field,lamp,FieldValue.numeric(value))));
   return;
  }
  if(RGB.equals(field)){
   send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),"color",(double)packed(value)));
   return;
  }
  // An out-of-range number is refused here as the old page's box refused it, rather than sent to be clamped:
  // the page's own field never accepted a value outside the lamp's bounds in the first place.
  double next=FieldValue.numeric(value);
  if(Double.isNaN(next)||next<min(field)||next>max(field))return;
  send(owner,GeneratorEditPayload.of(lamp.getBlockPos(),field,next));
 }
 /** The row a picker settled on, kept inside what that picker currently offers. */
 private static int choiceIndex(String field,SpotlightBlockEntity lamp,double index){
  int rows=switch(field){
   case "volumeMode"->3;
   case "control"->MODES.length;
   case "falloff"->dev.sablelightoverhaul.Falloff.choices((int)Math.round(lamp.number("falloff"))).size();
   default->SourceColor.COUNT;
  };
  int picked=(int)Math.round(index);
  return Math.max(0,Math.min(rows-1,picked));
 }
}
