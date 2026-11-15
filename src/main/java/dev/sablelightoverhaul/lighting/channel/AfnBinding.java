package dev.sablelightoverhaul.lighting.channel;

import com.createafn.api.channel.AFNChannelApi;
import com.createafn.api.channel.ChannelAccess;
import com.createafn.api.channel.ChannelEndpoint;
import com.createafn.api.channel.ChannelFrequencyPair;
import com.createafn.api.channel.ChannelIdentity;
import com.createafn.api.channel.ChannelMode;
import com.createafn.api.channel.ChannelReceivedValue;
import com.createafn.api.channel.ChannelReception;
import com.createafn.api.channel.ChannelValue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * The AFN implementation of a block's channel link. Every AFN type this mod touches lives here.
 *
 * <p>Two boards make a channel, exactly as they do for the mod's own devices, so a player sets one up
 * the same way they set anything else on the network up: right-click the block with a board, and use
 * a second board for the pair's other half if the channel needs one.
 *
 * <p><b>The boards are the only thing stored.</b> The live channel is this mod's own object, made from
 * them on the server thread and never written to disk, so a world that loses AFN keeps its boards and
 * picks the channel back up if the mod returns. Nothing here is created during loading: AFN's own
 * contract says an endpoint belongs to the thread that owns the level, and a block entity can be
 * loaded off it, so the endpoint is created on the first tick instead.
 *
 * <p>AFN channels hold their value. That is why this class hands out {@link ChannelSignal} and not
 * edges: a device that follows a channel is on while the channel is on, and the rising-edge semantics
 * this mod's static sources can use for redstone deliberately have no place here.
 */
public final class AfnBinding implements ChannelBinding {
 private static final String TAG_FIRST="AfnBoardA",TAG_SECOND="AfnBoardB",TAG_DEVICE="AfnDevice",TAG_CHANNEL="AfnChannel";
 private ItemStack first=ItemStack.EMPTY,second=ItemStack.EMPTY;
 private UUID deviceId,channelId;
 private ChannelEndpoint endpoint;
 private ServerLevel level;
 private BlockPos pos;
 private ChannelSignal latest=ChannelSignal.NOTHING;
 @Override public boolean bound(){return !first.isEmpty()||!second.isEmpty();}
 @Override public boolean available(){return true;}
 @Override public boolean configured(){
  if(!bound())return false;
  if(endpoint!=null&&!endpoint.closed())return endpoint.configured();
  return AFNChannelApi.isUsable(pair());
 }
 @Override public ChannelSignal signal(){return latest;}
 @Override public boolean hasValue(){return latest.present();}
 @Override public void tick(ServerLevel tickLevel,BlockPos tickPos){
  if(!bound()){close();return;}
  if(endpoint==null||endpoint.closed()||level!=tickLevel||!tickPos.equals(pos)){
   close();
   level=tickLevel;
   pos=tickPos.immutable();
   if(deviceId==null||channelId==null){deviceId=UUID.randomUUID();channelId=UUID.randomUUID();}
   endpoint=AFNChannelApi.createEndpoint(level,pos,pair(),ChannelMode.RECEIVE,new ChannelIdentity(deviceId,channelId));
   endpoint.attach();
   latest=ChannelSignal.NOTHING;
  }
  latest=convert(endpoint.reception());
 }
 @Override public BindResult bind(ItemStack stack,UUID playerId){
  if(stack==null||stack.isEmpty()||!isBoard(stack))return BindResult.NOT_A_BOARD;
  // With both slots taken the second one is replaced: a player swapping the pair's other half should
  // not have to clear the channel and start again.
  boolean toFirst=first.isEmpty();
  // Asked before it is installed: a board can carry a read list, and a device that cannot read the
  // channel would look bound and simply never work.
  ChannelFrequencyPair candidate=toFirst?ChannelFrequencyPair.of(stack,second):ChannelFrequencyPair.of(first,stack);
  if(playerId!=null){
   ChannelAccess access=AFNChannelApi.accessFor(candidate,playerId);
   if(!access.canRead())return BindResult.NO_READ_ACCESS;
  }
  ItemStack copy=stack.copy();
  copy.setCount(1);
  if(toFirst)first=copy;else second=copy;
  close();
  return toFirst?BindResult.FIRST_SLOT:BindResult.SECOND_SLOT;
 }
 @Override public void clear(){
  close();
  first=ItemStack.EMPTY;
  second=ItemStack.EMPTY;
 }
 @Override public void close(){
  if(endpoint!=null){
   if(!endpoint.closed())endpoint.close();
   endpoint=null;
  }
  latest=ChannelSignal.NOTHING;
 }
 @Override public void save(CompoundTag tag,HolderLookup.Provider registries){
  if(!first.isEmpty())tag.put(TAG_FIRST,first.save(registries));
  if(!second.isEmpty())tag.put(TAG_SECOND,second.save(registries));
  if(deviceId!=null){
   tag.putUUID(TAG_DEVICE,deviceId);
   tag.putUUID(TAG_CHANNEL,channelId);
  }
 }
 @Override public void load(CompoundTag tag,HolderLookup.Provider registries){
  close();
  first=tag.contains(TAG_FIRST)?ItemStack.parse(registries,tag.getCompound(TAG_FIRST)).orElse(ItemStack.EMPTY):ItemStack.EMPTY;
  second=tag.contains(TAG_SECOND)?ItemStack.parse(registries,tag.getCompound(TAG_SECOND)).orElse(ItemStack.EMPTY):ItemStack.EMPTY;
  deviceId=tag.contains(TAG_DEVICE)?tag.getUUID(TAG_DEVICE):null;
  channelId=tag.contains(TAG_CHANNEL)?tag.getUUID(TAG_CHANNEL):null;
 }
 @Override public String describe(){
  if(!bound())return "no board";
  return (first.isEmpty()?"-":first.getHoverName().getString())
    +" / "+(second.isEmpty()?"-":second.getHoverName().getString())
    +(configured()?" (live)":" (not a live channel)");
 }
 private ChannelFrequencyPair pair(){return ChannelFrequencyPair.of(first,second);}
 /** Whether an item is one of AFN's frequency boards. */
 public static boolean isBoard(ItemStack stack){return AFNChannelApi.isFrequencyBoard(stack);}
 /**
  * AFN's value model turned into this mod's.
  *
  * <p>A real value keeps its components as they are and its text projection beside them, so a device
  * that wants colours can read them out of the same message a numeric device ignores.
  */
 private static ChannelSignal convert(ChannelReception reception){
  ChannelValue value=reception.value().map(ChannelReceivedValue::value).orElse(null);
  if(value instanceof ChannelValue.Bool bool){
   return new ChannelSignal(reception.strength(),ChannelSignal.Kind.BOOL,bool.value(),new double[0],"");
  }
  if(value instanceof ChannelValue.Real real){
   double[] components=real.components().stream().mapToDouble(Double::doubleValue).toArray();
   return new ChannelSignal(reception.strength(),ChannelSignal.Kind.REAL,false,components,ChannelValue.asText(real));
  }
  if(value instanceof ChannelValue.Text text){
   return new ChannelSignal(reception.strength(),ChannelSignal.Kind.TEXT,false,new double[0],text.value());
  }
  return new ChannelSignal(reception.strength(),ChannelSignal.Kind.NONE,false,new double[0],"");
 }
}
