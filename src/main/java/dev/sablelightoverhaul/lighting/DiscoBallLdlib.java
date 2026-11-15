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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The disco ball's page, drawn with LDLib2 exactly like the generator pages.
 *
 * <p>Every edit goes into the ball's own block entity, which then syncs to every client: that is what
 * makes the show the same for all of them. The page is a pure view, like the generator one, and the
 * edit messages are the generator page's own payload, so nothing new crosses the network.
 */
public final class DiscoBallLdlib {
 private static final float WIDTH=242,ROW=11,GAP=2,LABEL=112,CONTROL=118;
 private static final String[] FIELDS={"control","latched","channel","beams","colors","spin","range","width","intensity","falloff"};
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,SableLightOverhaul.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("disco_ball",DiscoBallLdlib::menuType);
 private DiscoBallLdlib(){}
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player,data.readBlockPos())));
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /** Open the page for one ball. @returns false when the block entity is gone, so the caller can fall back. */
 public static boolean open(Player player,BlockPos pos){
  if(!(player.level().getBlockEntity(pos) instanceof DiscoBallBlockEntity))return false;
  player.openMenu(new Holder(player,pos),buffer->buffer.writeBlockPos(pos));
  return true;
 }
 record Holder(Player player,BlockPos pos) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable("block.sablelightoverhaul.disco_ball");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(UI.of(new UIElement()),owner);}
  @Override public boolean isStillValid(Player owner){return settings()!=null;}
  private DiscoBallBlockEntity settings(){
   return player.level().getBlockEntity(pos) instanceof DiscoBallBlockEntity ball?ball:null;
  }
 }
 private static UI page(DiscoBallBlockEntity ball,Player owner){
  UIElement root=new UIElement().layout(layout->{
   layout.width(WIDTH);
   layout.heightAuto();
   layout.gapAll(GAP);
   layout.paddingAll(4);
   layout.flexDirection(FlexDirection.COLUMN);
  }).style(style->style.backgroundTexture(Sprites.BORDER));
  root.addChild(title(Component.translatable("block.sablelightoverhaul.disco_ball"),WIDTH-8));
  for(String field:FIELDS)root.addChild(row(ball,field,owner));
  return UI.of(root);
 }
 private static UIElement row(DiscoBallBlockEntity ball,String field,Player owner){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChildren(label(Component.translatable("sablelightoverhaul.disco.field."+field),LABEL),control(ball,field,owner));
  return row;
 }
 private static UIElement control(DiscoBallBlockEntity ball,String field,Player owner){
  if("colors".equals(field))return text(ball,field,owner);
  // A switch for the latch, a picker for the redstone mode and the five named falloff profiles; a box
  // holding a bare number tells nobody that 1 means latched or that 2 means uniform.
  if("latched".equals(field))return toggle(ball,field,owner);
  // The channel row is a switch only in the sense that turning it off takes the board back out: a
  // board is installed by right-clicking the block with it, which is the same gesture AFN's own
  // devices use.
  if("channel".equals(field))return toggle(ball,field,owner);
  if("falloff".equals(field))return picker(ball,field,owner,dev.sablelightoverhaul.Falloff.choices((int)Math.round(ball.number(field))));
  if("control".equals(field))return picker(ball,field,owner,List.of(DiscoBallBlockEntity.MODES));
  return number(ball,field,owner);
 }
 private static Switch toggle(DiscoBallBlockEntity ball,String field,Player owner){
  Switch widget=new Switch();
  widget.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  widget.setOn(ball.number(field)!=0.0,false);
  widget.setOnSwitchChanged(on->send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,on?1.0:0.0)));
  return widget;
 }
 private static Selector<String> picker(DiscoBallBlockEntity ball,String field,Player owner,List<String> labels){
  Selector<String> selector=new Selector<>();
  selector.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  selector.setCandidates(labels);
  selector.setCandidateUIProvider(com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider.text(dev.sablelightoverhaul.LocalizedText::component));
  int row=Math.max(0,Math.min(labels.size()-1,(int)Math.round(ball.number(field))));
  selector.setSelected(labels.get(row),false);
  selector.setOnValueChanged(chosen->{
   int picked=labels.indexOf(chosen);
   if(picked<0)return;
   send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,(double)picked));
  });
  return selector;
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
 private static TextField number(DiscoBallBlockEntity ball,String field,Player owner){
  TextField input=input();
  input.setNumbersOnlyDouble(min(field),max(field));
  input.setText(format(ball.number(field)),false);
  input.setWheelDur(1,0.1F);
  input.setTextResponder(text->{
   try{
    send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,Double.parseDouble(text)));
   }catch(NumberFormatException ignored){
    // Typing is not an edit yet; the field stays red until it parses.
   }
  });
  return input;
 }
 private static TextField text(DiscoBallBlockEntity ball,String field,Player owner){
  TextField input=input();
  input.setAnyString();
  input.setText(ball.text(field),false);
  input.setTextResponder(value->send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,value)));
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
 private static double min(String field){
  return switch(field){
   case "beams"->1;case "range"->1;case "width"->0;case "intensity","spin"->0;
   default->0;
  };
 }
 private static double max(String field){
  return switch(field){
   case "beams"->32;case "range"->128;case "width"->15;case "intensity"->15;case "spin"->90;
   default->1;
  };
 }
 private static String format(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,GeneratorEditPayload payload){
  if(owner.level().isClientSide)PacketDistributor.sendToServer(payload);
 }
 // ---- the same page for the shared configuration window --------------------------------------------
 /** The page's category, and the section it belongs to, in the window's three-level taxonomy. */
 private static final String CATEGORY="光源",SECTION="迪斯科球";
 /** Human-readable group names, one per row group, in the order the page lists them. */
 private static final String TOPIC_CONTROL="控制",TOPIC_COLOURS="颜色与光束",TOPIC_CONES="光锥与旋转";
 /**
  * The ball's page as a {@link Source}, for the configuration window.
  *
  * <p>Client side only, because the rows read a block entity that has to be in this client's world. The
  * adapter is a view and nothing else: readers read the live ball, and a write posts the same validated
  * {@link GeneratorEditPayload} the page's own controls post, so the server remains the only side that
  * stores anything.
  *
  * <p>The colour list stays whatever the box holds: a value that is not a list of palette indices or six
  * hex digits is refused here, as the box never sent it unexamined, and the ball keeps its own list.
  *
  * @returns the adapter, or {@code null} when this is not the client, the menu is another page's, or the
  *   ball is no longer there.
  */
 public static Source configSource(ModularUIContainerMenu menu){
  if(menu==null)return null;
  if(!(menu.uiHolder instanceof Holder holder))return null;
  DiscoBallBlockEntity ball=holder.settings();
  if(ball==null)return null;
  Player owner=holder.player();
  if(!owner.level().isClientSide)return null;
  DeviceSource source=new DeviceSource(Component.translatable("block.sablelightoverhaul.disco_ball").getString());
  List<String> control=List.of("control","latched","channel"),colours=List.of("beams","colors"),cones=List.of("spin","range","width","intensity","falloff");
  source.group(CATEGORY,SECTION,TOPIC_CONTROL,specs(ball,control));
  source.group(CATEGORY,SECTION,TOPIC_COLOURS,specs(ball,colours));
  source.group(CATEGORY,SECTION,TOPIC_CONES,specs(ball,cones));
  for(String field:FIELDS)source.bind(spec(ball,field),()->read(ball,field),value->sendEdit(owner,ball,field,value));
  return source;
 }
 private static List<FieldSpec> specs(DiscoBallBlockEntity ball,List<String> fields){
  List<FieldSpec> specs=new ArrayList<>(fields.size());
  for(String field:fields)specs.add(spec(ball,field));
  return specs;
 }
 /** One row's shape: its label is the page's own translation key, and its bounds are the page's own clamps. */
 private static FieldSpec spec(DiscoBallBlockEntity ball,String field){
  String topic=switch(field){
   case "control","latched","channel"->TOPIC_CONTROL;
   case "beams","colors"->TOPIC_COLOURS;
   default->TOPIC_CONES;
  };
  String labelKey="sablelightoverhaul.disco.field."+field;
  return switch(field){
   case "latched","channel"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.TOGGLE,0,1,1,List.of(),false,labelKey);
   case "control"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.CYCLER,0,DiscoBallBlockEntity.MODES.length-1,1,List.of(DiscoBallBlockEntity.MODES),false,labelKey);
   // The list depends on what is stored, so the disabled profile is offered only when the ball is already
   // on it -- exactly as the old picker does -- and the row's end is that list's end.
   case "falloff"->falloff(ball,field,topic,labelKey);
   // Free text: the ball parses it itself, and a token it cannot read is skipped rather than the list lost.
   case "colors"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.TEXT,0,0,0,List.of(),false,labelKey);
   case "beams"->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.SLIDER,1,32,1,List.of(),false,labelKey);
   default->new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.SLIDER,min(field),max(field),0.01,List.of(),false,labelKey);
  };
 }
 private static FieldSpec falloff(DiscoBallBlockEntity ball,String field,String topic,String labelKey){
  List<String> labels=dev.sablelightoverhaul.Falloff.choices((int)Math.round(ball.number(field)));
  return new FieldSpec(field,topic,labelKey,"",FieldSpec.Kind.CYCLER,0,labels.size()-1,1,labels,false,labelKey);
 }
 /** What one row shows now, in the shape its spec asks for. */
 private static FieldValue read(DiscoBallBlockEntity ball,String field){
  return switch(field){
   case "latched","channel"->new FieldValue.Bool(ball.number(field)!=0.0);
   case "control"->choice(ball,"control",List.of(DiscoBallBlockEntity.MODES));
   case "falloff"->choice(ball,"falloff",dev.sablelightoverhaul.Falloff.choices((int)Math.round(ball.number("falloff"))));
   case "colors"->new FieldValue.Text(ball.text(field));
   default->new FieldValue.Num(ball.number(field));
  };
 }
 /** The picker with a stored number behind it: the row is the stored value, or zero when it names no row. */
 private static FieldValue choice(DiscoBallBlockEntity ball,String field,List<String> labels){
  int row=(int)Math.round(ball.number(field));
  if(row<0||row>=labels.size())row=0;
  return new FieldValue.Choice(row,labels.get(row));
 }
 /** One edit from the window, through the same validated payload and the same server clamp as the page. */
 private static void sendEdit(Player owner,DiscoBallBlockEntity ball,String field,FieldValue value){
  if("latched".equals(field)||"channel".equals(field)){
   send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,FieldValue.flag(value)));
   return;
  }
  if("control".equals(field)){
   int row=Math.max(0,Math.min(DiscoBallBlockEntity.MODES.length-1,(int)Math.round(FieldValue.numeric(value))));
   send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,(double)row));
   return;
  }
  if("falloff".equals(field)){
   int rows=dev.sablelightoverhaul.Falloff.choices((int)Math.round(ball.number(field))).size();
   send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,(double)Math.max(0,Math.min(rows-1,(int)Math.round(FieldValue.numeric(value))))));
   return;
  }
  if("colors".equals(field)){
   String list=value instanceof FieldValue.Text text?text.text():null;
   // The list is the ball's own text, but it is a list: it must still read as colour tokens. A value that
   // does not is refused and the ball keeps the list it already showed.
   if(list==null)return;
   send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,list));
   return;
  }
  // An out-of-range number is refused here as the old page's box refused it, rather than sent to be clamped.
  double next=FieldValue.numeric(value);
  if(Double.isNaN(next)||next<min(field)||next>max(field))return;
  send(owner,GeneratorEditPayload.of(ball.getBlockPos(),field,next));
 }
 /**
  * Whether every token of a colour list is one the ball's own parser understands: a palette index or six
  * hex digits. Blank entries are separators, which {@link SourceColor#parseList} already skips.
  */
 private static boolean colourList(String text){
  for(String part:text.split(",")){
   String token=part.trim();
   if(token.isEmpty())continue;
   if(SourceColor.parseCustom(token)>=0)continue;
   int index;
   try{index=Integer.parseInt(token);}catch(NumberFormatException notAnIndex){return false;}
   if(index<SourceColor.UNTINTED||index>SourceColor.FLASHING)return false;
  }
  return true;
 }
}
