package dev.sablelightoverhaul;

/** Shared envelopes for a pressure-driven plume. Temperature never controls smoke coverage. */
public final class BigSmokeModel {
 private BigSmokeModel(){}
 /** Same factor for length, transverse radius and flash-linked minimum; quantity stays independent. */
 public static double chargeScale(double power,double reference,double exponent,double maximum){
  return Math.clamp(Math.pow(Math.max(.01,power)/Math.max(.01,reference),Math.max(0,exponent)),.1,Math.max(.1,maximum));
 }
 public static double front(double seconds){return -Math.expm1(-Math.max(0,seconds)*1.2);}
 public static double spreadProgress(double seconds){return -Math.expm1(-Math.max(0,seconds-.18)/2.4);}
 public static double expansion(double seconds,double amount){return 1+Math.max(0,amount)*spreadProgress(seconds);}
 /** Residual outward motion shared by every lobe of the shot, after the fast jet. */
 public static double outwardDrift(double seconds){return .28*(-Math.expm1(-Math.max(0,seconds-.20)/5.1));}
 /** Additional transverse travel along the lobe's fixed radial heading. */
 public static double radialTravel(double seconds,double expansion){
  if(expansion<=0)return 0;
  double amount=expansion, progress=spreadProgress(seconds);
  // Normalized volume increase for a diameter multiplier of 1 + amount * progress.
  double volume=progress*(3+amount*progress*(3+amount*progress))/(3+amount*(3+amount));
  return 1.35*volume*volume;
 }
 public static double breakup(double spreadProgress){
  double t=Math.clamp((spreadProgress-.44)/.42,0,1);return t*t*(3-2*t);
 }
 public static double growth(double seconds){return growth(seconds,.8);}
 public static double growth(double seconds,double amount){return (.28+.72*(-Math.expm1(-Math.max(0,seconds)*14)))*expansion(seconds,amount);}
 /** Interleaved stations: every release batch fills the cloud, rather than appending a faster tip. */
 public static double station(int index){return Integer.reverse(index+1)/4294967296.0+(Integer.reverse(index+1)<0?1:0);}
 public static double linkedRadius(double base,double flash,double link){return base+(Math.clamp(flash,base,base*3)-base)*Math.clamp(link,0,1);}
 /** A short absolute-time fade-in; a long smoke lifetime must not delay the flash handoff. */
 public static double coverage(double seconds,double lifetime,double expansion,double fadeFrom){
  double p=Math.clamp(seconds/Math.max(.05,lifetime),0,1);
  double from=Math.clamp(fadeFrom,0,.95),fade=Math.clamp((p-from)/(1-from),0,1);
  return Math.clamp(seconds/.08,0,1)*(1-fade*fade*(3-2*fade))/Math.pow(Math.max(1,expansion),1.6);
 }
 /** Shared near/far thermal clock for material and external smoke lights; independent of coverage. */
 public static double thermalTicks(double base,double station,double rootScale,double tipScale,double variation){
  double p=Math.clamp(station,0,1);p=p*p*(3-2*p);
  return Math.max(1,base*(Math.max(.05,rootScale)+(Math.max(.05,tipScale)-Math.max(.05,rootScale))*p)*Math.clamp(variation,.85,1.15));
 }
 public static double heat(double age,double hotTicks){return Math.max(0,1-age/Math.max(1,hotTicks));}
 public static int encodedSeed(int seed,double thermalAge){return 128+(seed&63)+64*(int)Math.clamp(Math.floor(thermalAge*32),0,255);}
 public static int children(int total,int releaseTicks,int tick){
  if(total<=0||releaseTicks<=0||tick<0||tick>=releaseTicks)return 0;
  return (int)(((long)(tick+1)*total+releaseTicks-1)/releaseTicks)-(int)(((long)tick*total+releaseTicks-1)/releaseTicks);
 }
}
