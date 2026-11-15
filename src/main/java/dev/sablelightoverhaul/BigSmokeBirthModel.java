package dev.sablelightoverhaul;

/** Cold clouds replace hot slots; geometry and drift are independent of emissive age. */
public final class BigSmokeBirthModel {
 private BigSmokeBirthModel(){}
 public static boolean isCold(int index,double fraction){
  if(index<0)return false;
  double f=Math.clamp(fraction,0,1);
  return Math.floor((index+1.0)*f+.5)>Math.floor(index*f+.5);
 }
 public static double growth(double seconds,double expansion,boolean cold,double coldScale){
  if(!cold)return BigSmokeModel.growth(seconds,expansion);
  double t=Math.max(0,seconds);
  return Math.max(.25,coldScale)*(.60+.40*(-Math.expm1(-t*9)))*BigSmokeModel.expansion(t,expansion);
 }
 public static double curlSide(double seconds,double phase,double strength){
  double t=Math.max(0,seconds),angle=2.2*(-Math.expm1(-t*.9));
  return Math.max(0,strength)*(-Math.expm1(-t*2))*(Math.sin(phase+angle)-Math.sin(phase));
 }
 public static double curlUp(double seconds,double phase,double strength){
  double t=Math.max(0,seconds),angle=2.2*(-Math.expm1(-t*.9));
  return Math.max(0,strength)*(-Math.expm1(-t*2))*(Math.cos(phase)-Math.cos(phase+angle));
 }
}
