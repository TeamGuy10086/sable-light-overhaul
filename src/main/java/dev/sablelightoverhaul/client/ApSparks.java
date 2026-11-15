package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.lighting.ImpactParticleData;
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
 private static final org.joml.Matrix4d POSE=new org.joml.Matrix4d();
 /**
  * Registers this client's block hit listener, once, from the client bootstrap.
  *
  * <p>The sparks are the default listener for {@code BlockHitEvent}, which is the point of the exercise:
  * another mod can register beside them (its own sparks, a light, a sound), run before them and cancel
  * {@code SPAWN_PARTICLES} to replace them, without either side knowing about the other.
  */
 public static void install(){
  dev.sablelightoverhaul.api.EffectApi.onBlockHit(dev.sablelightoverhaul.effects.EffectBus.DEFAULT_PRIORITY,ApSparks::blockHit);
 }
 /** Set once: a single line that says whether a hit ever reached this listener, and with what. */
  private static boolean LOGGED_ONCE;
  private static void blockHit(dev.sablelightoverhaul.api.EffectApi.BlockHitEvent event){
  if(event.cancellation().blocks(dev.sablelightoverhaul.api.EffectApi.Section.SPAWN_PARTICLES))return;
  // The event is dispatched on the client, where CBC plays the hit effect; the guard is here so that a
  // listener which draws can never act on a server level by accident.
  if(!event.level().isClientSide())return;
  if(!LOGGED_ONCE){
   // One line, once: an impact with no sparks is otherwise impossible to tell apart from an impact whose event
   // never arrived, and those two have nothing to do with each other.
   LOGGED_ONCE=true;
   com.mojang.logging.LogUtils.getLogger().info(
     "Sable Light Overhaul: block hit reached the sparks (ricochet={} projectile={} enabled={})",
     event.ricochet(),
     event.projectileType()==null?"?":net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.projectileType()),
     ExtraConfig.AP_ENABLED.get());
  }
  fire(event.level(),event.pos(),event.direction(),event.ricochet(),event.projectileType());
 }
 /** One hit's worth of sparks, along {@code direction} (already the direction CBC chose). */
 public static void fire(Level level,Vec3 at,Vec3 direction,boolean bounced,net.minecraft.world.entity.EntityType<?> projectile){
  if(!ExtraConfig.AP_ENABLED.get())return;
  direction=toWorld(level,at,direction);
  if(direction.lengthSqr()<1.0E-8||!Double.isFinite(direction.x)||!Double.isFinite(direction.y)||!Double.isFinite(direction.z))return;
  Vec3 base=direction.normalize();
  // Two axes across the spray, so a cone can be built without a lookup for every particle.
  Vec3 reference=Math.abs(base.y)>0.95?new Vec3(1.0,0.0,0.0):new Vec3(0.0,1.0,0.0);
  Vec3 side=base.cross(reference).normalize();
  Vec3 up=base.cross(side).normalize();
  // A giant cannon's AP shell is a far bigger round than an autocannon's, so it deserves a bigger spray.
  // Which shells count is a list rather than a guess at the registry path: CBC fires the giant cannon's AP
  // shells under ap_shell and ap_shot, and an autocannon round under ap_autocannon.
  double countScale=1.0, sizeScale=1.0;
  var projectileId=projectile==null?null:net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile);
  try{
   if(projectileId!=null&&matchesBigShell(projectileId.getPath())){
    countScale=ExtraConfig.AP_BIG_COUNT_SCALE.get();
    sizeScale=ExtraConfig.AP_BIG_SIZE_SCALE.get();
   }
  }catch(RuntimeException configNotReadable){
   // A scaling rule that cannot be read must not cost the sparks themselves; the defaults are 1.0 anyway.
   countScale=1.0;sizeScale=1.0;
  }
  int color=ExtraConfig.AP_COLOR.get();
  int count=(int)Math.round((bounced?ExtraConfig.AP_RICO_COUNT.get():ExtraConfig.AP_HIT_COUNT.get())*countScale);
  double speed=bounced?ExtraConfig.AP_RICO_SPEED.get():ExtraConfig.AP_HIT_SPEED.get();
  double spread=bounced?ExtraConfig.AP_RICO_SPREAD.get():ExtraConfig.AP_HIT_SPREAD.get();
  double size=(bounced?ExtraConfig.AP_RICO_SIZE.get():ExtraConfig.AP_HIT_SIZE.get())*sizeScale;
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
  int chips=(int)Math.round((bounced?ExtraConfig.AP_RICO_CHIPS.get():0)*countScale);
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
 /**
  * Puts a spray direction back into world space when the hit was on a Sable physics body.
  *
  * <p>CBC derives that direction from the hit block's surface normal, and the whole hit happens inside
  * the body's own plot frame: the packet's position is brought back to world space but the direction is
  * not, so on a sub-level it points along the ship's axes instead of the world's. That is what made a
  * ricochet off a hull read as straight up or down no matter which side was hit. The body's render pose
  * is exactly the plot-to-world transform, so rotating the direction by it puts the spray back on the
  * surface. When the position is not inside any body -- every ordinary block hit, and every hit while
  * Sable is absent -- the direction is returned untouched.
  */
 /** Whether this projectile is one of the shells listed as a big one. Path fragments, case sensitive. */
 private static boolean matchesBigShell(String path){
  if(path==null)return false;
  for(String name:ExtraConfig.AP_BIG_NAMES.get()){
   if(name!=null&&!name.isEmpty()&&path.contains(name))return true;
  }
  return false;
 }
 private static Vec3 toWorld(Level level,Vec3 at,Vec3 direction){
  if(direction.lengthSqr()<1.0E-8||!(level instanceof net.minecraft.client.multiplayer.ClientLevel client))return direction;
  try{
   var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(client);
   if(container==null)return direction;
   var box=new net.minecraft.world.phys.AABB(at.x-.5,at.y-.5,at.z-.5,at.x+.5,at.y+.5,at.z+.5);
   for(var ship:container.queryIntersecting(new dev.ryanhcode.sable.companion.math.BoundingBox3d(box))){
    if(!(ship instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel body)||body.isRemoved())continue;
    var pose=body.renderPose(0.0F);
    if(pose==null)continue;
    pose.bakeIntoMatrix(POSE);
    POSE.setTranslation(0.0,0.0,0.0);
    var rotated=new org.joml.Vector3f((float)direction.x,(float)direction.y,(float)direction.z);
    POSE.transformDirection(rotated);
    return new Vec3(rotated.x(),rotated.y(),rotated.z());
   }
  }catch(Throwable absentOrUnavailable){/* Sable absent, or a body that cannot answer: keep CBC's value */}
  return direction;
 }
}
