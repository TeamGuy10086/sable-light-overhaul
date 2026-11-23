package dev.sablelightoverhaul.lighting;
import java.util.*;
import dev.sablelightoverhaul.LocalizedText;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class LightingServer {
 private static final int MAX_RADIUS=64;
 private static final Map<ServerPlayer,SourcePayload> sent=new WeakHashMap<>();
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%5!=0)return;
  // Anything a blast queued for the far LOD is marked and written here, one scan after the blocks were
  // actually removed, so a client that re-asks cannot be served the pre-crater column.
  dev.sablelightoverhaul.voxy.VoxyLodNudger.drain(level);
  var states=new ArrayList<SourcePayload.State>();
  List<Integer> gone=null;
  for(var e:RegisteredSources.get(level).entries.values()){
   var pos=e.anchor().resolve(level);if(pos==null||!level.hasChunkAt(pos))continue;
   var state=level.getBlockState(pos);
// A lamp we replaced carries its brightness in our own block's state; one we merely registered emits
// whatever its block was built to emit, so the page's own override is the only way to dim it.
int emission=e.emission()>0?e.emission():LightSourceBlock.emissionOf(level,pos,state);
// Whether this source is a block of ours. It decides two things here: a lamp that is only registered
// never casts (it is the soft, cheap class the user asked for, and its own white light was already taken
// over at the light engine), and the block-state fields below it do not exist for it.
boolean replaced=state.getBlock() instanceof LightSourceBlock;
if(replaced)emission=LightSourceBlock.emissionOf(level,pos,state);
var appearance=!e.spotlight()&&level.getBlockEntity(pos) instanceof LightSourceBlockEntity host?host.appearance():PointLightAppearance.DEFAULT;
   // A registration pointing at air can never light anything again: a light block that was broken or
   // pushed away leaves this behind, and the map has a hard cap, so it is dropped here rather than
   // kept forever. A lamp that is merely switched off still holds its block and is left alone.
   if(!e.spotlight()&&state.isAir()){(gone==null?gone=new ArrayList<>():gone).add(e.id());continue;}
   int intensity=0,widthDriver=0,colorSignal=0,distance=0,beamMode=0,coneTenths=0,volumeMode=0;
  float offX=0.0F,offY=0.0F,offZ=0.0F;String blink="";float tilt=0.0F,azimuth=0.0F;
   if(e.spotlight()) {
    if(!state.is(LightingBlocks.SPOTLIGHT.get()))continue;
    // Vanilla signal queries may reach two blocks out; never let one load a chunk.
    if(!neighboursLoaded(level,pos))continue;
    var lamp=lampAt(level,pos,state);
    if(lamp==null)continue;
    volumeMode=lamp.volumeMode();
    // Keep off lamps in the snapshot at intensity 0: the model still needs their effective pose.
    // This uses the existing payload shape and does not allocate a light for an off fixture.
    int down=input(level,pos,Direction.DOWN);
    switch(lamp.control()){
     case SpotlightBlockEntity.SEMI -> {
      // Redstone is a switch here: a level, or one flip per rising edge when the latch is on. The
      // latch state is the block entity's, flipped in its own ticker, which samples every tick so a
      // button's one-tick pulse cannot be missed.
      boolean on=lamp.latch()?lamp.latchedOn():SpotlightBlockEntity.anySignal(level,pos)>0;
      intensity=on?lamp.intensity():0;widthDriver=lamp.width();colorSignal=lamp.color();tilt=(float)lamp.angle();
     }
     case SpotlightBlockEntity.MANUAL -> {
      intensity=lamp.switchOn()?lamp.intensity():0;widthDriver=lamp.width();colorSignal=lamp.color();tilt=(float)lamp.angle();
     }
     case SpotlightBlockEntity.CHANNEL -> {
      // An AFN channel holds its value, so there is no edge to catch and no latch to consult: the lamp
      // is on while the channel is on. The channel writes the lamp's own fields, so the payload below
      // reads them exactly as it does in the other modes -- one set of values, one place they live.
      intensity=lamp.channelOn()?lamp.intensity():0;widthDriver=lamp.width();colorSignal=lamp.color();tilt=(float)lamp.angle();
     }
     default -> {
      // The mode the lamp always had: bottom face = brightness, top = width, sides = colour, and the
      // page's switch is a master gate so a full-redstone lamp can still be turned off by hand.
      intensity=lamp.switchOn()?down:0;
      widthDriver=input(level,pos,Direction.UP);
      for(var side:Direction.Plane.HORIZONTAL)colorSignal=Math.max(colorSignal,input(level,pos,side));
      tilt=(float)lamp.sweep(down);
     }
    }
    // The opening the cone actually gets, in tenths of a degree. The lamp resolves it from its own two
    // limits and whichever 0..15 driver is in charge -- redstone, the page or a channel -- so a client is
    // told an angle, never a signal strength, and a hand-tuned lamp gets 0.1 degree steps instead of the
    // sixteen the driver alone could offer.
    coneTenths=(int)Math.round(lamp.coneDegrees(widthDriver)*10);
   offX=(float)lamp.offsetX();offY=(float)lamp.offsetY();offZ=(float)lamp.offsetZ();
    distance=lamp.range();
    beamMode=state.getValue(SpotlightBlock.MODE);
    azimuth=(float)lamp.azimuth();
   }
   else {
    if(emission<=0)continue;
    // Registered sources carry a resolved palette index; AUTO means the id decides it. The id that
    // decides it has to be the block this stand-in replaced: a taken-over lamp *is* a light_source
    // block on the server, so reading its own id resolved to "untinted" and the lamp's colour was lost
    // the moment it was taken over. Only a stand-in placed by hand has no original and speaks for
    // itself, and that one has no colour to inherit either.
    colorSignal=e.color()<0?LightingCommon.colorOf(LightSourceBlock.colorHost(level,pos,state).getBlock()):e.color();
   // The pattern lives on the registration (which every lamp has) and, for a stand-in, also on its block
   // entity. The registration is the truth; the block entity is the fallback for a lamp placed by hand
   // and for saves written before the registration carried it.
   blink=e.blink();
   if(blink.isEmpty()&&level.getBlockEntity(pos) instanceof LightSourceBlockEntity host)blink=host.blink();
   }
   // The cones are stored as the text the page was given and parsed here, in the one place that turns a
   // lamp's settings into what a client needs: four packed words, zero-padded, where a zero word means
   // "no cone" and the count is therefore the number of leading non-zero ones. The text is validated when
   // it is written, so a failure here means a hand-edited save: the lamp keeps its light and loses only
   // its cones, which is the safe direction.
   int[] cone;
   try {
    cone=Cones.parse(e.cones());
   } catch(IllegalArgumentException bad) {
    cone=Cones.NONE;
   }
   states.add(new SourcePayload.State(e.id(),e.anchor(),e.mode(),e.spotlight(),emission,intensity,coneTenths,colorSignal,distance,beamMode,tilt,azimuth,e.spotlight()?RegisteredSources.RADIUS_AUTO:e.radius(),e.spotlight()?RegisteredSources.SHADOW_SOFT:e.shadowMode(),e.spotlight()?RegisteredSources.SHADOW_STEPS_AUTO:e.shadowSteps(),e.spotlight()?RegisteredSources.CLOUD_REACH_GLOBAL:e.cloudReach(),e.flood(),e.spotlight()?RegisteredSources.FACES_ALL:e.faces(),cone[0],cone[1],cone[2],cone[3],offX,offY,offZ,blink,volumeMode,appearance));if(states.size()>=8192)break;
  }
  if(gone!=null){var data=RegisteredSources.get(level);for(int id:gone)data.remove(id);}
  // One list per player, cut down to what that player could actually use. The client drops sources
  // beyond its own activation distance anyway, so sending the whole level's lamps to everyone was pure
  // bandwidth and client work. A force-enabled source (mode 2) is exempt because the client is told to
  // keep those at any distance, and a source whose anchor cannot be resolved is kept rather than lost.
  double range=LightingCommon.SEND_RANGE.get();
  double rangeSqr=range*range;
  for(var player:level.players()){
   List<SourcePayload.State> mine=states;
   if(range>0.0){
    var filtered=new ArrayList<SourcePayload.State>(Math.min(states.size(),64));
    double px=player.getX(),py=player.getY(),pz=player.getZ();
    for(var state:states){
     if(state.mode()==2){filtered.add(state);continue;}
     // The distance has to be measured where the lamp is *rendered*, not where its anchor resolves to. A
     // ship-mounted lamp resolves to its PLOT position, millions of blocks from any player: measured there,
     // every ship lamp is permanently out of range and is never sent to anyone -- except a force-enabled one,
     // which is exempt from this filter. That is exactly why only mode 2 lamps lit anything. world() is the
     // same plot-to-world conversion the client already does before its own range test; this side was missing it.
     var at=state.anchor().resolve(level);
     if(at==null){filtered.add(state);continue;}
     var look=state.appearance();
     var local=net.minecraft.world.phys.Vec3.atCenterOf(at);
     if(!state.spotlight())local=local.add(look.x(),look.y(),look.z());
     var center=state.anchor().world(level,at,local);
     if(center==null){filtered.add(state);continue;}
     double dx=center.x-px,dy=center.y-py,dz=center.z-pz;
     if(dx*dx+dy*dy+dz*dz<=rangeSqr)filtered.add(state);
    }
    mine=filtered;
   }
   var packet=new SourcePayload(level.dimension().location(),List.copyOf(mine));
   if(!packet.equals(sent.get(player))){PacketDistributor.sendToPlayer(player,packet);sent.put(player,packet);}
  }
 }
 /** Signal entering the lamp through one face: the neighbour in that direction must emit toward us. */
 private static int input(ServerLevel level,net.minecraft.core.BlockPos pos,Direction from){return level.getSignal(pos.relative(from),from);}
 /**
  * The lamp's block entity, created on demand if a world predates it.
  *
  * <p>A spotlight placed before lamps had settings has no block entity in its chunk data, and a block
  * entity that is never created is never ticked, so its latch could not work. Asking for it here makes
  * an old lamp behave like a new one without a migration pass over every chunk.
  */
 private static SpotlightBlockEntity lampAt(ServerLevel level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
  if(level.getBlockEntity(pos) instanceof SpotlightBlockEntity lamp)return lamp;
  var fresh=new SpotlightBlockEntity(pos,state);
  level.setBlockEntity(fresh);
  return level.getBlockEntity(pos) instanceof SpotlightBlockEntity created?created:null;
 }
 /** Vanilla conductor queries may inspect neighbours two blocks away. Never make them load a chunk. */
 private static boolean neighboursLoaded(ServerLevel level,net.minecraft.core.BlockPos pos){
  for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})if(!level.hasChunkAt(pos.offset(x,0,z)))return false;
  return true;
 }
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)sent.remove(p);}
 @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sent.remove(p);}
 @SubscribeEvent public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sent.remove(p);}
 @SubscribeEvent public static void respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sent.remove(p);}
 @SubscribeEvent public static void commands(RegisterCommandsEvent event){
  var root=Commands.literal("sablelightoverhaul").requires(s->s.hasPermission(2));
  var sources=Commands.literal("sources");
  sources.then(Commands.literal("list").executes(c->list(c.getSource(),1)).then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->list(c.getSource(),IntegerArgumentType.getInteger(c,"page")))));

  // add <center> <radius> [force] -- registers every emitting block in the sphere at once.
  var addCenter=Commands.argument("center",BlockPosArgument.blockPos());
  var addRadius=Commands.argument("radius",IntegerArgumentType.integer(1,MAX_RADIUS));
  addCenter.then(addRadius
    .executes(c->addNear(c.getSource(),BlockPosArgument.getLoadedBlockPos(c,"center"),IntegerArgumentType.getInteger(c,"radius"),1))
    .then(Commands.literal("force").executes(c->addNear(c.getSource(),BlockPosArgument.getLoadedBlockPos(c,"center"),IntegerArgumentType.getInteger(c,"radius"),2))));
  sources.then(Commands.literal("add").then(addCenter));

  // remove id <id>      -- one registration
  // remove near <center> <radius> -- every ground registration in the sphere
  var removeId=Commands.literal("id").then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->{
   var data=RegisteredSources.get(c.getSource().getLevel());int id=IntegerArgumentType.getInteger(c,"id");var entry=data.entries.get(id);
   boolean removed=entry!=null&&!entry.spotlight()&&data.remove(id);
   c.getSource().sendSuccess(()->LocalizedText.component(removed?"已删除登记":"未找到静态光源登记"),true);return removed?1:0;
  }));
  var removeCenter=Commands.argument("center",BlockPosArgument.blockPos());
  var removeRadius=Commands.argument("radius",IntegerArgumentType.integer(1,MAX_RADIUS));
  removeCenter.then(removeRadius.executes(c->removeNear(c.getSource(),BlockPosArgument.getLoadedBlockPos(c,"center"),IntegerArgumentType.getInteger(c,"radius"))));
  sources.then(Commands.literal("remove").then(removeId).then(Commands.literal("near").then(removeCenter)));

  // voxy nudge <center> <radius> -- hand the columns in a square to LSS, the debug stand-in for a shell
  // landing there. This is how the whole far-LOD chain is tested without firing a cannon.
  var voxy=Commands.literal("voxy");
  voxy.then(Commands.literal("status").executes(c->voxyStatus(c.getSource())));
  var nudgeCenter=Commands.argument("center",BlockPosArgument.blockPos());
  var nudgeRadius=Commands.argument("radius",IntegerArgumentType.integer(1,MAX_RADIUS));
  nudgeCenter.then(nudgeRadius.executes(c->voxyNudge(c.getSource(),BlockPosArgument.getLoadedBlockPos(c,"center"),IntegerArgumentType.getInteger(c,"radius"))));
  voxy.then(Commands.literal("nudge").then(nudgeCenter));

  event.getDispatcher().register(root.then(sources).then(voxy));
 }
 /** The far-LOD debug command: what this mod knows about Voxy/LSS and what it has done so far. */
 private static int voxyStatus(net.minecraft.commands.CommandSourceStack source){
  var level=source.getLevel();
  boolean enabled=LightingCommon.VOXY_ENABLED.get();
  boolean present=dev.sablelightoverhaul.voxy.LssBridge.present();
  boolean ready=dev.sablelightoverhaul.voxy.LssBridge.ready();
  source.sendSuccess(()->LocalizedText.component("Voxy LOD 刷新：%s / LSS %s / 服务 %s / 待处理列 %s",
    LocalizedText.component(enabled?"开":"关"),
    LocalizedText.component(present?(ready?"已连接":"已安装但内部未解析"):"未安装"),
    LocalizedText.component(dev.sablelightoverhaul.voxy.LssBridge.serviceLive()?"运行中":"未运行（单机需开放到 LAN）"),
    dev.sablelightoverhaul.voxy.VoxyLodNudger.pending(level)),false);
  source.sendSuccess(()->LocalizedText.component("LSS 统计：%s",dev.sablelightoverhaul.voxy.LssBridge.statusLine()),false);
  source.sendSuccess(()->LocalizedText.component("用法：/sablelightoverhaul voxy nudge <center> <radius> —— 把该范围内的列交给 LSS 重发（等同于一发炮弹落在这里）"),false);
  return ready?1:0;
 }
 /** Hands one square of columns over by hand, for testing the chain end to end. */
 private static int voxyNudge(net.minecraft.commands.CommandSourceStack source,net.minecraft.core.BlockPos center,int radius){
  int columns=dev.sablelightoverhaul.voxy.VoxyLodNudger.nudgeAround(source.getLevel(),center,radius);
  final int handed=columns;
  source.sendSuccess(()->LocalizedText.component("已为 %s 个区块列请求 LOD 重发",handed),true);
  return columns;
 }
 /** Registers every light-emitting block inside a sphere, in one command. */
 private static int addNear(net.minecraft.commands.CommandSourceStack source,net.minecraft.core.BlockPos center,int radius,int mode){
  var level=source.getLevel();var data=RegisteredSources.get(level);
  // A sphere rather than a cube, so a large radius cannot quietly sweep in a huge box.
  double radiusSquared=(double)radius*radius;
  int found=0,added=0;
  for(net.minecraft.core.BlockPos pos:net.minecraft.core.BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))){
   if(center.distSqr(pos)>radiusSquared||!level.isLoaded(pos))continue;
   var state=level.getBlockState(pos);
   if(LightSourceBlock.emissionOf(level,pos,state)<=0)continue;
   found++;
   var anchor=new SourceAnchor(null,pos.immutable());
   if(data.find(anchor,false)==null)added++;
   data.register(anchor,mode);
  }
  final int total=found,fresh=added;
  source.sendSuccess(()->LocalizedText.component("半径 %s 内发光方块 %s 个（新增 %s），已按%s登记；实际渲染仍受 maxStaticSources 预算限制",
    radius,total,fresh,LocalizedText.component(mode==2?"强制启用":"启用")),true);
  return found;
 }
 /** Removes every ground registration inside a sphere. Spotlights are removed by breaking the block. */
 private static int removeNear(net.minecraft.commands.CommandSourceStack source,net.minecraft.core.BlockPos center,int radius){
  var level=source.getLevel();var data=RegisteredSources.get(level);
  double radiusSquared=(double)radius*radius;
  var ids=new ArrayList<Integer>();
  for(var e:data.entries.values()){
   if(e.spotlight())continue;
   var world=e.anchor().resolve(level);
   if(world==null)world=e.anchor().pos();
   if(center.distSqr(world)<=radiusSquared)ids.add(e.id());
  }
  int removed=0;
  for(int id:ids)if(data.remove(id))removed++;
  final int total=removed;
  source.sendSuccess(()->LocalizedText.component("已移除半径 %s 内 %s 个静态光源登记",radius,total),true);
  return removed;
 }
 private static int list(net.minecraft.commands.CommandSourceStack source,int page){
  var level=source.getLevel();var all=RegisteredSources.get(level).entries.values().stream().filter(e->!e.spotlight()).toList();
  source.sendSuccess(()->LocalizedText.component("当前维度静态光源 %s 个，第 %s 页（每页10个）；距离激活由各客户端判断",all.size(),page),false);
  all.stream().skip((long)(page-1)*10).limit(10).forEach(e->{var p=e.anchor().resolve(level);boolean loaded=p!=null&&level.hasChunkAt(p);int light=loaded?LightSourceBlock.emissionOf(level,p,level.getBlockState(p)):0;
   Component status=!loaded?LocalizedText.component("未加载"):light<=0?LocalizedText.component("不发光"):LocalizedText.component("已加载，亮度%s",light);
   source.sendSuccess(()->LocalizedText.component("#%s %s %s ship=%s %s",e.id(),LocalizedText.component(e.mode()==2?"强制":"启用"),e.anchor().pos().toShortString(),e.anchor().ship(),status),false);});return all.size();
 }
}
