package dev.sablelightoverhaul.client;

import java.util.ArrayList;
import java.util.List;

/** Reserved legacy adapter API. No internal producer/consumer is active.
 * A future adapter must explicitly own report/read/clear lifecycle; never implicitly retain tick data. */
@Deprecated // Reserved adapter API; inactive until a reader and a frame lifecycle are installed.
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
