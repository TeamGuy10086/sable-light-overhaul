package dev.cbcmuzzlelight.client;

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
 */
final class ShadowBudget {
 /** Frame-rate bands, in frames per second, mapped to what is affordable at that rate. */
 private static final int BAND_STRUGGLING=30;
 private static final int BAND_TIGHT=45;
 private static final int BAND_COMFORTABLE=60;
 private ShadowBudget(){}
 /** The shadow count to publish this frame, between 0 and the player's configured ceiling. */
 static int effective(int ceiling){
  if(ceiling<=0)return 0;
  if(!ExtraConfig.GPU_SHADOW_AUTO.get())return ceiling;
  Minecraft minecraft=Minecraft.getInstance();
  int fps=minecraft==null?60:minecraft.getFps();
  if(fps<=0)return ceiling;                 // not measured yet: trust the player's setting
  // A struggling frame rate takes the count down to one, never to none: dropping to zero made the
  // whole feature look broken ("the shadows stopped working") instead of merely cheaper, and one shadow
  // ray is affordable at any frame rate that still draws a scene. Set shadowLightCountAuto=false to keep
  // the configured count whatever the frame rate does.
  if(fps<BAND_STRUGGLING)return Math.min(1,ceiling);
  if(fps<BAND_TIGHT)return Math.min(1,ceiling);
  if(fps<BAND_COMFORTABLE)return Math.min(2,ceiling);
  return ceiling;
 }
}
