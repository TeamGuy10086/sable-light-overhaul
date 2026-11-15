package dev.sablelightoverhaul;

/** Alpha-weighted sRGB representative color of NativeImage ABGR pixels. No transparent RGB bleed. */
public final class ParticleColorAverage {
    private long alpha,red,green,blue;
    public void add(int abgr){
        int a=abgr>>>24;alpha+=a;red+=(long)(abgr&255)*a;
        green+=(long)((abgr>>>8)&255)*a;blue+=(long)((abgr>>>16)&255)*a;
    }
    public int color(){return alpha==0?-1:((int)(red/alpha)<<16)|((int)(green/alpha)<<8)|(int)(blue/alpha);}
    public static int tint(int rgb,int tint,double shade){
        int r=(int)Math.round(((rgb>>>16)&255)*((tint>>>16)&255)/255.0*shade);
        int g=(int)Math.round(((rgb>>>8)&255)*((tint>>>8)&255)/255.0*shade);
        int b=(int)Math.round((rgb&255)*(tint&255)/255.0*shade);
        return (Math.clamp(r,0,255)<<16)|(Math.clamp(g,0,255)<<8)|Math.clamp(b,0,255);
    }
}
