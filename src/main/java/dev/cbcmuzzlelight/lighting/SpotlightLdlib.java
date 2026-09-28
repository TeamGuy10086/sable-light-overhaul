package dev.cbcmuzzlelight.lighting;

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
import dev.cbcmuzzlelight.MuzzleLight;
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
 /** Rows are grouped, and each group is introduced by its own heading. */
 private static final String[][] GROUPS={
   {"", "control","latch","switch"},
   {"", "intensity","color","width"},
   {"", "angle","azimuth"},
   {"", "sweepBase","sweepRange"},
   {"", "range","falloff","visible"},
 };
 /** The three modes, in the order the block entity stores them. */
 private static final String[] MODES={"0 全红石控制","1 半红石开关","2 手动"};
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MuzzleLight.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("spotlight",SpotlightLdlib::menuType);
 private SpotlightLdlib(){}
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player,data.readBlockPos())));
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /** Open the page for one lamp. @returns false when the block entity is gone, so the caller can fall back. */
 public static boolean open(Player player,BlockPos pos){
  if(!(player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity))return false;
  player.openMenu(new Holder(player,pos),buffer->buffer.writeBlockPos(pos));
  return true;
 }
 record Holder(Player player,BlockPos pos) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable("block.cbcmuzzlelight.spotlight");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(page(settings(),owner),owner);}
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
  root.addChild(title(Component.translatable("block.cbcmuzzlelight.spotlight"),WIDTH-8));
  root.addChild(hint(Component.translatable("cbcmuzzlelight.spotlight.hint"),WIDTH-8));
  for(int group=0;group<GROUPS.length;group++){
   root.addChild(section(Component.translatable("cbcmuzzlelight.spotlight.group."+group),WIDTH-8));
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
  row.addChildren(label(Component.translatable("cbcmuzzlelight.spotlight.field."+field),LABEL),control(lamp,field,owner));
  return row;
 }
 /** The control a field deserves: a switch for on/off, a picker where a number has names, a box otherwise. */
 private static UIElement control(SpotlightBlockEntity lamp,String field,Player owner){
  return switch(field){
   case "latch","switch","visible"->toggle(lamp,field,owner);
   case "control"->picker(lamp,field,owner,List.of(MODES));
   case "falloff"->picker(lamp,field,owner,List.of(SpotlightBlock.MODES));
   case "color"->picker(lamp,field,owner,palette());
   case "range"->picker(lamp,field,owner,ranges());
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
 /** Every distance step, labelled with the distance it actually means. */
 private static List<String> ranges(){
  var labels=new ArrayList<String>(LightingCommon.count());
  for(int step=1;step<=LightingCommon.count();step++)labels.add(LightingCommon.distance(step)+" 格");
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
   case "angle","sweepBase","sweepRange"->-90;case "azimuth"->-360;case "width","range"->1;
   default->0;
  };
 }
 private static double max(String field){
  return switch(field){
   case "control"->2;case "intensity","width","color"->15;case "angle","sweepBase","sweepRange"->90;
   case "azimuth"->360;case "range"->LightingCommon.count();case "falloff"->4;
   default->1;
  };
 }
 private static String format(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,GeneratorEditPayload payload){
  if(owner.level().isClientSide)PacketDistributor.sendToServer(payload);
 }
}
