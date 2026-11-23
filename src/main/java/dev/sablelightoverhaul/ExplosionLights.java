package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.effects.EffectBus;
import dev.sablelightoverhaul.effects.ImpactClassifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * One explosion, turned into two events.
 *
 * <p>This class used to be the whole explosion feature: it measured the blast, decided whether it was worth
 * drawing, lit it, wrote the log a test matrix reads, and told nearby clients to draw a shockwave -- five
 * decisions in one method, in one order, where an early return for any of them silently took the rest with
 * it. That is why "some explosions do not trigger anything" was hard to answer: nothing in the code said
 * which of the five had stopped it.
 *
 * <p>Now the measuring lives in {@link ImpactClassifier} and everything that draws is a listener on
 * {@link EffectBus}, so the order is registration order instead of statement order and a switch belongs to
 * the one thing it switches off. What is left here is the two facts an explosion produces:
 *
 * <ol>
 *   <li><b>Terrain changed.</b> Dispatched first, and outside every effect switch: a crater has to reach the
 *       far LOD even with the explosion light turned off, which is the one part of this that is not an
 *       effect. Its own thresholds are the listener's, not this method's.</li>
 *   <li><b>An impact.</b> Dispatched for every explosion the classifier can describe, including the small
 *       ones: the "too small to bother" threshold is a policy about this mod's own effects and now lives with
 *       them, so a third party can draw something for a grenade without having to re-derive our judgement.</li>
 * </ol>
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class ExplosionLights {
 private ExplosionLights(){}
 @SubscribeEvent
 public static void detonate(ExplosionEvent.Detonate event){
  EffectApi.ImpactEvent impact=ImpactClassifier.of(event);
  if(impact==null)return;
  // The block list is handed over as the game's own live list, valid for this dispatch only; the LOD
  // listener is called synchronously and reads nothing after it returns.
  EffectBus.terrainChanged(new EffectApi.TerrainEvent(impact.level(),impact.center(),
    event.getExplosion().radius(),event.getAffectedBlocks(),EffectApi.Cause.EXPLOSION));
  if (!BlastDebugItem.onDetonate(event)) BlastDebugItem.sendImpactVisual(impact, event.getExplosion());
  EffectBus.impact(impact);
 }
}
