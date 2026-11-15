package dev.sablelightoverhaul.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sablelightoverhaul.client.ClientFlashes;
import dev.sablelightoverhaul.client.SmokeTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticle",remap=false)
public abstract class BigPlumeMixin extends NoRenderParticle {
 @Shadow @Final private Vec3 direction;
 @Shadow @Final private float power;
 @Unique private boolean slo$owned;
 @Unique private int slo$count,slo$release;
 @Unique private Vec3 slo$origin;
 @Unique private SmokeTracker.Group slo$group;
 /** The shot kind this plume belongs to (0 big cannon, 1/3 autocannon, 2 tank cannon), or -1 until asked. */
 @Unique private int slo$kind = -1;
 protected BigPlumeMixin(ClientLevel level,double x,double y,double z) { super(level,x,y,z); }
 @WrapMethod(method="tick")
 private void slo$captureChildren(Operation<Void> original) {
float life = lifetime <= 0 ? 1.0F : Math.min(1.0F, (float) age / lifetime);
// Both CBC's big cannon and CBCMW's medium cannon spawn this same particle, with no weapon kind on the
// wire. The shot itself is already here as a flash carrying its kind, and the generator is born at the
// muzzle in the same tick, so the flash list is the cheap, exact answer. The nearest contraption is only
// the fallback for a plume whose flash was never sent (muzzle-flash switch off, or culled).
if(slo$kind<0){
    Vec3 slo$aim=direction.normalize();
    int slo$shotKind=ClientFlashes.STORE.plumeKind(x,y,z,slo$aim.x,slo$aim.y,slo$aim.z,level.getGameTime());
    if(slo$shotKind<0) slo$shotKind=dev.sablelightoverhaul.client.PlumeSource.tankCannon((ClientLevel)level,x,y,z)?2:0;
    slo$kind=slo$shotKind;
}
// Decide once per emitter. Successful takeover replaces children, including at the live budget cap.
if(age==0&&slo$kind==0&&Float.isFinite(power)&&power>3&&dev.sablelightoverhaul.client.BigSmokeParticle.available()){
 // FireDispatch places the flash 1.6 blocks ahead of CBC's emitter; new smoke uses that same origin.
 slo$owned=true;slo$origin=new Vec3(x,y,z).add(direction.normalize().scale(1.6));slo$group=SmokeTracker.create(level.getGameTime());
 slo$count=dev.sablelightoverhaul.client.BigSmokeParticle.lobeCount(power);
 slo$release=dev.sablelightoverhaul.client.BigSmokeConfig.RELEASE_TICKS.get();
}
if(slo$owned){
 Vec3 axis=direction.normalize();
 if(slo$group.token==0&&age<=4&&ClientFlashes.owns(level))slo$group.token=ClientFlashes.STORE.attachBigPlume(x+axis.x*1.6,y+axis.y*1.6,z+axis.z*1.6,axis.x,axis.y,axis.z,level.getGameTime());
 dev.sablelightoverhaul.client.BigSmokeParticle.emit((ClientLevel)level,slo$origin,axis,slo$group,age,slo$count,slo$release,power);
 if(++age>=slo$release)remove();
 return;
}
boolean slo$tank=slo$kind==2;
float sloInitial = (float) (slo$tank
        ? dev.sablelightoverhaul.client.ExtraConfig.TANK_PLUME_ALPHA.get()
        : dev.sablelightoverhaul.client.ExtraConfig.BIG_PLUME_ALPHA.get()).doubleValue();
float sloFrom = (float) (slo$tank
        ? dev.sablelightoverhaul.client.ExtraConfig.TANK_PLUME_FADE_FROM.get()
        : dev.sablelightoverhaul.client.ExtraConfig.BIG_PLUME_FADE_FROM.get()).doubleValue();
float sloRate = (float) (slo$tank
        ? dev.sablelightoverhaul.client.ExtraConfig.TANK_PLUME_FADE_RATE.get()
        : dev.sablelightoverhaul.client.ExtraConfig.BIG_PLUME_FADE_RATE.get()).doubleValue();
// Set before any of the light tracking can bail out. How CBC's own smoke looks is not conditional on this
// mod owning a light in this level, and while this lived below that early return a cannon whose flash we
// were not tracking drew at full opacity no matter what its keys said.
if(slo$group==null) slo$group=SmokeTracker.create(level.getGameTime());
slo$group.birthAlpha = life <= sloFrom ? sloInitial
        : Math.max(0.0F, Math.min(1.0F, sloInitial * (1.0F - (life - sloFrom) * sloRate)));
  // The guard below is about tracking a light, not about the smoke's opacity: the children CBC is about to
  // spawn must still be born inside this group, or CannonSmokeMixin finds no current group and multiplies
  // nothing -- which is why neither cannon faded no matter what its keys said.
  if (!ClientFlashes.owns(level)) {
   var sloHeld=SmokeTracker.enter(slo$group);
   try { original.call(); } finally { SmokeTracker.enter(sloHeld); }
   return;
  }
  Vec3 axis=direction.normalize();
  if(slo$origin==null) slo$origin=new Vec3(x,y,z).add(axis.scale(1.6));
  if(slo$group==null) slo$group=SmokeTracker.create(level.getGameTime());
  if(slo$group.token==0 && age<=4) slo$group.token=ClientFlashes.STORE.attachBigPlume(slo$origin.x,slo$origin.y,slo$origin.z,axis.x,axis.y,axis.z,level.getGameTime());
  // The big cannon is the one weapon whose plume is CBC's own particle, so its opacity has to be handed over
 // here rather than chosen by us somewhere else. It is sampled once per tick, from the plume's own life:
 // every child constructed in this tick takes the value as it is built, so the plume fades as it ages while
 // each puff stays as opaque as the day it was born -- which is how CBC's own smoke behaves.
 var previous=SmokeTracker.enter(slo$group);
  try { original.call(); } finally { SmokeTracker.enter(previous); }
 }
}
