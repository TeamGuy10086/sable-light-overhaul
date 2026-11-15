package dev.sablelightoverhaul;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** A stable forward footprint shared by every connected hull/turret body. No world queries. */
public final class HullDustPattern {
    private HullDustPattern(){}
    private record Ranked(CannonDustPayload.Surface surface,double score,double forward,double side,double height){}
    private record Cell(int forward,int side,int height,boolean roof){}

    /** coverage=1 includes the whole hull; smaller values select its forward fraction along shot yaw. */
    public static List<CannonDustPayload.Surface> select(List<CannonDustPayload.Surface> candidates,
            Vec3 muzzle,Vec3 axis,double coverage,int limit){
        if(!Double.isFinite(coverage)||coverage<.05||coverage>1||limit<0)throw new IllegalArgumentException("Invalid hull footprint");
        if(candidates.isEmpty()||limit==0)return List.of();
        Vec3 forward=new Vec3(axis.x,0,axis.z);
        // Near-vertical fire has no reliable yaw; do not arbitrarily assign world north as the front.
        boolean hasYaw=forward.lengthSqr()>1e-6;
        forward=hasYaw?forward.normalize():new Vec3(1,0,0);
        Vec3 side=new Vec3(-forward.z,0,forward.x);
        double[] along=new double[candidates.size()],across=new double[along.length];
        for(int i=0;i<along.length;i++){
            Vec3 delta=candidates.get(i).position().subtract(muzzle);
            along[i]=delta.dot(forward);across[i]=delta.dot(side);
        }
        Arrays.sort(along);Arrays.sort(across);
        // Trim thin barrel/antenna outliers; a long gun must not drag the body centre to its muzzle.
        double rear=quantile(along,.10),front=quantile(along,.90),length=Math.max(1,front-rear);
        double left=quantile(across,.10),right=quantile(across,.90),halfWidth=Math.max(.5,(right-left)*.5);
        double middle=(left+right)*.5;
        var cells=new HashMap<Cell,List<Ranked>>();
        for(var surface:candidates){
            Vec3 delta=surface.position().subtract(muzzle);
            double f=(delta.dot(forward)-rear)/length,s=(delta.dot(side)-middle)/halfWidth;
            double gate=!hasYaw||coverage==1?1:smooth((f-(1-coverage))/Math.min(.2,coverage*.5));
            if(gate<=.01)continue;
            // Broad paired shoulders: fill the front deck and skirts, without a thin ring/stripe.
            double shoulder=.45+.55*Math.exp(-Math.pow((Math.abs(s)-.65)/.55,2));
            double facing=hasYaw?.45+.55*Math.max(0,surface.normal().dot(forward)):1;
            double score=gate*shoulder*facing;
            boolean roof=surface.normal().y>.55;
            Cell key=new Cell((int)Math.floor(f*8),(int)Math.floor(s*4),(int)Math.floor(delta.y*.5),roof);
            cells.computeIfAbsent(key,unused->new ArrayList<>()).add(new Ranked(surface,score,f,s,delta.y));
        }
        Comparator<Ranked> order=Comparator.comparingDouble(Ranked::score).reversed()
            .thenComparingDouble(Ranked::forward).thenComparingDouble(Ranked::side).thenComparingDouble(Ranked::height)
            .thenComparing(r->r.surface.body()==null?"":r.surface.body().toString())
            .thenComparingDouble(r->r.surface.local().x).thenComparingDouble(r->r.surface.local().y).thenComparingDouble(r->r.surface.local().z);
        for(var cell:cells.values())cell.sort(order);
        var result=new ArrayList<CannonDustPayload.Surface>();
        int cap=Math.min(limit,CannonDustPayload.MAX_SURFACES);
        // One source per spatial cell before additional sources; interleave roof and skirt cells.
        // There is no compulsory per-body quota that could pull an excluded rear body back in.
        for(int round=0;result.size()<cap;round++){
            var roofs=new ArrayList<Ranked>();var sides=new ArrayList<Ranked>();
            for(var entry:cells.entrySet())if(round<entry.getValue().size())
                (entry.getKey().roof?roofs:sides).add(entry.getValue().get(round));
            if(roofs.isEmpty()&&sides.isEmpty())break;
            roofs.sort(order);sides.sort(order);int a=0,b=0;
            while(result.size()<cap&&(a<roofs.size()||b<sides.size())){
                if(a<roofs.size())result.add(roofs.get(a++).surface);
                if(result.size()<cap&&b<sides.size())result.add(sides.get(b++).surface);
            }
        }
        return List.copyOf(result);
    }
    private static double quantile(double[] values,double q){return values[(int)Math.floor((values.length-1)*q)];}
    private static double smooth(double x){x=Math.clamp(x,0,1);return x*x*(3-2*x);}
}
