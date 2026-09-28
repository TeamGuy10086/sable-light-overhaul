package dev.cbcmuzzlelight.lighting;

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
public class DiscoBallBlockEntity extends BlockEntity {
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
 /** The three modes, in the order the page lists them. */
 public static final String[] MODES={"0 忽略红石","1 红石锁存","2 有信号时激活"};
 private int control=ALWAYS;
 /** Latched mode: the state the pulses have flipped to. */
 private boolean latched=true;
 /** The signal seen last client tick, which is what makes a rising edge detectable. */
 private transient int lastSignal;
 public DiscoBallBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.DISCO_BALL_ENTITY.get(),pos,state);}
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
   default->true;
  };
 }
 /** One numeric field by id, for the page. */
 public double number(String field){
  return switch(field){
   case "beams"->beams;case "spin"->spin;case "range"->range;case "width"->width;case "intensity"->intensity;
   case "falloff"->falloff;case "control"->control;case "latched"->latched?1:0;
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
   default->{return;}
  }
  sync();
 }
 public void setText(String field,String value){
  if(!"colors".equals(field))return;
  colors=value==null?"":value;
  sync();
 }
 /** Palette indices to cycle through, parsed once per use. */
 public int[] palette(){
  if(colors==null||colors.isBlank())return new int[]{0};
  String[] parts=colors.split(",");
  int[] values=new int[parts.length];
  int count=0;
  for(String part:parts){
   try{values[count++]=Math.max(0,Math.min(15,Integer.parseInt(part.trim())));}catch(NumberFormatException ignored){}
  }
  if(count==0)return new int[]{0};
  int[] result=new int[count];
  System.arraycopy(values,0,result,0,count);
  return result;
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
 }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
  CompoundTag tag=new CompoundTag();
  saveAdditional(tag,registries);
  return tag;
 }
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
