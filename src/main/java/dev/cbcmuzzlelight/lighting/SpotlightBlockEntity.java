package dev.cbcmuzzlelight.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A spotlight's settings, and the one place that decides what its redstone input means.
 *
 * <p>The lamp used to be nothing but a block state, which was enough while redstone was the only thing
 * that could change it. It now has three control modes, a switch that can be latched, an aim with
 * limits and an offset, and every one of those has to persist and reach every client, so it lives here.
 *
 * <p>Range, falloff profile and model visibility deliberately stay in the block state: the server
 * already reads the first two out of it for the beam payload, the render shape reads the third, and
 * putting them here as well would mean two copies of the same value. This class just reads and writes
 * the block state for those three fields.
 *
 * <p>The latch is the reason this block entity ticks. Vanilla hands a block's redstone input over in
 * whole-tick steps and a button's pulse is a single tick, so a latch that samples the signal on a slow
 * schedule would swallow some presses. One signal read per lamp per tick is nothing next to that.
 */
public class SpotlightBlockEntity extends BlockEntity {
 /** Redstone means what it always did: bottom face = intensity, top = width, sides = colour. */
 public static final int FULL=0;
 /** Redstone only switches the lamp; whether a level or an edge does the switching is {@link #latch}. */
 public static final int SEMI=1;
 /** Redstone is ignored; the page decides. */
 public static final int MANUAL=2;
 /** Everything the page owns, with the range each field is clamped to. */
 public static final String[] FIELDS={"control","latch","switch","intensity","width","color","angle","azimuth","sweepBase","sweepRange","range","falloff","visible"};
 private int control=FULL;
 /** Semi mode: a rising edge flips the switch instead of the signal level setting it. */
 private boolean latch;
 /** The page's switch: the master gate in full mode, the authority in manual mode. */
 private boolean switchOn=true;
 /** Semi mode with a latch: the state the edges have flipped to. */
 private boolean latchedOn;
 private int intensity=15,width=8,color=0;
 /**
  * Where the cone points: {@code angle} is its tilt away from the block's own up, {@code azimuth} which
  * way it leans. The pair is what the lamp aims with; the cone's <i>opening</i> is {@link #width} and is
  * a different thing entirely.
  *
  * <p>A redstone input sweeps the tilt, and the sweep is described by where it starts and how far it
  * goes: {@code sweepBase} plus up to {@code sweepRange} degrees. That replaces a lower limit, an upper
  * limit and an offset, which could be combined into the same angles in several ways and never made it
  * clear which of the three "the angle" actually was.
  */
 private double angle=0,azimuth=0,sweepBase=0,sweepRange=60;
 /** The redstone input seen last tick, which is what makes a rising edge detectable at all. */
 private int lastSignal;
 public SpotlightBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.SPOTLIGHT_ENTITY.get(),pos,state);}
 // ---- what the lamp is set to -------------------------------------------------------------------
 public int control(){return control;}
 public boolean latch(){return latch;}
 public boolean switchOn(){return switchOn;}
 public boolean latchedOn(){return latchedOn;}
 public int intensity(){return intensity;}
 public int width(){return width;}
 public int color(){return color;}
 public double angle(){return angle;}
 public double azimuth(){return azimuth;}
 /**
  * The tilt a redstone input of this strength asks for, in degrees away from the block's own up.
  *
  * <p>The signal scales the sweep <b>proportionally</b>: the answer is
  * {@code sweepBase + (signal / 15) * sweepRange}, so no signal is exactly the base angle and a full
  * signal is exactly the base angle plus the full range, with everything between spread evenly over the
  * fifteen steps. {@code signal / 15} rather than {@code (signal - 1) / 14} is what makes the two ends
  * land on the two numbers the page shows. The result is clamped to what a lamp can actually do.
  */
 public double sweep(int signal){
  double t=Math.max(0,Math.min(15,signal))/15.0;
  return Math.max(-90,Math.min(90,sweepBase+sweepRange*t));
 }
 /**
  * A direction in the block's own space: straight up, tilted this many degrees towards this azimuth.
  * The client turns it into a world direction, so a lamp on a moving body keeps aiming with the body.
  */
 public static Vec3 aim(double angle,double azimuth){
  double tilt=Math.toRadians(angle),spin=Math.toRadians(azimuth);
  double lean=Math.sin(tilt);
  return new Vec3(Math.cos(spin)*lean,Math.cos(tilt),Math.sin(spin)*lean);
 }
 // ---- ticker: only the latch needs one ----------------------------------------------------------
 public static BlockEntityTicker<SpotlightBlockEntity> ticker(){
  return (level,pos,state,lamp)->lamp.tick(level,pos);
 }
 private void tick(Level level,BlockPos pos){
  if(!(level instanceof ServerLevel server))return;
  // Only a latched lamp looks at redstone every tick. Everything else is answered from the five-tick
  // scan, so a lamp that does not use the latch costs nothing at all.
  if(control!=SEMI||!latch)return;
  int signal=anySignal(server,pos);
  if(signal>0&&lastSignal==0){latchedOn=!latchedOn;sync();}
  lastSignal=signal;
 }
 /**
  * Re-reads the input without acting on it.
  *
  * <p>Switching the latch on while a lever is already up would otherwise look exactly like a rising
  * edge and flip the lamp by itself, and so would loading a chunk with the lever up. Priming the
  * remembered input is what makes the latch mean "one flip per press" rather than "one flip per load".
  */
 private void arm(){
  if(level instanceof ServerLevel server)lastSignal=anySignal(server,worldPosition);
 }
 /** The strongest signal entering the lamp through any face; the switch is switched by any of them. */
 public static int anySignal(ServerLevel level,BlockPos pos){
  int strongest=0;
  for(Direction side:Direction.values()){
   // Vanilla signal queries may reach two blocks out; never let one load a chunk.
   if(!level.hasChunkAt(pos.relative(side)))continue;
   strongest=Math.max(strongest,level.getSignal(pos.relative(side),side));
  }
  return strongest;
 }
 // ---- the page's view and its writes --------------------------------------------------------------
 /** One field by id, for the page. Range, falloff and visibility are answered from the block state. */
 public double number(String field){
  BlockState state=getBlockState();
  return switch(field){
   case "control"->control;
   case "latch"->latch?1:0;
   case "switch"->switchOn?1:0;
   case "intensity"->intensity;
   case "width"->width;
   case "color"->color;
   case "angle"->angle;
   case "azimuth"->azimuth;
   case "sweepBase"->sweepBase;
   case "sweepRange"->sweepRange;
   case "range"->state.getValue(SpotlightBlock.RANGE);
   case "falloff"->state.getValue(SpotlightBlock.MODE);
   case "visible"->state.getValue(SpotlightBlock.VISIBLE)?1:0;
   default->0;
  };
 }
 /** A write from the page: unknown ids are ignored and every value is clamped, never stored raw. */
 public void setNumber(String field,double value){
  switch(field){
   case "control"->control=clampInt(value,0,2);
   case "latch"->latch=value!=0;
   case "switch"->switchOn=value!=0;
   case "intensity"->intensity=clampInt(value,0,15);
   case "width"->width=clampInt(value,1,15);
   case "color"->color=clampInt(value,0,15);
   case "angle"->angle=clampAngle(value);
   case "azimuth"->azimuth=wrapDegrees(value);
   case "sweepBase"->sweepBase=clampAngle(value);
   case "sweepRange"->sweepRange=clampAngle(value);
   // The three block state fields go back to the block state: one value, one home.
   case "range"->setBlockStateField(SpotlightBlock.RANGE,clampInt(value,1,LightingCommon.count()));
   case "falloff"->setBlockStateField(SpotlightBlock.MODE,clampInt(value,0,SpotlightBlock.MODES.length-1));
   case "visible"->setBlockStateField(SpotlightBlock.VISIBLE,value!=0?1:0);
   default->{return;}
  }
  // Any write can change which mode is in charge, so the remembered input is refreshed: that is what
  // stops a mode change from being read as a redstone edge on the next tick.
  arm();
  sync();
 }
 /** Writes one of the three block state fields, which the block treats as its own. */
 private void setBlockStateField(net.minecraft.world.level.block.state.properties.Property<?> property,int value){
  if(level==null||level.isClientSide)return;
  BlockState state=getBlockState();
  if(property==SpotlightBlock.RANGE)state=state.setValue(SpotlightBlock.RANGE,value);
  else if(property==SpotlightBlock.MODE)state=state.setValue(SpotlightBlock.MODE,value);
  else state=state.setValue(SpotlightBlock.VISIBLE,value!=0);
  level.setBlock(worldPosition,state,3);
 }
 /** The aim this lamp is currently casting, whatever is driving it. */
 public double currentAngle(int signal){
  return switch(control){
   case FULL->sweep(signal);
   case SEMI,MANUAL->angle;
   default->angle;
  };
 }
 private static int clampInt(double value,int low,int high){return (int)Math.max(low,Math.min(high,Math.round(value)));}
 private static double clampAngle(double value){return Math.max(-90,Math.min(90,value));}
 private static double wrapDegrees(double value){return Mth.wrapDegrees(value);}
 public void sync(){
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.saveAdditional(tag,registries);
  tag.putInt("control",control);tag.putBoolean("latch",latch);tag.putBoolean("switch",switchOn);tag.putBoolean("latched",latchedOn);
  tag.putInt("intensity",intensity);tag.putInt("width",width);tag.putInt("color",color);
  tag.putDouble("angle",angle);tag.putDouble("azimuth",azimuth);
  tag.putDouble("sweepBase",sweepBase);tag.putDouble("sweepRange",sweepRange);
  // The remembered input is part of the latch, not a setting: without it a reload would look like an
  // edge and flip every latched lamp whose lever happens to be up.
  tag.putInt("lastSignal",lastSignal);
 }
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.loadAdditional(tag,registries);
  if(tag.contains("control"))control=clampInt(tag.getInt("control"),0,2);
  if(tag.contains("latch"))latch=tag.getBoolean("latch");
  if(tag.contains("switch"))switchOn=tag.getBoolean("switch");
  if(tag.contains("latched"))latchedOn=tag.getBoolean("latched");
  if(tag.contains("intensity"))intensity=clampInt(tag.getInt("intensity"),0,15);
  if(tag.contains("width"))width=clampInt(tag.getInt("width"),1,15);
  if(tag.contains("color"))color=clampInt(tag.getInt("color"),0,15);
  if(tag.contains("angle"))angle=clampAngle(tag.getDouble("angle"));
  if(tag.contains("azimuth"))azimuth=wrapDegrees(tag.getDouble("azimuth"));
  if(tag.contains("sweepBase"))sweepBase=clampAngle(tag.getDouble("sweepBase"));
  if(tag.contains("sweepRange"))sweepRange=clampAngle(tag.getDouble("sweepRange"));
  if(tag.contains("lastSignal"))lastSignal=clampInt(tag.getInt("lastSignal"),0,15);
 }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
  CompoundTag tag=new CompoundTag();
  saveAdditional(tag,registries);
  return tag;
 }
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
