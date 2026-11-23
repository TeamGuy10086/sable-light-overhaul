package dev.sablelightoverhaul.client.compat;
import java.util.*;
import dev.sablehealthbar.api.client.FlightClientApi;
import dev.sablehealthbar.api.flight.*;
import dev.sablelightoverhaul.FlightOwnership;
import dev.sablelightoverhaul.HostileMathFlightPayload;
import dev.sablelightoverhaul.HostileShellPayload;
import dev.sablelightoverhaul.client.*;
import dev.sablelightoverhaul.compat.HealthbarFlightPhase;
import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
/** Client-only optional adapter. Consumes Healthbar's prediction, never integrates another trajectory. */
public final class HealthbarFlightAdapter implements HealthbarFlightBridge.Adapter,ClientFlightListener {
 private final FlightOwnership owned=new FlightOwnership(ProjectileStreaks::retire,ProjectileStreaks::finish);
 private final Map<FlightOwnership.Key,ClientFlightFrame> frames=new HashMap<>();
 private record Launch(long tick,net.minecraft.world.phys.Vec3 muzzle){}
 private final Map<FlightOwnership.Key,Launch> directLaunches=new HashMap<>();
 private FlightApi.Subscription subscription;
 private ClientLevel world;
 private HealthbarFlightAdapter(){}
 public static HealthbarFlightBridge.Adapter create(){
  var a=new HealthbarFlightAdapter();
  a.subscription=FlightClientApi.listen(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","flight_visuals"),a);
  a.tick(Minecraft.getInstance().level);return a;
 }
 private static FlightOwnership.Key key(ProjectileId id){return new FlightOwnership.Key(id.serverSession(),id.number());}
 private static ProjectileId id(FlightOwnership.Key k){return new ProjectileId(k.session(),k.number());}
 private void world(ClientLevel level){if(world!=level){owned.reset();frames.clear();directLaunches.clear();world=level;}}
 @Override public void tick(ClientLevel level){
  boolean changed=world!=level;world(level);
  // Force the API's world-change guard before bootstrap; listen() itself deliberately does not replay.
  if(changed||owned.entries().isEmpty())for(var s:FlightClientApi.managed())if(owned.get(key(s.id()))==null)launch(s);
  if(level==null)return;
  for(var e:owned.entries())refresh(e.key());
 }
 private void launch(FlightSnapshot s){
  world(Minecraft.getInstance().level);
  if(world==null||!s.dimension().equals(world.dimension().location().toString()))return;
  if(HostileMathShells.ended(world,s.carrier())||HostileMathShells.released(world,s.carrier())
      ||!FlightClientApi.managesCarrier(s.carrier()))return;
  boolean auto=HealthbarFlightPhase.isAutocannon(s);
  // Missing metadata never implies a tracer; ownership is nevertheless authoritative.
  var key=key(s.id());
  var existing=owned.get(key);
  boolean tracer=FlightClientApi.tracer(s.id()).orElse(false);
  if(existing==null||!existing.carrier().equals(s.carrier())||existing.tracer()!=tracer||existing.auto()!=auto){
   owned.launch(key,s.carrier(),tracer,auto,s.dimension());
   HealthbarFlightBridge.claim(s.carrier());
  }
  refresh(key);
 }
 @Override public void launched(FlightSnapshot s,boolean replay){
  launch(s);
  if(!replay&&s.age()==0&&HealthbarFlightPhase.isDirectAutocannon(s)){
   var entry=owned.get(key(s.id()));
   if(entry!=null&&entry.tracer())directLaunches.put(entry.key(),new Launch(s.stateTick(),s.position()));
  }
 }
 @Override public void advanced(FlightSnapshot before,FlightSnapshot current){refresh(key(current.id()));}
 @Override public void visualRemoved(ProjectileId id,String reason){
  // The stop notification immediately follows this callback and supplies the authoritative endpoint.
  if(reason.startsWith("server-stop:")||reason.equals("replaced"))return;
  frames.remove(key(id));owned.visualRemoved(key(id));
 }
 @Override public void ended(FlightSnapshot s,String reason){
  var key=key(s.id());
  frames.remove(key);
  directLaunches.remove(key);
  var model=HostileShellPayload.Model.forType(s.profile().type());
  boolean sameWorld=world!=null&&s.dimension().equals(world.dimension().location().toString());
  if(HostileMathFlightPayload.NATIVE_HANDOFF.equals(reason)){
   owned.handoff(key);
   if(sameWorld&&model!=null)HostileMathShells.handoff(world,s.carrier());
  }else{
   if(sameWorld&&model!=null)HostileMathShells.end(world,s.carrier(),s.position());
   owned.end(key);
  }
 }
 @Override public void reset(String reason){
  HostileMathShells.reset(world,owned.entries().stream().map(FlightOwnership.Entry::carrier).toList(),reason.equals("server-clear"));
  frames.clear();directLaunches.clear();owned.reset();
 }
 @Override public void impacted(FlightSnapshot s,FlightContact contact){
  if(world==null||!s.dimension().equals(world.dimension().location().toString())
      ||!HealthbarFlightPhase.isDirectAutocannon(s))return;
  var entry=owned.get(key(s.id()));
  if(entry==null||!entry.tracer()||TrailTrack.hasConfirmedAutocannonFeed(true))return;
  int phase=HealthbarFlightPhase.firstSegment(s);
  if(ProjectileStreaks.active(entry.visualId())){
   ProjectileStreaks.finishMathAt(entry.visualId(),s.stateTick(),contact.point(),contact.fraction(),phase);
  }else{
   var launch=directLaunches.get(entry.key());
   if(launch!=null&&visible(launch.muzzle(),contact.point()))
    ProjectileStreaks.captureFirstContact(world,entry.visualId(),launch.tick(),launch.muzzle(),
        s.stateTick(),contact.point(),contact.fraction(),phase);
  }
 }
 private boolean visible(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to){
  var player=Minecraft.getInstance().player;
  if(world==null||player==null||!LightingCommon.TRAIL_FEED_ENABLED.get())return false;
  double range=LightingCommon.TRAIL_FEED_RANGE.get();
  return player.distanceToSqr(from)<=range*range||player.distanceToSqr(to)<=range*range;
 }
 private void refresh(FlightOwnership.Key k){
  var e=owned.get(k);if(e==null)return;
  var sample=FlightClientApi.sample(id(k));
  if(sample.isEmpty()){frames.remove(k);return;}
  var frame=sample.get();frames.put(k,frame);
  if(!e.tracer()||!visible(frame.previousPosition(),frame.current().position())||HostileMathShells.released(world,e.carrier())||HostileMathShells.ended(world,e.carrier())){
   ProjectileStreaks.retire(e.visualId());return;
  }
  if(confirmedNaval(frame)){ProjectileStreaks.retire(e.visualId());return;}
  // The legacy feed owns the streak when negotiated. Direct autocannons on sampled peers use the
  // same Healthbar frame at every distance, with the stop event supplying their final contact.
  if(e.auto()&&TrailTrack.hasConfirmedAutocannonFeed(HealthbarFlightPhase.isDirectAutocannon(frame.current()))){
   ProjectileStreaks.retire(e.visualId());return;
  }
  if(!HealthbarFlightPhase.visible(frame,1)){
   ProjectileStreaks.retire(e.visualId());return;
  }
  ProjectileStreaks.acceptMath(world,e.visualId(),frame.previousPosition(),frame.current().position(),frame.current().stateTick(),frame.clockHold(),e.auto(),frame.current().profile().type().equals("createbigcannons:flak_autocannon"),HealthbarFlightPhase.firstSegment(frame.current()),
      HealthbarFlightPhase.isDirectAutocannon(frame.current()));
 }
 private static boolean confirmedNaval(ClientFlightFrame frame){
  return HostileShellPayload.Model.forType(frame.current().profile().type())!=null && HostileMathShells.negotiated();
 }
 @Override public boolean owns(UUID carrier){return owned.owns(carrier);}
 @Override public net.minecraft.world.phys.Vec3 heading(UUID carrier){
  var e=owned.carrier(carrier);
  if(e==null||world!=Minecraft.getInstance().level)return null;
  var frame=frames.get(e.key());
  return frame==null?null:frame.current().velocity();
 }
 @Override public net.minecraft.world.phys.Vec3 position(long visualId,float partial){
  if(world!=Minecraft.getInstance().level)return null;
  var e=owned.visual(visualId);
  return e==null?null:lightPosition(e,FlightClientApi.sample(id(e.key())).orElse(null),partial);
 }
 @Override public List<HealthbarFlightBridge.Light> lights(float partial){
  if(world!=Minecraft.getInstance().level)return List.of();
  var out=new ArrayList<HealthbarFlightBridge.Light>();
  for(var e:owned.entries()){
   var position=lightPosition(e,frames.get(e.key()),partial);
   if(position!=null)out.add(new HealthbarFlightBridge.Light(e.visualId(),e.auto(),position));
  }
  return out;
 }
 private net.minecraft.world.phys.Vec3 lightPosition(FlightOwnership.Entry e,ClientFlightFrame frame,float partial){
  if(!e.tracer()||HostileMathShells.released(world,e.carrier())||HostileMathShells.ended(world,e.carrier())
      ||!LightingCommon.TRAIL_FEED_ENABLED.get())return null;
  if(frame!=null&&!HealthbarFlightPhase.allowsProjectileLight(frame.current()))return null;
  var confirmed=HostileMathShells.position(world,e.carrier(),partial);
  if(confirmed!=null){
   var player=Minecraft.getInstance().player;
   double range=LightingCommon.TRAIL_FEED_RANGE.get();
   return player!=null&&player.distanceToSqr(confirmed)<=range*range?confirmed:null;
  }
  if(frame==null||confirmedNaval(frame)||!HealthbarFlightPhase.visible(frame,partial))return null;
  var player=Minecraft.getInstance().player;
  double range=LightingCommon.TRAIL_FEED_RANGE.get();
  return player!=null&&player.distanceToSqr(frame.current().position())<=range*range?frame.position(partial):null;
 }
}
