package dev.cbcmuzzlelight.lighting;
import java.util.*;
import dev.cbcmuzzlelight.MuzzleLight;
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
@EventBusSubscriber(modid=MuzzleLight.ID)
public final class LightingServer {
 private static final int MAX_RADIUS=64;
 private static final Map<ServerPlayer,SourcePayload> sent=new WeakHashMap<>();
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%5!=0)return;
  // Anything a blast queued for the far LOD is marked and written here, one scan after the blocks were
  // actually removed, so a client that re-asks cannot be served the pre-crater column.
  dev.cbcmuzzlelight.voxy.VoxyLodNudger.drain(level);
  var states=new ArrayList<SourcePayload.State>();
  List<Integer> gone=null;
  for(var e:RegisteredSources.get(level).entries.values()){
   var pos=e.anchor().resolve(level);if(pos==null||!level.hasChunkAt(pos))continue;
   var state=level.getBlockState(pos);int emission=LightSourceBlock.emissionOf(level,pos,state);
   // A registration pointing at air can never light anything again: a light block that was broken or
   // pushed away leaves this behind, and the map has a hard cap, so it is dropped here rather than
   // kept forever. A lamp that is merely switched off still holds its block and is left alone.
   if(!e.spotlight()&&state.isAir()){(gone==null?gone=new ArrayList<>():gone).add(e.id());continue;}
   int intensity=0,widthSignal=0,colorSignal=0,distance=0,beamMode=0;float tilt=0.0F,azimuth=0.0F;
   if(e.spotlight()) {
    if(!state.is(LightingBlocks.SPOTLIGHT.get()))continue;
    // Vanilla signal queries may reach two blocks out; never let one load a chunk.
    if(!neighboursLoaded(level,pos))continue;
    var lamp=lampAt(level,pos,state);
    if(lamp==null)continue;
    int down=input(level,pos,Direction.DOWN);
    switch(lamp.control()){
     case SpotlightBlockEntity.SEMI -> {
      // Redstone is a switch here: a level, or one flip per rising edge when the latch is on. The
      // latch state is the block entity's, flipped in its own ticker, which samples every tick so a
      // button's one-tick pulse cannot be missed.
      boolean on=lamp.latch()?lamp.latchedOn():SpotlightBlockEntity.anySignal(level,pos)>0;
      if(!on)continue;
      intensity=lamp.intensity();widthSignal=lamp.width();colorSignal=lamp.color();tilt=(float)lamp.angle();
     }
     case SpotlightBlockEntity.MANUAL -> {
      if(!lamp.switchOn())continue;
      intensity=lamp.intensity();widthSignal=lamp.width();colorSignal=lamp.color();tilt=(float)lamp.angle();
     }
     default -> {
      // The mode the lamp always had: bottom face = brightness, top = width, sides = colour, and the
      // page's switch is a master gate so a full-redstone lamp can still be turned off by hand.
      if(!lamp.switchOn()||down<=0)continue;
      intensity=down;
      widthSignal=input(level,pos,Direction.UP);
      for(var side:Direction.Plane.HORIZONTAL)colorSignal=Math.max(colorSignal,input(level,pos,side));
      tilt=(float)lamp.sweep(down);
     }
    }
    distance=LightingCommon.distance(state.getValue(SpotlightBlock.RANGE));
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
   }
   states.add(new SourcePayload.State(e.id(),e.anchor(),e.mode(),e.spotlight(),emission,intensity,widthSignal,colorSignal,distance,beamMode,tilt,azimuth,e.spotlight()?RegisteredSources.RADIUS_AUTO:e.radius(),e.spotlight()?RegisteredSources.SHADOW_SOFT:e.shadowMode(),e.spotlight()?RegisteredSources.SHADOW_STEPS_AUTO:e.shadowSteps()));if(states.size()>=8192)break;
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
     var at=state.anchor().resolve(level);
     if(at==null){filtered.add(state);continue;}
     double dx=at.getX()+0.5-px,dy=at.getY()+0.5-py,dz=at.getZ()+0.5-pz;
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
  var root=Commands.literal("cbcmuzzlelight").requires(s->s.hasPermission(2));
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
   c.getSource().sendSuccess(()->Component.literal(removed?"已删除登记":"未找到静态光源登记"),true);return removed?1:0;
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
  boolean present=dev.cbcmuzzlelight.voxy.LssBridge.present();
  boolean ready=dev.cbcmuzzlelight.voxy.LssBridge.ready();
  source.sendSuccess(()->Component.literal("Voxy LOD 刷新："+(enabled?"开":"关")
    +" / LSS "+(present?(ready?"已连接":"已安装但内部未解析"):"未安装")
    +" / 服务 "+(dev.cbcmuzzlelight.voxy.LssBridge.serviceLive()?"运行中":"未运行（单机需开放到 LAN）")
    +" / 待处理列 "+dev.cbcmuzzlelight.voxy.VoxyLodNudger.pending(level)),false);
  source.sendSuccess(()->Component.literal("LSS 统计："+dev.cbcmuzzlelight.voxy.LssBridge.statusLine()),false);
  source.sendSuccess(()->Component.literal("用法：/cbcmuzzlelight voxy nudge <center> <radius> —— 把该范围内的列交给 LSS 重发（等同于一发炮弹落在这里）"),false);
  return ready?1:0;
 }
 /** Hands one square of columns over by hand, for testing the chain end to end. */
 private static int voxyNudge(net.minecraft.commands.CommandSourceStack source,net.minecraft.core.BlockPos center,int radius){
  int columns=dev.cbcmuzzlelight.voxy.VoxyLodNudger.nudgeAround(source.getLevel(),center,radius);
  final int handed=columns;
  source.sendSuccess(()->Component.literal("已为 "+handed+" 个区块列请求 LOD 重发（第一次会写盘，之后按 voxy.saveIntervalTicks 限流）"),true);
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
  source.sendSuccess(()->Component.literal("半径 "+radius+" 内发光方块 "+total+" 个（新增 "+fresh+"），已按"+(mode==2?"强制启用":"启用")+"登记；实际渲染仍受 maxStaticSources 预算限制"),true);
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
  source.sendSuccess(()->Component.literal("已移除半径 "+radius+" 内 "+total+" 个静态光源登记"),true);
  return removed;
 }
 private static int list(net.minecraft.commands.CommandSourceStack source,int page){
  var level=source.getLevel();var all=RegisteredSources.get(level).entries.values().stream().filter(e->!e.spotlight()).toList();
  source.sendSuccess(()->Component.literal("当前维度静态光源 "+all.size()+" 个，第 "+page+" 页（每页10个）；距离激活由各客户端判断"),false);
  all.stream().skip((long)(page-1)*10).limit(10).forEach(e->{var p=e.anchor().resolve(level);boolean loaded=p!=null&&level.hasChunkAt(p);int light=loaded?LightSourceBlock.emissionOf(level,p,level.getBlockState(p)):0;source.sendSuccess(()->Component.literal("#"+e.id()+" "+(e.mode()==2?"强制":"启用")+" "+e.anchor().pos().toShortString()+" ship="+e.anchor().ship()+" "+(!loaded?"未加载":light<=0?"不发光":"已加载，亮度"+light)),false);});return all.size();
 }
}
