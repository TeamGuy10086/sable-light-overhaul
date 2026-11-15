package dev.sablelightoverhaul.lighting;

import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Particle spawns that are not cut off at 32 blocks.
 *
 * <p>{@code ServerLevel.sendParticles} quietly drops every player further than 32 blocks away, and the
 * overload that takes a player is no different: the distance test lives inside it, so a cannon's dust
 * and sparks vanished for anyone watching from a hill even though this mod had already selected those
 * players by its own, much larger, cull distance. A muzzle flash is a light and travelled the whole
 * way; the particles that go with it did not, which is what made a distant shot look bare.
 *
 * <p>So the packet is built here and handed straight to the players this mod already chose, which skips
 * the distance test entirely: the limit is a server-side decision, there is no distance field in the
 * packet. {@code particles.forceLoad} false restores vanilla's 32-block behaviour.
 */
public final class ForcedParticles {
 /** How far vanilla lets a particle travel; only mentioned to explain what is being worked around. */
 public static final double VANILLA_RANGE=32.0;
 private ForcedParticles(){}
 /**
  * One spawn, sent to exactly the players given.
  * @param count - particle count; 0 spawns a single particle whose velocity is exactly the delta.
  */
 public static void send(ServerLevel level,List<ServerPlayer> players,ParticleOptions options,
                         double x,double y,double z,int count,double dx,double dy,double dz,double speed){
  if(players.isEmpty())return;
  if(!LightingCommon.PARTICLE_FORCE.get()){
   // Untouched vanilla path: a caller that asked for a cull of its own still gets it, and vanilla
   // still applies its 32 blocks on top. This is the switch that exists to reproduce old behaviour.
   for(ServerPlayer player:players)level.sendParticles(player,options,true,x,y,z,count,dx,dy,dz,speed);
   return;
  }
  ClientboundLevelParticlesPacket packet=packet(options,x,y,z,count,dx,dy,dz,speed);
  for(ServerPlayer player:players){
   if(player.level()==level)player.connection.send(packet);
  }
 }
 /**
  * One spawn for the whole level, for call sites that have no player list: CBC's own breech smoke,
  * which vanilla sends to everyone within 32 blocks.
  */
 public static int broadcast(ServerLevel level,ParticleOptions options,
                             double x,double y,double z,int count,double dx,double dy,double dz,double speed){
  if(!LightingCommon.PARTICLE_FORCE.get())return level.sendParticles(options,x,y,z,count,dx,dy,dz,speed);
  ClientboundLevelParticlesPacket packet=packet(options,x,y,z,count,dx,dy,dz,speed);
  int sent=0;
  for(ServerPlayer player:level.players()){
   player.connection.send(packet);
   sent++;
  }
  return sent;
 }
 /** The packet as the client wants it: the distance is a server-side decision, not a field in it. */
 private static ClientboundLevelParticlesPacket packet(ParticleOptions options,
                                                       double x,double y,double z,int count,
                                                       double dx,double dy,double dz,double speed){
  return new ClientboundLevelParticlesPacket(options,true,x,y,z,
    (float)dx,(float)dy,(float)dz,(float)speed,count);
 }
}
