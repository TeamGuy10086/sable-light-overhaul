package dev.sablelightoverhaul.lighting;
import java.util.*;
import dev.sablelightoverhaul.FlarePayload;
import dev.sablelightoverhaul.LocalizedText;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.arguments.DoubleArgumentType;
/** Server side of the debug flares: /sloflare commands and full-snapshot broadcasts. Never loads chunks. */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class DebugFlareServer {
 private static final int DEFAULT_RGB=0xFF5030;
 private static final Map<ServerLevel,List<FlarePayload.State>> FLARES=new WeakHashMap<>();
 private static int nextId=1;
 @SubscribeEvent public static void commands(RegisterCommandsEvent event){
  var root=Commands.literal("sloflare").requires(s->s.hasPermission(2));
  var lifetime=Commands.argument("lifetime",DoubleArgumentType.doubleArg(0.1,3600));
  var position=Commands.argument("position",Vec3Argument.vec3());
  root.then(Commands.literal("signal").then(position.then(lifetime
   .executes(c->add(c.getSource(),false,Vec3Argument.getVec3(c,"position"),DoubleArgumentType.getDouble(c,"lifetime"),DEFAULT_RGB,15F))
   .then(Commands.argument("color",StringArgumentType.word()).executes(c->add(c.getSource(),false,Vec3Argument.getVec3(c,"position"),DoubleArgumentType.getDouble(c,"lifetime"),color(StringArgumentType.getString(c,"color")),15F))))));
  root.then(Commands.literal("illumination").then(Commands.argument("position",Vec3Argument.vec3()).then(Commands.argument("lifetime",DoubleArgumentType.doubleArg(0.1,3600))
   .executes(c->add(c.getSource(),true,Vec3Argument.getVec3(c,"position"),DoubleArgumentType.getDouble(c,"lifetime"),DEFAULT_RGB,dev.sablelightoverhaul.munitions.FlareSettings.DEFAULT.brightness()))
   .then(Commands.argument("brightness",DoubleArgumentType.doubleArg(0,15))
    .executes(c->add(c.getSource(),true,Vec3Argument.getVec3(c,"position"),DoubleArgumentType.getDouble(c,"lifetime"),DEFAULT_RGB,(float)DoubleArgumentType.getDouble(c,"brightness")))))));
  root.then(Commands.literal("clear").executes(c->clear(c.getSource())));
  event.getDispatcher().register(root);
 }
 private static int color(String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
  if(!text.matches("[0-9a-fA-F]{6}"))throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(LocalizedText.component("颜色需为六位十六进制，如 FF5030")).create();
  return Integer.parseInt(text,16);
 }
 private static List<FlarePayload.State> list(ServerLevel level){return FLARES.computeIfAbsent(level,k->new ArrayList<>());}
 private static int add(CommandSourceStack source,boolean illumination,Vec3 p,double lifetime,int rgb,float brightness){
  if(!Double.isFinite(lifetime)||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||Math.abs(p.x)>30000000||Math.abs(p.y)>30000000||Math.abs(p.z)>30000000){source.sendFailure(LocalizedText.component("坐标或寿命超出范围"));return 0;}
  ServerLevel level=source.getLevel();
  long duration=Math.max(2,Math.min(72000,(long)Math.ceil(lifetime*20.0)));
  List<FlarePayload.State> list=list(level);
  if(list.size()>=FlarePayload.MAX_FLARES){source.sendFailure(LocalizedText.component("信号弹已达上限 %s，请先清除",FlarePayload.MAX_FLARES));return 0;}
  int id=nextId++;nextId=Math.max(1,nextId);
  list.add(new FlarePayload.State(id,illumination,p.x,p.y,p.z,level.getGameTime(),(int)duration,rgb,brightness));
  FlarePayload.State st=list.get(list.size()-1);
  broadcast(level);
  source.sendSuccess(()->LocalizedText.component("已生成%s弹 #%s 于 %s，持续 %s 刻",
    LocalizedText.component(illumination?"照明":"信号"),
    st.id(),
    String.format("%.1f %.1f %.1f",st.x(),st.y(),st.z()),
    duration),true);
  return 1;
 }
 /** Shared release path for CBC fuze callbacks; an actual shot may replace the oldest effect at the cap. */
 public static void releaseIllumination(ServerLevel level,Vec3 position,int lifetimeSeconds,float brightness){
  release(level,position,lifetimeSeconds,brightness,true,DEFAULT_RGB);
 }
 public static void release(ServerLevel level,Vec3 position,int lifetimeSeconds,float brightness,boolean illumination,int rgb){
  release(level,position,lifetimeSeconds,brightness,illumination,rgb,360);
 }
 public static void release(ServerLevel level,Vec3 position,int lifetimeSeconds,float brightness,boolean illumination,int rgb,int angleDegrees){
  var entries=list(level);if(entries.size()>=FlarePayload.MAX_FLARES)entries.remove(0);
  int id=nextId++;nextId=Math.max(1,nextId);
  entries.add(new FlarePayload.State(id,illumination,position.x,position.y,position.z,level.getGameTime(),Math.clamp(lifetimeSeconds,1,3600)*20,rgb,brightness,angleDegrees));
  broadcast(level);
 }
 private static int clear(CommandSourceStack source){
  List<FlarePayload.State> list=list(source.getLevel());
  int n=list.size();list.clear();broadcast(source.getLevel());
  Component message=n==0?LocalizedText.component("当前维度没有信号弹"):LocalizedText.component("已清除 %s 个信号弹",n);
  source.sendSuccess(()->message,true);
  return n;
 }
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  List<FlarePayload.State> list=FLARES.get(level);
  if(list==null||list.isEmpty())return;
  long now=level.getGameTime();
  if(list.removeIf(s->now-s.bornTick()>=s.durationTicks()))broadcast(level);
 }
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)send(p,p.serverLevel());}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)send(p,p.serverLevel());}
 @SubscribeEvent public static void stopped(ServerStoppedEvent event){FLARES.clear();}
 private static void send(ServerPlayer player,ServerLevel level){PacketDistributor.sendToPlayer(player,new FlarePayload(level.dimension().location(),snapshot(level)));}
 private static void broadcast(ServerLevel level){if(level.players().isEmpty())return;FlarePayload packet=new FlarePayload(level.dimension().location(),snapshot(level));for(ServerPlayer player:level.players())PacketDistributor.sendToPlayer(player,packet);}
 private static List<FlarePayload.State> snapshot(ServerLevel level){List<FlarePayload.State> list=FLARES.get(level);return list==null?List.of():List.copyOf(list);}
}
