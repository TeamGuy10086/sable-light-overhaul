package dev.sablelightoverhaul;
/** Time coordinate of a supplied consecutive frame; never extrapolates across clock corrections. */
public final class FlightVisualClock {
 private FlightVisualClock(){}
 public static double time(long segmentEnd,boolean hold,double partial){return segmentEnd-1+(hold?1:Math.clamp(partial,0,1));}
}
