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
import dev.cbcmuzzlelight.LightSourceEditPayload;
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
 * The static light source's page, drawn with LDLib2 exactly like the lamps'.
 *
 * <p>This is the spotlight's kind of page — one block in the world, found by position — with one
 * difference that shapes it. Only two of its five fields live in the block: the brightness is the
 * stand-in's own block state and the takeover is its block entity, both of which the client already
 * has. The registration mode, the palette index and the radius are in the level's save data and exist
 * only on the server, so the server reads them when it opens the page and sends them with it. Every
 * edit is still one field in one message, validated and stored on the server alone.
 *
 * <p>Every row is given the control that says what its number means: a picker wherever the numbers have
 * names, a box where they are just numbers.
 *
 * <p>Taking a block over is not a row here, and deliberately so: it changes which block is there rather
 * than how the light behaves, and the block it acts on is the lamp being replaced, which a page opened
 * on the stand-in cannot name. The registrar tool does both directions with the right block under the
 * cursor (see {@link LightSourceServer}).
 */
public final class LightSourceLdlib {
 private static final float WIDTH=242,ROW=11,GAP=2,LABEL=112,CONTROL=118;
 /** The rows, grouped; concatenated they are exactly {@link LightSourceServer#FIELDS}, in that order. */
 private static final String[][] GROUPS={
   {"", "mode","color","emission","radius"},
   {"", "shadow","shadowSteps"},
 };
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MuzzleLight.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("light_source",LightSourceLdlib::menuType);
 private LightSourceLdlib(){}
 /**
  * The stored values are read on the server and travel with the page, so the client's page can show them
  * without a second message; the position travels the same way.
  */
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->{
   BlockPos pos=data.readBlockPos();
   int mode=data.readVarInt(),color=data.readVarInt(),radius=data.readVarInt();
   int shadow=data.readVarInt(),shadowSteps=data.readVarInt();
   LightSourceServer.remember(pos,mode,color,radius,shadow,shadowSteps);
   return new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player,pos,mode,color,radius,shadow,shadowSteps));
  });
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /** Open the page for one stand-in. @returns false when the block is gone, so the caller can fall back. */
 public static boolean open(Player player,BlockPos pos){
  if(!(player.level().getBlockState(pos).getBlock() instanceof LightSourceBlock))return false;
  int mode=whole(player,pos,"mode"),color=whole(player,pos,"color"),radius=whole(player,pos,"radius");
  int shadow=whole(player,pos,"shadow"),shadowSteps=whole(player,pos,"shadowSteps");
  player.openMenu(new Holder(player,pos,mode,color,radius,shadow,shadowSteps),buffer->{
   buffer.writeBlockPos(pos);
   buffer.writeVarInt(mode);
   buffer.writeVarInt(color);
   buffer.writeVarInt(radius);
   buffer.writeVarInt(shadow);
   buffer.writeVarInt(shadowSteps);
  });
  return true;
 }
 record Holder(Player player,BlockPos pos,int mode,int color,int radius,int shadow,int shadowSteps) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable("cbcmuzzlelight.lightsource.title");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(page(owner,pos),owner);}
  /** The stand-in is what the page edits, so it has to still be there while the page is used. */
  @Override public boolean isStillValid(Player owner){return player.level().getBlockState(pos).getBlock() instanceof LightSourceBlock;}
 }
 private static UI page(Player owner,BlockPos pos){
  UIElement root=new UIElement().layout(layout->{
   layout.width(WIDTH);
   layout.heightAuto();
   layout.gapAll(GAP);
   layout.paddingAll(4);
   layout.flexDirection(FlexDirection.COLUMN);
  }).style(style->style.backgroundTexture(Sprites.BORDER));
  root.addChild(title(Component.translatable("cbcmuzzlelight.lightsource.title"),WIDTH-8));
  root.addChild(hint(Component.translatable("cbcmuzzlelight.lightsource.pageHint"),WIDTH-8));
  for(int group=0;group<GROUPS.length;group++){
   root.addChild(section(Component.translatable("cbcmuzzlelight.lightsource.group."+group),WIDTH-8));
   for(int i=1;i<GROUPS[group].length;i++)root.addChild(row(owner,pos,GROUPS[group][i]));
  }
  return UI.of(root);
 }
 private static UIElement row(Player owner,BlockPos pos,String field){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChildren(label(Component.translatable("cbcmuzzlelight.lightsource.field."+field),LABEL),control(owner,pos,field));
  return row;
 }
 /** The control a field deserves: a picker where a number has names, a box where it is just a number. */
 private static UIElement control(Player owner,BlockPos pos,String field){
  return switch(field){
   case "mode"->picker(owner,pos,field,List.of(LightSourceServer.MODES));
   case "color"->picker(owner,pos,field,palette());
   case "shadow"->picker(owner,pos,field,List.of(LightSourceServer.SHADOWS));
   default->number(owner,pos,field);
  };
 }
 private static Selector<String> picker(Player owner,BlockPos pos,String field,List<String> labels){
  Selector<String> selector=new Selector<>();
  selector.layout(layout->{
   layout.width(CONTROL);
   layout.height(10.0F);
  });
  selector.setCandidates(labels);
  // Every picker here stores its own row number, so the stored value is already the row: mode counts
  // 0..2, the palette 0..15 and the takeover 0..1.
  int row=Math.max(0,Math.min(labels.size()-1,(int)Math.round(LightSourceServer.number(owner,pos,field))));
  selector.setSelected(labels.get(row),false);
  selector.setOnValueChanged(chosen->{
   int picked=labels.indexOf(chosen);
   if(picked<0)return;
   send(owner,pos,field,(double)picked);
  });
  return selector;
 }
 /** The palette, by the names this mod already shows for it (透明 / 各染料 / 闪烁). */
 private static List<String> palette(){
  var labels=new ArrayList<String>(SourceColor.COUNT);
  for(int i=0;i<SourceColor.COUNT;i++)labels.add(SourceColor.name(i));
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
 /** One line of explanation, because what the mode does to the block is not visible from the page. */
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
 private static TextField number(Player owner,BlockPos pos,String field){
  TextField input=input();
  input.setNumbersOnlyDouble(min(field),max(field));
  input.setText(format(LightSourceServer.number(owner,pos,field)),false);
  input.setWheelDur(1,0.1F);
  input.setTextResponder(text->{
   try{
    send(owner,pos,field,Double.parseDouble(text));
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
 private static double min(String field){return "emission".equals(field)?LightSourceServer.MIN_EMISSION:0;}
 private static double max(String field){
  return switch(field){
   case "emission"->LightSourceServer.MAX_EMISSION;case "radius"->LightSourceServer.MAX_RADIUS;
   case "shadowSteps"->LightSourceServer.MAX_SHADOW_STEPS;
   default->1;
  };
 }
 private static String format(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
 private static int whole(Player owner,BlockPos pos,String field){return (int)Math.round(LightSourceServer.number(owner,pos,field));}
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,BlockPos pos,String field,double value){
  if(owner.level().isClientSide)PacketDistributor.sendToServer(new LightSourceEditPayload(pos,field,value));
 }
}
