package dev.cbcmuzzlelight.client;
import dev.cbcmuzzlelight.*;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class LightProfile {
    public final ModConfigSpec.DoubleValue front,rear,width,height,cutoff,core,middle,tail,minimum,decay,reference,cap;
    public final ModConfigSpec.IntValue duration,hold;
    private final boolean diamond;
    public LightProfile(ModConfigSpec.Builder b,boolean diamond,double f,double r,double w,double cut,
                        int ticks,int peak,double rate,double ref) {
        this.diamond=diamond;
        b.push("shape");
        front=b.comment("Forward distance from the light source to zero brightness, at reference size.").defineInRange("forwardRange",f,0.1,96.0);
        rear=b.defineInRange("rearRange",r,0.1,96.0);
        width=b.comment("Sideways radius; shape is widest at the source.").defineInRange("sideRange",w,0.1,96.0);
        height=b.comment("Radius perpendicular to the gun axis and side axis; world-up reference, not ship roll.").defineInRange("verticalRange",w,0.1,96.0);
        cutoff=b.comment("Rear clipping distance; approximates muzzle masking, not real occlusion. Set >= rearRange to disable.").defineInRange("rearClipDistance",cut,0.0,96.0);
        b.pop().push("spatial");
        core=b.defineInRange("coreFraction",0.25,0.0,0.9);
        middle=b.comment("Middle segment fraction of the distance remaining after the core.").defineInRange("middleFraction",0.55,0.05,0.95);
        tail=b.comment("Tail starting brightness; capped so outer falloff is slower than middle.").defineInRange("tailBrightness",0.2,0.0,0.9);
        b.pop().push("time");
        duration=b.comment("Stage lifetime in ticks. Smoke time starts after blast stage; each stage has its own clock.").defineInRange("durationTicks",ticks,1,600);
        hold=b.defineInRange("peakHoldTicks",peak,0,600);
        minimum=b.defineInRange("minimumBrightness",0.0,0.0,1.0);
        decay=b.comment("Fraction of initial brightness lost per tick after hold.").defineInRange("decayPerTick",rate,0.0,1.0);
        b.pop().push("scaling");
        reference=b.comment(diamond ? "Unused for autocannon (fixed size)." : "Blast: effective CBC charge power. Smoke: heat-weighted rendered quad half-size.").defineInRange("referenceSize",ref,0.1,64.0);
        cap=b.comment("Maximum individual range after scaling.").defineInRange("maximumRange",64.0,1.0,96.0);
        b.pop();
    }
    public LightCurve curve() { return new LightCurve(duration.get(),minimum.get(),decay.get(),hold.get()); }
    public AreaLight light(double x,double y,double z,double dx,double dy,double dz,double size,double strength) {
        double scale=diamond?1:Math.max(0,size)/reference.get();
        double f=LightSizing.radius(front.get(),scale,cap.get()),r=LightSizing.radius(rear.get(),scale,cap.get());
        double w=LightSizing.radius(width.get(),scale,cap.get()),h=LightSizing.radius(height.get(),scale,cap.get());
        double c=core.get(),fraction=middle.get();
        return AreaLight.directional(x,y,z,dx,dy,dz,f,r,w,h,Math.min(cap.get(),cutoff.get()*scale),
                c,c+(1-c)*fraction,Math.min(tail.get(),(1-fraction)*0.95),15*strength,diamond);
    }
}
