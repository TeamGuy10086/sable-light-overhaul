package dev.cbcmuzzlelight.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
 public static final String[] FIELDS={"mode","color","emission","radius","shadow","shadowSteps"};
 /** The three registration modes, in the order the picker lists them; the tool uses the same words. */
 public static final String[] MODES={"取消登记","启用","强制启用"};
 /** The three shadow answers, in the order the picker lists them. */
 public static final String[] SHADOWS={"不投影","硬边","软边"};
 /** The most taps a lamp may ask for; the client's own ceiling is applied on top of this. */
 public static final int MAX_SHADOW_STEPS=16;
 /** The widest radius a page may set; {@link RegisteredSources#RADIUS_AUTO} means "from the brightness". */
 public static final int MAX_RADIUS=64;
 /** Brightness is a block light level, so it runs 1..15: a stand-in at 0 would be a lamp that is off. */
 public static final int MIN_EMISSION=1,MAX_EMISSION=15;
 /** The page is only reachable at the block, so a write from far away is not a real edit. */
 private static final double MAX_REACH=64.0;
 private LightSourceServer(){}
 /** Client-side copy of the values that live in the save data; filled when the page opens. */
 private static BlockPos pagePos;
 private static int pageMode,pageColor,pageRadius,pageShadow,pageShadowSteps;
 /**
  * Remember the values the server sent with the page.
  *
  * <p>Only effective on the client, which is the side that cannot compute them: the registration is in
  * the level's save data. The server has no use for a copy of what it just read.
  */
 public static void remember(BlockPos pos,int mode,int color,int radius,int shadow,int shadowSteps){
  pagePos=pos.immutable();pageMode=mode;pageColor=color;pageRadius=radius;
  pageShadow=shadow;pageShadowSteps=shadowSteps;
 }
 /**
  * The page's current value for one field: the server reads the registration and the world, the client
  * reads the two values that are synced to it and the ones the server sent with the page.
  */
 public static double number(Player player,BlockPos pos,String field){
  Level level=player.level();
  if(level.isClientSide)return switch(field){
   // The block state arrives with the chunk, so brightness is read here rather than sent: a page that
   // showed it from a snapshot would go stale the moment anything else changed it.
   case "emission"->emission(level,pos);
   default->remembered(pos,field);
  };
  if(!(level instanceof ServerLevel server))return 0.0;
  var entry=RegisteredSources.get(server).find(SourceAnchor.capture(server,pos),false);
  return switch(field){
   case "mode"->entry==null?0:entry.mode();
   case "color"->color(server,pos,entry);
   case "emission"->emission(server,pos);
   case "radius"->entry==null?0:entry.radius();
   case "shadow"->entry==null?0:entry.shadowMode();
   case "shadowSteps"->entry==null?0:entry.shadowSteps();
   default->0.0;
  };
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
  if(!(player.level() instanceof ServerLevel level))return false;
  if(!player.mayBuild())return false;
  if(player.distanceToSqr(pos.getCenter())>MAX_REACH*MAX_REACH)return false;
  BlockState state=level.getBlockState(pos);
  if(!(state.getBlock() instanceof LightSourceBlock))return false;
  var data=RegisteredSources.get(level);
  var anchor=SourceAnchor.capture(level,pos);
  return switch(field){
   // Mode 0 is how a page unregisters: the entry goes, exactly as the third step of a cycle does.
   case "mode"->{int mode=(int)Math.round(value);yield mode<0||mode>2?false:data.setMode(anchor,mode);}
   case "color"->{int index=(int)Math.round(value);yield SourceColor.valid(index)&&data.setColor(anchor,index);}
   case "emission"->setEmission(level,pos,state,value);
   case "radius"->{int radius=(int)Math.round(value);yield radius>=0&&radius<=MAX_RADIUS&&data.setRadius(anchor,radius);}
   // The shadow fields are written as a pair, because they are one answer: the page saves whichever of
   // the two was not just edited by reading it back, so editing one can never silently drop the other.
   case "shadow"->{var e=data.find(anchor,false);int mode=(int)Math.round(value);
     yield e!=null&&mode>=0&&mode<=2&&data.setShadow(anchor,mode,e.shadowSteps());}
   case "shadowSteps"->{var e=data.find(anchor,false);int steps=(int)Math.round(value);
     yield e!=null&&steps>=0&&steps<=MAX_SHADOW_STEPS&&data.setShadow(anchor,e.shadowMode(),steps);}
   default->false;
  };
 }
 /** Every value, in {@link #FIELDS} order, for a player who cannot be shown the page. */
 public static void report(Player player,BlockPos pos){
  player.displayClientMessage(Component.translatable("cbcmuzzlelight.lightsource.title"),false);
  for(String field:FIELDS)
   player.displayClientMessage(Component.translatable("cbcmuzzlelight.lightsource.field."+field)
     .append(" = "+text(player,pos,field)),false);
 }
 /**
  * A stand-in's brightness lives in its block state, not in its registration: vanilla has to see the
  * block emit nothing, so the level the replaced block emitted is kept in {@link LightSourceBlock#EMISSION}
  * and read back through {@link LightSourceBlock#emissionOf}.
  */
 private static boolean setEmission(ServerLevel level,BlockPos pos,BlockState state,double value){
  int emission=(int)Math.round(value);
  if(emission<MIN_EMISSION||emission>MAX_EMISSION)return false;
  level.setBlock(pos,state.setValue(LightSourceBlock.EMISSION,emission),3);
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
 private static String text(Player player,BlockPos pos,String field){
  double value=number(player,pos,field);
  return switch(field){
   case "mode"->MODES[clamp((int)Math.round(value),0,MODES.length-1)];
   case "color"->SourceColor.name((int)Math.round(value));
   // The radius needs its unit more than the other fields do: 0 is a rule, not a distance.
   case "radius"->Math.round(value)==0?"0（按亮度自动推导）":Math.round(value)+" 格";
   case "shadow"->SHADOWS[clamp((int)Math.round(value),0,SHADOWS.length-1)];
   // And the steps need to say who is in charge when they are zero.
   case "shadowSteps"->Math.round(value)==0?"0（用客户端上限）":Math.round(value)+" 次";
   default->Long.toString(Math.round(value));
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
   default->0.0;
  };
 }
 private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 private static void say(Player player,String text){player.displayClientMessage(Component.literal(text),true);}
}
