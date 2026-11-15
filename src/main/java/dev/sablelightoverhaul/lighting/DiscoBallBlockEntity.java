package dev.sablelightoverhaul.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The disco ball's settings, stored on the ball itself.
 *
 * <p>They live here rather than in a client config so that everyone sees the same show: the values are
 * taken from the server's configuration when the ball is placed, edits from the page are written here,
 * and the block entity sends them to every client that can see it. Each ball can therefore be its own
 * colour and speed, and two players standing next to each other are never watching two different shows.
 */
public class DiscoBallBlockEntity extends BlockEntity
  implements dev.sablelightoverhaul.lighting.channel.ChannelInteractions.ChannelDevice, BlockOwners.Owned {
 /** The player who placed this ball, or null for a legacy block. */
 private java.util.UUID owner;
 @Override public java.util.UUID owner(){return owner;}
 @Override public void setOwner(java.util.UUID id){owner=id;setChanged();}
 private int beams=LightingCommon.DISCO_BEAMS.get();
 private double spin=LightingCommon.DISCO_SPIN.get();
 private int range=LightingCommon.DISCO_RANGE.get();
 private int width=LightingCommon.DISCO_WIDTH.get();
 private double intensity=LightingCommon.DISCO_INTENSITY.get();
 /** The spot's falloff profile, the same five the spotlights have; the page and the light both read it. */
 private int falloff=LightingCommon.DISCO_FALLOFF.get();
 private String colors=LightingCommon.DISCO_COLORS.get();
 /**
  * How redstone drives the show: {@link #ALWAYS} ignores it, {@link #LATCHED} flips a switch on each
  * pulse, {@link #POWERED} runs only while there is a signal.
  */
 public static final int ALWAYS=0,LATCHED=1,POWERED=2;
 /**
  * An AFN channel runs the show.
  *
  * <p>AFN channels hold their value rather than pulsing it, so this is the held behaviour and never an
  * edge: the ball runs while the channel is on. The channel can also set the spin, the ray width, the
  * colours and the rest -- see {@link dev.sablelightoverhaul.lighting.channel.DiscoChannel} -- and it does
  * that on the server, writing the ball's own fields, so every client sees the same show.
  */
 public static final int CHANNEL=3;
 /** The modes, in the order the page lists them. */
 public static final String[] MODES={"0 忽略红石","1 红石锁存","2 有信号时激活","3 AFN信道"};
 private int control=ALWAYS;
 /** Latched mode: the state the pulses have flipped to. */
 private boolean latched=true;
 /** The signal seen last client tick, which is what makes a rising edge detectable. */
 private transient int lastSignal;
 /** The AFN channel this ball follows, if a board has been installed. */
 private final dev.sablelightoverhaul.lighting.channel.ChannelBinding channel=
   dev.sablelightoverhaul.lighting.channel.ChannelBindings.create();
 /** What the channel is asking for. Synced, because the show is drawn on the client. */
 private boolean channelOn;
 public DiscoBallBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.DISCO_BALL_ENTITY.get(),pos,state);}
 /** The channel link, for the gesture handler. */
 public dev.sablelightoverhaul.lighting.channel.ChannelBinding channel(){return channel;}
 /** Whether the channel is asking for the show right now. */
 public boolean channelOn(){return channelOn;}
 public int beams(){return beams;}
 public double spin(){return spin;}
 public int range(){return range;}
 public int width(){return width;}
 public double intensity(){return intensity;}
 public int falloff(){return falloff;}
 public int control(){return control;}
 public boolean latched(){return latched;}
 /**
  * Whether the show is running right now, given the redstone signal entering the ball.
  *
  * <p>Decided on the client, because the whole show is: the ball's settings are already on the block
  * entity and reach every client, and reading a redstone signal is a plain level query. A latched ball
  * flips on a rising edge only, so holding a lever up does not make it flicker; that edge is tracked
  * per client, which is harmless because it is derived from the same synced signal.
  */
 public boolean poweredOn(int signal){
  signal=Math.max(0,Math.min(15,signal));
  return switch(control){
   case LATCHED->{
    if(signal>0&&lastSignal==0)latched=!latched;
    lastSignal=signal;
    yield latched;
   }
   case POWERED->{
    lastSignal=signal;
    yield signal>0;
   }
   // Held, not edged: an AFN channel is a lever, not a button, so nothing here watches for a rise.
   case CHANNEL->channelOn;
   default->true;
  };
 }
 /** One server tick: follow the channel, if one is installed. */
 public static net.minecraft.world.level.block.entity.BlockEntityTicker<DiscoBallBlockEntity> ticker(){
  return (level,pos,state,ball)->ball.tick(level,pos);
 }
 void tick(net.minecraft.world.level.Level level,BlockPos pos){
  if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;
  if(!channel.bound())return;
  channel.tick(server,pos);
  applyChannel();
 }
 /**
  * Take the channel's latest command.
  *
  * <p>Only what the message mentions is written, and nothing is sent unless something moved: a channel
  * that repeats the same command every tick must not become a packet every tick. Everything written
  * here travels to the clients the usual way, so the show is the same for everyone watching.
  */
 private void applyChannel(){
  dev.sablelightoverhaul.lighting.channel.ChannelSignal signal=channel.signal();
  if(!signal.present()&&!signal.powered())return;
  dev.sablelightoverhaul.lighting.channel.DiscoChannel asked=
    dev.sablelightoverhaul.lighting.channel.DiscoChannel.decode(signal);
  boolean changed=false;
  if(channelOn!=asked.on()){channelOn=asked.on();changed=true;}
  if(asked.spin()!=null&&differs(asked.spin(),spin)){spin=clamp(asked.spin(),0.0,90.0);changed=true;}
  if(asked.width()!=null&&differs(asked.width(),width)){width=(int)clamp(asked.width(),0,15);changed=true;}
  if(asked.intensity()!=null&&differs(asked.intensity(),intensity)){intensity=clamp(asked.intensity(),0.0,15.0);changed=true;}
  if(asked.range()!=null&&differs(asked.range(),range)){range=(int)clamp(asked.range(),1,128);changed=true;}
  if(asked.beams()!=null&&differs(asked.beams(),beams)){beams=(int)clamp(asked.beams(),1,32);changed=true;}
  if(asked.falloff()!=null&&differs(asked.falloff(),falloff)){falloff=(int)clamp(asked.falloff(),0,SpotlightBlock.MODES.length-1);changed=true;}
  if(asked.colors().length>0){
   String joined=join(asked.colors());
   if(!joined.equals(colors)){colors=joined;changed=true;}
  }
  if(changed)sync();
 }
 private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
 private static boolean differs(double a,double b){return Math.abs(a-b)>1.0E-4;}
 /** The channel's colour list in the same syntax the ball's own page writes. */
 private static String join(int[] colors){
  StringBuilder out=new StringBuilder();
  for(int colour:colors){
   if(out.length()>0)out.append(',');
   // A hand-typed colour is written back as the hex the page reads, not as its packed int: the string is what
   // the page and the channel both show, and a packed int there would be unreadable.
   out.append(SourceColor.isCustom(colour)?SourceColor.hex(colour):Integer.toString(colour));
  }
  return out.toString();
 }
 /** One numeric field by id, for the page. */
 public double number(String field){
  return switch(field){
   case "beams"->beams;case "spin"->spin;case "range"->range;case "width"->width;case "intensity"->intensity;
   case "falloff"->falloff;case "control"->control;case "latched"->latched?1:0;
   case "channel"->channel.bound()?1:0;
   default->0.0;
  };
 }
 /** One text field by id, for the page. */
 public String text(String field){return "colors".equals(field)?(colors==null?"":colors):"";}
 /** A write from the page: unknown ids and out-of-range values are clamped or ignored, never stored raw. */
 public void setNumber(String field,double value){
  switch(field){
   case "beams"->beams=(int)Math.max(1,Math.min(32,Math.round(value)));
   case "spin"->spin=Math.max(0.0,Math.min(90.0,value));
   case "range"->range=(int)Math.max(1,Math.min(128,Math.round(value)));
   case "width"->width=(int)Math.max(0,Math.min(15,Math.round(value)));
   case "intensity"->intensity=Math.max(0.0,Math.min(15.0,value));
   case "falloff"->falloff=(int)Math.max(0,Math.min(SpotlightBlock.MODES.length-1,Math.round(value)));
   case "control"->{control=(int)Math.max(0,Math.min(MODES.length-1,Math.round(value)));latched=true;}
   case "latched"->latched=value!=0;
   // A board is installed by right-clicking with it; this row can only take it away again.
   case "channel"->{if(value==0)channel.clear();}
   default->{return;}
  }
  sync();
 }
 public void setText(String field,String value){
  if(!"colors".equals(field))return;
  colors=value==null?"":value;
  sync();
 }
 /**
  * The colours to cycle through, parsed once per use.
  *
  * <p>Through {@link SourceColor#parseList}, the same parser the page's client fallback and a channel's
  * message use. This method used to accept palette indices only, so a hex colour typed into the page's own
  * colour box was dropped on the way to the light -- the box looked broken while the text was stored fine.
  */
 public int[] palette(){
  return SourceColor.parseList(colors);
 }
 /** Re-reads the server's defaults; used when a ball is placed, so tuning the config applies to new ones. */
 public void useConfiguredDefaults(){
  beams=LightingCommon.DISCO_BEAMS.get();
  spin=LightingCommon.DISCO_SPIN.get();
  range=LightingCommon.DISCO_RANGE.get();
  width=LightingCommon.DISCO_WIDTH.get();
  intensity=LightingCommon.DISCO_INTENSITY.get();
  falloff=LightingCommon.DISCO_FALLOFF.get();
  colors=LightingCommon.DISCO_COLORS.get();
  control=ALWAYS;latched=true;
  sync();
 }
 public void sync(){
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.saveAdditional(tag,registries);
  tag.putInt("beams",beams);tag.putDouble("spin",spin);tag.putInt("range",range);
  tag.putInt("width",width);tag.putDouble("intensity",intensity);tag.putInt("falloff",falloff);
  tag.putInt("control",control);tag.putBoolean("latched",latched);
  tag.putString("colors",colors==null?"":colors);
  channel.save(tag,registries);
  tag.putBoolean("channelOn",channelOn);
  BlockOwners.save(tag,owner);
 }
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.loadAdditional(tag,registries);
  if(tag.contains("beams"))beams=tag.getInt("beams");
  if(tag.contains("spin"))spin=tag.getDouble("spin");
  if(tag.contains("range"))range=tag.getInt("range");
  if(tag.contains("width"))width=tag.getInt("width");
  if(tag.contains("intensity"))intensity=tag.getDouble("intensity");
  if(tag.contains("falloff"))falloff=tag.getInt("falloff");
  if(tag.contains("control"))control=Math.max(0,Math.min(MODES.length-1,tag.getInt("control")));
  if(tag.contains("latched"))latched=tag.getBoolean("latched");
  if(tag.contains("colors"))colors=tag.getString("colors");
  channel.load(tag,registries);
  if(tag.contains("channelOn"))channelOn=tag.getBoolean("channelOn");
  owner=BlockOwners.load(tag);
 }
 /** The live channel belongs to the level's thread, so it is let go with the block entity. */
 @Override public void setRemoved(){
  channel.close();
  super.setRemoved();
 }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
  CompoundTag tag=new CompoundTag();
  saveAdditional(tag,registries);
  return tag;
 }
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
