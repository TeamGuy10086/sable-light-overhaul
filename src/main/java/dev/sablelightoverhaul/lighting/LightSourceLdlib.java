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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.sablelightoverhaul.LightSourceEditPayload;
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
   {"", "mode","color","rgb","emission","radius","control","channel"},
   {"", "shadow","shadowSteps"},
   {"", "cloudReach"},
   {"", "flood","faces","shadowCones","blink"},
   {"", "offsetX","offsetY","offsetZ"},
   {"", "glow","glowRing","glowRingSize","glowAxis","glowAxisLength","glowAxisWidth"},
 };
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,SableLightOverhaul.ID);
 public static final DeferredHolder<MenuType<?>,MenuType<ModularUIContainerMenu>> MENU=MENUS.register("light_source",LightSourceLdlib::menuType);
 private LightSourceLdlib(){}
 /**
  * The stored values are read on the server and travel with the page, so the client's page can show them
  * without a second message; the position travels the same way.
  */
 private static MenuType<ModularUIContainerMenu> menuType(){
  return IMenuTypeExtension.create((id,inventory,data)->{
   BlockPos pos=data.readBlockPos();
 int entryId=data.readVarInt();
 LightSourceServer.rememberId(pos,entryId);
   int mode=data.readVarInt(),color=data.readVarInt(),radius=data.readVarInt();
   int shadow=data.readVarInt(),shadowSteps=data.readVarInt(),cloudReach=data.readVarInt();
   int flood=data.readVarInt(),faces=data.readVarInt();
   String cones=data.readUtf(256);
   String blink=data.readUtf(64);
   LightSourceServer.remember(pos,mode,color,radius,shadow,shadowSteps,cloudReach,flood,faces,cones,blink);
   return new ModularUIContainerMenu(MENU.get(),id,inventory,new Holder(inventory.player,pos,mode,color,radius,shadow,shadowSteps,cloudReach,flood,faces,cones,blink));
  });
 }
 public static void register(IEventBus bus){MENUS.register(bus);}
 /** Open the page for one source. @returns false when there is nothing to open, so the caller can fall back. */
 public static boolean open(Player player,BlockPos pos){
  if (player instanceof net.minecraft.server.level.ServerPlayer remote && !remote.connection.hasChannel(dev.sablelightoverhaul.LightSourceEditPayload.TYPE)) return false;
  // A source whose block we never replaced has a page too. Requiring our own block here is what used to
  // make such a lamp's colour, radius and brightness unreachable: the page is the only place they live.
  var level=player.level();
  boolean replaced=level.getBlockState(pos).getBlock() instanceof LightSourceBlock;
  // The registration is looked up by the position the page was handed, and so is the fallback that finds it
  // where the lamp is drawn. See LightSourceServer#anchorOf for why the two can differ on a physics body.
  SourceAnchor anchor=SourceAnchor.capture(level,pos);
  boolean registered=false;
  if(level instanceof net.minecraft.server.level.ServerLevel server){
   anchor=LightSourceServer.anchorOf(server,pos);
   registered=LightSourceServer.findRegistered(RegisteredSources.get(server),server,anchor)!=null;
   // One line per page opened, because a page whose numbers are all zero is indistinguishable from a lamp
   // that has no registration, and the difference is exactly what this line records.
   com.mojang.logging.LogUtils.getLogger().info(
     "Sable Light Overhaul: page pos={} ownBlock={} anchor=ship?{} plot={},{} registered={}",
     pos,replaced,anchor.ship()!=null?1:0,anchor.pos().getX(),anchor.pos().getZ(),registered);
  }
  if(!replaced&&!registered)return false;
  int mode=whole(player,pos,"mode"),color=whole(player,pos,"color"),radius=whole(player,pos,"radius");
  int shadow=whole(player,pos,"shadow"),shadowSteps=whole(player,pos,"shadowSteps");
  int cloudReach=whole(player,pos,"cloudReach");
  int flood=whole(player,pos,"flood"),faces=whole(player,pos,"faces");
  String cones=LightSourceServer.coneText(player,pos);
  String blink=LightSourceServer.blinkText(player,pos);
  player.openMenu(new Holder(player,pos,mode,color,radius,shadow,shadowSteps,cloudReach,flood,faces,cones,blink),buffer->{
   buffer.writeBlockPos(pos);
   // The registration this page is for, so every edit can name it exactly instead of being recognised by
   // the position it was clicked at.
   buffer.writeVarInt(LightSourceServer.idAt((net.minecraft.server.level.ServerLevel)player.level(),pos));
   buffer.writeVarInt(mode);
   buffer.writeVarInt(color);
   buffer.writeVarInt(radius);
   buffer.writeVarInt(shadow);
   buffer.writeVarInt(shadowSteps);
   buffer.writeVarInt(cloudReach);
   buffer.writeVarInt(flood);
   buffer.writeVarInt(faces);
   buffer.writeUtf(cones,256);
   buffer.writeUtf(blink,64);
  });
  return true;
 }
 record Holder(Player player,BlockPos pos,int mode,int color,int radius,int shadow,int shadowSteps,int cloudReach,int flood,int faces,String cones,String blink) implements MenuProvider,IContainerUIHolder {
  @Override public Component getDisplayName(){return Component.translatable("sablelightoverhaul.lightsource.title");}
  @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){
   return new ModularUIContainerMenu(MENU.get(),id,inventory,this);
  }
  @Override public ModularUI createUI(Player owner){return ModularUI.of(UI.of(new UIElement()),owner);}
  /**
   * The source is what the page edits, so it has to still be there while the page is used.
   *
   * <p>Two things count as there: our own block, or -- for a lamp we never replaced -- a live
   * registration. Without the second half the page of such a lamp would close itself the instant it
   * opened.
   */
  @Override public boolean isStillValid(Player owner){
   var level=player.level();
   if(level.getBlockState(pos).getBlock() instanceof LightSourceBlock)return true;
   return level instanceof net.minecraft.server.level.ServerLevel server
     &&RegisteredSources.get(server).find(SourceAnchor.capture(level,pos),false)!=null;
  } }
 private static UI page(Player owner,BlockPos pos){
  UIElement root=new UIElement().layout(layout->{
   layout.width(WIDTH);
   layout.heightAuto();
   layout.gapAll(GAP);
   layout.paddingAll(4);
   layout.flexDirection(FlexDirection.COLUMN);
  }).style(style->style.backgroundTexture(Sprites.BORDER));
  root.addChild(title(Component.translatable("sablelightoverhaul.lightsource.title"),WIDTH-8));
  root.addChild(hint(Component.translatable("sablelightoverhaul.lightsource.pageHint"),WIDTH-8));
  for(int group=0;group<GROUPS.length;group++){
   root.addChild(section(Component.translatable("sablelightoverhaul.lightsource.group."+group),WIDTH-8));
   for(int i=1;i<GROUPS[group].length;i++){
    String field=GROUPS[group][i];
    if(PointLightAppearance.owns(field)&&!(owner.level().getBlockEntity(pos) instanceof LightSourceBlockEntity))continue;
    root.addChild("faces".equals(field)?faceRow(owner,pos):row(owner,pos,field));
    // The cones are the one field whose value is a small language, so the row that carries it gets the
    // syntax under it rather than in a tooltip nobody hovers.
    if("shadowCones".equals(field))root.addChild(hint(Component.translatable("sablelightoverhaul.lightsource.conesHint"),WIDTH-8));
    // The blink pattern is the second such field, and the one whose half-typed form is refused.
    if("blink".equals(field))root.addChild(hint(Component.translatable("sablelightoverhaul.lightsource.blinkHint"),WIDTH-8));
   }
  }
  return UI.of(root);
 }
 /**
  * The same rows as a {@link Source}, for the unified screen. Values are read through
  * {@link LightSourceServer} and written as the validated payload, exactly as the page writes them.
  */
 public static Source configSource(ModularUIContainerMenu menu){
  Player owner=menu.inventory.player;
  BlockPos pos=menu.uiHolder instanceof Holder holder?holder.pos():BlockPos.ZERO;
  DeviceSource source=new DeviceSource(titleOr("sablelightoverhaul.lightsource.title"));
  for(int group=0;group<GROUPS.length;group++){
   List<FieldSpec> specs=new ArrayList<>();
   for(int i=1;i<GROUPS[group].length;i++){
    String field=GROUPS[group][i];
    if(PointLightAppearance.owns(field)&&!(owner.level().getBlockEntity(pos) instanceof LightSourceBlockEntity))continue;
    if(!field.equals("faces")){specs.add(spec(owner,pos,field));continue;}
    int[] mask={whole(owner,pos,"faces")};
    String[] names={"下","上","北","南","西","东"};
    for(int f=0;f<6;f++){
     int bit=1<<f;
     FieldSpec face=new FieldSpec("faces."+f,"faces",dev.sablelightoverhaul.LocalizedText.component("出光面：%s",dev.sablelightoverhaul.LocalizedText.component(names[f])).getString(),"",FieldSpec.Kind.TOGGLE,0,1,1,List.of(),false,null);
     specs.add(face);source.bind(face,()->new FieldValue.Bool((whole(owner,pos,"faces")&bit)!=0),v->{mask[0]=FieldValue.flag(v)?mask[0]|bit:mask[0]&~bit;send(owner,pos,"faces",mask[0]);});
    }
   }
   if(!specs.isEmpty())source.group("光源",titleOr("sablelightoverhaul.lightsource.title"),titleOr(groupKey(group)),specs);
  }
  for(String field:LightSourceServer.FIELDS)if(!field.equals("faces")&&(!PointLightAppearance.owns(field)||owner.level().getBlockEntity(pos) instanceof LightSourceBlockEntity))bind(source,owner,pos,field);
  return source;
 }
 private static UIElement row(Player owner,BlockPos pos,String field){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(4);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChildren(label(Component.translatable("sablelightoverhaul.lightsource.field."+field),LABEL),control(owner,pos,field));
  return row;
 }
 /**
 * The six faces a lamp is allowed to shine through, as six switches.
 *
 * <p>A row of its own rather than a picker because the answer is six independent yes/no questions, and
 * a sum of bits is not one of them. Each switch writes the whole mask, built from the mask the client
 * last saw: that is what makes two quick clicks two edits instead of one lost edit.
 */
 private static UIElement faceRow(Player owner,BlockPos pos){
  UIElement row=new UIElement().layout(layout->{
   layout.width(WIDTH-8);
   layout.height(ROW);
   layout.gapAll(2);
   layout.flexDirection(FlexDirection.ROW);
   layout.alignItems(AlignItems.CENTER);
  });
  row.addChild(label(Component.translatable("sablelightoverhaul.lightsource.field.faces"),LABEL-40));
  int mask=whole(owner,pos,"faces");
  for(int i=0;i<RegisteredSources.FACE_BITS.length;i++){
   final int bit=1<<i;
   Toggle toggle=new Toggle();
   toggle.noText();
   toggle.layout(layout->{
    layout.width(16.0F);
    layout.height(10.0F);
   });
   toggle.setOn((mask&bit)!=0,false);
   toggle.setOnToggleChanged(on->{
    int current=whole(owner,pos,"faces");
    send(owner,pos,"faces",(double)(on?(current|bit):(current&~bit)));
   });
   row.addChild(toggle);
  }
  return row;
 }
 /** The control a field deserves: a picker where a number has names, a box where it is just a number. */
 private static UIElement control(Player owner,BlockPos pos,String field){
  return switch(field){
   case "mode"->picker(owner,pos,field,List.of(LightSourceServer.MODES));
   case "color"->picker(owner,pos,field,palette());
   // The colour box: six hex digits, and when it parses it wins over the row above it. Kept as its own
   // field rather than merged into the picker because a picker cannot say "something else".
   case "rgb"->rgbField(owner,pos);
   case "shadow"->picker(owner,pos,field,List.of(LightSourceServer.SHADOWS));
   // Two ways to light a lamp: the way vanilla block light behaves, or this mod's own shaped term.
   case "flood"->picker(owner,pos,field,List.of(LightSourceServer.STYLES));
   // The cones are written rather than picked: a small language, four angles to a group, and a text box is
   // the only control that can hold it. The server parses it, and names the group in chat when it cannot.
   case "shadowCones"->coneField(owner,pos);
   case "blink"->blinkField(owner,pos);
   // The three redstone answers, in the same words the block entity uses; the row number *is* the stored
   // value, like every other picker here.
   case "control"->picker(owner,pos,field,List.of(LightSourceBlockEntity.CONTROLS));
   // The channel row reports and clears only: a board goes in with the right-click gesture, which is
   // the same way AFN's own devices take one. Picking "已绑定" therefore does nothing, and the row
   // snapping back to 未绑定 is the honest answer rather than a silent no-op.
   case "channel"->picker(owner,pos,field,List.of("未绑定","已绑定"));
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
  selector.setCandidateUIProvider(com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider.text(dev.sablelightoverhaul.LocalizedText::component));
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
 private static TextField rgbField(Player owner,BlockPos pos){
  TextField input=input();
  input.setText(LightSourceServer.rgbText(owner,pos),false);
  input.setTextResponder(text->send(owner,pos,"rgb",0.0,text));
  return input;
 }
 /**
  * The blink pattern box. Empty is a steady lamp, so clearing the box is how a lamp stops blinking.
  *
  * <p>Half-typed patterns are not edits: the validator refuses "4," while it is being typed, so the field
  * is red and sends nothing until the text parses whole. Sending every prefix is what used to answer the
  * player with a chat complaint per keystroke.
  */
 private static TextField blinkField(Player owner,BlockPos pos){
  TextField input=input();
  input.setText(LightSourceServer.blinkText(owner,pos),false);
  input.setTextValidator(text->{
   try{BlinkPattern.parse(text);return true;}
   catch(IllegalArgumentException stillTyping){return false;}
  });
  input.setTextResponder(text->send(owner,pos,"blink",0.0,text));
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
 private static double min(String field){if(field.startsWith("offset"))return -64;if(field.equals("glowRingSize")||field.equals("glowAxisLength")||field.equals("glowAxisWidth"))return .05;return "emission".equals(field)?LightSourceServer.MIN_EMISSION:0;}
 private static double max(String field){
  if(field.startsWith("offset"))return 64;if(field.equals("glowRingSize")||field.equals("glowAxisLength")||field.equals("glowAxisWidth"))return 32;
  return switch(field){
   case "emission"->LightSourceServer.MAX_EMISSION;case "radius"->LightSourceServer.MAX_RADIUS;
   case "shadowSteps"->LightSourceServer.MAX_SHADOW_STEPS;
   case "cloudReach"->LightSourceServer.MAX_CLOUD_REACH;
   case "faces"->RegisteredSources.FACES_ALL;
   default->1;
  };
 }
 private static String format(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}
 private static int whole(Player owner,BlockPos pos,String field){return (int)Math.round(LightSourceServer.number(owner,pos,field));}
 /** Edits only originate from a player using the page, which is always the client side. */
 private static void send(Player owner,BlockPos pos,String field,double value){
  if(owner.level().isClientSide){
   // The page said it saved and the lamp never changed; this says whether the page sent anything at all.
   com.mojang.logging.LogUtils.getLogger().info(
           "Sable Light Overhaul page send: field={} value={} at {}",field,value,pos.toShortString());
   ClientChannels.sendToServer(new LightSourceEditPayload(pos,LightSourceServer.rememberedId(pos),field,value,""));
  }
 }
 /** The same, for the one field whose value is text. */
 private static void send(Player owner,BlockPos pos,String field,double value,String text){
  if(owner.level().isClientSide)ClientChannels.sendToServer(new LightSourceEditPayload(pos,LightSourceServer.rememberedId(pos),field,value,text));
 }
 /**
  * The shadow cones, as the player writes them: {@code {h1,h2,v1,v2};{...}}, degrees, empty for every
  * direction. Plain text rather than the numeric box the other fields use, because the value is a list of
  * groups rather than a number, and the server answers a malformed one in chat.
  */
 private static TextField coneField(Player owner,BlockPos pos){
  TextField input=input();
  input.setText(LightSourceServer.coneText(owner,pos),false);
  // Same rule as the blink box: a half-written group is not an edit, so it is not sent and not answered
  // with a chat error. The field is red until the whole text parses.
  input.setTextValidator(text->{
   try{Cones.parse(text);return true;}
   catch(IllegalArgumentException stillTyping){return false;}
  });
  input.setTextResponder(text->send(owner,pos,"shadowCones",0.0,text));
  return input;
 }
 /** The shape of one row, using the same labels, options and bounds as its control on the page. */
 private static FieldSpec spec(Player owner,BlockPos pos,String field){
  return new FieldSpec(field,"lightsource",fieldKey(field),"",kind(field),min(field),max(field),step(field),
          options(field),false,fieldKey(field));
 }
 private static FieldSpec.Kind kind(String field){
  return switch(field){
   case "faces"->FieldSpec.Kind.SLIDER;case "rgb","shadowCones","blink"->FieldSpec.Kind.TEXT;
   case "emission","radius","shadowSteps","cloudReach","offsetX","offsetY","offsetZ","glowRingSize","glowAxisLength","glowAxisWidth"->FieldSpec.Kind.SLIDER;
   default->FieldSpec.Kind.CYCLER;
  };
 }
 private static List<String> options(String field){
  if(field.equals("glow")||field.equals("glowRing")||field.equals("glowAxis"))return List.of("关","开");
  return switch(field){
   case "mode"->List.of(LightSourceServer.MODES);case "color"->palette();
   case "shadow"->List.of(LightSourceServer.SHADOWS);case "flood"->List.of(LightSourceServer.STYLES);
   case "control"->List.of(LightSourceBlockEntity.CONTROLS);case "channel"->List.of("未绑定","已绑定");
   default->List.of();
  };
 }
 private static double step(String field){
  if(PointLightAppearance.owns(field))return field.equals("glow")||field.equals("glowRing")||field.equals("glowAxis")?1:.05;
  return switch(field){
   case "emission","radius","shadowSteps","cloudReach","faces","rgb","shadowCones","blink"->1.0;
   default->0.0;
  };
 }
 private static String fieldKey(String field){return "sablelightoverhaul.lightsource.field."+field;}
 private static String groupKey(int group){return "sablelightoverhaul.lightsource.group."+group;}
 private static String titleOr(String key){return Component.translatable(key).getString();}
 /** One reading and one writer per field: the number for the numbers, the text for the three text fields. */
 private static void bind(DeviceSource source,Player owner,BlockPos pos,String field){
  FieldSpec spec=spec(owner,pos,field);
  switch(field){
   case "rgb"->source.bind(spec,()->new FieldValue.Text(LightSourceServer.rgbText(owner,pos)),
           value->sendValidText(owner,pos,field,value));
   case "shadowCones"->source.bind(spec,()->new FieldValue.Text(LightSourceServer.coneText(owner,pos)),
           value->sendValidText(owner,pos,field,value));
   case "blink"->source.bind(spec,()->new FieldValue.Text(LightSourceServer.blinkText(owner,pos)),
           value->sendValidText(owner,pos,field,value));
   default->source.bind(spec,()->{
    double n=LightSourceServer.number(owner,pos,field);
    if(kind(field)==FieldSpec.Kind.CYCLER){var labels=options(field);int i=Math.max(0,Math.min(labels.size()-1,(int)n));return new FieldValue.Choice(i,labels.get(i));}
    return new FieldValue.Num(n);
   },
           value->send(owner,pos,field,FieldValue.numeric(value)));
  }
 }
 private static void sendValidText(Player owner,BlockPos pos,String field,FieldValue value){
  if(!(value instanceof FieldValue.Text text))return;
  try{
   if(field.equals("shadowCones"))Cones.parse(text.text());
   if(field.equals("blink"))BlinkPattern.parse(text.text());
   if(field.equals("rgb") && !text.text().isBlank() && SourceColor.parseCustom(text.text())<0)return;
   send(owner,pos,field,0,text.text());
  }catch(IllegalArgumentException ignored){}
 }
}
