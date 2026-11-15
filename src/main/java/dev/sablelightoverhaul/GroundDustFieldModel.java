package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AfterSmokeModel.Point;
import java.util.*;

/** Bounded world-space dust patches sharing one advancing front and continuous flow. */
public final class GroundDustFieldModel {
    public static final int MAX_PATCHES=64;
    public static final int MAX_NODES=512;
    public static final double STEP=.05;
    public record Patch(Point position,Point normal,int color,int materialState,long materialPos) {
        public Patch(Point position,Point normal,int color){this(position,normal,color,-1,0);}
        public Patch {
            if(materialState<0)materialPos=0;
            if(position==null||!position.finite()||position.length()>6e7||normal==null||!normal.finite()
                    ||normal.y()<.2||normal.length()<.9||normal.length()>1.1||color<0||color>0xFFFFFF
                    ||materialState < -1||materialState>0xFFFFFF)
                throw new IllegalArgumentException("Invalid dust patch");
        }
    }
    public record Settings(double radius,double spacing,double lifetime,double spreadSeconds,
            double speed,double lift,double turbulence,double opacity,double sizeScale,double maxStep,double density,double corridorWidth,double outerLift,double motionScale,double heightScale) {
        public Settings(double radius,double spacing,double lifetime,double spreadSeconds,double speed,double lift,double turbulence,double opacity,double sizeScale,double maxStep,double density){this(radius,spacing,lifetime,spreadSeconds,speed,lift,turbulence,opacity,sizeScale,maxStep,density,0,1,1,1);}
        public Settings(double radius,double spacing,double lifetime,double spreadSeconds,double speed,double lift,double turbulence,double opacity,double sizeScale,double maxStep){this(radius,spacing,lifetime,spreadSeconds,speed,lift,turbulence,opacity,sizeScale,maxStep,1);}
        public Settings {
            bounded(heightScale,.1,4);bounded(corridorWidth,0,64);bounded(outerLift,0,1);bounded(motionScale,.1,4);bounded(density,0,8);bounded(radius,.1,64);bounded(spacing,.025,64);bounded(lifetime,.1,60);
            bounded(spreadSeconds,.05,5);bounded(speed,0,32);bounded(lift,0,16);
            bounded(turbulence,0,8);bounded(opacity,0,1);bounded(sizeScale,.25,4);bounded(maxStep,0,8);
        }
    }
    public final class Node {
        public final int index,patchIndex,materialSeed;
        public final Patch patch;
        public final double delay;
        public Point previous,position;
        private Point velocity;
        private double ground,death=Double.POSITIVE_INFINITY;
        private final double strength,phase,front,sideSign,coherence,sizeVariation,outer,rangeLift;
        private Node(int i,int source,Patch patch) {
            index=i;patchIndex=source;this.patch=patch;previous=position=patch.position;ground=position.y();
            if(i>=patches.size()){
                position=position.add(side.scale((random(i+971)-.5)*settings.spacing*.28));previous=position;
            }
            materialSeed=(int)(random(i+11)*64);phase=random(i+79)*Math.PI*2;
            Point delta=position.subtract(origin);double distance=Math.hypot(delta.x(),delta.z());
            double along=dot(delta,axis),across=dot(delta,side);
            sideSign=across<0?-1:1;
            outer=smooth(Math.abs(across)/settings.radius);
            rangeLift=1-(1-settings.outerLift)*smooth(distance/settings.radius);
            front=smooth((along/settings.radius+.08)/.35);
            // Broad variations shared by adjacent cells, rather than a new random trajectory per puff.
            coherence=.5+.25*(Math.sin(along/settings.radius*3.5+random(701)*6.28)
                +Math.cos(across/settings.radius*4.3+random(702)*6.28));
            sizeVariation=.78+.44*coherence+.10*(random(i+89)-.5);
            double corridor=.30+.70*smooth(Math.abs(across)/(settings.radius*.30+.2));
            strength=(.22+.78*front)*(.72+.28*(1-Math.min(1,distance/settings.radius)))
                *corridor*(.75+.25*coherence);
            delay=settings.spreadSeconds*Math.min(1.5,distance/settings.radius)/(.7+.3*front)
                *(.8+.35*coherence+.1*random(i+123));
            velocity=axis.scale(settings.speed*(1.2*front-.18))
                .add(side.scale(sideSign*settings.speed*(.12+.28*front)));
        }
        public double corridorHalfWidth(){return settings.corridorWidth*.5;}
        public Point corridorOrigin(){return origin;}
        public Point corridorAxis(){return firingAxis;}
        public double corridorPhase(){return corridorPhase;}
        public double age(double partial){return clock-STEP*(1-clamp(partial))-delay;}
        public boolean finished(){return clock>Math.min(delay+settings.lifetime,death);}
        private double growth(double age){return 1+(.9+.2*coherence)/2.4*smooth(age/(.7+.4*coherence));}
        public double width(double partial){return settings.spacing*settings.sizeScale*2.4*sizeVariation
            *(.8+.2*front)*growth(age(partial));}
        public double height(double partial){return width(partial)*(.24+.28*smooth(age(partial)/(.75+.3*coherence)))*settings.heightScale*(.35+.65*rangeLift);}
        private double angle(double age){
            // The inner bank rolls first; its outer apron is still near the ground while it curls
            // overhead. A uniform phase would lift the entire sampled grid into one floating sheet.
            double rollingAge=Math.max(0,age*settings.motionScale-.05-.65*outer);
            return 4.5*(1-Math.exp(-rollingAge/(.75+.6*coherence+.65*outer)))*(.25+.75*front);
        }
        private double lateralRoll(double age){return sideSign*settings.lift*rangeLift*(1.35+.3*coherence)*front*Math.sin(angle(age));}
        public double roll(double partial){return .1*Math.sin(phase)+sideSign*.18*angle(age(partial));}
        public double alpha(double partial){
            double age=age(partial),time=clock-STEP*(1-clamp(partial));
            if(age<=0||age>=settings.lifetime||time>=death)return 0;
            double envelope=smooth(age/Math.min(.22,settings.lifetime*.2))
                *(1-smooth((age/settings.lifetime-.35)/.65))*clamp((death-time)/.25);
            // sizeScale is a visual extent control, opacity is a separate concentration control.
            // Dilute temporal expansion only; do not make a deliberately larger cloud disappear.
            double coverage=1/Math.pow(growth(age),2);
            return -Math.expm1(-settings.opacity*strength*envelope*coverage*2.6);
        }
        private void tick(Point windStep){
            previous=position;double age=clock-delay;
            if(age<=0||finished())return;
            Point relative=position.subtract(origin);
            var flow=SmokeFlow.sample(dot(relative,axis)*.14,relative.y()*.14,dot(relative,side)*.14,clock,seed);
            velocity=velocity.scale(Math.exp(-.60*STEP))
                .add(axis.scale(flow.x()).add(side.scale(flow.z())).scale(settings.turbulence*.45*STEP));
            // A fast node must not tunnel across a missing cell into a valid cell on the far side.
            Point displacement=velocity.scale(STEP*settings.motionScale).add(side.scale(lateralRoll(age)-lateralRoll(age-STEP)));
            if(windStep.x()!=0||windStep.z()!=0)displacement=displacement.add(windStep);
            double distance=displacement.length();
            if(distance>settings.spacing*.25)displacement=displacement.scale(settings.spacing*.25/distance);
            Point candidate=position.add(displacement);
            double support=surface(candidate,ground);
            if(!Double.isFinite(surface(position.add(displacement.scale(.5)),ground)))support=Double.NaN;
            if(Double.isFinite(support)){ground=support;position=candidate;}
            else velocity=velocity.scale(.2); // Do not jump from a valid patch across a hole/height break.
            // The same angle drives outward motion, lift and the upper inward return: a world-space
            // pair of counter-rolling banks parallel to the shot axis, not just billboard rotation.
            double rise=settings.heightScale*settings.lift*rangeLift*(.18+.82*front)*(1-Math.cos(angle(age)));
            rise=Math.max(0,rise+flow.y()*settings.heightScale*settings.turbulence*.3*rangeLift*smooth(age/.5));
            position=new Point(position.x(),ground+.08+rise+height(1)*.18,position.z());
        }
    }
    private final Point origin,axis,side,firingAxis;
    private final double corridorPhase;
    private final Settings settings;
    private final long seed;
    private final List<Node> nodes;
    private final List<Patch> patches;
    private final boolean[] valid;
    private double clock;
    public GroundDustFieldModel(Point origin,Point axis,List<Patch> patches,Settings settings,long seed){
        if(origin==null||!origin.finite()||origin.length()>6e7||axis==null||!axis.finite()
                ||axis.length()<.9||axis.length()>1.1||patches==null||settings==null
                ||patches.size()>MAX_PATCHES)throw new IllegalArgumentException("Invalid dust field");
        this.origin=origin;this.firingAxis=axis.normalized();this.axis=Math.hypot(axis.x(),axis.z())<1e-6?new Point(1,0,0):new Point(axis.x(),0,axis.z()).normalized();
        this.side=new Point(-this.axis.z(),0,this.axis.x());
        this.settings=settings;this.seed=seed;this.corridorPhase=random(703)*Math.PI*2;this.patches=List.copyOf(patches);valid=new boolean[patches.size()];Arrays.fill(valid,true);
        var list=new ArrayList<Node>();
        int count=(int)Math.round(patches.size()*settings.density);
        for(int i=0;i<count;i++){int source=count<patches.size()?(int)((long)i*patches.size()/count):i<patches.size()?i:(int)((i-patches.size())*0.61803398875*patches.size())%patches.size();list.add(new Node(i,source,patches.get(source)));}
        nodes=Collections.unmodifiableList(list);
    }
    public List<Node> nodes(){return nodes;}
    private static double dot(Point a,Point b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static final Point NO_WIND=new Point(0,0,0);
    public void tick(){tick(NO_WIND);}
    public void tick(Point windStep){clock+=STEP;for(var node:nodes)node.tick(windStep);}
    public boolean finished(){return nodes.stream().allMatch(Node::finished);}
    public void invalidate(int index){int source=nodes.get(index).patchIndex;valid[source]=false;for(var n:nodes)if(n.patchIndex==source)n.death=Math.min(n.death,clock+.25);}
    private double surface(Point at,double previousHeight){
        double nearest=Double.POSITIVE_INFINITY,base=Double.NaN;
        for(int i=0;i<patches.size();i++)if(valid[i]){
            Point p=patches.get(i).position;double d=Math.pow(at.x()-p.x(),2)+Math.pow(at.z()-p.z(),2);
            if(d<nearest){nearest=d;base=p.y();}
        }
        if(nearest>settings.spacing*settings.spacing*.64||!Double.isFinite(base)||Math.abs(base-previousHeight)>settings.maxStep)return Double.NaN;
        double sum=0,weight=0;
        for(int i=0;i<patches.size();i++)if(valid[i]){
            Point p=patches.get(i).position;double d=Math.pow(at.x()-p.x(),2)+Math.pow(at.z()-p.z(),2);
            if(d<=settings.spacing*settings.spacing*1.6&&Math.abs(p.y()-base)<=settings.maxStep){
                double w=1/(.01+d);sum+=p.y()*w;weight+=w;
            }
        }
        return weight>0?sum/weight:Double.NaN;
    }
    private double random(long n){long x=seed+n*0x9E3779B97F4A7C15L;x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;x=(x^(x>>>27))*0x94D049BB133111EBL;return ((x^(x>>>31))>>>11)*0x1.0p-53;}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static double smooth(double x){x=clamp(x);return x*x*(3-2*x);}
    private static void bounded(double x,double min,double max){if(!Double.isFinite(x)||x<min||x>max)throw new IllegalArgumentException("Invalid field parameter");}
}
