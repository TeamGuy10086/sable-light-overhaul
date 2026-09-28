package dev.cbcmuzzlelight;
import dev.cbcmuzzlelight.lighting.*;
import dev.cbcmuzzlelight.client.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NewLightingTest {
 @Test void registrationCyclesAndPersistsShipIdentity(){
  var data=new RegisteredSources();var a=new SourceAnchor(UUID.randomUUID(),new BlockPos(1,64,2));
  assertEquals(1,data.cycle(a));int id=data.find(a,false).id();assertEquals(2,data.cycle(a));assertEquals(id,data.find(a,false).id());
  var saved=data.save(new CompoundTag(),RegistryAccess.EMPTY);var read=RegisteredSources.load(saved,RegistryAccess.EMPTY);
  assertEquals(data.entries,read.entries);assertEquals(0,read.cycle(a));assertTrue(read.entries.isEmpty());
  assertEquals(1,read.cycle(a));assertTrue(read.find(a,false).id()>id);assertTrue(read.remove(read.find(a,false).id()));
 }
 @Test void aDyeColourSurvivesCyclingAndAnAutomaticOneStaysAutomatic(){
  var data=new RegisteredSources();var a=new SourceAnchor(null,new BlockPos(4,70,9));
  data.cycle(a);assertEquals(SourceColor.AUTO,data.find(a,false).color(),"a new entry lets the block id decide");
  // A dyed colour is the user's choice: it survives the rest of the cycle, and untinted (0) is real.
  assertTrue(data.setColor(a,0));assertEquals(0,data.find(a,false).color());
  data.cycle(a);assertEquals(2,data.find(a,false).mode());assertEquals(0,data.find(a,false).color());
  // The third step unregisters, so the colour goes with it; registering again starts automatic.
  data.cycle(a);assertNull(data.find(a,false));
  data.cycle(a);assertEquals(SourceColor.AUTO,data.find(a,false).color());
  assertTrue(data.setColor(a,14));data.cycle(a);assertEquals(14,data.find(a,false).color());
  var saved=data.save(new CompoundTag(),RegistryAccess.EMPTY);
  assertEquals(14,RegisteredSources.load(saved,RegistryAccess.EMPTY).find(a,false).color());
 }
 @Test void aSaveWithoutAColourKeepsResolvingFromTheBlock(){
  // 0.9.2 and older wrote no Color tag: those registrations must fall back to automatic.
  var tag=new CompoundTag();tag.putInt("NextId",2);
  var list=new ListTag();var row=new CompoundTag();
  row.putInt("Id",1);row.putInt("Mode",2);row.putBoolean("Spotlight",false);row.putLong("Pos",new BlockPos(2,3,4).asLong());
  list.add(row);tag.put("Sources",list);
  var data=RegisteredSources.load(tag,RegistryAccess.EMPTY);
  assertEquals(SourceColor.AUTO,data.find(new SourceAnchor(null,new BlockPos(2,3,4)),false).color());
 }
 @Test void registeredColoursResolveFromTableThenNameAndIgnoreTheNamespace(){
  var overrides=SourceColor.parse(List.of("create:red_ramp=blue","broken","x:y=nonsense"));
  assertEquals(11,SourceColor.resolve("create:red_ramp",overrides,true),"the override table wins over the name");
  assertEquals(0,SourceColor.resolve("broken",overrides,true),"an unparsable row is ignored");
  assertEquals(14,SourceColor.resolve("simplylight:red_lamp",overrides,true));
  assertEquals(14,SourceColor.resolve("create:red_ramp",Map.of(),true),"left to right, first colour word");
  assertEquals(3,SourceColor.resolve("m:light_blue_lamp",Map.of(),true),"light_blue beats blue at the same word");
  assertEquals(3,SourceColor.resolve("m:glass_light_blue_lamp",Map.of(),true),"and starts a word earlier than blue");
  assertEquals(8,SourceColor.resolve("m:light_gray_lamp",Map.of(),true));
  assertEquals(7,SourceColor.resolve("m:gray_blue_lamp",Map.of(),true),"the leftmost word still wins");
  assertEquals(14,SourceColor.resolve("m:scarlet_lamp",Map.of(),true),"synonyms count as colour words");
  assertEquals(13,SourceColor.resolve("m:emerald_lamp",Map.of(),true));
  assertEquals(0,SourceColor.resolve("m:greenhouse_lamp",Map.of(),true),"a word must be a colour word, not a prefix");
  assertEquals(0,SourceColor.resolve("m:glowstone",Map.of(),true));
  assertEquals(0,SourceColor.resolve("m:white_lamp",Map.of(),true),"white is the untinted slot");
  assertEquals(15,SourceColor.resolve("m:black_lamp",Map.of(),true),"black is the flashing slot");
  assertEquals(0,SourceColor.resolve("redpower:lamp",Map.of(),true),"only the path is scanned");
  assertEquals(0,SourceColor.resolve("create:red_ramp",Map.of(),false),"the name scan can be switched off");
 }
 @Test void paletteSlotsAndFlashingAreStable(){
  assertEquals(16,SourceColor.COUNT);
  assertEquals(SourceColor.UNTINTED,SourceColor.slot(0,0));
  assertEquals(1,SourceColor.slot(SourceColor.FLASHING,0),"the flashing marker resolves to a real tint");
  assertEquals(14,SourceColor.slot(SourceColor.FLASHING,13));
  for(int phase=0;phase<40;phase++)assertTrue(SourceColor.slot(SourceColor.FLASHING,phase)>=1&&SourceColor.slot(SourceColor.FLASHING,phase)<=14);
  assertEquals(SourceColor.slot(SourceColor.FLASHING,4),SourceColor.slot(SourceColor.FLASHING,18),"flashing wraps every 14 steps");
  assertEquals(SourceColor.UNTINTED,SourceColor.slot(-1,3));
  assertEquals(SourceColor.UNTINTED,SourceColor.slot(99,3));
  // Untinted returns the configured default; a tint returns the dye's own linear colour.
  float[] out=new float[3];
  SourceColor.rgb(0,0,out,0.5F,0.25F,0.125F);
  assertArrayEquals(new float[]{0.5F,0.25F,0.125F},out);
  SourceColor.rgb(14,0,out,1,1,1);
  assertTrue(out[0]>out[1]&&out[0]>out[2],"red is dominated by its red channel");
  assertTrue(out[1]<0.1F&&out[2]<0.1F,"and keeps green/blue low");
  SourceColor.rgb(4,0,out,1,1,1);
  assertTrue(out[0]>0.9F&&out[1]>0.5F&&out[2]<0.2F,"yellow is red plus green");
  assertEquals(SourceColor.name(0),"透明");
  assertEquals(SourceColor.name(15),"闪烁");
  assertEquals(SourceColor.name(14),"红");
 }
 @Test void spotlightRegistrationDoesNotOverwriteStaticRegistration(){
  var data=new RegisteredSources();var a=new SourceAnchor(null,BlockPos.ZERO);
  data.cycle(a);data.spotlight(a,true);data.spotlight(a,true);assertEquals(2,data.entries.size());
  data.spotlight(a,false);assertEquals(1,data.entries.size());assertNotNull(data.find(a,false));
 }
 @Test void snapshotsRoundTripIncludingEmptyResetAndShip(){
  var states=List.of(new SourcePayload.State(42,new SourceAnchor(UUID.randomUUID(),new BlockPos(-12,100,18)),2,true,0,15,10,3,5,2,35.5F,-120.25F,0,2,12));
  for(var entries:List.of(states,List.<SourcePayload.State>of())){
   var p=new SourcePayload(ResourceLocation.parse("minecraft:overworld"),entries);
   var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   try{SourcePayload.CODEC.encode(buf,p);assertEquals(p,SourcePayload.CODEC.decode(buf));assertEquals(0,buf.readableBytes());}finally{buf.release();}
  }
 }
 @Test void decoderRejectsUnboundedSnapshotCount(){
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try{buf.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));buf.writeVarInt(8193);assertThrows(IllegalArgumentException.class,()->SourcePayload.CODEC.decode(buf));}finally{buf.release();}
 }
 @Test void redstoneUsesFullAngleAndDistanceCycleHasEightSteps(){
  assertEquals(10,BeamLight.angle(1));assertEquals(52,BeamLight.angle(15));
  // Signal 0 is the narrowest beam, never a degenerate zero-width cone.
  assertEquals(10,BeamLight.angle(0));assertEquals(52,BeamLight.angle(99));
  assertEquals(8,BeamLight.count(16,128,16));assertEquals(16,BeamLight.distance(1,16,128,16));assertEquals(128,BeamLight.distance(8,16,128,16));
  // Airship ranges: 16..512 in steps of 32, 17 stops, and the last one lands exactly on the cap.
  assertEquals(17,BeamLight.count(16,512,32));assertEquals(16,BeamLight.distance(1,16,512,32));
  assertEquals(496,BeamLight.distance(16,16,512,32));assertEquals(512,BeamLight.distance(17,16,512,32));
  var light=BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,1,15,0);
  double x=64.51,edge=64*Math.tan(Math.toRadians(5));assertEquals(15,light.at(x,edge*.9,0),1e-8);assertEquals(0,light.at(x,edge*1.1,0));assertEquals(0,light.at(-1,0,0));
 }
 @Test void beamIsAngularOnlyAndRangeIsAHardCutOff(){
  var light=BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,15,15,2);
  double lateral=Math.tan(Math.toRadians(BeamLight.angle(15)/2));
  // Same angle at 8 and 64 blocks out must give the same brightness: no distance falloff at all.
  double near=light.at(0.51+8,0.5*8*lateral,0),far=light.at(0.51+64,0.5*64*lateral,0);
  assertEquals(near,far,1e-9);assertTrue(near>0);
  // A wider angle is dimmer, and everything past the range or behind the lamp is dark.
  assertTrue(light.at(0.51+32,0.8*32*lateral,0)<light.at(0.51+32,0.2*32*lateral,0));
  assertEquals(0,light.at(0.51+128.5,0,0));assertEquals(0,light.at(0.51+200,0,0));assertEquals(0,light.at(-5,0,0));
 }
 @Test void fiveBeamModesDifferAndBoundsCoverLight(){
  var axis=new Vec3(1,2,3).normalize();
  for(int mode=0;mode<5;mode++){
   var light=BeamLight.create(Vec3.ZERO,axis,128,15,15,mode);var b=AreaSnapshot.bounds(light);
   for(int k=1;k<128;k++){var point=axis.scale(k+.51);assertTrue(b.contains(point));if(mode==0)assertEquals(15,light.at(point.x,point.y,point.z),1e-8);if(mode==4)assertTrue(light.at(point.x,point.y,point.z)<=3.001);}
  }
  // Mode 0 is flat, 1 falls off fastest, 2 is a straight linear ramp, 3 holds up longest, 4 is dim.
  double lateral=Math.tan(Math.toRadians(BeamLight.angle(15)/2)),along=32,half=0.5*along*lateral,x=0.51+along;
  var fast=BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,15,15,1);
  var uniform=BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,15,15,2);
  var slow=BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,15,15,3);
  var dim=BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,15,15,4);
  assertTrue(uniform.at(x,half,0)>fast.at(x,half,0));
  assertTrue(slow.at(x,half,0)>uniform.at(x,half,0));
  assertTrue(dim.at(x,half,0)<uniform.at(x,half,0));
  assertTrue(fast.at(x,half,0)>0);
  assertThrows(IllegalArgumentException.class,()->BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),128,15,15,5));
  assertThrows(IllegalArgumentException.class,()->BeamLight.create(Vec3.ZERO,new Vec3(1,0,0),0,15,15,2));
 }
 @Test void budgetsAndSwitchesAreSeparateAndDefaultConfigIncludesNewFeatures(){
  var c=com.electronwill.nightconfig.core.CommentedConfig.inMemory();MuzzleClientConfig.SPEC.correct(c);
  c.set("advanced.tracers.autocannon.enabled",false);c.set("advanced.tracers.autocannon.maximumCount",16384);
  assertEquals(true,c.get("advanced.tracers.bigCannon.enabled"));assertEquals(128,(Integer)c.get("advanced.tracers.bigCannon.maximumCount"));assertTrue(MuzzleClientConfig.SPEC.isCorrect(c));
 }
}
