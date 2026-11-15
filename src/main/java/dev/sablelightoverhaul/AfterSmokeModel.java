package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleFunction;

/** Fixed 20 Hz world-space smoke. Source motion affects births, never the existing cloud. */
public final class AfterSmokeModel {
    public static final double STEP_SECONDS=.05;
    public static final int MAX_NODES=64;
    public record Point(double x,double y,double z){
        public Point add(Point p){return new Point(x+p.x,y+p.y,z+p.z);}
        public Point subtract(Point p){return new Point(x-p.x,y-p.y,z-p.z);}
        public Point scale(double k){return new Point(x*k,y*k,z*k);}
        public double length(){return Math.sqrt(x*x+y*y+z*z);}
        public Point normalized(){double n=length();return n>1e-8?scale(1/n):new Point(0,0,0);}
        public Point lerp(Point b,double t){return add(b.subtract(this).scale(t));}
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public record Pose(Point position,Point direction){
        public boolean finite(){return position!=null&&direction!=null&&position.finite()&&direction.finite()
            &&direction.length()>1e-8&&direction.length()<1e8&&position.length()<1e9;}
    }
    public record Settings(double delaySeconds,double emitSeconds,double lifeSeconds,double birthDiameter,
            double endDiameter,double exitSpeed,double riseSpeed,double inheritMotion,double curl,double opacity){}
    public final class Node {
        public final long id;
        public final int chain;
        public Point previous,position;
        public double age;
        public final double u;
        public final int materialSeed;
        private Point velocity;
        private final double density,phase;
        private Node(Point p,Point velocity,double u,double density,double phase){
            this.id=++serial;this.chain=AfterSmokeModel.this.chain;this.previous=p;this.position=p;
            this.velocity=velocity;this.u=u;this.density=density;this.phase=phase;
            this.materialSeed=(int)(random(id*13)*64);
        }
        private double visualAge(double partial){return Math.max(0,age-STEP_SECONDS*(1-clamp(partial)));}
        public double diameter(double partial){double t=clamp(visualAge(partial)/settings.lifeSeconds);
            return settings.birthDiameter+(settings.endDiameter-settings.birthDiameter)*(1-(1-t)*(1-t));}
        public double alpha(double partial){double t=visualAge(partial),life=settings.lifeSeconds;
            // A birth carries a share of this tick's emission, independent of motion-driven sub-sampling.
            // Expansion reduces optical thickness as well as eventual lifetime fade.
            double dilution=Math.min(1,Math.pow(settings.birthDiameter/diameter(partial),.65));
            double tau=settings.opacity*density*.65*dilution*smooth(t/.12)*(1-smooth((t/life-.45)/.55));
            return -Math.expm1(-tau);}
        public double aspect(double partial){return .85+random(id*7)*.5+.12*smooth(visualAge(partial)/settings.lifeSeconds);}
        public double roll(double partial){return phase+(random(id*19)-.5)*.45*visualAge(partial);}
        private void tick(Point windStep){
            previous=position;age+=STEP_SECONDS;
            Point local=position.subtract(fieldOrigin).scale(.85);
            var flow=SmokeFlow.sample(local.x,local.y,local.z,clock,seed);
            double drag=.45+.35*smooth(age/settings.lifeSeconds);
            velocity=velocity.scale(Math.exp(-drag*STEP_SECONDS))
                .add(new Point(flow.x(),flow.y(),flow.z()).scale(settings.curl*1.4*STEP_SECONDS))
                .add(new Point(0,settings.riseSpeed*drag*smooth(age/.5)*STEP_SECONDS,0));
            position=new Point(position.x()+velocity.x()*STEP_SECONDS+windStep.x(),
                position.y()+velocity.y()*STEP_SECONDS,position.z()+velocity.z()*STEP_SECONDS+windStep.z());
        }
    }
    private final Settings settings;
    private final double seedPhase;
    private final long seed;
    // Fixed once: source motion must not drag the field under already airborne smoke.
    private Point fieldOrigin;
    private final ArrayList<Node> nodes=new ArrayList<>();
    private final List<Node> view=Collections.unmodifiableList(nodes);
    private Pose last;
    private double delay,remaining,clock,u,power=1;
    private int chain;
    private long serial;
    public AfterSmokeModel(Settings settings,long seed){
        this.settings=settings;this.seed=seed;seedPhase=(seed&65535)*.001;u=seedPhase;
        delay=settings.delaySeconds;remaining=settings.emitSeconds;
    }
    public void restart(){if(remaining<=0){chain++;last=null;delay=settings.delaySeconds;}remaining=settings.emitSeconds;power=1;}
    public void stop(){remaining=0;delay=0;last=null;}
    public boolean finished(){return remaining<=0&&nodes.isEmpty();}
    public List<Node> nodes(){return view;}
    public void trimOldest(int count){if(count>0)nodes.subList(0,Math.min(count,nodes.size())).clear();}
    public void tick(Pose pose){tick(pose,null);}
    /** The sampler evaluates real interpolated source poses when a rotating body needs extra births. */
    public void tick(Pose pose,DoubleFunction<Pose> sampler){tick(pose,sampler,NO_WIND);}
    private static final Point NO_WIND=new Point(0,0,0);
    public void tick(Pose pose,DoubleFunction<Pose> sampler,Point windStep){
        clock+=STEP_SECONDS;
        for(Node n:nodes)n.tick(windStep);
        nodes.removeIf(n->n.age>=settings.lifeSeconds);
        if(pose==null||!pose.finite()){stop();return;}
        if(fieldOrigin==null)fieldOrigin=pose.position;
        Pose before=last;last=pose;
        if(remaining<=0)return;
        if(delay>1e-8){delay=Math.max(0,delay-STEP_SECONDS);return;}
        double distance=before==null?0:pose.position.subtract(before.position).length();
        // An unrepresentable fast motion cuts the chain, instead of a stretched polygon across scenery.
        boolean jump=distance>8;
        int count=Math.max(1,(int)Math.ceil(distance/.18));
        count=Math.min(4,count);
        if(distance>4){chain++;count=1;before=null;}
        Point velocity=before==null?new Point(0,0,0):pose.position.subtract(before.position).scale(20);
        if(jump)velocity=new Point(0,0,0);
        double speed=velocity.length();if(speed>40)velocity=velocity.scale(40/speed);
        power+=(smooth(remaining/Math.min(1,settings.emitSeconds))-power)*.22;
        for(int i=1;i<=count;i++){
            double f=(double)i/count;
            Pose sample=before==null?pose:sampler==null?
                new Pose(before.position.lerp(pose.position,f),before.direction.lerp(pose.direction,f).normalized()):sampler.apply(f);
            if(sample==null||!sample.finite())continue;
            u+=.06/count+(jump?0:Math.min(distance,.72))*.25/count;
            Point direction=sample.direction.normalized();
            Point helper=Math.abs(direction.y)<.9?new Point(0,1,0):new Point(1,0,0);
            Point side=cross(direction,helper).normalized(),up=cross(side,direction);
            long next=serial+1;
            Point spread=side.scale(random(next*3)-.5).add(up.scale(random(next*5)-.5))
                .scale(settings.curl*(.35+settings.exitSpeed*.15));
            Point birthVelocity=direction.scale(settings.exitSpeed*(.9+.2*random(next)))
                .add(velocity.scale(settings.inheritMotion)).add(spread);
            nodes.add(new Node(sample.position,birthVelocity,u,power/count,random(next*11)*Math.PI*2));
        }
        trimOldest(nodes.size()-MAX_NODES);
        remaining=Math.max(0,remaining-STEP_SECONDS);
    }
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static double smooth(double x){x=clamp(x);return x*x*(3-2*x);}
    private static Point cross(Point a,Point b){return new Point(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x);}
    private double random(long value){
        long x=seed+value*0x9E3779B97F4A7C15L;x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;
        x=(x^(x>>>27))*0x94D049BB133111EBL;return ((x^(x>>>31))>>>11)*0x1.0p-53;
    }
}
