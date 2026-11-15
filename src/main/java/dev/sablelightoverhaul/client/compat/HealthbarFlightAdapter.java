package dev.sablelightoverhaul.client.compat;
import java.util.*;
import dev.sablehealthbar.api.client.FlightClientApi;
import dev.sablehealthbar.api.flight.*;
import dev.sablelightoverhaul.FlightOwnership;
import dev.sablelightoverhaul.HostileMathFlightPayload;
import dev.sablelightoverhaul.HostileShellPayload;
import dev.sablelightoverhaul.client.*;
import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
/** Client-only optional adapter. Consumes Healthbar's prediction, never integrates another trajectory. */
public final class HealthbarFlightAdapter implements HealthbarFlightBridge.Adapter,ClientFlightListener {
 private final FlightOwnership owned=new FlightOwnership(ProjectileStreaks::retire,ProjectileStreaks::finish);
 private final Map<FlightOwnership.Key,ClientFlightFrame> frames=new HashMap<>();
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
 private void world(ClientLevel level){if(world!=level){owned.reset();frames.clear();world=level;}}
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
  boolean auto=s.profile().family().equals("cbc-autocannon")||s.profile().family().equals("cbc:at-twin");
  // Missing metadata never implies a tracer; ownership is nevertheless authoritative.
  owned.launch(key(s.id()),s.carrier(),FlightClientApi.tracer(s.id()).orElse(false),auto,s.dimension());
  HealthbarFlightBridge.claim(s.carrier());
  frames.remove(key(s.id()));refresh(key(s.id()));
 }
 @Override public void launched(FlightSnapshot s,boolean replay){launch(s);}
 @Override public void advanced(FlightSnapshot before,FlightSnapshot current){refresh(key(current.id()));}
 @Override public void visualRemoved(ProjectileId id,String reason){frames.remove(key(id));owned.visualRemoved(key(id));}
 @Override public void ended(FlightSnapshot s,String reason){
  var key=key(s.id());
  frames.remove(key);
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
  frames.clear();owned.reset();
 }
 // No impacted hook: contact does not imply fuze detonation; CBC owns explosion effects.
 private boolean visible(ClientFlightFrame frame){
  var mc=Minecraft.getInstance();if(world==null||mc.player==null||!LightingCommon.TRAIL_FEED_ENABLED.get())return false;
  double range=LightingCommon.TRAIL_FEED_RANGE.get();return mc.player.distanceToSqr(frame.current().position())<=range*range;
 }
 private void refresh(FlightOwnership.Key k){
  var e=owned.get(k);if(e==null)return;
  var sample=FlightClientApi.sample(id(k));
  if(sample.isEmpty()){frames.remove(k);owned.visualRemoved(k);return;}
  var frame=sample.get();frames.put(k,frame);
  if(!e.tracer()||!visible(frame)||HostileMathShells.released(world,e.carrier())||HostileMathShells.ended(world,e.carrier())){
   ProjectileStreaks.retire(e.visualId());return;
  }
  if(confirmedNaval(frame)){ProjectileStreaks.retire(e.visualId());return;}
  // A negotiated feed renders the server's collision-resolved segments. The predicted sample still drives lights.
  if(e.auto()&&TrailTrack.hasConfirmedAutocannonFeed()){ProjectileStreaks.retire(e.visualId());return;}
  ProjectileStreaks.acceptMath(world,e.visualId(),frame.previousPosition(),frame.current().position(),frame.current().stateTick(),frame.clockHold(),e.auto(),frame.current().profile().type().equals("createbigcannons:flak_autocannon"));
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
  var confirmed=HostileMathShells.position(world,e.carrier(),partial);
  if(confirmed!=null){
   var player=Minecraft.getInstance().player;
   double range=LightingCommon.TRAIL_FEED_RANGE.get();
   return player!=null&&player.distanceToSqr(confirmed)<=range*range?confirmed:null;
  }
  return frame==null||!visible(frame)||confirmedNaval(frame)?null:frame.position(partial);
 }
}
