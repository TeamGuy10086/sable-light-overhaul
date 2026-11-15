package dev.sablelightoverhaul.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Remembers the block a stand-in replaced, so the renderer can draw that block and the block can be
 * put back exactly as it was, plus the redstone answer that decides whether it lights at all.
 *
 * <p>The state is sent to the client with the block update, because the client has to draw a model
 * that depends on it. The redstone fields ride along in the same tag, which is why the page can read
 * them on both sides without a payload of their own.
 *
 * <p><b>Brightness while off.</b> A stand-in's brightness is its block state's {@code EMISSION}, because
 * that is what the server's light pass reads. Switching the lamp off therefore means writing 0 there --
 * which would throw the configured level away, so the level itself is kept here as {@link #litEmission}
 * and written back when the lamp comes on. The page writes both, see
 * {@link LightSourceServer#setNumber}.
 *
 * <p><b>What can switch it.</b> Two sources and three ways of using one signal: the sources are the
 * block's own redstone and a bound AFN channel ({@link #source}), and the ways are {@link #IGNORE},
 * {@link #LEVEL} and {@link #EDGE}. Keeping those apart is the whole point: a channel is a source of
 * redstone signal, not a mode, so it feeds the same three answers and an edge is an edge whoever sent
 * it. (The spotlight and the disco ball are different: they use a channel's typed value to drive their
 * parameters, which is that device's own business and deliberately not touched by this model.)
 */
public class LightSourceBlockEntity extends BlockEntity
  implements dev.sablelightoverhaul.lighting.channel.ChannelInteractions.ChannelDevice, BlockOwners.Owned {
 /** The player who placed this device, or null for a legacy block (and for one placed by a machine). */
 private java.util.UUID owner;
 @Override public java.util.UUID owner(){return owner;}
 @Override public void setOwner(java.util.UUID id){owner=id;setChanged();}
 /** Redstone is ignored: the lamp is on whenever it is registered. The default, so nothing changes for
  *  the lamps that were placed before this existed. */
 public static final int IGNORE=0;
 /** The signal level is the answer: powered is on, unpowered is off. */
 public static final int LEVEL=1;
 /** A rising edge flips the lamp: a button toggles it, a lever's own edges toggle it too. */
 public static final int EDGE=2;
 /**
 * How the signal is used. **Where it comes from is a separate question** -- see {@link #source}.
 *
 * <p>AFN is a source of redstone signal, not a fourth way of using one. This lamp used to carry an
 * "AFN channel" mode, which meant a bound board switched the lamp and the three modes above were
 * bypassed; that is the wrong shape. A board's channel is a signal like any other, so it feeds the same
 * three modes, and every one of them works with it -- including {@link #EDGE}, because a channel that
 * holds its value still changes it, and an edge is observable as long as the previous sample is
 * remembered (which is exactly what {@link #lastSignal} is for).
 */
 public static final String[] CONTROLS={"忽略红石","信号使能","上升沿切换"};
 /** The AFN channel this lamp follows, if a board has been installed. */
 private final dev.sablelightoverhaul.lighting.channel.ChannelBinding channel=
   dev.sablelightoverhaul.lighting.channel.ChannelBindings.create();
 /** The level the bound channel last held, 0..15; the signal while it is what the lamp follows. */
 private int channelSignal;
 /** Whether the channel was bound last tick, so a board appearing or going does not look like an edge. */
 private boolean lastBound;
 private BlockState original;
 private int control=IGNORE;
 /** EDGE mode's state. It starts lit, so a lamp with no wiring behaves exactly as it did before. */
 private boolean latchedOn=true;
 /** The signal seen last tick, which is what makes a rising edge detectable at all. */
 private int lastSignal;
 /** What the lamp lights with while it is on. Zero only if a page somehow asked for it. */
  /**
   * The blink pattern this lamp was given: comma separated on/off durations in ticks, empty for a steady
   * lamp. It lives on the lamp rather than in the registry because it is the lamp's own presentation --
   * the client runs it, the server only carries the text.
   */
  private String blink="";
 private PointLightAppearance appearance=PointLightAppearance.DEFAULT;
 public PointLightAppearance appearance(){return appearance;}
 public void setAppearance(String field,double value){
  appearance=appearance.with(field,value);
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 private int litEmission=LightSourceServer.MAX_EMISSION;
 public LightSourceBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.LIGHT_SOURCE_ENTITY.get(),pos,state);}
 /** The block this stand-in speaks for, or null when it was never set (nothing is drawn then). */
 public BlockState originalState(){return original;}
 public void setOriginal(BlockState state){
  original=state;
  // A lamp that was taken over adopted the level it used to emit, and that level is now this stand-in's
  // own. Reading it back here is what stops the first tick from "correcting" it to the default.
  adoptEmission();
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 public int control(){return control;}
 public boolean latchedOn(){return latchedOn;}
 public int litEmission(){return litEmission;}
 /** One write from the page, already range-checked there. */
 public void setControl(int mode){
  if(mode<0||mode>=CONTROLS.length||control==mode)return;
  control=mode;
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 /** The page changed the brightness: it becomes both the on level and the level in the state. */
 public void setLitEmission(int emission){
  if(emission<LightSourceServer.MIN_EMISSION||emission>LightSourceServer.MAX_EMISSION)return;
  litEmission=emission;
  setChanged();
 }
 private void adoptEmission(){
  if(getBlockState().getBlock() instanceof LightSourceBlock){
   int state=getBlockState().getValue(LightSourceBlock.EMISSION);
   if(state>0)litEmission=state;
  }
 }
 // ---- the redstone answer, as pure arithmetic so it can be tested without a world -----------------
 /** Whether a lamp with this mode, latch and input is lit. */
 public static boolean lit(int control,boolean latchedOn,int signal){
  return switch(control){
   case LEVEL->signal>0;
   case EDGE->latchedOn;
   default->true;
  };
 }
 /**
  * The latch after sampling {@code signal}, having seen {@code lastSignal} the tick before.
  *
  * <p>Only a rising edge counts, and only one: a held lever leaves the lamp where the edge put it, and a
  * button's single-tick pulse is enough to flip it, which is why this is sampled every tick.
  */
 public static boolean flipped(boolean latchedOn,int signal,int lastSignal){
  return signal>0&&lastSignal<=0?!latchedOn:latchedOn;
 }
 /**
  * The signal this lamp follows: the bound channel's level, or the block's own redstone.
  *
  * <p>A bound board wins rather than the two being combined, because the page already reports which one
  * is in charge (the channel row) and "either can turn it on" is a rule nobody can see from the outside.
  */
 public static int source(boolean channelBound,int channelStrength,int redstone){
  return channelBound?channelStrength:redstone;
 }
 /**
  * A control value read from a world written before the channel became a signal source.
  *
  * <p>The retired mode 3 was "on while the channel is on", which is now {@link #LEVEL} with the channel
  * as its source -- the same behaviour, expressed the right way round. A lamp that was in that mode
  * without a board was dark and now follows its redstone, which is the one behaviour this changes.
  */
 public static int migrateControl(int stored){
  return stored==3?LEVEL:Math.max(0,Math.min(CONTROLS.length-1,stored));
 }
 /**
  * Whether this lamp is lit right now, whatever is driving it.
  *
  * <p>One place, so the ticker and the brightness write cannot drift apart.
  */
 public static boolean on(LightSourceBlockEntity standIn,int signal){
  return lit(standIn.control,standIn.latchedOn,signal);
 }
 /** The channel link, for the gesture handler. */
 public dev.sablelightoverhaul.lighting.channel.ChannelBinding channel(){return channel;}
 /** The level the bound channel last held, as the lamp's own signal. */
 public int channelSignal(){return channelSignal;}
 /**
  * One server tick of a stand-in: sample the input, and write the brightness the answer asks for.
  *
  * <p>A stand-in placed by hand has no original block and no redstone of its own, so this is a no-op for
  * it in {@link #IGNORE} mode -- the common case costs one signal read and nothing else.
  */
 public static void tick(Level level,BlockPos pos,BlockState state,LightSourceBlockEntity standIn){
  if(level.isClientSide)return;
  // The signal source first: a bound board answers instead of the block's own redstone, and the answer
  // then travels the same path a redstone input would. One path, so an edge is an edge whoever sent it.
  boolean bound=standIn.channel.bound();
  int signal;
  if(bound){
   standIn.channel.tick((net.minecraft.server.level.ServerLevel)level,pos);
   signal=standIn.channel.signal().strength();
  }else signal=level.getBestNeighborSignal(pos);
  if(bound!=standIn.lastBound){
   // A board appearing or going is not an edge of the signal: take the new level as the baseline, or
   // binding a channel that already says "on" would silently toggle a lamp in EDGE mode.
   standIn.lastBound=bound;
   standIn.lastSignal=signal;
   standIn.latchedOn=lit(standIn.control,standIn.latchedOn,signal);
   standIn.setChanged();
  }
  standIn.channelSignal=bound?signal:0;
  boolean before=standIn.latchedOn;
  standIn.latchedOn=flipped(standIn.latchedOn,signal,standIn.lastSignal);
  boolean edge=standIn.latchedOn!=before;
  boolean signalChanged=signal!=standIn.lastSignal;
  standIn.lastSignal=signal;
  if(edge||signalChanged)standIn.setChanged();
  if(standIn.control==IGNORE)return;
  int wanted=on(standIn,signal)?standIn.litEmission:0;
  if(state.getValue(LightSourceBlock.EMISSION)!=wanted)
   level.setBlock(pos,state.setValue(LightSourceBlock.EMISSION,wanted),3);
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.saveAdditional(tag,registries);
  tag.put("appearance",appearance.save());
  if(original!=null)tag.put("original",NbtUtils.writeBlockState(original));
  tag.putInt("control",control);
  tag.putBoolean("latchedOn",latchedOn);
  tag.putInt("lastSignal",lastSignal);
  tag.putInt("litEmission",litEmission);
  if(!blink.isEmpty())tag.putString("blink",blink);
  BlockOwners.save(tag,owner);
  channel.save(tag,registries);
 }
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.loadAdditional(tag,registries);
  appearance=PointLightAppearance.load(tag.getCompound("appearance"));
  original=tag.contains("original")?NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),tag.getCompound("original")):null;
  control=tag.contains("control")?migrateControl(tag.getInt("control")):IGNORE;
  // A lamp written before any of this existed is an always-on lamp, which is what it was.
  latchedOn=!tag.contains("latchedOn")||tag.getBoolean("latchedOn");
  lastSignal=tag.getInt("lastSignal");
  if(tag.contains("litEmission"))litEmission=tag.getInt("litEmission");
  else adoptEmission();
  blink=tag.contains("blink")?tag.getString("blink"):"";
  owner=BlockOwners.load(tag);
  channel.load(tag,registries);
 }
 /** The live channel belongs to the level's thread, so it is let go with the block entity. */
 @Override public void setRemoved(){
  channel.close();
  super.setRemoved();
 }
 public String blink(){return blink;}
  /** Stores an already validated pattern, or clears it when the text is empty. */
  public void setBlink(String pattern){
    String text=pattern==null?"":pattern.trim();
    if(text.equals(blink))return;
    blink=text;
    setChanged();
    if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
  }
  @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
  CompoundTag tag=new CompoundTag();
  saveAdditional(tag,registries);
  return tag;
 }
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
