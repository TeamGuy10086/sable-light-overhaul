package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AfterSmokeModel.Point;

/** Detached world puff from one surface snapshot. No persistent attachment or collision solver. */
public final class HullDustModel {
    public static final double STEP=.05,MIN_LIFT=.05,MAX_LIFE=60,MAX_TRAVEL=32;
    public record Settings(double lift,double turbulence,double releaseSeconds,double motionScale,double attachSeconds,double surfaceSpeed,double clearance,double inheritMotion,double density,double spread,double stagger,double detachFadeSeconds,double detachExpansion){
        public Settings(double lift,double turbulence,double releaseSeconds,double motionScale,double attachSeconds,double surfaceSpeed,double clearance,double inheritMotion,double density,double spread,double stagger){this(lift,turbulence,releaseSeconds,motionScale,attachSeconds,surfaceSpeed,clearance,inheritMotion,density,spread,stagger,.45,.8);}
        public Settings(double lift,double turbulence,double releaseSeconds,double motionScale,double attachSeconds,double surfaceSpeed,double clearance,double inheritMotion){this(lift,turbulence,releaseSeconds,motionScale,attachSeconds,surfaceSpeed,clearance,inheritMotion,2,.3,.10);}
        public Settings(double lift,double turbulence,double releaseSeconds,double motionScale){this(lift,turbulence,releaseSeconds,motionScale,.35,.15,.06,.8);}
        public Settings(double lift,double turbulence,double releaseSeconds){this(lift,turbulence,releaseSeconds,1);}
        public Settings{bound(detachFadeSeconds,.05,10);bound(detachExpansion,0,8);bound(density,0,8);bound(spread,0,2);bound(stagger,0,1);bound(attachSeconds,0,3);bound(surfaceSpeed,0,2);bound(clearance,.01,1);bound(inheritMotion,0,2);bound(motionScale,.1,4);bound(lift,0,16);bound(turbulence,0,8);bound(releaseSeconds,0,3);}
    }
    private final Point at,normal,direction;
    private final Settings settings;
    private final long seed,variation;
    private final double size,life,speed,drag,gravity,opacity,fade,delay;
    private double clock;
    public Point previous,position;
    public HullDustModel(Point at,Point normal,Point direction,Point muzzle,long seed,double size,double lifetime,
            double speed,double damping,double gravity,double opacity,double fade,Settings settings){
        this(at,normal,direction,muzzle,seed,size,lifetime,speed,damping,gravity,opacity,fade,settings,0);
    }
    public HullDustModel(Point at,Point normal,Point direction,Point muzzle,long seed,double size,double lifetime,
            double speed,double damping,double gravity,double opacity,double fade,Settings settings,double extraDelay){
        bound(extraDelay,0,1);
        check(at);check(normal);check(direction);check(muzzle);
        bound(size,.1,32);bound(lifetime,.1,MAX_LIFE);bound(speed,0,320);bound(damping,.1,1);
        bound(gravity,0,4);bound(opacity,0,1);bound(fade,.02,1);
        if(settings==null||normal.length()<.9||direction.length()<.9)throw new IllegalArgumentException("Invalid hull basis");
        this.at=at;this.normal=normal.normalized();
        Point tangent=direction.subtract(this.normal.scale(dot(direction,this.normal)));
        if(tangent.length()<1e-6)throw new IllegalArgumentException("No surface tangent");
        this.direction=tangent.normalized();
        this.settings=settings;this.seed=seed;variation=seed^Double.doubleToLongBits(at.x()*.71+at.y()*1.13+at.z()*.37);this.size=size;life=lifetime;this.speed=speed;
        drag=Math.max(.6,-20*Math.log(damping));this.gravity=gravity;this.opacity=opacity;this.fade=fade;
        delay=settings.releaseSeconds*(.2+.8*Math.min(1,at.subtract(muzzle).length()/16))*(.65+.35*hash(3))+extraDelay;
        previous=position=at;
    }
    private static final Point NO_WIND=new Point(0,0,0);
    private double windX,windZ;
    public void tick(){tick(NO_WIND);}
    public void tick(Point windStep){
        previous=position;clock+=STEP;double t=(clock-delay)*settings.motionScale;if(t<=0||finished())return;
        // Peeling displacement is monotonic, with no programmed curl/orbit.
        double sweep=speed*(-Math.expm1(-drag*t))/drag;
        var flow=SmokeFlow.sample(at.x()*.16,at.y()*.16,at.z()*.16,t,seed);
        double disturbance=settings.turbulence*.28*smooth(t/.4);
        double outward=settings.lift*(1-Math.exp(-t/.55))-.2*gravity*t*t;
        Point displacement=direction.scale(sweep)
            .add(new Point(flow.x(),flow.y(),flow.z()).scale(disturbance)).add(normal.scale(outward));
        double n=dot(displacement,normal);if(n<MIN_LIFT)displacement=displacement.add(normal.scale(MIN_LIFT-n));
        double length=displacement.length();if(length>MAX_TRAVEL)displacement=displacement.scale(MAX_TRAVEL/length);
        windX+=windStep.x();windZ+=windStep.z();
        position=new Point(at.x()+displacement.x()+windX,at.y()+displacement.y(),at.z()+displacement.z()+windZ);
    }
    public double width(double partial){return size*(.72+.16*hash(6)+1.2*smooth(age(partial)*settings.motionScale/Math.min(.8,life)));}
    public double height(double partial){return width(partial)*(.42+.32*smooth(age(partial)*settings.motionScale/Math.min(.7,life)));}
    public double roll(double partial){return 0;}
    public double alpha(double partial){
        double t=age(partial);if(t<=0||t>=life)return 0;
        double envelope=smooth(t/Math.min(.12,life*.2))*(1-smooth((t/life-(1-fade))/fade));
        return -Math.expm1(-opacity*envelope/(1+.6*Math.max(0,t)));
    }
    public boolean finished(){return clock>=delay+life;}
    private double age(double p){return clock-STEP*(1-Math.max(0,Math.min(1,p)))-delay;}
    private static double dot(Point a,Point b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    private static void check(Point p){if(p==null||!p.finite()||p.length()>6e7)throw new IllegalArgumentException("Invalid hull point");}
    private static void bound(double v,double min,double max){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Invalid hull setting");}
    private double hash(int k){long x=variation+k*0x9E3779B97F4A7C15L;x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;x=(x^(x>>>27))*0x94D049BB133111EBL;return ((x^(x>>>31))>>>11)*0x1.0p-53;}
}
