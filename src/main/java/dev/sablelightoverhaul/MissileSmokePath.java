package dev.sablelightoverhaul;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Bounded visual history, never a projectile simulator. Times are client ticks. */
public final class MissileSmokePath {
    public record Node(Vec3 position,double born,double station,int strip,double sourceTick) {
        public Node(Vec3 position,double born,double station,int strip){this(position,born,station,strip,born);}
    }
    public final ArrayDeque<Node> nodes=new ArrayDeque<>();
    private Vec3 anchor;
    private double anchorTime,station;
    private long anchorSequence;
    private long sequence=Long.MIN_VALUE;
    private int strip;
    public static final int MAX_NODES=256;
    public void sample(Vec3 pos,long sequence,double now,boolean emitting) {
        if(!finite(pos)||!Double.isFinite(now)||sequence<=this.sequence)return;
        this.sequence=sequence;
        if(!emitting){anchor=null;return;}
        if(anchor==null||now-anchorTime>10||now<anchorTime||pos.distanceToSqr(anchor)>128*128){
            strip++;anchor=pos;anchorTime=now;anchorSequence=sequence;add(new Node(pos,now,station,strip,sequence));return;
        }
        double distance=pos.distanceTo(anchor);
        if(distance<.35)return;
        int count=Math.min(64,Math.max(1,(int)Math.ceil(distance/2)));
        for(int i=1;i<=count;i++) {
            double t=(double)i/count;
            add(new Node(anchor.lerp(pos,t),anchorTime+(now-anchorTime)*t,station+distance*t,strip,
                    anchorSequence+((double)sequence-anchorSequence)*t));
        }
        station+=distance;anchor=pos;anchorTime=now;anchorSequence=sequence;
    }
    public boolean emitting(){return anchor!=null;}
    /** A temporary render view only. Never edits history or its sampling clock/sequence. */
    public record FrameNodes(List<Node> nodes,boolean liveHead) {}
    public FrameNodes frameNodes(long latestSequence,double displayTick,Vec3 nozzle,double now){
        if(!emitting()||!finite(nozzle)||!Double.isFinite(displayTick)||!Double.isFinite(now)
                ||latestSequence<sequence||displayTick>latestSequence)return new FrameNodes(List.copyOf(nodes),false);
        var visible=new ArrayList<Node>();Node future=null;
        for(var node:nodes){if(node.sourceTick()>displayTick){future=node;break;}visible.add(node);}
        if(visible.isEmpty())return new FrameNodes(visible,false);
        Node last=visible.getLast();
        // Do not reconnect a teleport, stopped strip, large packet gap, or a stale emission anchor.
        if((future!=null&&future.strip()!=last.strip())||nozzle.distanceToSqr(last.position())>128*128
                ||now-anchorTime>10)return new FrameNodes(visible,false);
        double distance=nozzle.distanceTo(last.position());
        double station=last.station()+distance;
        if(Math.abs(last.sourceTick()-displayTick)<1e-8)visible.removeLast();
        visible.add(new Node(nozzle,now,station,last.strip(),displayTick));
        return new FrameNodes(visible,true);
    }
    public void stop(){anchor=null;}
    public void expire(double now,double lifetime){while(!nodes.isEmpty()&&now-nodes.getFirst().born()>lifetime)nodes.removeFirst();}
    private void add(Node n){while(nodes.size()>=MAX_NODES)nodes.removeFirst();nodes.addLast(n);}
    public static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static double radius(double age,double initial,double expansion){return initial+expansion*Math.sqrt(Math.max(0,age)/20);}
    public static double opacity(double age,double lifetime){double t=Math.clamp(age/lifetime,0,1);return .85*(1-t*t)*(1-Math.pow(t,6));}
}
