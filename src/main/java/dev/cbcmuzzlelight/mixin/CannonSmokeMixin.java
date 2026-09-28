package dev.cbcmuzzlelight.mixin;
import dev.cbcmuzzlelight.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Fallback smoke subclasses this target, so both constructors and inherited ticks are covered. */
@Mixin(targets="rbasamoyai.createbigcannons.effects.particles.smoke.CannonSmokeParticle",remap=false)
public abstract class CannonSmokeMixin extends TextureSheetParticle implements TrackedSmoke {
    @Unique private SmokeTracker.Group muzzle$group;
    protected CannonSmokeMixin(ClientLevel level,double x,double y,double z) { super(level,x,y,z); }
    @Inject(method="<init>",at=@At("RETURN"))
    private void muzzle$capture(CallbackInfo ci) {
        muzzle$group=SmokeTracker.current();
        if(muzzle$group!=null) muzzle$group.capture(this);
    }
    public double muzzle$x() { return x; } public double muzzle$y() { return y; } public double muzzle$z() { return z; }
    public double muzzle$size() { return getQuadSize(0); }
    public boolean muzzle$alive() { return !removed; }
    public double muzzle$heat() { return muzzle$group==null || lifetime<=0 ? 0 : Math.max(0,1-age/(lifetime*muzzle$group.hotFraction)); }
}
