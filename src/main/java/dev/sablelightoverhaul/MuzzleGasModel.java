package dev.sablelightoverhaul;

/** Controlled smoke geometry; distinct from the accumulated muzzle haze mass. */
public final class MuzzleGasModel {
    private MuzzleGasModel() {}
    public enum SmokeStyle { NATIVE, COMPACT }
    public static int smokeLifetime(int requested,SmokeStyle style) {
        return Math.max(style==SmokeStyle.NATIVE?30:1,requested);
    }
    /** Native CBC fades the release at birth, then lets each child spend its own cooling curve. */
    public static float smokeBirthAlpha(float initial,float release,float from,float rate,SmokeStyle style) {
        float factor=style==SmokeStyle.NATIVE?1-Math.max(0,release-from)*rate:1-release*0.6f;
        return Math.clamp(initial*factor,0,1);
    }
    /** Distribute one shot's finite smoke quantity, independently of its release duration. */
    public static int smokeChildren(int total,int releaseTicks,int tick,int quality) {
        int amount=(int)Math.round(Math.clamp(total,0,128)*Math.clamp(quality,1,5)/5.0);
        int duration=Math.max(1,releaseTicks);
        if(tick<0||tick>=duration)return 0;
        return ((tick+1)*amount)/duration-(tick*amount)/duration;
    }
    /**
     * Initial axial speed before the per-puff brake envelope. The cloud is meant to be thrown
     * quickly and then slow down sharply, not drift at a constant rate; cloud.forward scales
     * this and cloud.brake controls the decay.
     */
    public static double smokeSpeed(double travel,double releaseProgress) {
        return travel*0.12;
    }
    /** Geometry only: keep the server's scatter/travel independent of this per-client puff override. */
    public static float smokeHalfWidth(float inherited,float override,float scale,float jitter) {
        return Math.max(.05f,(override>0?override:inherited)*scale*jitter);
    }
    public static float nativeSmokeGrowth(float progress) { return 0.9f+3.2f*Math.clamp(progress,0,1); }
    public static float nativeSmokeAlpha(float birth,float progress) { float p=Math.clamp(progress,0,1);return Math.clamp(birth*(1-p*p),0,1); }
    public static float smokeGrowth(float progress,float expansion) {
        return 1+(Math.max(1,expansion)-1)*(float)Math.sqrt(Math.clamp(progress,0,1));
    }
    /**
     * Tank cloud opacity from a start fraction, a rate multiplier and a curve exponent. At
     * rate >= 1 the curve reaches exactly zero before the particle lifetime ends, so removal is
     * never the moment the alpha pops out; rate 0 disables the fade for A/B.
     */
    /** Per-child lifetime around the configured base; never below 2 ticks, never above 200. */
    public static int tankLifetime(int base,float jitter,float roll) {
        float factor=1+(Math.clamp(roll,0,1)*2-1)*Math.clamp(jitter,0,1);
        return Math.clamp(Math.round(Math.max(1,base)*factor),2,200);
    }
    public static float tankFadeAlpha(float birth,float progress,float start,float rate,float curve) {
        float p=Math.clamp(progress,0,1);
        float s=Math.clamp(start,0,0.999f);
        if(p<=s || rate<=0) return Math.clamp(birth,0,1);
        float q=Math.clamp((p-s)/(1-s)*rate,0,1);
        return Math.clamp(birth*(float)Math.pow(1-q,Math.max(0.05f,curve)),0,1);
    }
}
