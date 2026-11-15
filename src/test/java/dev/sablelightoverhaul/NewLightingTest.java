package dev.sablelightoverhaul;
import dev.sablelightoverhaul.lighting.*;
import dev.sablelightoverhaul.client.*;
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
 @Test void blastHdrGainIsImmutableAndSeparateFromVanillaBrightness(){
  var light=AreaLight.directional(0,0,0,1,0,0,24,8,12,12,8,.25,.65,.2,15,false);
  var normal=new AreaSnapshot(light,AreaSnapshot.bounds(light),null);
  var burst=normal.withRadiance(3);
  assertEquals(1,normal.radiance);assertEquals(3,burst.radiance);
  assertEquals(15,burst.light.brightness());assertEquals(normal.bounds,burst.bounds);
  assertFalse(normal.same(burst));
  assertThrows(IllegalArgumentException.class,()->normal.withRadiance(Float.NaN));
  assertThrows(IllegalArgumentException.class,()->normal.withRadiance(9));
 }

 @Test void registrationCyclesAndPersistsShipIdentity(){
  var data=new RegisteredSources();var a=new SourceAnchor(UUID.randomUUID(),new BlockPos(1,64,2));
  assertEquals(1,data.cycle(a));int id=data.find(a,false).id();assertEquals(2,data.cycle(a));assertEquals(id,data.find(a,false).id());
  var saved=data.save(new CompoundTag(),RegistryAccess.EMPTY);var read=RegisteredSources.load(saved,RegistryAccess.EMPTY);
  assertEquals(data.entries,read.entries);assertEquals(0,read.cycle(a));assertTrue(read.entries.isEmpty());
  assertEquals(1,read.cycle(a));assertTrue(read.find(a,false).id()>id);assertTrue(read.remove(read.find(a,false).id()));
 }
 @Test void aLampsCloudReachIsItsOwnAndSurvivesTheRestOfThePage(){
  var data=new RegisteredSources();var a=new SourceAnchor(null,new BlockPos(7,80,3));
  data.cycle(a);
  // A new lamp has no opinion, which is what keeps the shipped behaviour: the client's global setting
  // decides, and that setting is zero by default.
  assertEquals(RegisteredSources.CLOUD_REACH_GLOBAL,data.find(a,false).cloudReach());
  assertTrue(data.setCloudReach(a,96));assertEquals(96,data.find(a,false).cloudReach());
  // The page writes one field at a time: the others must survive a cloud edit, and it must survive theirs.
  assertTrue(data.setRadius(a,20));assertEquals(96,data.find(a,false).cloudReach());
  assertTrue(data.setShadow(a,0,8));assertEquals(96,data.find(a,false).cloudReach());
  data.cycle(a);assertEquals(96,data.find(a,false).cloudReach());
  var saved=data.save(new CompoundTag(),RegistryAccess.EMPTY);
  assertEquals(96,RegisteredSources.load(saved,RegistryAccess.EMPTY).find(a,false).cloudReach());
  // A world written before the field existed must not read as "reach 0", which would be an opinion the
  // lamp never expressed: it is the global setting that applies.
  var older=new CompoundTag();older.putInt("NextId",2);var list=new net.minecraft.nbt.ListTag();
  var entry=new CompoundTag();entry.putInt("Id",1);entry.putInt("Mode",1);entry.putLong("Pos",new BlockPos(1,2,3).asLong());
  list.add(entry);older.put("Sources",list);
  assertEquals(RegisteredSources.CLOUD_REACH_GLOBAL,RegisteredSources.load(older,RegistryAccess.EMPTY).entries.values().iterator().next().cloudReach());
 }
 @Test void aLampsShadowConesAreTextAndSurviveTheRestOfThePage(){
  var data=new RegisteredSources();var a=new SourceAnchor(null,new BlockPos(8,70,1));
  data.cycle(a);
  assertEquals("",data.find(a,false).cones(),"a lamp nobody configured has no cones");
  assertTrue(data.setCones(a," {0, 90, -10, 40} ; {270,360,0,60} "));
  assertEquals("{0, 90, -10, 40} ; {270,360,0,60}",data.find(a,false).cones());
  // The other page fields write one at a time and must not drop it.
  assertTrue(data.setLightStyle(a,false,1));assertEquals("{0, 90, -10, 40} ; {270,360,0,60}",data.find(a,false).cones());
  assertTrue(data.setEmissionOverride(a,9));assertTrue(data.setRadius(a,20));
  var saved=data.save(new CompoundTag(),RegistryAccess.EMPTY);
  assertEquals("{0, 90, -10, 40} ; {270,360,0,60}",
    RegisteredSources.load(saved,RegistryAccess.EMPTY).find(a,false).cones());
  // A malformed text is refused and the lamp keeps what it had.
  assertThrows(IllegalArgumentException.class,()->data.setCones(a,"{10,5,0,10}"));
  assertThrows(IllegalArgumentException.class,()->data.setCones(a,"{0,90,0,10};{0,90,0,10};{0,90,0,10};{0,90,0,10};{0,90,0,10}"));
  assertEquals("{0, 90, -10, 40} ; {270,360,0,60}",data.find(a,false).cones());
  assertTrue(data.setCones(a,""),"an empty text is how every direction is spelled");
  assertEquals("",data.find(a,false).cones());
 }
 @Test void aLampsLightStyleIsItsOwnAndThePageWritesOneFieldAtATime(){
  var data=new RegisteredSources();var a=new SourceAnchor(null,new BlockPos(2,90,5));
  data.cycle(a);
  // A lamp that has never been asked is lit like vanilla block light, with every face open: that is
  // what makes every lamp in an existing world stop looking dim the moment this version loads it.
  assertTrue(data.find(a,false).flood());
  assertEquals(RegisteredSources.FACES_ALL,data.find(a,false).faces());
  assertTrue(data.setLightStyle(a,false,RegisteredSources.FACES_ALL&~(1<<2)));
  assertFalse(data.find(a,false).flood());
  assertEquals(RegisteredSources.FACES_ALL&~(1<<2),data.find(a,false).faces());
  // Every other page field reads the style back before writing, so no edit can drop it.
  assertTrue(data.setCloudReach(a,64));assertFalse(data.find(a,false).flood());
  assertTrue(data.setShadow(a,0,9));assertEquals(RegisteredSources.FACES_ALL&~(1<<2),data.find(a,false).faces());
  assertTrue(data.setRadius(a,12));assertFalse(data.find(a,false).flood());
  data.cycle(a);assertFalse(data.find(a,false).flood());
  var saved=data.save(new CompoundTag(),RegistryAccess.EMPTY);
  var read=RegisteredSources.load(saved,RegistryAccess.EMPTY).find(a,false);
  assertFalse(read.flood());assertEquals(RegisteredSources.FACES_ALL&~(1<<2),read.faces());
  // A world written before the style existed gets the shipped default rather than "faces: 0", which
  // would be a lamp that lights nothing but its own cell.
  var older=new CompoundTag();older.putInt("NextId",2);var list=new net.minecraft.nbt.ListTag();
  var entry=new CompoundTag();entry.putInt("Id",1);entry.putInt("Mode",1);entry.putLong("Pos",new BlockPos(1,2,3).asLong());
  list.add(entry);older.put("Sources",list);
  var old=RegisteredSources.load(older,RegistryAccess.EMPTY).entries.values().iterator().next();
  assertTrue(old.flood());assertEquals(RegisteredSources.FACES_ALL,old.faces());
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
  // Sixteen dye slots with the two useless ends repurposed, plus one addition.
  //
  // What the two ends mean changed on purpose: the first slot is now vanilla block light (so a lamp nobody
  // has touched matches the torch beside it), and the warm amber the client configures moved to the extra
  // slot as 暖光. The dynamic lights -- muzzle flashes, tracers -- no longer go through the palette at all;
  // they read the configured colour directly (see AdvancedLights.isPaged).
  assertEquals(17,SourceColor.COUNT);
  assertEquals(SourceColor.WARM,SourceColor.COUNT-1);
  assertEquals("暖光",SourceColor.name(SourceColor.WARM));
  assertEquals("原版",SourceColor.name(SourceColor.UNTINTED));
  assertTrue(SourceColor.valid(SourceColor.WARM));
  assertFalse(SourceColor.valid(SourceColor.COUNT));
  // 暖光 is exactly the client's configured colour, whatever it is.
  float[] warm=SourceColor.rgb(SourceColor.WARM,0,new float[3],1.0F,0.58F,0.25F);
  assertEquals(1.0F,warm[0],1e-6);assertEquals(0.58F,warm[1],1e-6);assertEquals(0.25F,warm[2],1e-6);
  // 原版 is #FFD9C0 sRGB as linear: the warm white the user picked once the brightness shape underneath it
  // was vanilla's own. It carries more blue than the overlay amber, which is what "ours is clearly more
  // orange" was about, and less than a pure white would -- a torch's pool is warm, not neutral.
  float[] vanilla=SourceColor.rgb(SourceColor.UNTINTED,0,new float[3],1.0F,0.58F,0.25F);
  assertEquals(1.0F,vanilla[0],1e-3);
  assertEquals(0.701F,vanilla[1],0.01F);
  assertEquals(0.535F,vanilla[2],0.01F);
  assertTrue(vanilla[2]>warm[2]+0.1F,"the vanilla tint carries more blue than the overlay amber");
  assertEquals(SourceColor.UNTINTED,SourceColor.slot(0,0));
  assertEquals(1,SourceColor.slot(SourceColor.FLASHING,0),"the flashing marker resolves to a real tint");
  assertEquals(14,SourceColor.slot(SourceColor.FLASHING,13));
  for(int phase=0;phase<40;phase++)assertTrue(SourceColor.slot(SourceColor.FLASHING,phase)>=1&&SourceColor.slot(SourceColor.FLASHING,phase)<=14);
  assertEquals(SourceColor.slot(SourceColor.FLASHING,4),SourceColor.slot(SourceColor.FLASHING,18),"flashing wraps every 14 steps");
  assertEquals(SourceColor.UNTINTED,SourceColor.slot(-1,3));
  assertEquals(SourceColor.UNTINTED,SourceColor.slot(99,3));
  // A tint returns the dye's own linear colour; the ignore-the-defaults argument no longer leaks into the
  // first slot, which is why the four numbers below are asserted against the dye alone.
  float[] out=new float[3];
  SourceColor.rgb(14,0,out,0.5F,0.25F,0.125F);
  assertTrue(out[0]>out[1]&&out[0]>out[2],"red is dominated by its red channel");
  assertTrue(out[1]<0.1F&&out[2]<0.1F,"and keeps green/blue low");
  SourceColor.rgb(4,0,out,1,1,1);
  assertTrue(out[0]>0.9F&&out[1]>0.5F&&out[2]<0.2F,"yellow is red plus green");
  assertEquals(SourceColor.name(0),"原版");
  assertEquals(SourceColor.name(15),"闪烁");
  assertEquals(SourceColor.name(14),"红");
 }
 @Test void spotlightRegistrationDoesNotOverwriteStaticRegistration(){
  var data=new RegisteredSources();var a=new SourceAnchor(null,BlockPos.ZERO);
  data.cycle(a);data.spotlight(a,true);data.spotlight(a,true);assertEquals(2,data.entries.size());
  data.spotlight(a,false);assertEquals(1,data.entries.size());assertNotNull(data.find(a,false));
 }
 @Test void snapshotsRoundTripIncludingEmptyResetAndShip(){
  var states=List.of(new SourcePayload.State(42,new SourceAnchor(UUID.randomUUID(),new BlockPos(-12,100,18)),2,true,0,15,10,3,5,2,35.5F,-120.25F,0,2,12,96,true,61,Cones.pack(-45,45,-10,40),0,0,0,0.0F,0.0F,0.0F,""));
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
  c.set("tracerLight.autocannon.enabled",false);c.set("tracerLight.autocannon.maximumCount",16384);
  assertEquals(true,c.get("tracerLight.bigCannon.enabled"));assertEquals(128,(Integer)c.get("tracerLight.bigCannon.maximumCount"));assertTrue(MuzzleClientConfig.SPEC.isCorrect(c));
 }
}
