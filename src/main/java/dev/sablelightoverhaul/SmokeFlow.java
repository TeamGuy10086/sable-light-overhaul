package dev.sablelightoverhaul;

/** Curl of three seeded smooth scalar potentials. No per-frame RNG or normalized-vector sinks. */
public final class SmokeFlow {
    private SmokeFlow() {}
    public record Vector(double x,double y,double z) {}
    public static Vector sample(double x,double y,double z,double time,long seed) {
        x+=time*.17;y-=time*.11;z+=time*.13;
        Vector a=gradient(x,y,z,seed);
        Vector b=gradient(x+17.3,y-9.1,z+5.7,seed^0x632BE59BD9B4E019L);
        Vector c=gradient(x-3.7,y+11.9,z-21.3,seed^0x9E3779B97F4A7C15L);
        // Each potential derivative is bounded by 3.75; a curl component by 7.5.
        return new Vector((c.y-b.z)*.25,(a.z-c.x)*.25,(b.x-a.y)*.25);
    }
    private static Vector gradient(double x,double y,double z,long seed) {
        long ix=(long)Math.floor(x),iy=(long)Math.floor(y),iz=(long)Math.floor(z);
        double fx=x-ix,fy=y-iy,fz=z-iz;
        double sx=fade(fx),sy=fade(fy),sz=fade(fz);
        double dx=derivative(fx),dy=derivative(fy),dz=derivative(fz);
        double gx=0,gy=0,gz=0;
        for(int i=0;i<2;i++)for(int j=0;j<2;j++)for(int k=0;k<2;k++) {
            double value=hash(ix+i,iy+j,iz+k,seed);
            double wx=i==0?1-sx:sx,wy=j==0?1-sy:sy,wz=k==0?1-sz:sz;
            gx+=value*(i==0?-dx:dx)*wy*wz;
            gy+=value*wx*(j==0?-dy:dy)*wz;
            gz+=value*wx*wy*(k==0?-dz:dz);
        }
        return new Vector(gx,gy,gz);
    }
    private static double fade(double t){return t*t*t*(t*(t*6-15)+10);}
    private static double derivative(double t){return 30*t*t*(t-1)*(t-1);}
    private static double hash(long x,long y,long z,long seed){
        long h=seed^x*0x9E3779B97F4A7C15L^y*0xD1B54A32D192ED03L^z*0x94D049BB133111EBL;
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;
        return ((h^(h>>>31))>>>11)*0x1.0p-52-1;
    }
}
