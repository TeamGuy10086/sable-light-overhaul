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
 private static final String[] FIELDS={"control","latched","beams","colors","spin","range","width","intensity","falloff"};
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MuzzleLight.ID);
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
  @Override public Component getDisplayName(){return Component.translatable("block.cbcmuzzlelight.disco_ball");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(page(settings(),owner),owner);}
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
  root.addChild(title(Component.translatable("block.cbcmuzzlelight.disco_ball"),WIDTH-8));
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
  row.addChildren(label(Component.translatable("cbcmuzzlelight.disco.field."+field),LABEL),control(ball,field,owner));
  return row;
 }
 private static UIElement control(DiscoBallBlockEntity ball,String field,Player owner){
  if("colors".equals(field))return text(ball,field,owner);
  // A switch for the latch, a picker for the redstone mode and the five named falloff profiles; a box
  // holding a bare number tells nobody that 1 means latched or that 2 means uniform.
  if("latched".equals(field))return toggle(ball,field,owner);
  if("falloff".equals(field))return picker(ball,field,owner,List.of(SpotlightBlock.MODES));
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
}
