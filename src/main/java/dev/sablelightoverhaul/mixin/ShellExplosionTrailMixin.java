package dev.sablelightoverhaul.mixin;

import java.util.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.sablelightoverhaul.client.ExplosionTrailMesh;
import dev.sablelightoverhaul.client.ImpactExplosionSmoke;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cloud emitter is independent of CBC's blast-wave and audio emitter. */
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.explosions.ShellExplosionCloudParticle",remap=false)
abstract class ShellExplosionTrailMixin {
    @Shadow @Final private float power;
    @Shadow @Final private boolean isPlume;
    @Unique private Boolean slo$mesh;
    @Unique private boolean slo$checked;

    @Inject(method="tick", at=@At("HEAD"), cancellable=true)
    private void slo$impactVisual(CallbackInfo ci) {
        if (slo$checked) return;
        slo$checked = true;
        var position = (ParticlePositionAccessor)this;
        if (ImpactExplosionSmoke.spawn(new Vec3(position.slo$x(), position.slo$y(), position.slo$z()), power, isPlume)) {
            ((Particle)(Object)this).remove();
            ci.cancel();
        }
    }
    @WrapOperation(method="tick",require=1,allow=1,at=@At(value="INVOKE",target="Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<?> slo$trail(List<?> trails,Operation<Iterator<?>> original){
        if(slo$mesh==null){
            slo$mesh=ExplosionTrailMesh.available();
            if(slo$mesh&&!trails.isEmpty()){
                var p=(ParticlePositionAccessor)this;
                ExplosionTrailMesh.spawn(new Vec3(p.slo$x(),p.slo$y(),p.slo$z()),power,isPlume,trails.size());
                trails.clear();
            }
        }
        return slo$mesh?Collections.emptyIterator():original.call(trails);
    }
}
