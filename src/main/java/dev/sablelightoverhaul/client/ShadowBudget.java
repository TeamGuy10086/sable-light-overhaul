package dev.sablelightoverhaul.client;

import net.minecraft.client.Minecraft;

/**
 * How many lights are allowed to cast a screen-space shadow this second.
 *
 * <p>The shadow ray is the most expensive thing this mod asks a shader pack to do: twelve depth taps
 * per pixel per shadow-casting light, over the whole deferred pass, every frame. The configured count
 * is therefore treated as a <b>ceiling the player chose</b>, and this class spends only as much of it
 * as the frame time can afford. Having a pack configured for two shadow lights is fine while the scene
 * is quiet and ruinous during a barrage, and the player cannot be expected to retune a config file
 * mid-battle.
 *
 * <p>It can only ever lower the count, never raise it: a player who set zero shadows keeps zero, and
 * the sample is the client's own once-a-second frame rate rather than a per-frame timer, so this costs
 * nothing and cannot itself become the hitch it is trying to avoid.
 *
 * <p><b>Every change here is a visible pop, so changes are rationed.</b> The count is what decides
 * whether a lit surface is shaded or not, so dropping a lamp's ray brightens everything it lights and
 * giving it back darkens it again. A plain threshold on the frame rate -- which is a rolling average
 * that wanders either side of sixty on any real scene -- therefore flickered whole rooms several times
 * a second, and walking towards a lamp was enough to trigger it because the frame rate moved with the
 * view. Two things keep that from happening now:
 *
 * <ul>
 *   <li>a <b>Schmitt ladder</b>: the frame rate has to clear the next band's threshold by
 *       {@link #MARGIN} before the count climbs, and fall {@link #MARGIN} below the current band's
 *       threshold before it drops, so a rate hovering on a boundary settles on one side of it;</li>
 *   <li>a <b>dwell</b>: even a decision the ladder is sure about is held for {@link #DWELL_MILLIS}
 *       before it is published, which bounds the pop rate no matter what the frame rate does.</li>
 * </ul>
 *
 * <p>Set {@code shadowLightCountAuto=false} to keep the configured count whatever the frame rate does.
 */
final class ShadowBudget {
 /** Frame-rate bands, in frames per second, mapped to what is affordable at that rate. */
 private static final int BAND_STRUGGLING=30;
 private static final int BAND_TIGHT=45;
 private static final int BAND_COMFORTABLE=60;
 /** How far past a band's threshold the frame rate must climb before the count goes back up. */
 private static final int MARGIN=5;
 /** How long a decision is held before it is published, in milliseconds. */
 private static final long DWELL_MILLIS=1500L;
 /** The count in force, or -1 before the first measurement. */
 private static int settled=-1;
 /** When the count in force was decided, in milliseconds. */
 private static long settledAt;
 private ShadowBudget(){}
 /** The shadow count to publish this frame, between 0 and the player's configured ceiling. */
 static int effective(int ceiling){
  if(ceiling<=0){settled=-1;return 0;}
  // Quality mode is a promise about the image, not about the frame time: the count is whatever the player
  // configured, whatever the frame rate happens to be doing. Trimming it by the frame rate would make two
  // frames of the same scene carry different numbers of shadows, which is the one thing a shoot cannot have.
  if(ExtraConfig.GPU_QUALITY_STEPS.get()){settled=ceiling;return ceiling;}
  if(!ExtraConfig.GPU_SHADOW_AUTO.get()){settled=ceiling;return ceiling;}
  Minecraft minecraft=Minecraft.getInstance();
  int fps=minecraft==null?60:minecraft.getFps();
  if(fps<=0)return Math.min(Math.max(settled,0),ceiling);   // not measured yet: keep what we had
  int band=band(fps,bandOf(settled));
  // A struggling frame rate takes the count down to one, never to none: dropping to zero made the
  // whole feature look broken ("the shadows stopped working") instead of merely cheaper, and one shadow
  // ray is affordable at any frame rate that still draws a scene.
  int want=Math.min(band<=1?1:band==2?2:ceiling,ceiling);
  long now=System.currentTimeMillis();
  if(settled<0){settled=want;settledAt=now;return want;}
  if(want!=settled&&now-settledAt>=DWELL_MILLIS){settled=want;settledAt=now;}
  return Math.min(settled,ceiling);
 }
 /**
  * The band the frame rate belongs in, given the one in force.
  *
  * <p>Each threshold is a pair: the rate that <em>keeps</em> the higher band, and the higher rate that
  * is needed to <em>return</em> to it. Without the second number a rate resting on a boundary would
  * cross it on every frame and the count would never settle.
  */
 private static int band(int fps,int current){
  if(current>=3)return fps<BAND_COMFORTABLE?2:3;
  if(current==2){
   if(fps<BAND_TIGHT)return 1;
   if(fps>=BAND_COMFORTABLE+MARGIN)return 3;
   return 2;
  }
  if(current==1){
   if(fps<BAND_STRUGGLING)return 0;
   if(fps>=BAND_TIGHT+MARGIN)return 2;
   return 1;
  }
  return fps>=BAND_STRUGGLING+MARGIN?1:0;
 }
 private static int bandOf(int effective){
  if(effective<0)return 3;                                 // nothing measured yet: assume the best
  return effective<=1?1:effective==2?2:3;
 }
}
