package dev.cbcmuzzlelight;
import net.minecraft.world.phys.Vec3;
/** Angular-only beam geometry and five intensity profiles; no vanilla light updates. */
public final class BeamLight {
 private BeamLight(){}
 /**
  * Full beam angle in degrees for the redstone strength feeding the width input. Strength 0 is
  * treated as the narrowest beam rather than a degenerate zero-width cone.
  */
 public static double angle(int signal){return 10+3*(Math.max(Math.min(signal,15),1)-1);}
 /** Number of distance steps; the last one lands exactly on {@code max} (see {@link #distance}). */
 public static int count(int min,int max,int step){return Math.min(32,1+(int)Math.ceil((Math.max(min,max)-min)/(double)step));}
 public static int distance(int index,int min,int max,int step){return Math.min(Math.max(min,max),min+(index-1)*step);}
 /**
  * Builds an angular-only beam from the lamp's position: brightness depends only on the angle
  * between a point and the axis, never on the distance along it. The range is a hard cut-off.
  *
  * <p>Modes: 0 forced maximum, 1 fast falloff, 2 uniform (linear), 3 slow falloff, 4 dim.
  */
 public static AreaLight create(Vec3 lamp,Vec3 axis,int range,int widthSignal,int intensity,int mode){
  if(range<1||range>512||mode<0||mode>4)throw new IllegalArgumentException();
  int strength=Math.max(Math.min(intensity,15),0);
  double halfAngleTangent=Math.tan(Math.toRadians(angle(widthSignal)/2));
  double core=0,middle=.4,tail=.05,bright=strength;
  switch(mode){
   case 0:core=.999999;middle=.9999999;tail=0;break; // forced maximum: flat 100% across the beam
   case 2:core=0;middle=.5;tail=.5;break;            // straight linear ramp, 1 at the axis to 0 at the rim
   case 3:core=.25;middle=.7;tail=.35;break;
   case 4:core=.1;middle=.65;tail=.2;bright=strength*.2;break;
   default:break;                                    // case 1: fast falloff
  }
  // Start just outside the lamp so its own block does not shadow the beam.
  var start=lamp.add(axis.scale(.51));
  return AreaLight.beam(start.x,start.y,start.z,axis.x,axis.y,axis.z,range,halfAngleTangent,core,middle,tail,bright);
 }
}
