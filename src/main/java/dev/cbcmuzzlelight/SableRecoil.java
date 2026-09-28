package dev.cbcmuzzlelight;

import dev.cbcmuzzlelight.lighting.LightingCommon;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * The gun's recoil, applied to the physics body that carries it.
 *
 * <p>Which body counts as the vehicle is decided by <b>collision box</b>, never by mass. Block masses
 * are not physical (somebody can always bolt something absurd onto a turret), while the bounding box
 * follows what the thing actually is: the largest box in the connected chain is the hull.
 *
 * <p>The configuration is a <b>velocity change</b>, and the impulse handed to Sable is that change
 * times the body's own mass, so the number a player types means what it says no matter what the build
 * weighs. It is applied inside Sable's physics step (see {@link #step}), once per step for
 * {@code tankCannon.recoil.physicsSteps} steps. Off by default: it is the only part of this mod that
 * can throw a build around, so it has to be switched on deliberately.
 *
 * <p>A cannon that is not part of a physics body is left alone.
 */
public final class SableRecoil {
 /**
  * Hard ceiling on the velocity change, whatever the configuration says.
  *
  * <p>Raised to 5.0: a cannon that lifts its own vehicle is half the fun of the feature, so the clamp
  * is only here to stop a mistyped configuration from teleporting a build off the map, not to keep the
  * kick civil. Per physics step, so the total over {@code cannonRecoilSteps} can be several times this.
  */
 private static final double MAX_DELTA_V=5.0;
 /** Its own group, so a recoil can be told apart from gravity and propulsion in Sable's force list. */
 /** Kicks waiting for a physics step, keyed by the body they belong to. */
 private static final java.util.Map<java.util.UUID,Pending> PENDING=new java.util.HashMap<>();
 record Pending(Vector3d linear,Vector3d angular,long at,int steps){}

 private static final dev.ryanhcode.sable.api.physics.force.ForceGroup RECOIL_GROUP=new dev.ryanhcode.sable.api.physics.force.ForceGroup(
   net.minecraft.network.chat.Component.literal("CBC Muzzle Light recoil"),
   net.minecraft.network.chat.Component.literal("The kick a cannon gives the vehicle carrying it"),
   0xFFAA33,false);
 private SableRecoil(){}
 static void apply(ServerLevel level,Vec3 muzzle,Vec3 shellDirection){
  if(!LightingCommon.CANNON_RECOIL_ENABLED.get())return;
  double deltaV=Math.min(Math.max(LightingCommon.CANNON_RECOIL_VELOCITY.get(),0.0),MAX_DELTA_V);
  if(!(deltaV>0.0))return;
  if(!net.neoforged.fml.ModList.get().isLoaded("sable"))return;
  var container=SubLevelContainer.getContainer(level);
  if(container==null)return;
  ServerSubLevel carrier=null;
  ServerSubLevel nearest=null;
  double nearestDistance=Double.MAX_VALUE;
  for(SubLevel candidate:container.getAllSubLevels()){
   if(!(candidate instanceof ServerSubLevel server)||server.isRemoved())continue;
   double[] box=dev.cbcmuzzlelight.lighting.SableMarkers.boxNear(level,server,muzzle);
   double distance=dev.cbcmuzzlelight.lighting.SableMarkers.distanceSquared(box,muzzle);
   if(distance<=0.0){carrier=server;break;}
   if(distance<nearestDistance){nearestDistance=distance;nearest=server;}
  }
  if(carrier==null)carrier=nearest;
  if(carrier==null)return;
  ServerSubLevel hull=carrier;
  double largest=dev.cbcmuzzlelight.lighting.SableMarkers.volume(dev.cbcmuzzlelight.lighting.SableMarkers.boxNear(level,carrier,muzzle));
  for(SubLevel linked:SubLevelHelper.getConnectedChain(carrier)){
   if(!(linked instanceof ServerSubLevel server)||server.isRemoved())continue;
   double volume=dev.cbcmuzzlelight.lighting.SableMarkers.volume(dev.cbcmuzzlelight.lighting.SableMarkers.boxNear(level,server,muzzle));
   if(volume>largest){largest=volume;hull=server;}
  }
  // A hull marker settles the question outright; without one the largest box stands in for it.
  ServerSubLevel marked=dev.cbcmuzzlelight.lighting.SableMarkers.markedBody(level,muzzle);
  if(marked!=null)hull=marked;
  RigidBodyHandle handle=RigidBodyHandle.of(hull);
  if(handle==null||!handle.isValid())return;
  // The kick is handed to Sable's queue so it lands at the physics step: writing a velocity straight
  // into the body does nothing, because the pipeline rebuilds the body from its own state every tick.
  // Mass is multiplied in so the configured number stays a velocity change while Sable still receives
  // an impulse it can integrate.
  double mass=Math.max(1.0,hull.getMassTracker().getMass());
  // A force applied at the muzzle: the linear part is the kick, and the angular part is that kick
  // acting at an arm from the centre of the body, which is what makes the nose lift. The arm is
  // measured against the body's own box centre, so no assumption about what space Sable reports mass in.
  double[] box=dev.cbcmuzzlelight.lighting.SableMarkers.boxNear(level,hull,muzzle);
  Vector3d linear=new Vector3d(-shellDirection.x*deltaV*mass,-shellDirection.y*deltaV*mass,-shellDirection.z*deltaV*mass);
  Vector3d arm=new Vector3d(muzzle.x-(box[0]+box[3])*0.5,muzzle.y-(box[1]+box[4])*0.5,muzzle.z-(box[2]+box[5])*0.5);
  Vector3d angular=arm.cross(linear,new Vector3d());
  PENDING.put(hull.getUniqueId(),new Pending(linear,angular,System.currentTimeMillis(),Math.max(1,LightingCommon.CANNON_RECOIL_STEPS.get())));
  if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light recoil: applied {}/tick to {} at {} (anchor={})",deltaV,hull.getUniqueId(),muzzle,marked!=null?"hull-marker":"largest-box");
 }
 /** Called from the marker's block entity inside the physics step: the only moment a push sticks. */
 public static void step(dev.ryanhcode.sable.sublevel.ServerSubLevel body,dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle){
  Pending pending=PENDING.get(body.getUniqueId());
  if(pending==null)return;
  if(System.currentTimeMillis()-pending.at()>2000L){PENDING.remove(body.getUniqueId());return;}
  var before=handle.getLinearVelocity();
  handle.applyLinearAndAngularImpulse(pending.linear(),pending.angular(),true);
  // The angular pair of the delta is passed through untouched, so a player can read from this line
  // whether the kick actually rotated the hull or only slid it.
  double angularSpeed=handle.getAngularVelocity().length();
  // One step is over: the rest of the kick keeps arriving on the following steps, the way a
  // thruster keeps pushing, so a stabiliser cannot quietly erase the whole thing at once.
  if(pending.steps()>1)PENDING.put(body.getUniqueId(),new Pending(pending.linear(),pending.angular(),pending.at(),pending.steps()-1));
  else PENDING.remove(body.getUniqueId());
  var after=handle.getLinearVelocity();
  if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light recoil: velocity delta {} applied in the physics step on body {} (speed {} -> {}, inverseMass={}, stepsLeft={}, angular={})",pending.linear(),body.getUniqueId(),String.format(java.util.Locale.ROOT,"%.4f",before.length()),String.format(java.util.Locale.ROOT,"%.4f",after.length()),String.format(java.util.Locale.ROOT,"%.3e",body.getMassTracker().getInverseMass()),pending.steps()-1,String.format(java.util.Locale.ROOT,"%.4f",angularSpeed));
 }
}