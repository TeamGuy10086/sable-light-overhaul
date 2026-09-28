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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.cbcmuzzlelight.MuzzleLight;
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
import net.neoforged.neoforge.network.PacketDistributor;
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
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MuzzleLight.ID);
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
  @Override public ModularUI createUI(Player owner){return ModularUI.of(page(generator(),owner),owner);}
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
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,GeneratorEditPayload payload){
  if(owner.level().isClientSide)PacketDistributor.sendToServer(payload);
 }
}
