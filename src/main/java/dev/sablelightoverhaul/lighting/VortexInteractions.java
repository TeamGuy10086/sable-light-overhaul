package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.SloNetwork;
import dev.sablelightoverhaul.SableLightOverhaul;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The vortex generator's marking wand: which blocks are registered, and how the player says so.
 *
 * <p><b>Two mouse buttons, one modifier.</b> Right-click adds the block you are pointing at,
 * left-click takes it back out, and holding shift turns either one into a box: shift-right drops a
 * corner and the next shift-right fills the box between the two, shift-left does the same but takes
 * the box back out. Box mode is how a whole wing is marked in two clicks; the single-block versions
 * are how an irregular wing is trimmed afterwards. Nothing here assumes the selection is a box --
 * the fill skips anything that is not a block, so a box drawn around an irregular wing registers the
 * wing's own blocks and no others, and the erasures take it from there.
 *
 * <p>Every gesture is resolved on the server, from a click the engine already range-checked. The
 * click arrives in the level's own coordinates, which for a physics body is the body's plot space,
 * so the marks are stored as offsets from the generator in that same space and follow the hull
 * without anything being re-sent when it moves.
 *
 * <p>The marks are pushed to whoever is holding the wand, because the highlight is the only reason to
 * hold it. That is the same split the rest of the mod uses: the server owns the data and the client
 * owns the drawing. What triggers a push is the wand being in the player's hand -- a fact the server
 * can see -- and never a client-side display setting.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class VortexInteractions {
 /** How often the marks are re-sent while the wand is held, in ticks. */
 private static final int PUSH_INTERVAL=20;
 /** How far from a generator its marks are worth sending, in blocks of world space. */
 private static final double PUSH_RANGE=64.0;
 /** Ceiling on one box selection, so a mis-dragged corner cannot ask for millions of lookups. */
 private static final int MAX_FILL=32768;
 /** One player's dropped corner: the body, its plot-space position, and its position in the level. */
 private record Corner(UUID ship,BlockPos local,BlockPos level){}
 private static final Map<UUID,Corner> CORNERS=new HashMap<>();
 private VortexInteractions(){}
 /** One resolved click: the body it is on, the generator that owns it, and the mark's offset. */
 private record Click(SourceAnchor anchor,BlockPos local,BlockPos level,GeneratorServer.Found generator,BlockPos offset){}
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void rightClick(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  Player player=event.getEntity();
  if(!isWand(event.getItemStack()))return;
  Click click=resolve(level,player,event.getPos());
  if(click==null)return;
  if(click.generator()==null){say(player,"sablelightoverhaul.vortex.no_generator");return;}
  event.setUseBlock(TriState.FALSE);
  event.setUseItem(TriState.FALSE);
  GeneratorBlockEntity entity=click.generator().generator();
  if(player.isShiftKeyDown()){
   Corner corner=CORNERS.get(player.getUUID());
   if(corner==null||!corner.ship().equals(click.anchor().ship())){
    CORNERS.put(player.getUUID(),new Corner(click.anchor().ship(),click.local(),click.level()));
    say(player,"sablelightoverhaul.vortex.corner",click.local().toShortString());
   }else{
    CORNERS.remove(player.getUUID());
    fill(level,player,click.generator(),entity,corner.local(),click.local(),true);
   }
  }else{
   if(level.getBlockState(click.level()).isAir()){
    say(player,"sablelightoverhaul.vortex.air");
   }else if(entity.marks().add(click.offset().getX(),click.offset().getY(),click.offset().getZ())){
    entity.marksChanged();
    say(player,"sablelightoverhaul.vortex.added",click.offset().toShortString(),entity.marks().count());
   }else if(!entity.marks().accepts(click.offset().getX(),click.offset().getY(),click.offset().getZ())){
    say(player,"sablelightoverhaul.vortex.too_spread");
   }else{
    say(player,"sablelightoverhaul.vortex.already");
   }
  }
  push(player);
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void leftClick(PlayerInteractEvent.LeftClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  Player player=event.getEntity();
  // The left button is the eraser, so it must not also be a pickaxe while the wand is held.
  if(!isWand(event.getItemStack()))return;
  Click click=resolve(level,player,event.getPos());
  if(click==null)return;
  event.setUseBlock(TriState.FALSE);
  event.setUseItem(TriState.FALSE);
  if(click.generator()==null){say(player,"sablelightoverhaul.vortex.no_generator");return;}
  GeneratorBlockEntity entity=click.generator().generator();
  if(player.isShiftKeyDown()){
   Corner corner=CORNERS.get(player.getUUID());
   if(corner==null||!corner.ship().equals(click.anchor().ship())){
    CORNERS.put(player.getUUID(),new Corner(click.anchor().ship(),click.local(),click.level()));
    say(player,"sablelightoverhaul.vortex.corner",click.local().toShortString());
   }else{
    CORNERS.remove(player.getUUID());
    fill(level,player,click.generator(),entity,corner.local(),click.local(),false);
   }
  }else if(entity.marks().remove(click.offset().getX(),click.offset().getY(),click.offset().getZ())){
   entity.marksChanged();
   say(player,"sablelightoverhaul.vortex.removed",click.offset().toShortString(),entity.marks().count());
  }else{
   say(player,"sablelightoverhaul.vortex.not_marked");
  }
  push(player);
 }
 /** While the wand is held, keep the highlight's copy of the marks fresh. */
 @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
  if(!(event.getEntity() instanceof ServerPlayer player))return;
  if(player.level().isClientSide)return;
  if(!isWand(player.getMainHandItem())&&!isWand(player.getOffhandItem()))return;
  if(player.tickCount%PUSH_INTERVAL!=0)return;
  push(player);
 }
 /**
  * Send this player every nearby generator's marks, straight away.
  *
  * <p>Called after every gesture as well as on the timer, so a click shows up in the highlight in the
  * same frame instead of up to a second later.
  */
 public static void push(Player player){
  if(!(player instanceof ServerPlayer server))return;
  if(!(server.level() instanceof ServerLevel level))return;
  List<VortexMarksPayload.Mark> marks=new ArrayList<>();
  for(GeneratorServer.Found found:GeneratorServer.generators(level)){
   if(found.generator().kind()!=GeneratorKind.VORTEX)continue;
   if(found.generator().marks().empty())continue;
   BlockPos global=found.anchor().resolve(level);
   if(global==null)continue;
   Vec3 world=found.anchor().world(level,global,Vec3.atCenterOf(global));
   if(world==null||world.distanceToSqr(server.position())>PUSH_RANGE*PUSH_RANGE)continue;
   VortexMarks registered=found.generator().marks();
   marks.add(new VortexMarksPayload.Mark(found.anchor().ship(),global,
     registered.min(),registered.size(),registered.bits()));
  }
  Corner corner=CORNERS.get(server.getUUID());
  SloNetwork.sendToPlayer(server,new VortexMarksPayload(level.dimension().location(),List.copyOf(marks),
    corner!=null,corner==null?null:corner.ship(),corner==null?null:corner.level()));
 }
 /**
  * Register (or unregister) every block of a box.
  *
  * <p>Air is skipped, and so are our own generator and marker blocks. That skip is what makes an
  * irregular wing work: the player boxes the wing's envelope, and what gets registered is whatever
  * solid blocks the envelope actually contains.
  */
 private static void fill(ServerLevel level,Player player,GeneratorServer.Found found,
                          GeneratorBlockEntity entity,BlockPos a,BlockPos b,boolean add){
  BlockPos generator=found.anchor().pos();
  BlockPos origin=plotOrigin(level,found);
  if(origin==null){say(player,"sablelightoverhaul.vortex.unloaded");return;}
  int x0=Math.min(a.getX(),b.getX()),x1=Math.max(a.getX(),b.getX());
  int y0=Math.min(a.getY(),b.getY()),y1=Math.max(a.getY(),b.getY());
  int z0=Math.min(a.getZ(),b.getZ()),z1=Math.max(a.getZ(),b.getZ());
  long volume=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);
  if(volume>MAX_FILL){say(player,"sablelightoverhaul.vortex.too_big",MAX_FILL);return;}
  VortexMarks marks=entity.marks();
  int changed=0,skipped=0,refused=0;
  for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
   BlockPos offset=new BlockPos(x,y,z).subtract(generator);
   if(!add){
    if(marks.remove(offset.getX(),offset.getY(),offset.getZ()))changed++;
    continue;
   }
   BlockState state=level.getBlockState(origin.offset(x,y,z));
   if(state.isAir()||state.getBlock() instanceof GeneratorBlock||state.getBlock() instanceof MarkerBlock){skipped++;continue;}
   if(marks.add(offset.getX(),offset.getY(),offset.getZ()))changed++;
   else if(!marks.accepts(offset.getX(),offset.getY(),offset.getZ()))refused++;
  }
  if(changed>0)entity.marksChanged();
  if(add)say(player,"sablelightoverhaul.vortex.filled",changed,skipped,refused,marks.count());
  else say(player,"sablelightoverhaul.vortex.erased",changed,marks.count());
 }
 /** The level position that plot-space (0,0,0) answers to, so any plot position can be looked up. */
 private static BlockPos plotOrigin(ServerLevel level,GeneratorServer.Found found){
  BlockPos global=found.anchor().resolve(level);
  return global==null?null:global.offset(found.anchor().pos().multiply(-1));
 }
 /** The body at this click, the generator that owns it, and the offset the click stands for. */
 private static Click resolve(ServerLevel level,Player player,BlockPos pos){
  SourceAnchor anchor=SourceAnchor.capture(level,pos);
  if(anchor.ship()==null){
   say(player,"sablelightoverhaul.vortex.not_on_body");
   return null;
  }
  GeneratorServer.Found generator=nearestGenerator(level,anchor);
  return new Click(anchor,anchor.pos(),pos,generator,generator==null?null:anchor.pos().subtract(generator.anchor().pos()));
 }
 /** The registered vortex generator on this body that the click is closest to. */
 private static GeneratorServer.Found nearestGenerator(ServerLevel level,SourceAnchor from){
  GeneratorServer.Found best=null;
  double bestDistance=Double.MAX_VALUE;
  for(GeneratorServer.Found found:GeneratorServer.generators(level)){
   if(found.generator().kind()!=GeneratorKind.VORTEX)continue;
   if(found.anchor().ship()==null||!found.anchor().ship().equals(from.ship()))continue;
   double distance=found.anchor().pos().distSqr(from.pos());
   if(distance<bestDistance){bestDistance=distance;best=found;}
  }
  return best;
 }
 private static boolean isWand(ItemStack stack){return stack.is(LightingBlocks.VORTEX_WAND.get());}
 private static void say(Player player,String key,Object...args){
  if(player!=null)player.displayClientMessage(Component.translatable(key,args),true);
 }
}
