package dev.cbcmuzzlelight.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbcmuzzlelight.client.ClientFlashes;
import dev.cbcmuzzlelight.client.SmokeTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticle",remap=false)
public abstract class BigPlumeMixin extends NoRenderParticle {
 @Shadow @Final private Vec3 direction;
 @Unique private Vec3 muzzle$origin;
 @Unique private SmokeTracker.Group muzzle$group;
 protected BigPlumeMixin(ClientLevel level,double x,double y,double z) { super(level,x,y,z); }
 @WrapMethod(method="tick")
 private void muzzle$captureChildren(Operation<Void> original) {
  if (!ClientFlashes.owns(level)) { original.call(); return; }
  Vec3 axis=direction.normalize();
  if(muzzle$origin==null) muzzle$origin=new Vec3(x,y,z).add(axis.scale(1.6));
  if(muzzle$group==null) muzzle$group=SmokeTracker.create(level.getGameTime());
  if(muzzle$group.token==0 && age<=4) muzzle$group.token=ClientFlashes.STORE.attachBigPlume(muzzle$origin.x,muzzle$origin.y,muzzle$origin.z,axis.x,axis.y,axis.z,level.getGameTime());
  var previous=SmokeTracker.enter(muzzle$group);
  try { original.call(); } finally { SmokeTracker.enter(previous); }
 }
}
