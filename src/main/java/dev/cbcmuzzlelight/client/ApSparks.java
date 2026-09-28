package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.MuzzleLight;
import dev.cbcmuzzlelight.lighting.ImpactParticleData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The sparks an armour-piercing hit throws.
 *
 * <p>Create: Big Cannons already works out both directions we need, and hands them over in the packet
 * that plays the block hit effect: on a plain hit it sends the reverse of the incoming velocity, and
 * on a ricochet it sends the reflected one. So the rule this effect follows is exactly:
 *
 * <ul>
 *   <li>a hit sprays <b>back along the incident direction</b>, and it is thin: a handful of streaks
 *       leaving the hole;
 *   <li>a ricochet sprays <b>along the outgoing direction</b>, and it is thick: twice as many streaks
 *       plus a few tumbling chips scraped off the surface.
 * </ul>
 *
 * <p>Everything is emitted client side, where the pack draws it, and with this mod's own particle:
 * the original one draws itself through a custom render type and is invisible under a shader pack.
 */
public final class ApSparks {
 private ApSparks(){}
 /** One hit's worth of sparks, along {@code direction} (already the direction CBC chose). */
 public static void fire(Level level,Vec3 at,Vec3 direction,boolean bounced){
  if(!ExtraConfig.AP_ENABLED.get())return;
  if(direction.lengthSqr()<1.0E-8||!Double.isFinite(direction.x)||!Double.isFinite(direction.y)||!Double.isFinite(direction.z))return;
  Vec3 base=direction.normalize();
  // Two axes across the spray, so a cone can be built without a lookup for every particle.
  Vec3 reference=Math.abs(base.y)>0.95?new Vec3(1.0,0.0,0.0):new Vec3(0.0,1.0,0.0);
  Vec3 side=base.cross(reference).normalize();
  Vec3 up=base.cross(side).normalize();
  int color=ExtraConfig.AP_COLOR.get();
  int count=bounced?ExtraConfig.AP_RICO_COUNT.get():ExtraConfig.AP_HIT_COUNT.get();
  double speed=bounced?ExtraConfig.AP_RICO_SPEED.get():ExtraConfig.AP_HIT_SPEED.get();
  double spread=bounced?ExtraConfig.AP_RICO_SPREAD.get():ExtraConfig.AP_HIT_SPREAD.get();
  double size=bounced?ExtraConfig.AP_RICO_SIZE.get():ExtraConfig.AP_HIT_SIZE.get();
  int lifetime=(bounced?ExtraConfig.AP_RICO_LIFE.get():ExtraConfig.AP_HIT_LIFE.get()).intValue();
  for(int i=0;i<count;i++){
   // A cone: mostly along the direction, with a widening cross-section the further it goes.
   double cone=spread*Math.sqrt(level.random.nextDouble());
   double angle=level.random.nextDouble()*Math.PI*2;
   Vec3 shot=base.scale(speed*(0.7+level.random.nextDouble()*0.6))
     .add(side.scale(Math.cos(angle)*cone*speed))
     .add(up.scale(Math.sin(angle)*cone*speed));
   level.addParticle(new ImpactParticleData((float)size,color,lifetime,(float)ExtraConfig.AP_BOUNCE.get().doubleValue(),ImpactParticleData.STREAK,1.0F),
     true,at.x,at.y,at.z,shot.x,shot.y,shot.z);
  }
  int chips=bounced?ExtraConfig.AP_RICO_CHIPS.get():0;
  for(int i=0;i<chips;i++){
   double cone=spread*1.4*Math.sqrt(level.random.nextDouble());
   double angle=level.random.nextDouble()*Math.PI*2;
   Vec3 shot=base.scale(speed*0.55*(0.6+level.random.nextDouble()*0.8))
     .add(side.scale(Math.cos(angle)*cone*speed))
     .add(up.scale(Math.sin(angle)*cone*speed*0.6));
   level.addParticle(new ImpactParticleData((float)(size*2.4),color,lifetime*2,(float)ExtraConfig.AP_BOUNCE.get().doubleValue(),ImpactParticleData.CHIP,0.5F),
     true,at.x,at.y,at.z,shot.x,shot.y,shot.z);
  }
 }
}
