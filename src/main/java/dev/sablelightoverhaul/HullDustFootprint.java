package dev.sablelightoverhaul;

import java.util.*;

/** Conservative continuous planar support, spanning block seams but not gaps or holes. */
public final class HullDustFootprint {
    private HullDustFootprint(){}
    public record Rect(double minU,double minV,double maxU,double maxV){}
    /** Radius of a centred square contained in the union. Its inscribed disk bounds the smoke. */
    public static double radius(List<Rect> faces,double u,double v,double requested){
        if(!Double.isFinite(u+v+requested)||requested<=0||faces.isEmpty())return 0;
        double lo=0,hi=requested;
        for(int i=0;i<10;i++){double mid=(lo+hi)*.5;if(covers(faces,u,v,mid))lo=mid;else hi=mid;}
        // A little separation from the mathematical edge; no noise/alpha outside the support plane.
        return Math.max(0,lo-.025);
    }
    private static boolean covers(List<Rect> faces,double u,double v,double r){
        double left=u-r,right=u+r,bottom=v-r,top=v+r;
        var cuts=new ArrayList<Double>();cuts.add(left);cuts.add(right);
        for(var f:faces){if(f.minU>left&&f.minU<right)cuts.add(f.minU);if(f.maxU>left&&f.maxU<right)cuts.add(f.maxU);}
        cuts.sort(Double::compare);
        var intervals=new ArrayList<Rect>();
        for(int i=1;i<cuts.size();i++){
            if(cuts.get(i)-cuts.get(i-1)<1e-9)continue;
            double x=(cuts.get(i)+cuts.get(i-1))*.5;intervals.clear();
            for(var f:faces)if(f.minU<=x&&f.maxU>=x&&f.maxV>bottom&&f.minV<top)intervals.add(f);
            intervals.sort(Comparator.comparingDouble(Rect::minV));double end=bottom;
            for(var f:intervals){if(f.minV>end+1e-8)return false;end=Math.max(end,f.maxV);if(end>=top-1e-8)break;}
            if(end<top-1e-8)return false;
        }
        return true;
    }
}
