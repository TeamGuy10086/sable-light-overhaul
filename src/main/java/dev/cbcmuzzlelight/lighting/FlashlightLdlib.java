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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.cbcmuzzlelight.FlashlightBeam;
import dev.cbcmuzzlelight.FlashlightEditPayload;
import dev.cbcmuzzlelight.MuzzleLight;
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
   {"", "pointRadius","pointIntensity","color"},
   {"", "range","width","decay","intensity"},
 };
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MuzzleLight.ID);
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
  @Override public Component getDisplayName(){return Component.translatable("item.cbcmuzzlelight.flashlight");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(page(owner),owner);}
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
  root.addChild(title(Component.translatable("item.cbcmuzzlelight.flashlight"),WIDTH-8));
  root.addChild(hint(Component.translatable("cbcmuzzlelight.flashlight.pageHint"),WIDTH-8));
  for(int group=0;group<GROUPS.length;group++){
   root.addChild(section(Component.translatable("cbcmuzzlelight.flashlight.group."+group),WIDTH-8));
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
  row.addChildren(label(Component.translatable("cbcmuzzlelight.flashlight.field."+field),LABEL),control(owner,field));
  return row;
 }
 /** The control a field deserves: a picker where a number has names, a box where it is just a number. */
 private static UIElement control(Player owner,String field){
  return switch(field){
   case "color"->picker(owner,field,palette());
   case "range"->picker(owner,field,ranges());
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
}
