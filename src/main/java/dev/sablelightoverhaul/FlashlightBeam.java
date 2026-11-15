package dev.sablelightoverhaul;

/**
 * The flashlight's beam shape, as pure arithmetic, so the draw path and the page cannot disagree.
 *
 * <p>Two numbers describe the whole cone:
 *
 * <ul>
 *   <li><b>Width, in degrees.</b> A full angle, converted to {@code tan(half angle)} because that is what
 *       {@link AreaLight#beam} wants and what the patched shader reads out of {@code axis.w}. It replaced
 *       a 1..15 "signal" that mapped to 10..52 degrees through the spotlights' own table — a value the
 *       player had to translate before it meant anything.</li>
 *   <li><b>Decay speed, in percent.</b> How fast the beam gives up its brightness along its own length.
 *       {@code 100} is the reference shape. It governs the <em>distance</em> ramp only; the cross-section is
 *       one of the five named profiles the spotlights use, and the page picks it separately.</li>
 * </ul>
 *
 * <p>It is deliberately kept out of {@code HeldLights}: the client class cannot be unit-tested without a
 * game, and this is the part with an actual rule in it.
 */
public final class FlashlightBeam {
 /** Narrowest and widest a beam may be, as a full angle in degrees. */
 public static final int MIN_WIDTH_DEGREES=5, MAX_WIDTH_DEGREES=90;
 /** Decay speed bounds, as a percentage of the reference shape. */
 public static final int MIN_DECAY_PERCENT=20, MAX_DECAY_PERCENT=300, DEFAULT_DECAY_PERCENT=100;
 /** Which of the spotlights' five angular profiles the cross-section uses. */
 public static final int MIN_PROFILE=0, MAX_PROFILE=4, DEFAULT_PROFILE=2;
 /**
  * The reference profile: at {@code decay = 100} the cone ramps linearly from full brightness on the axis
  * to half at the knee, then linearly to nothing at the end of its range. That is the same shape the
  * flashlight shipped with, so the default look is unchanged.
  */
 private static final double REFERENCE_SHARE=0.5;
 /** Kept inside what {@link AreaLight} accepts: the knee must sit strictly between the core and one. */
 private static final double MIN_SHARE=0.02, MAX_SHARE=0.95;

 private FlashlightBeam(){}

 /** Full beam angle in degrees to the tangent of its half angle. Clamped, so a bad TOML cannot throw. */
 public static double halfAngleTangent(int degrees){
  return Math.tan(Math.toRadians(clamp(degrees,MIN_WIDTH_DEGREES,MAX_WIDTH_DEGREES))/2.0);
 }

 /**
  * Where the profile's knee sits, for a decay speed in percent.
  *
  * <p>This number no longer touches the cross-section: that is the profile the page picks (see
  * {@link BeamLight#profile}), applied to the angle alone. All this decides is how fast the beam gives up
  * its brightness along its own length:
  *
  * <ul>
  *   <li>{@code 100} → the knee at half the range, so the near half is bright and the far half dims.</li>
  *   <li>{@code 300} → the knee at a sixth of the range: a short, bright pool of light.</li>
  *   <li>{@code 20} → the knee almost at the end: nearly the whole beam stays bright, like the
  *       searchlights' angle-only profile.</li>
  * </ul>
  */
 public static double knee(int decayPercent){
  double speed=clamp(decayPercent,MIN_DECAY_PERCENT,MAX_DECAY_PERCENT)/100.0;
  return clamp(REFERENCE_SHARE/speed,MIN_SHARE,MAX_SHARE);
 }

 private static int clamp(int value,int min,int max){
  return Math.max(min,Math.min(max,value));
 }

 private static double clamp(double value,double min,double max){
  return Math.max(min,Math.min(max,value));
 }
}
