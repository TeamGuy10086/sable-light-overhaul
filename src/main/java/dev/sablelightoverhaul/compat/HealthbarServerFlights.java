package dev.sablelightoverhaul.compat;
import dev.sablelightoverhaul.SloNetwork;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import dev.sablehealthbar.api.flight.FlightApi;
import dev.sablehealthbar.api.flight.FlightSnapshot;
import dev.sablehealthbar.api.flight.FlightContact;
import dev.sablelightoverhaul.effects.NavalImpactAudio;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import dev.sablehealthbar.api.flight.ServerFlightListener;
import dev.sablelightoverhaul.AutocannonFlightPayload;
import dev.sablelightoverhaul.HostileMathFlightPayload;
import dev.sablelightoverhaul.HostileShellFeed;
import dev.sablelightoverhaul.HostileShellPayload;
import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Resolved only after the common bridge confirms the optional API. */
public final class HealthbarServerFlights {
 private HealthbarServerFlights(){}
 public static BiPredicate<ServerLevel,UUID> provider(){return FlightApi::managesCarrier;}

 public static void installAutocannonFeed(){
  var feed=new ConfirmedFeed();
  FlightApi.listen(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","autocannon_confirmed_flight"),feed);
  NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,feed::flush);
  var naval = new NavalFeed();
  FlightApi.listen(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","naval_confirmed_flight"),naval);
  NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,naval::flush);
  FlightApi.listen(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","naval_impact_audio"),new NavalContacts());
 }

 private static final class NavalFeed implements ServerFlightListener {
  private final WeakHashMap<ServerLevel, LinkedHashMap<UUID, HostileShellPayload.Shell>> levels = new WeakHashMap<>();

  @Override public void launched(ServerLevel level, FlightSnapshot start) {
   var model = HostileShellPayload.Model.forType(start.profile().type());
   if (model == null || !HostileShellPayload.validStep(start.position(), start.position())) return;
   levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(start.carrier(),
       new HostileShellPayload.Shell(0, start.carrier(), model, start.position(), start.position(),
           false, FlightApi.tracer(level, start.id()).orElse(false)));
  }

  @Override public void advanced(ServerLevel level, FlightSnapshot before, FlightSnapshot current) {
   var model = HostileShellPayload.Model.forType(current.profile().type());
   if (model == null || !HostileShellPayload.validStep(before.position(), current.position())) return;
   var shells = levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
   if (before.position().equals(current.position())) return;
   shells.put(current.carrier(), new HostileShellPayload.Shell(0, current.carrier(), model,
       before.position(), current.position(), false, FlightApi.tracer(level,current.id()).orElse(false)));
  }

  @Override public void ended(ServerLevel level, FlightSnapshot end, String reason) {
   var model = HostileShellPayload.Model.forType(end.profile().type());
   if (HostileMathFlightPayload.NATIVE_HANDOFF.equals(reason) && model != null) {
    if (HostileShellPayload.validStep(end.position(), end.position())) {
     levels.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(end.carrier(),
         HostileMathFlightPayload.handoff(end.carrier(), model, end.position()));
     return;
    }
   }
   var shells = levels.get(level);
   if (shells == null) return;
   var last = shells.get(end.carrier());
   if (last == null) return;
   if (HostileShellPayload.validStep(end.position(), end.position())) shells.put(end.carrier(), last.terminalAt(end.position()));
   else shells.remove(end.carrier());
  }

  private void flush(ServerTickEvent.Post event) {
   var it = levels.entrySet().iterator();
   while (it.hasNext()) {
    var entry = it.next();
    var shells = entry.getValue().values();
    if (entry.getKey().getServer() != event.getServer()) { it.remove(); continue; }
    // Held flights reuse their last confirmed pose. A terminal frame is only sent once.
    HostileShellFeed.deliverMath(entry.getKey(),
        LightingCommon.TRAIL_FEED_ENABLED.get() ? java.util.List.copyOf(shells) : java.util.List.of());
    shells.removeIf(HostileShellPayload.Shell::terminal);
    // A subsequent empty snapshot clears a viewer even if its budget excluded the terminal row.
    if (shells.isEmpty() && !HostileShellFeed.hasMathRecipients(entry.getKey())) it.remove();
   }
  }
 }

 private static final class NavalContacts implements ServerFlightListener {
  @Override public void impacted(ServerLevel level, FlightSnapshot flight, FlightContact contact) {
   var model = HostileShellPayload.Model.forType(flight.profile().type());
   var pos = contact.blockPos();
   if (model == null || pos == null || contact.blockType() == null) return;
   var chunkPos = new ChunkPos(pos);
   var chunk = switch (contact.kind()) {
    case TERRAIN -> level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
    case BODY -> {
     var container = SubLevelContainer.getContainer(level);
     var body = container == null || contact.bodyId() == null ? null : container.getSubLevel(contact.bodyId());
     yield body == null || body.isRemoved() || !body.getPlot().contains(chunkPos) ? null
         : body.getPlot().getChunk(body.getPlot().toLocal(chunkPos));
    }
    default -> null;
   };
   // Healthbar publishes the selected contact synchronously before applying the CBC blast.
   // The public contact carries a block id only. An unavailable or changed block is not guessed.
   if (chunk == null) return;
   var state = chunk.getBlockState(pos);
   if (!contact.blockType().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return;
   NavalImpactAudio.play(level, contact.point(), model, NavalImpactAudio.surface(level, pos, state), false);
  }
 }

 private static final class ConfirmedFeed implements ServerFlightListener {
  private record Pending(AutocannonFlightPayload.Segment segment,boolean requiresPhases,boolean grouped){}
  private final HashMap<ServerLevel,ArrayList<Pending>> pending=new HashMap<>();
  private final HashMap<ServerLevel,HashMap<Long,Integer>> latest=new HashMap<>();
  private final HashMap<ServerLevel,Boolean> legacyDirectViewers=new HashMap<>();
  private UUID session;

  private static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> HEALTHBAR_FLIGHT =
      new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(ResourceLocation.parse("sablehealthbar:flight"));

  private static boolean samplesDirectFlight(net.minecraft.server.level.ServerPlayer player){
   return player.connection.hasChannel(AutocannonFlightPayload.Sampled.TYPE)
       &&player.connection.hasChannel(HEALTHBAR_FLIGHT);
  }

  @Override public void advanced(ServerLevel level,FlightSnapshot previous,FlightSnapshot current){
   if(!HealthbarFlightPhase.isAutocannon(current)||!LightingCommon.TRAIL_FEED_ENABLED.get()
       ||level.players().isEmpty()||previous.position().equals(current.position())
       ||!FlightApi.tracer(level,current.id()).orElse(false))return;
   boolean direct=HealthbarFlightPhase.isDirectAutocannon(current);
   if(direct&&!legacyDirectViewers.computeIfAbsent(level,world->world.players().stream().anyMatch(
       player->player.connection.hasChannel(AutocannonFlightPayload.Phased.TYPE)&&!samplesDirectFlight(player))))return;
   session=current.id().serverSession();
   var segments=pending.computeIfAbsent(level,ignored->new ArrayList<>());
   latest.computeIfAbsent(level,ignored->new HashMap<>()).put(current.id().number(),segments.size());
   var segment=new AutocannonFlightPayload.Segment(
       current.id().number(),previous.position(),current.position(),current.profile().type().equals("createbigcannons:flak_autocannon"),
       HealthbarFlightPhase.firstSegment(current));
   segments.add(new Pending(segment,direct,direct && HealthbarFlightPhase.isGrouped(level,current)));
  }

  @Override public void ended(ServerLevel level,FlightSnapshot end,String reason){
   var index=latest.get(level);
   if(index==null)return;
   Integer i=index.remove(end.id().number());
   if(i==null)return;
   var segments=pending.get(level);
   var previous=segments.get(i);
   var last=previous.segment();
   segments.set(i,new Pending(new AutocannonFlightPayload.Segment(last.number(),last.from(),last.to(),
       last.flak(),last.phaseFifths(),true),previous.requiresPhases(),previous.grouped()));
  }

  private void flush(ServerTickEvent.Post event){
   legacyDirectViewers.clear();
   if(pending.isEmpty())return;
   double range=LightingCommon.TRAIL_FEED_RANGE.get(),rangeSquared=range*range;
   try {
    for(var entry:pending.entrySet()){
     var level=entry.getKey();
     for(var player:level.players()){
      boolean sampled=samplesDirectFlight(player);
      boolean phased=player.connection.hasChannel(AutocannonFlightPayload.Phased.TYPE);
      if(!sampled&&!phased&&!player.connection.hasChannel(AutocannonFlightPayload.TYPE))continue;
      boolean burst=player.connection.hasChannel(dev.sablelightoverhaul.PhalanxBurstPayload.TYPE);
      var nearby=entry.getValue().stream().filter(item->!burst || !item.grouped()).filter(item->!item.requiresPhases() || phased&&!sampled).map(Pending::segment)
          .filter(segment->player.distanceToSqr(segment.to())<=rangeSquared)
          .sorted(Comparator.comparingDouble(segment->player.distanceToSqr(segment.to())))
          .limit(AutocannonFlightPayload.LIMIT).toList();
      if(!nearby.isEmpty()){
       var payload=new AutocannonFlightPayload(level.dimension().location(),session,nearby);
       if(sampled)SloNetwork.sendToPlayer(player,new AutocannonFlightPayload.Sampled(payload));
       else if(phased)SloNetwork.sendToPlayer(player,new AutocannonFlightPayload.Phased(payload));
       else SloNetwork.sendToPlayer(player,payload);
      }
     }
    }
   } finally {pending.clear();latest.clear();}
  }
 }
}
