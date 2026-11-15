package dev.sablelightoverhaul;
import net.minecraft.world.phys.Vec3;
/** Angular-only beam geometry and five intensity profiles; no vanilla light updates. */
public final class BeamLight {
 private BeamLight(){}
 /**
  * Full beam angle in degrees for the redstone strength feeding the width input. Strength 0 is
  * treated as the narrowest beam rather than a degenerate zero-width cone.
  */
 public static double angle(int signal){return 10+3*(Math.max(Math.min(signal,15),1)-1);}
 /**
  * A spotlight's cone opening in degrees, from its own two limits and a 0..15 driver.
  *
  * <p>Zero is the lower limit, fifteen the upper, and a straight line between them. That indirection exists
  * because redstone and an AFN channel can only send a strength, and a strength is exactly what makes the
  * cone sweep; a lamp tuned by hand leaves the driver at fifteen and moves the upper limit, which is
  * continuous, so it is not limited to sixteen openings.
  *
  * <p>The two limits are ordered here rather than trusted: a page can write either first, and the beam is
  * built from whatever comes out. The answer is clamped to 1..90 degrees -- a narrower cone than one degree
  * is a laser and wider than a right angle is not a spotlight.
  */
 public static double coneDegrees(double low,double high,int driver){
  double t=Math.max(0,Math.min(15,driver))/15.0;
  double from=Math.min(low,high),to=Math.max(low,high);
  return Math.max(1.0,Math.min(90.0,from+(to-from)*t));
 }
 /** Number of distance steps; the last one lands exactly on {@code max} (see {@link #distance}). */
 public static int count(int min,int max,int step){return Math.min(32,1+(int)Math.ceil((Math.max(min,max)-min)/(double)step));}
 public static int distance(int index,int min,int max,int step){return Math.min(Math.max(min,max),min+(index-1)*step);}
 /**
  * One of the five named angular profiles: the three knee fractions of the falloff curve plus a scale on
  * the brightness.
  *
  * <p>The flashlight offers the same five for its <em>cross-section</em>, which is why the table lives here
  * rather than inside the spotlight's builder: one table, one meaning, and the two lamps cannot drift apart.
  */
 public record Profile(double core,double middle,double tail,double brightness) {}
 /**
  * A profile by index. Modes: 0 forced maximum, 1 fast falloff, 2 uniform (linear), 3 slow falloff, 4 dim.
  */
 public static Profile profile(int mode){
  return switch(mode){
   case 0->new Profile(.999999,.9999999,0,1.0);   // forced maximum: flat 100% across the beam
   case 2->new Profile(0,.5,.5,1.0);              // straight linear ramp, 1 at the axis to 0 at the rim
   case 3->new Profile(.25,.7,.35,1.0);           // slow falloff
   case 4->new Profile(.1,.65,.2,0.2);            // dim: the whole beam at a fifth of the brightness
   default->new Profile(0,.4,.05,1.0);            // case 1: fast falloff
   // Mode 5 is a flag, not a curve: a negative core, which no other profile uses. The shader's sphere branch
   // reads the sign and adds the physical 1/(d*d+1) term; every other mode keeps the windowed curve. It lives
   // in the profile because the mode is already a number the owner picks per light (a lamp's block state, the
   // flashlight's cross-section list), and a boolean field would have to be threaded through every light.
   case 5->new Profile(-1.0,.5,.5,1.0);
  };
 }
 /**
  * Builds an angular-only beam from the lamp's position: brightness depends only on the angle
  * between a point and the axis, never on the distance along it. The range is a hard cut-off.
  *
  * <p>This overload takes a 0..15 width <em>signal</em>, which is what a disco ball's own width row and
  * every lamp's redstone input are. A spotlight now resolves its two limits into degrees on the server
  * and calls {@link #create(Vec3, Vec3, int, double, int, int)} instead.
  */
 public static AreaLight create(Vec3 lamp,Vec3 axis,int range,int widthSignal,int intensity,int mode){
  return create(lamp,axis,range,angle(widthSignal),intensity,mode);
 }
 /**
  * The same beam, with its opening already in degrees.
  *
  * <p>Degrees rather than a strength because the spotlight's own two limits are degrees: a redstone input
  * of zero asks for the lower one and fifteen for the upper one, and a lamp tuned by hand sits at fifteen
  * with a continuous upper limit. Resolving that on the client would mean shipping both limits to every
  * client and doing the same interpolation twice.
  */
 public static AreaLight create(Vec3 lamp,Vec3 axis,int range,double coneDegrees,int intensity,int mode){
  if(range<1||range>512||mode<0||mode>4)throw new IllegalArgumentException();
  int strength=Math.max(Math.min(intensity,15),0);
  double clamped=Math.max(1.0,Math.min(90.0,coneDegrees));
  double halfAngleTangent=Math.tan(Math.toRadians(clamped/2));
  Profile profile=profile(mode);
  // Start just outside the lamp so its own block does not shadow the beam.
  var start=lamp.add(axis.scale(.51));
  return AreaLight.beam(start.x,start.y,start.z,axis.x,axis.y,axis.z,range,halfAngleTangent,
    profile.core(),profile.middle(),profile.tail(),strength*profile.brightness());
 }
}
