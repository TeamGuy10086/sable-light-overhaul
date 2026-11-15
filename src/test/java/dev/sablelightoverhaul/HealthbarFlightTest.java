package dev.sablelightoverhaul;
import java.util.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import dev.sablehealthbar.api.flight.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
class HealthbarFlightTest {
 private static final UUID SESSION=UUID.fromString("6a3e3774-0b91-4b68-85ad-a515778e3771");
 private FlightOwnership.Key key(long number){return new FlightOwnership.Key(SESSION,number);}
 @Test void expiryAndImpactDrainTheLineButWorldResetRetiresIt(){
  var retired=new ArrayList<Long>();var finished=new ArrayList<Long>();
  var r=new FlightOwnership(retired::add,finished::add);var carrier=UUID.randomUUID();
  var e=r.launch(key(1),carrier,true,false,"minecraft:overworld");r.visualRemoved(key(1));
  assertTrue(r.owns(carrier));assertEquals(e,r.get(key(1)));assertEquals(List.of(e.visualId()),finished);
  r.end(key(1));assertFalse(r.owns(carrier));assertNull(r.visual(e.visualId()));
  assertEquals(List.of(e.visualId(),e.visualId()),finished);assertTrue(retired.isEmpty());
  var another=r.launch(key(2),carrier,true,false,"minecraft:overworld");r.reset();
  assertEquals(List.of(another.visualId()),retired);
 }
 @Test void replayRetiresOldVisualWithoutDuplicatingOwnership(){
  var retired=new ArrayList<Long>();var r=new FlightOwnership(retired::add);var c=UUID.randomUUID();
  var old=r.launch(key(1),c,true,false,"minecraft:overworld");var next=r.launch(key(1),c,false,false,"minecraft:overworld");
  assertEquals(old.visualId(),next.visualId());assertEquals(1,r.entries().size());assertFalse(next.tracer());assertEquals(List.of(old.visualId()),retired);
 }
 @Test void nativeHandoffImmediatelyReleasesCarrierAndRetiresMathVisual(){
  var retired=new ArrayList<Long>();var finished=new ArrayList<Long>();
  var ownership=new FlightOwnership(retired::add,finished::add);
  var carrier=UUID.randomUUID();
  var entry=ownership.launch(key(1),carrier,true,false,"minecraft:overworld");
  ownership.handoff(key(1));
  ownership.handoff(key(1));
  assertFalse(ownership.owns(carrier));
  assertNull(ownership.visual(entry.visualId()));
  assertEquals(List.of(entry.visualId()),retired);
  assertTrue(finished.isEmpty());
 }
 @Test void sessionQualifiedNumbersAndResetNeverAliasNativeEntityIds(){
  var r=new FlightOwnership(id->{});var e=r.launch(key(1),UUID.randomUUID(),true,true,"minecraft:overworld");
  var other=r.launch(new FlightOwnership.Key(UUID.randomUUID(),1),UUID.randomUUID(),true,false,"minecraft:overworld");
  assertNotEquals(e.visualId(),other.visualId());assertTrue(e.visualId()<Integer.MIN_VALUE);assertTrue(other.visualId()<Integer.MIN_VALUE);
  r.reset();assertTrue(r.entries().isEmpty());assertFalse(r.owns(e.carrier()));
  var fresh=r.launch(key(1),UUID.randomUUID(),true,true,"minecraft:overworld");assertNotEquals(e.visualId(),fresh.visualId());
 }
 @Test void unknownNativeCarrierAndNonTracerAreNotGloballyCaptured(){
  var r=new FlightOwnership(id->{});var c=UUID.randomUUID();r.launch(key(1),c,false,true,"minecraft:overworld");
  assertTrue(r.owns(c));assertFalse(r.get(key(1)).tracer());assertFalse(r.owns(UUID.randomUUID()));assertFalse(r.owns(null));
 }
 @Test void replayWithChangedCarrierClearsOldCarrierIndex(){
  var r=new FlightOwnership(id->{});var old=UUID.randomUUID();var replacement=UUID.randomUUID();
  r.launch(key(1),old,true,false,"minecraft:overworld");r.launch(key(1),replacement,true,false,"minecraft:overworld");
  assertFalse(r.owns(old));assertTrue(r.owns(replacement));
 }
 @Test void actualApiFrameAndStreakClockAgreeDuringHoldAndNormalInterpolation(){
  var p=new ProjectileProfile("cbc:test","cbc-big",1,.01,-.05,false,.1);
  var now=new FlightSnapshot(new ProjectileId(SESSION,1),"minecraft:overworld",p,UUID.randomUUID(),null,new Vec3(100,200,300),new Vec3(5,0,0),10,100,500);
  Vec3 before=new Vec3(95,201,301);
  for(boolean hold:new boolean[]{false,true})for(double partial:new double[]{-.2,0,.25,.5,1,1.2}){
   var frame=new ClientFlightFrame(now,before,hold);
   double interpolation=FlightVisualClock.time(500,hold,partial)-499;
   assertEquals(frame.position(partial),before.lerp(now.position(),interpolation));
  }
 }
 @Test void longNavalFlightKeepsTheSameCarrierAndVisualThroughTick400(){
  var owned=new FlightOwnership(id->{});
  var carrier=UUID.randomUUID();
  var flightId=new ProjectileId(SESSION,1);
  var entry=owned.launch(key(1),carrier,true,false,"minecraft:overworld");
  var profile=new ProjectileProfile("sablehostile:shell_203mm_ap","cbc-big",1,.01,-.05,false,.1);
  for(int age:new int[]{399,400,401,900}){
   var previous=new Vec3(age-1,80,0);
   var snapshot=new FlightSnapshot(flightId,entry.dimension(),profile,carrier,null,
       new Vec3(age,80,0),new Vec3(1,0,0),age,1200,1000L+age);
   assertEquals(new Vec3(age-.5,80,0),new ClientFlightFrame(snapshot,previous,false).position(.5));
   assertEquals(entry,owned.carrier(snapshot.carrier()));
   assertEquals(entry,owned.visual(entry.visualId()));
  }
  owned.handoff(key(1));
  assertFalse(owned.owns(carrier));
  assertNull(owned.visual(entry.visualId()));
 }
 @Test void carrierUuidRoundTripsWithoutChangingWorldCoordinates(){
  var carrier=UUID.randomUUID();var s=new ShellTrackPayload.Shell(17,1500,82,-950,1490,83,-952,10,-1,2,carrier);
  var value=new ShellTrackPayload(ResourceLocation.parse("minecraft:overworld"),List.of(s));
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try{ShellTrackPayload.CODEC.encode(buf,value);assertEquals(value,ShellTrackPayload.CODEC.decode(buf));assertEquals(0,buf.readableBytes());}finally{buf.release();}
 }
 @Test void thirdPartyHostWithoutCarrierKeepsNativeFallbackWirePath(){
  var value=new ShellTrackPayload(ResourceLocation.parse("minecraft:overworld"),List.of(new ShellTrackPayload.Shell(8,1,2,3,0,1,2,1,1,1)));
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try{AutocannonTrackPayload.CODEC.encode(buf,new AutocannonTrackPayload(value));assertEquals(value,AutocannonTrackPayload.CODEC.decode(buf).data());assertEquals(0,buf.readableBytes());var flak=new AutocannonTrackPayload(value,java.util.Set.of(8));AutocannonTrackPayload.CODEC.encode(buf,flak);assertEquals(flak,AutocannonTrackPayload.CODEC.decode(buf));assertEquals(0,buf.readableBytes());}finally{buf.release();}
 }
 @Test void nativeRenderInjectionMatchesInstalledCbcDescriptor()throws Exception{
  try(var jar=new JarFile("libs/cbc.jar")){
   var node=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry("rbasamoyai/createbigcannons/munitions/autocannon/AutocannonProjectileRenderer.class"))).accept(node,0);
   assertTrue(node.methods.stream().anyMatch(m->m.name.equals("render")&&m.desc.equals("(Lrbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")));
  }
 }
 @Test void optionalBoundaryClassesLinkWithHealthbarCompletelyAbsent()throws Exception{
  var parent=getClass().getClassLoader();
  var loader=new ClassLoader(parent){@Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
   if(name.startsWith("dev.sablehealthbar."))throw new ClassNotFoundException("simulated absent Healthbar");
   if(name.equals("dev.sablelightoverhaul.HealthbarServerBridge")||name.startsWith("dev.sablelightoverhaul.client.HealthbarFlightBridge")){
    var old=findLoadedClass(name);if(old!=null)return old;
    try(var in=parent.getResourceAsStream(name.replace('.','/')+".class")){byte[] bytes=in.readAllBytes();var c=defineClass(name,bytes,0,bytes.length);if(resolve)resolveClass(c);return c;}catch(java.io.IOException e){throw new ClassNotFoundException(name,e);}
   }
   return super.loadClass(name,resolve);
  }};
  for(String name:List.of("dev.sablelightoverhaul.HealthbarServerBridge","dev.sablelightoverhaul.client.HealthbarFlightBridge"))assertTrue(Class.forName(name,true,loader).getDeclaredMethods().length>0);
 }
 @Test void sdkContainsNewTracerMetadataWithoutRuntimeClientImplementation()throws Exception{
  try(var jar=new JarFile("libs/healthbar-api.jar")){
   assertNull(jar.getJarEntry("dev/sablehealthbar/client/ClientFlights.class"));
   var n=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry("dev/sablehealthbar/api/client/FlightClientApi.class"))).accept(n,0);
   assertTrue(n.methods.stream().anyMatch(m->m.name.equals("tracer")&&m.desc.equals("(Ldev/sablehealthbar/api/flight/ProjectileId;)Ljava/util/Optional;")));
  }
 }
}
