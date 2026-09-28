package dev.cbcmuzzlelight.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The emissive interface for particles this mod draws itself.
 *
 * <p>Every particle that is meant to glow reports itself here once per tick with its world position,
 * colour and strength, and the buffer is cleared at the start of every frame. Nothing consumes it
 * yet: the particles already draw themselves self-lit, so an effect looks right today, and a shader
 * adapter that offers an emissive pass (a pack with a bloom or emissive channel, the same way the
 * in-pack lighting path works) can pick these up later without any change to the effects themselves.
 */
public final class EmissiveParticles {
 /** One reported glow. */
 public record Glow(double x,double y,double z,float red,float green,float blue,float strength) {}
 private static final List<Glow> CURRENT=new ArrayList<>();
 private EmissiveParticles(){}
 /** Called by a particle each tick while it is alive. */
 public static void report(double x,double y,double z,float red,float green,float blue,float strength){
  if(strength<=0.0F)return;
  if(CURRENT.size()>=4096)return;
  CURRENT.add(new Glow(x,y,z,red,green,blue,strength));
 }
 /** Everything reported since the last clear; a shader adapter reads this once per frame. */
 public static List<Glow> current(){return CURRENT;}
 /** Called once per frame before the particles tick, so a frame never sees last frame's glows. */
 public static void clear(){CURRENT.clear();}
}
