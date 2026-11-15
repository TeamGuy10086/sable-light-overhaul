package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.LocalizedText;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The static light source's four settings, and the one validated write its page can make.
 *
 * <p>This is the {@link FlashlightSettings} of the registered lamps: the page is a plain view, so it
 * reads every value through {@link #number} and sends every edit through {@link #setNumber}, and the
 * server is the only side that decides anything. Unknown fields, out-of-range numbers and players
 * without building rights are all refused here rather than on the page, because a page is a client.
 *
 * <p>The four fields do not live in the same place, which is why {@link #number} is not a one-line
 * lookup. Brightness is the stand-in's own block state, so both sides can read it; the mode, the palette
 * index and the radius are registrations in the level's save data and exist only on the server, so the
 * server sends those three along with the page and the client answers from what it was sent. Only the
 * three that live in the save data are kept here; brightness is read from the world on both sides, so it
 * is never stale.
 *
 * <p><b>Taking a block over is deliberately not on this page.</b> It is not a setting of a light, it is
 * a change to which block is there, and the block it acts on is the lamp being replaced — not the
 * stand-in the page was opened on. A page can only name its own position, so it has no way to say what
 * to take over, and every attempt would be refused. The registrar tool already does both directions with
 * the right block under the cursor: shift-right-click a lamp to take it over, shift-right-click the
 * stand-in to give it back.
 */
public final class LightSourceServer {
 /** The page's rows, in the order it shows them. */
 public static final String[] FIELDS={"mode","color","rgb","emission","radius","control","channel","shadow","shadowSteps","cloudReach","flood","faces","shadowCones","blink","offsetX","offsetY","offsetZ","glow","glowRing","glowAxis","glowRingSize","glowAxisLength","glowAxisWidth"};
 /** The two light styles, in the order the picker lists them. */
 public static final String[] STYLES={"原版光场（像原版方块光 · 忽略锥体/发光面/阴影档/步数）","射线光（锐利阴影 · 使用锥体与发光面）"};
 /** The three registration modes, in the order the picker lists them; the tool uses the same words. */
 public static final String[] MODES={"取消登记","启用","强制启用"};
 /** The three shadow answers, in the order the picker lists them. */
 public static final String[] SHADOWS={"不投影","硬边","软边"};
 /** Localized names for the picker sources above, for messages rather than client-side selector rows. */
 public static Component modeName(int mode){return LocalizedText.component(MODES[clamp(mode,0,MODES.length-1)]);}
 public static Component shadowName(int mode){return LocalizedText.component(SHADOWS[clamp(mode,0,SHADOWS.length-1)]);}
 public static Component styleName(int mode){return LocalizedText.component(STYLES[clamp(mode,0,STYLES.length-1)]);}
 public static Component controlName(int mode){return LocalizedText.component(LightSourceBlockEntity.CONTROLS[clamp(mode,0,LightSourceBlockEntity.CONTROLS.length-1)]);}
 /** A palette or custom colour name, kept as a component so a chat line localizes at the recipient. */
 public static Component colorName(int index){
  if(SourceColor.isCustom(index))return LocalizedText.component("自定义 #%s",SourceColor.hex(index&0xFFFFFF));
  return LocalizedText.component(SourceColor.name(index));
 }
 /** The most taps a lamp may ask for; the client's own ceiling is applied on top of this, and 255 is also
 *  the shader's compile-time bound -- the wish travels in a byte as well -- so a page may ask for
 *  everything the quality mode can spend. */
 public static final int MAX_SHADOW_STEPS=255;
 /** The widest radius a page may set; {@link RegisteredSources#RADIUS_AUTO} means "from the brightness". */
 public static final int MAX_RADIUS=64;
 /**
  * The widest cloud reach a page may set, in blocks. It is larger than a lamp's own radius on purpose:
  * the cloud deck is around y=192 and a lamp on deck is 140 blocks from it, so a reach that means
  * anything there is a reach no lamp's own light radius would ever be. Zero keeps the shipped answer --
  * the receiving client's global setting, which itself defaults to "lamps light no clouds".
  */
 public static final int MAX_CLOUD_REACH=128;
 /** Brightness is a block light level, so it runs 1..15: a stand-in at 0 would be a lamp that is off. */
 public static final int MIN_EMISSION=1,MAX_EMISSION=15;
 /** The page is only reachable at the block, so a write from far away is not a real edit. */
 private static final double MAX_REACH=64.0;
 private LightSourceServer(){}
 /** Client-side copy of the values that live in the save data; filled when the page opens. */
 private static BlockPos pagePos;
 private static int pageMode,pageColor,pageRadius,pageShadow,pageShadowSteps,pageCloudReach,pageFlood,pageFaces;
 /** The cone text the server sent with the page; a string, so it rides beside the numbers. */
 private static String pageCones="";
 /** The blink text the server sent with the page, for the client half. */
 private static String pageBlink="";
 /**
  * Remember the values the server sent with the page.
  *
  * <p>Only effective on the client, which is the side that cannot compute them: the registration is in
  * the level's save data. The server has no use for a copy of what it just read.
  */

 /** The registration id the open page was built for, per position. Sent with every edit so the server never has to
  * recognise a lamp by the world position it was clicked at -- on a body that position is a rendering, not an identity. */
 private static final java.util.Map<BlockPos,Integer> PAGE_IDS=new java.util.concurrent.ConcurrentHashMap<>();

 public static void rememberId(BlockPos pos,int id){ if(id>0)PAGE_IDS.put(pos.immutable(),id); }
 /** The registration id at a position, for a page that is about to be opened. Zero when there is none. */
 public static int idAt(ServerLevel level,BlockPos pos){
  var data=RegisteredSources.get(level);
  var entry=findRegistered(data,level,anchorOf(level,pos));
  if(entry==null&&level.getBlockState(pos).getBlock() instanceof LightSourceBlock)entry=data.find(SourceAnchor.capture(level,pos),false);
  // Everything the identity question was ever answered with, on one line: what was clicked, what the plain capture
  // made of it, what each body made of it, and which registration that resolved to.
  var plain=SourceAnchor.capture(level,pos);
  com.mojang.logging.LogUtils.getLogger().info(
          "Sable Light Overhaul page open: clicked={} plain={} | bodies: {} | entry={} id={}",
          pos.toShortString(),plain,SourceAnchor.probe(level,pos),
          entry==null?"none":entry.id(),entry==null?0:entry.id());
  return entry==null?0:entry.id();
 }

 public static int rememberedId(BlockPos pos){ return PAGE_IDS.getOrDefault(pos,-1); }
 public static void remember(BlockPos pos,int mode,int color,int radius,int shadow,int shadowSteps,int cloudReach,int flood,int faces,String cones,String blink){
  pagePos=pos.immutable();pageMode=mode;pageColor=color;pageRadius=radius;
  pageShadow=shadow;pageShadowSteps=shadowSteps;pageCloudReach=cloudReach;
  pageFlood=flood;pageFaces=faces;pageCones=cones==null?"":cones;pageBlink=blink==null?"":blink;
 }
 /**
  * The page's current value for one field: the server reads the registration and the world, the client
  * reads the two values that are synced to it and the ones the server sent with the page.
  */
 public static double number(Player player,BlockPos pos,String field){
  if(PointLightAppearance.owns(field))return (player.level().getBlockEntity(pos) instanceof LightSourceBlockEntity lamp?lamp.appearance():PointLightAppearance.DEFAULT).number(field);
  Level level=player.level();
  if(level.isClientSide)return switch(field){
   // The block state arrives with the chunk, so brightness is read here rather than sent: a page that
   // showed it from a snapshot would go stale the moment anything else changed it.
   case "emission"->emission(level,pos);
   // The redstone answer lives in the block entity, whose own update tag already carries it to the
   // client, so this field needs no payload of its own and reads the same way on both sides.
   case "control"->control(level,pos);
   // The installed boards travel in the block entity's own update tag, so the client can answer this
   // one itself rather than being sent a copy of it.
   case "channel"->channelBound(level,pos)?1:0;
   default->remembered(pos,field);
  };
  if(!(level instanceof ServerLevel server))return 0.0;
  // Resolved the same way a write is: the direct anchor first, then the body lookup. Looking this up more weakly
  // than the write path is what made a registered lamp's page show zeros -- and then write those zeros back,
  // which is how a lamp that had shadows lost them and never got them back.
  var entry=findRegistered(RegisteredSources.get(server),server,anchorOf(server,pos));
  // A lamp we never replaced has no block state of ours to read, but it does cast: the registration carries
  // its own shadow answer and the light list passes it straight through, so the page has to report that
  // answer rather than a zero. Reporting zero here was how a registered lamp lost its shadows -- the page
  // read "none", and the next paired write (face style, steps, cones) saved that zero back over the lamp.
  boolean replaced=level.getBlockState(pos).getBlock() instanceof LightSourceBlock;
  return switch(field){
   case "mode"->entry==null?0:entry.mode();
   case "color"->color(server,pos,entry);
   // The override wins when there is one: it is set together with a stand-in's block state, so the two
   // agree, and for a lamp we did not replace it is the only brightness there is.
   case "emission"->entry!=null&&entry.emission()>0?entry.emission()
     :(replaced?emission(server,pos):LightSourceBlock.emissionOf(server,pos,level.getBlockState(pos)));
   case "radius"->entry==null?0:entry.radius();
   case "shadow"->entry==null?0:entry.shadowMode();
   case "shadowSteps"->entry==null?0:entry.shadowSteps();
   // Zero is "no opinion", not "reach zero": the lamp then follows the receiving client's global setting,
   // which is the only side that knows how expensive the cloud term is on this machine.
   case "cloudReach"->entry==null?0:entry.cloudReach();
   // And zero here is the shaped term, which is what a lamp with no registration gets: reading one was how a
   // registered lamp came back as flat vanilla-like light with no normal term at all.
   case "flood"->entry!=null&&!entry.flood()?1:0;
   case "faces"->entry==null?RegisteredSources.FACES_ALL:entry.faces();
   // The channel row only reports and clears: a board goes in with the gesture, which needs no page.
   // Reporting it is what makes the row say the truth instead of always zero.
   case "channel"->channelBound(server,pos)?1:0;
   default->0.0;
  };
 }
 /**
  * One edit from a page, whichever shape it has: a number for most fields, a string for the cones.
  *
  * <p>The two are one entry point so the packet has one handler and the permission and reach checks live
  * in one place; which of the two payloads a field uses is decided by the field's own name.
  */
 public static boolean set(BlockPos pos,int id,String field,double number,String text,Player player){
  if(!BlockOwners.canEdit(player, player.level(), pos)){
   player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.no_permission"), true);
   return false;
  }
  if("shadowCones".equals(field))return setCones(player,pos,text);
  if("blink".equals(field))return setBlink(player,pos,text);
  if("rgb".equals(field))return setRgb(player,pos,text);
  return setNumber(player,pos,id,field,number);
 }

 public static boolean set(BlockPos pos,String field,double number,String text,Player player){
  if(!BlockOwners.canEdit(player, player.level(), pos)){
   player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.no_permission"), true);
   return false;
  }
  if("shadowCones".equals(field))return setCones(player,pos,text);
  if("blink".equals(field))return setBlink(player,pos,text);
  // The colour box. A valid six-digit value wins over the palette row beside it, and an empty box hands the
  // lamp back to the palette -- which is the only way to undo a custom colour from the page.
  if("rgb".equals(field))return setRgb(player,pos,text);
  return setNumber(player,pos,field,number);
 }
 /**
  * One validated write of a lamp's shadow cones.
  *
  * <p>A string that does not parse is refused whole, in the player's own chat: leaving the lamp as it was
  * is the only safe direction, and "my shadows are wrong" is a much worse thing to debug than a sentence
  * naming the group that was malformed.
  */
 /**
  * One validated write of a lamp's own colour from the hex box.
  *
  * <p>Empty clears the override and hands the lamp back to the palette. A value that is not six hex digits is
  * refused rather than coerced -- the box goes red while it is still being typed, and the lamp keeps whatever
  * it had. There is no silent fallback to the dropdown: "if this box parses, use it" has to mean the box is
  * either in charge or not in charge, never half of each.
  */
 public static boolean setRgb(Player player,BlockPos pos,String text){
  if(text==null||text.trim().isEmpty())return set(pos,"color",SourceColor.UNTINTED,"",player);
  int packed=SourceColor.parseCustom(text);
  if(packed<0){
   // Only complain about something that looks finished: IGNORE and NO_COLOR are not six digits either, and
   // the page's own responder already debounces keystrokes, so a half-typed value is normal traffic.
   if(text.trim().length()>=6){
    player.displayClientMessage(LocalizedText.component("颜色没改：需要 6 位十六进制，例如 1A2B3C"),true);
   }
   return false;
  }
  return set(pos,"color",packed,"",player);
  }

 /**
  * The hex box's current contents: the lamp's custom colour, or empty when it has none.
  *
  * <p>Empty is the honest answer rather than the palette's colour printed as hex: the box is an override, and
  * showing the palette's value in it would make an untouched lamp look as though somebody had typed it.
  */
 public static String rgbText(Player player,BlockPos pos){
  int stored=(int)Math.round(number(player,pos,"color"));
  return SourceColor.isCustom(stored)?SourceColor.hex(stored):"";
 }


 /**
  * Whether the player is beyond arm's length of the lamp, measured at the lamp's <em>world</em> position.
  *
  * <p>The position a page is given is the block's own, and a lamp mounted on a physics body keeps its block
  * in that body's plot -- twenty million blocks away in this world. Comparing the player to that number
  * refused every write a vehicle lamp's page ever tried, which is why its shadow cones, its colour and its
  * brightness could all be set on the ground and none of them on a hull. The pose puts the block back where
  * the player sees it, and that is what the reach is about.
  */
 /**
  * The anchor the position a page was given really belongs to.
  *
  * <p>A lamp has two positions that name the same object: its block, which on a physics body sits in that
  * body's plot, and the spot in the world where the player sees and clicks it. A page that arrives with one
  * and looks up the other found nothing, so it reported every value as zero, and the write that followed
  * registered a second lamp at the world position. That is one lamp drawing two lights, one of them nailed
  * to a place the hull has already left, and a page whose numbers never seemed to stick.
  */
 static SourceAnchor anchorOf(ServerLevel level,BlockPos pos){
  // A lamp's identity is its position in its own frame, exactly. The proximity search this used to run -- nearest
  // registration within three and a half blocks -- predates the transform, and for a position that is a rendering it
  // never found the lamp that was clicked: it found whichever lamp happened to sit near that world coordinate. The
  // log showed the result plainly: a page opened on one lamp, and the write stored on a lamp on another body.
  // Ground lamps are registered at their world position, so this is exact for them. A lamp on a body arrives in plot
  // coordinates, which are exact too. A body lamp clicked at a world position is not a case that happens; if it ever
  // does it belongs to the interaction layer, and the honest answer here is "no registration" rather than a guess --
  // every guess this file used to make wrote to a lamp on a different body.
  return SourceAnchor.capture(level,pos);
 }

 /** One registration, by its exact key. */
 static RegisteredSources.Entry findRegistered(RegisteredSources data,ServerLevel level,SourceAnchor anchor){
  return data.find(anchor,false);
 }
 private static boolean tooFar(Player player,ServerLevel level,BlockPos pos){
  var center=pos.getCenter();
  var anchor=SourceAnchor.capture(level,pos);
  if(anchor.ship()!=null){
   var resolved=anchor.resolve(level);
   if(resolved!=null){
    var world=anchor.world(level,resolved,Vec3.atCenterOf(resolved));
    if(world!=null)center=world;
   }
  }
  return player.distanceToSqr(center)>MAX_REACH*MAX_REACH;
 }

 /**
  * One validated write of a lamp's blink pattern.
  *
  * <p>The text is a list of on/off durations in ticks, alternating, so {@code 4,4,4,28} is on four, off four,
  * on four, off twenty-eight -- a two second cycle a player can build a signal light out of without any
  * redstone. Empty means steady. Anything that does not parse is refused whole and named in chat, for the same
  * reason the cone text is: a lamp that silently keeps an old pattern is worse to debug than a sentence.
  */
 public static boolean setBlink(Player player,net.minecraft.core.BlockPos pos,String text){
  if(!(player.level() instanceof ServerLevel level))return false;
  if(!player.mayBuild())return false;
  if(tooFar(player,level,pos))return false;
  String parsed;
  try{
   parsed=BlinkPattern.parse(text);
  }catch(IllegalArgumentException bad){
   player.displayClientMessage(LocalizedText.component("闪烁没改：%s",LocalizedText.errorMessage(bad)),true);
   return false;
  }
  // Both places are written when they exist. A lamp we only registered has no block entity of ours, so
  // the registration is the only place that can keep the pattern; a hand-placed stand-in has no
  // registration, so its block entity is. Keeping the two in step means a later take-over does not lose it.
  boolean wrote=false;
  if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity lamp){
   lamp.setBlink(parsed);
   wrote=true;
  }
  var data=RegisteredSources.get(level);
  if(data.setBlink(anchorOf(level,pos),parsed))wrote=true;
  if(wrote)return true;
  player.displayClientMessage(LocalizedText.component("闪烁没改：这里没有可写的光源"),true);
  return false;
 }

 /** A lamp's blink text, the way the page shows it. */
 public static String blinkText(Player player,net.minecraft.core.BlockPos pos){
  if(player.level() instanceof ServerLevel server){
   var entry=findRegistered(RegisteredSources.get(server),server,anchorOf(server,pos));
   if(entry!=null&&!entry.blink().isEmpty())return entry.blink();
   if(server.getBlockEntity(pos) instanceof LightSourceBlockEntity lamp&&!lamp.blink().isEmpty()){
    return lamp.blink();
   }
   if(entry!=null)return entry.blink();
  }
  return pagePos!=null&&pagePos.equals(pos)?pageBlink:"";
 }
 public static boolean setCones(Player player,BlockPos pos,String text){
  if(!(player.level() instanceof ServerLevel level))return false;
  if(!player.mayBuild())return false;
  if(tooFar(player,level,pos))return false;
  BlockState state=level.getBlockState(pos);
  var data=RegisteredSources.get(level);
  var anchor=anchorOf(level,pos);
  boolean replaced=state.getBlock() instanceof LightSourceBlock;
  if(!replaced&&findRegistered(data,level,anchor)==null)return false;
  try{
   return data.setCones(anchor,text);
  }catch(IllegalArgumentException bad){
   player.displayClientMessage(LocalizedText.component("阴影投射角度没改：%s",LocalizedText.errorMessage(bad)),true);
   return false;
  }
 }
 /**
  * A lamp's cone text, the way the page shows it: the server reads the registration, the client reads
  * what the server sent with the page.
  */
 public static String coneText(Player player,BlockPos pos){
  if(player.level() instanceof ServerLevel server){
   // Resolved the same way a write is: the direct anchor first, then the body lookup. Looking this up more weakly
  // than the write path is what made a registered lamp's page show zeros -- and then write those zeros back,
  // which is how a lamp that had shadows lost them and never got them back.
  var entry=findRegistered(RegisteredSources.get(server),server,anchorOf(server,pos));
   return entry==null?"":entry.cones();
  }
  return pagePos!=null&&pagePos.equals(pos)?pageCones:"";
 }
 /**
  * One validated write from the page.
  * @param player - the player using the page.
  * @param pos - the stand-in's position in the level the player is in.
  * @param field - one of {@link #FIELDS}.
  * @param value - the requested value, clamped or refused by field.
  * @returns whether the world changed.
  */
 public static boolean setNumber(Player player,BlockPos pos,String field,double value){
  return setNumber(player,pos,-1,field,value);
 }

 public static boolean setNumber(Player player,BlockPos pos,int id,String field,double value){  if(!(player.level() instanceof ServerLevel level))return false;
  if(!player.mayBuild())return false;
  if(tooFar(player,level,pos))return false;
  BlockState state=level.getBlockState(pos);
  var data=RegisteredSources.get(level);
  var anchor=SourceAnchor.capture(level,pos);
  // A registered source whose block we never replaced has a page too: its colour, radius, cloud reach,
  // light style, faces and brightness all live in the registration, and refusing the whole write path
  // here is exactly what used to make such a lamp uncontrollable. Only the block-state fields below
  // (brightness on a stand-in, the redstone answer, the channel row) still need our own block.
  boolean replaced=state.getBlock() instanceof LightSourceBlock;
  // The entry the write is for, resolved once. Everything below has to use *its* anchor, not the one captured from
  // the click: the setters look an entry up by anchor, so writing with a captured anchor that only matched by the
  // proximity fallback finds nothing and returns false -- a refused write with no message, which is exactly how a
  // lamp on a hull could show a page full of values that never changed.
  // The id travels with the edit, so the usual case needs no recognition at all.
  var matched=replaced?null:id>0?data.entries.get(id):findRegistered(data,level,anchor);
  if(!replaced&&matched==null){
   com.mojang.logging.LogUtils.getLogger().warn(
           "Sable Light Overhaul lamp write refused: field={} value={} at {} anchor={}",
           field,value,pos.toShortString(),anchor);
   return false;
  }
  if(PointLightAppearance.owns(field)){
   if(!(level.getBlockEntity(pos) instanceof LightSourceBlockEntity lamp))return false;
   try{lamp.setAppearance(field,value);return true;}catch(IllegalArgumentException invalid){return false;}
  }
  var target=matched==null?anchor:matched.anchor();
  boolean accepted=switch(field){
   // Mode 0 is how a page unregisters: the entry goes, exactly as the third step of a cycle does.
   case "mode"->{int mode=(int)Math.round(value);yield mode<0||mode>2?false:data.setMode(target,mode);}
   // A palette row, or a colour typed into the box beside it. One int carries both: a custom value is
   // tagged above every palette index, so a single test accepts either and no other field had to grow.
   case "color"->{int index=(int)Math.round(value);
    yield (SourceColor.valid(index)||SourceColor.isCustom(index))&&data.setColor(target,index);}
   // Brightness lives in two places depending on the lamp: a stand-in keeps it in its block state (that
   // is what the light pass reads, and it is written too so the model and the light agree), while a block
   // we did not replace can only be dimmed through the registration's own override.
   case "emission"->{int brightness=(int)Math.round(value);
     if(brightness<MIN_EMISSION||brightness>MAX_EMISSION)yield false;
     // The override is best-effort on purpose: a stand-in that was placed while dark may not be
     // registered yet, and refusing the brightness there would leave a lamp that cannot be turned up.
     data.setEmissionOverride(anchor,brightness);
     yield replaced?setEmission(level,pos,state,value):true;}
   case "control"->replaced&&setControl(level,pos,value);
   case "radius"->{int radius=(int)Math.round(value);yield radius>=0&&radius<=MAX_RADIUS&&data.setRadius(target,radius);}
   // The shadow fields are written as a pair, because they are one answer: the page saves whichever of
   // the two was not just edited by reading it back, so editing one can never silently drop the other.
   case "shadow"->{var e=findRegistered(data,level,anchor);int mode=(int)Math.round(value);
     yield e!=null&&mode>=0&&mode<=2&&data.setShadow(target,mode,e.shadowSteps());}
   case "shadowSteps"->{var e=findRegistered(data,level,anchor);int steps=(int)Math.round(value);
     yield e!=null&&steps>=0&&steps<=MAX_SHADOW_STEPS&&data.setShadow(target,e.shadowMode(),steps);}
   case "cloudReach"->{int reach=(int)Math.round(value);
     yield reach>=0&&reach<=MAX_CLOUD_REACH&&data.setCloudReach(target,reach);}
   // The two light-style fields are written as a pair for the same reason the shadow pair is: the page
   // saves the one it did not just edit by reading it back, so editing one cannot drop the other.
   case "flood"->{var e=findRegistered(data,level,anchor);int mode=(int)Math.round(value);
     yield e!=null&&mode>=0&&mode<=1&&data.setLightStyle(target,mode==0,e.faces());}
   case "faces"->{var e=findRegistered(data,level,anchor);int faces=(int)Math.round(value);
     yield e!=null&&faces>=0&&faces<=RegisteredSources.FACES_ALL&&data.setLightStyle(target,e.flood(),faces);}
   // Turning the row off takes the boards back out; turning it on does nothing, because there is no
   // board to install from a page and the gesture is the way in.
   case "channel"->{
     if(value!=0)yield false;
     if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn)standIn.channel().clear();
     yield true;
   }
   default->false;
  };
  com.mojang.logging.LogUtils.getLogger().info(
          "Sable Light Overhaul lamp write: field={} value={} replaced={} entry={} accepted={}",
          field,value,replaced,matched==null?"none":matched.id(),accepted);
  return accepted;
 }
 /** Every value, in {@link #FIELDS} order, for a player who cannot be shown the page. */
 public static void report(Player player,BlockPos pos){
  player.displayClientMessage(Component.translatable("sablelightoverhaul.lightsource.title"),false);
  for(String field:FIELDS)
   player.displayClientMessage(LocalizedText.component("%s = %s",
     Component.translatable("sablelightoverhaul.lightsource.field."+field),text(player,pos,field)),false);
 }
 /**
  * A stand-in's brightness lives in its block state, not in its registration: vanilla has to see the
  * block emit nothing, so the level the replaced block emitted is kept in {@link LightSourceBlock#EMISSION}
  * and read back through {@link LightSourceBlock#emissionOf}.
  */
 private static boolean setEmission(ServerLevel level,BlockPos pos,BlockState state,double value){
  int emission=(int)Math.round(value);
  if(emission<MIN_EMISSION||emission>MAX_EMISSION)return false;
  var standIn=level.getBlockEntity(pos) instanceof LightSourceBlockEntity be?be:null;
  if(standIn!=null)standIn.setLitEmission(emission);
  // A lamp the redstone is currently holding off stays off: the new level is remembered on the block
  // entity and written into the state when the lamp comes on, so editing brightness never lights a lamp
  // that its own wiring says should be dark.
  boolean on=standIn==null||LightSourceBlockEntity.on(standIn,level.getBestNeighborSignal(pos));
  level.setBlock(pos,state.setValue(LightSourceBlock.EMISSION,on?emission:0),3);
  return true;
 }
 /** The stand-in's redstone answer, or 0 for a block that is not one. */
 private static double control(Level level,BlockPos pos){
  return level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn?standIn.control():0.0;
 }
 /** Whether this stand-in has a channel board installed. */
 private static boolean channelBound(Level level,BlockPos pos){
  return level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn&&standIn.channel().bound();
 }
 /** One write of the redstone mode. The block entity pushes it to the client with the block update. */
 private static boolean setControl(ServerLevel level,BlockPos pos,double value){
  int mode=(int)Math.round(value);
  if(mode<0||mode>=LightSourceBlockEntity.CONTROLS.length)return false;
  if(!(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn))return false;
  standIn.setControl(mode);
  return true;
 }
 /** The stand-in's own brightness, or zero for a block that is not one. */
 private static double emission(Level level,BlockPos pos){
  BlockState state=level.getBlockState(pos);
  return state.getBlock() instanceof LightSourceBlock?state.getValue(LightSourceBlock.EMISSION):0.0;
 }
 /** Whether a block was replaced here: the block entity remembers it only when one was. */
 private static boolean takenOver(Level level,BlockPos pos){
  return level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn&&standIn.originalState()!=null;
 }
 /**
  * The palette index the page should show: an explicit one, or the one this block's id resolves to.
  *
  * <p>The stored value may be {@link SourceColor#AUTO}, which is not a row a picker can sit on. It is
  * resolved here the way the light pass resolves it, from the block the stand-in replaced, so the page
  * names the tint the player is actually looking at.
  */
 private static double color(ServerLevel level,BlockPos pos,RegisteredSources.Entry entry){
  if(entry!=null&&entry.color()>=0)return entry.color();
  BlockState state=level.getBlockState(pos);
  return LightingCommon.colorOf(LightSourceBlock.colorHost(level,pos,state).getBlock());
 }
 /** One value written the way the page's own control would show it. */
 private static Component text(Player player,BlockPos pos,String field){
  double value=number(player,pos,field);
  return switch(field){
   case "mode"->modeName((int)Math.round(value));
   case "color"->colorName((int)Math.round(value));
   case "rgb"->SourceColor.isCustom((int)Math.round(value))?Component.literal(SourceColor.hex((int)Math.round(value))):Component.empty();
   // The radius needs its unit more than the other fields do: 0 is a rule, not a distance.
   case "radius"->Math.round(value)==0?LocalizedText.component("0（按亮度自动推导）"):LocalizedText.component("%s 格",Math.round(value));
   case "shadow"->shadowName((int)Math.round(value));
   case "control"->controlName((int)Math.round(value));
   // And the steps need to say who is in charge when they are zero.
   case "shadowSteps"->Math.round(value)==0?LocalizedText.component("0（用客户端上限）"):LocalizedText.component("%s 次",Math.round(value));
   // Same idea for the cloud reach: zero is not a distance, it is "ask the client's setting".
   case "cloudReach"->Math.round(value)==0?LocalizedText.component("0（跟随客户端全局设置）"):LocalizedText.component("%s 格",Math.round(value));
   case "flood"->styleName((int)Math.round(value));
   // A text field: reported here so the chat fallback and the page agree on what it says.
   case "shadowCones"->{String cones=coneText(player,pos);yield cones.isEmpty()?LocalizedText.component("全部方向"):Component.literal(cones);}
   case "faces"->facesText((int)Math.round(value));
   default->Component.literal(Long.toString(Math.round(value)));
  };
 }
 private static double remembered(BlockPos pos,String field){
  if(pagePos==null||!pagePos.equals(pos))return 0.0;
  return switch(field){
   case "mode"->pageMode;
   case "color"->pageColor;
   case "radius"->pageRadius;
   case "shadow"->pageShadow;
   case "shadowSteps"->pageShadowSteps;
   case "cloudReach"->pageCloudReach;
   case "flood"->pageFlood;
   case "faces"->pageFaces;
   default->0.0;
  };
 }
 private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 /**
  * The faces a lamp emits through, as names, or "全部" when it is every one of them.
  *
  * <p>Read out rather than shown as a number because the mask is six independent answers and a sum of
  * bits is not one.
  */
 private static Component facesText(int faces){
  int mask=faces&RegisteredSources.FACES_ALL;
  if(mask==RegisteredSources.FACES_ALL)return LocalizedText.component("全部");
  if(mask==0)return LocalizedText.component("全部关闭（只剩自己那一格）");
  var text=Component.empty();
  boolean first=true;
  for(int i=0;i<RegisteredSources.FACE_BITS.length;i++)
   if((mask&(1<<i))!=0){if(!first)text.append("/");text.append(LocalizedText.component(RegisteredSources.FACE_BITS[i]));first=false;}
  return text;
 }
 private static void say(Player player,String text){player.displayClientMessage(Component.literal(text),true);}
}
