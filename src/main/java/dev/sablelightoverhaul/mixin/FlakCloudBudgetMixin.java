package dev.sablelightoverhaul.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sablelightoverhaul.client.*;
import dev.sablelightoverhaul.lighting.ImpactParticleData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import java.util.*;
import rbasamoyai.createbigcannons.effects.particles.smoke.FlakSmokeParticleData;
/** Replace the CBC smoke and flame children while suppressing the stock explosion sprite. */
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.explosions.FlakCloudParticle",remap=false)
public abstract class FlakCloudBudgetMixin {
 @Unique private int slo$flameIndex;
 @Unique private boolean slo$cloudSpawned;
 @WrapOperation(method="tick",require=1,allow=1,at=@At(value="INVOKE",target="Ljava/util/List;iterator()Ljava/util/Iterator;"))
 private Iterator<?> slo$compactTrails(List<?> list,Operation<Iterator<?>> original){
  if(!ExtraConfig.FLAK_COMPACT.get())return original.call(list);
  return Collections.emptyIterator();
 }
 @WrapOperation(method="tick",require=1,allow=1,at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
 private void slo$hideCrescent(ClientLevel level,ParticleOptions particle,double x,double y,double z,double dx,double dy,double dz,Operation<Void> original){
  if(particle!=ParticleTypes.EXPLOSION)original.call(level,particle,x,y,z,dx,dy,dz);
 }
 @WrapOperation(method="tick",require=3,allow=3,at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
 private void slo$largerMainCloud(ClientLevel level,ParticleOptions particle,boolean force,double x,double y,double z,double dx,double dy,double dz,Operation<Void> original){
  if(ExtraConfig.FLAK_COMPACT.get()&&particle instanceof FlakSmokeParticleData smoke&&smoke.scale()==3.0f){
   if(!slo$cloudSpawned){
    slo$cloudSpawned=true;
    var center=(ParticlePositionAccessor)this;
    FlakCloudPuffParticle.spawn(level,center.slo$x(),center.slo$y(),center.slo$z(),smoke.lifetime());
   }
   return;
  }
  if(ExtraConfig.FLAK_COMPACT.get()&&particle==ParticleTypes.FLAME){
   if((slo$flameIndex++&1)==0){
    var spark=new ImpactParticleData(.18F,0xFFC070,5,0F,ImpactParticleData.FLAK_STREAK,1F);
    original.call(level,spark,force,x,y,z,dx*8,dy*8,dz*8);
   }
   return;
  }
  original.call(level,particle,force,x,y,z,dx,dy,dz);
 }
}
