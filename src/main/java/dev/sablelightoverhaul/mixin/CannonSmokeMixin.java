package dev.sablelightoverhaul.mixin;
import dev.sablelightoverhaul.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/**
 * Fallback smoke subclasses this target, so both constructors and inherited ticks are covered.
 *
 * <p>The opacity this mod adds to CBC's own smoke is remembered when a puff is born and spent at the end of
 * every tick, not once at birth. CBC's own tick writes `alpha` -- it fades its smoke over its life -- so a
 * multiply in the constructor was overwritten before the particle was ever drawn, which is why no value of the
 * key, down to 0.05, changed anything on screen. Multiplying at the end of the tick keeps CBC's own fade and
 * puts the cannon's scale on top of it.
 *
 * <p>The field is inherited from TextureSheetParticle -- which is why this mixin extends it -- so there is no
 * shadow and no accessor, and nothing that can fail to resolve when the game loads.
 */
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.smoke.CannonSmokeParticle",remap=false)
public abstract class CannonSmokeMixin extends TextureSheetParticle implements TrackedSmoke {
    @Unique private SmokeTracker.Group slo$group;
    @Unique private float slo$birthAlpha = 1.0F;
    @Unique private float slo$expansion,slo$fadeFrom,slo$fadeRate;
    @Unique private boolean slo$controlledTank;
    protected CannonSmokeMixin(ClientLevel level,double x,double y,double z) { super(level,x,y,z); }
    @Inject(method="<init>",at=@At("RETURN"))
    private void slo$capture(CallbackInfo ci) {
        slo$group=SmokeTracker.current();
        if(slo$group!=null) {
            slo$birthAlpha=slo$group.birthAlpha;
            slo$controlledTank=slo$group.controlledTank;
            slo$expansion=slo$group.controlledExpansion;
            slo$fadeFrom=slo$group.controlledFadeFrom;
            slo$fadeRate=slo$group.controlledFadeRate;
            slo$group.capture(this);
            if(slo$controlledTank) MuzzleVisualProbe.nativeTankSmoke();
        }
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void slo$alpha(CallbackInfo ci) {
        if(slo$birthAlpha!=1.0F) this.alpha*=slo$birthAlpha;
        if(slo$expansion>0 && lifetime>0) {
            float progress=(float)age/lifetime;
            if(progress>slo$fadeFrom) this.alpha*=Math.clamp(1-(progress-slo$fadeFrom)*slo$fadeRate,0,1);
        }
        if(slo$controlledTank) MuzzleVisualProbe.smokeTick(!removed,age,lifetime,alpha);
    }
    @Inject(method="getQuadSize",at=@At("HEAD"),cancellable=true)
    private void slo$controlledGrowth(float partialTick,CallbackInfoReturnable<Float> ci) {
        if(slo$expansion>0) ci.setReturnValue(quadSize*dev.sablelightoverhaul.MuzzleGasModel.smokeGrowth(
            lifetime<=0?1:(age+partialTick)/lifetime,slo$expansion));
    }
    public boolean slo$tankControlled() { return slo$controlledTank; }
    public boolean slo$muzzleManaged() { return slo$group!=null; }
    @Inject(method="getLightColor",at=@At("RETURN"),cancellable=true)
    private void slo$smokeMaterial(float partialTick,CallbackInfoReturnable<Integer> ci) {
        // Only the shader-compatible subclass joins our particle batch; keep CBC's custom shader untouched.
        if(slo$group!=null && (Object)this instanceof rbasamoyai.createbigcannons.effects.particles.smoke.FallbackCannonSmokeParticle)
            ci.setReturnValue(MuzzleParticleLayer.markLight(ci.getReturnValue(),false));
    }
    public double slo$x() { return x; } public double slo$y() { return y; } public double slo$z() { return z; }
    // Where the particle was at the start of this tick: a host reports both ends of its step, and for a
    // particle those are xo/yo/zo, which vanilla updates before it moves.
    public double slo$px() { return xo; } public double slo$py() { return yo; } public double slo$pz() { return zo; }
    public double slo$size() { return getQuadSize(0); }
    public boolean slo$alive() { return !removed; }
    public double slo$heat() { return slo$group==null || lifetime<=0 ? 0 : Math.max(0,1-age/(lifetime*slo$group.hotFraction)); }
}
