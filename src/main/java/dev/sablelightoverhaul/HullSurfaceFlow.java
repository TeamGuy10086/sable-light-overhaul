package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AfterSmokeModel.Point;

/** Short surface-constrained stage followed by irreversible world-space release. */
public final class HullSurfaceFlow {
    public static final double STEP=.05;
    public record Contact(Point local,Point normal,Point world,Point worldNormal,Point tangent,Point pointVelocity){}
    public interface Surface {
        boolean valid();
        boolean available();
        Contact sample(Point local,Point normal,double step);
        Point sweep(Point from,Point to);
        /** Local surface displacement to world velocity, without body motion or visual correction. */
        default Point slideVelocity(Point from,Point to){return to.subtract(from).scale(1/STEP);}
        /** World-space supported half-width. NaN defers a budget-starved first query. */
        default double supportRadius(Point local,Point normal,double requested){return Double.POSITIVE_INFINITY;}
    }
    private final Surface surface;
    private final HullDustModel.Settings settings;
    private final long seed,variation;
    private final double size,life,speed,drag,gravity,opacity,fade,delay;
    private double clock,detachedAt=Double.POSITIVE_INFINITY;
    private boolean attached=true,dead;
    private Point local,normal,velocity=new Point(0,0,0);
    private Point slideVelocity=new Point(0,0,0);
    private boolean sampledSlide;
    private double supportedRadius=Double.POSITIVE_INFINITY;
    private boolean supportReady;
    private double supportWait;
    private Contact contact;
    public Point previous,position;
    public HullSurfaceFlow(Surface surface,Point local,Point normal,Point world,long seed,double size,double life,
            double speed,double damping,double gravity,double opacity,double fade,HullDustModel.Settings settings){
        this(surface,local,normal,world,seed,size,life,speed,damping,gravity,opacity,fade,settings,0);
    }
    public HullSurfaceFlow(Surface surface,Point local,Point normal,Point world,long seed,double size,double life,
            double speed,double damping,double gravity,double opacity,double fade,HullDustModel.Settings settings,double extraDelay){
        if(!Double.isFinite(extraDelay)||extraDelay<0||extraDelay>1)throw new IllegalArgumentException("Invalid cluster delay");
        if(surface==null||local==null||normal==null||world==null||!local.finite()||!normal.finite()||!world.finite()
                ||settings==null||!Double.isFinite(size+life+speed+damping+gravity+opacity+fade)
                ||size<.1||size>32||life<.1||life>60||speed<0||speed>320||damping<.1||damping>1
                ||gravity<0||gravity>4||opacity<0||opacity>1||fade<.02||fade>1)throw new IllegalArgumentException("Invalid surface flow");
        this.surface=surface;this.local=local;this.normal=normal;position=previous=world;
        this.seed=seed;variation=seed^Double.doubleToLongBits(local.x()*.71+local.y()*1.13+local.z()*.37)^Double.doubleToLongBits(size*31);this.size=size;this.life=life;this.speed=speed;this.gravity=gravity;this.opacity=opacity;this.fade=fade;this.settings=settings;
        drag=Math.max(.6,-20*Math.log(damping));delay=settings.releaseSeconds()*(.2+.8*hash(1))+extraDelay;
    }
    public void tick(){
        previous=position;clock+=STEP;if(clock>=delay+life||clock>=detachedAt+settings.detachFadeSeconds()+STEP){dead=true;return;}
        double age=clock-delay;
        if(attached){
            if(!surface.valid()){dead=true;return;}
            Contact at=surface.sample(local,normal,0);
            if(at==null){if(surface.available())dead=true;return;}
            contact=at;local=at.local;normal=at.normal;
            position=at.world.add(at.worldNormal.scale(settings.clearance()));
            if(!fitSupport())return;
            if(age<=0)return;
            if(age>=Math.min(settings.attachSeconds(),life*.65)){release();return;}
            double step=Math.min(.22,speed*settings.surfaceSpeed()*settings.motionScale()*Math.exp(-age*2)*STEP);
            Contact next=surface.sample(local,normal,step);
            if(next==null){if(surface.available())release();return;}
            Point transported=surface.slideVelocity(local,next.local);
            supportedRadius=Math.max(0,supportedRadius-transported.length()*STEP);
            slideVelocity=tangentVelocity(transported,next.worldNormal);
            sampledSlide=true;
            contact=next;local=next.local;normal=next.normal;
            position=next.world.add(next.worldNormal.scale(settings.clearance()));
            fitSupport();
        }else{
            double t=clock-detachedAt;
            var flow=SmokeFlow.sample(position.x()*.16,position.y()*.16,position.z()*.16,t,seed);
            velocity=velocity.scale(Math.exp(-drag*STEP*.35)).add(new Point(flow.x(),flow.y(),flow.z()).scale(settings.turbulence()*.5*STEP*smooth(t/.25)))
                .add(new Point(0,-gravity*.6*STEP,0));
            Point desired=position.add(cap(velocity,32).scale(STEP));
            Point accepted=surface.sweep(position,desired);
            if(accepted!=null&&accepted.finite()){if(accepted.subtract(desired).length()>.005)velocity=velocity.scale(.2);position=accepted;}
            else position=desired;
        }
    }
    private boolean fitSupport(){
        double radius=surface.supportRadius(local,normal,size*1.57*.5);
        if(Double.isNaN(radius)){
            if(!supportReady){clock-=STEP;supportWait+=STEP;if(supportWait>=.5)dead=true;}
            else if(supportedRadius<.12){supportedRadius=.06;release();}
            return false;
        }
        if(radius<.12){
            if(!supportReady){dead=true;return false;}
            supportedRadius=Math.min(supportedRadius,.06);release();return false;
        }
        supportReady=true;supportedRadius=Math.min(supportedRadius,radius);return true;
    }
    private void release(){
        attached=false;detachedAt=clock;
        // Preserve the speed that actually reached the edge. A blocked puff must not regain wind
        // speed, and the normal is not an ejection direction (roof normals point straight up).
        Point relative=sampledSlide?slideVelocity:contact.tangent.scale(Math.min(.22/STEP,
            speed*settings.surfaceSpeed()*settings.motionScale()*Math.exp(-Math.max(0,clock-delay)*2)));
        velocity=cap(contact.pointVelocity,32).scale(settings.inheritMotion())
            .add(cap(relative,32));
    }
    /** Projection must retain magnitude; normalizing a tiny remainder can create a vertical jet. */
    public static Point tangentVelocity(Point velocity,Point normal){
        Point n=normal.normalized();
        return velocity.subtract(n.scale(velocity.x()*n.x()+velocity.y()*n.y()+velocity.z()*n.z()));
    }
    public boolean attached(){return attached;}
    public boolean finished(){return dead;}
    public Contact contact(){return contact;}
    public Point local(){return local;}
    public Point normal(){return normal;}
    public double blend(double partial){return attached?0:smooth((clock-STEP*(1-clamp(partial))-detachedAt)/.2);}
    private double detachProgress(double partial){return clamp((clock-STEP*(1-clamp(partial))-detachedAt)/settings.detachFadeSeconds());}
    private double expansion(double partial){double u=detachProgress(partial);return 1+settings.detachExpansion()*u*(2-u);}
    public double width(double partial){
        double age=Math.max(0,Math.min(clock-STEP*(1-clamp(partial)),detachedAt)-delay);
        return Math.min(2*supportedRadius,size*(.8+.12*hash(2)+.65*smooth(age*settings.motionScale()/1.1)))*expansion(partial);
    }
    public double height(double partial){return width(partial)*(.22+.42*blend(partial)+.16*smooth(detachProgress(partial)));}
    public double roll(double partial){return 0;}
    public double alpha(double partial){
        double age=clock-STEP*(1-clamp(partial))-delay;if(age<=0||age>=life||dead||contact==null||!supportReady)return 0;
        double tau=opticalThickness(age);
        if(!attached){
            // Cap at release density so an early edge exit cannot resume its birth fade-in.
            tau=Math.min(tau,opticalThickness(Math.max(0,detachedAt-delay)));
            double scale=expansion(partial);
            tau*=(1-smooth(detachProgress(partial)))/(scale*scale);
        }
        return -Math.expm1(-tau);
    }
    private double opticalThickness(double age){
        double envelope=smooth(age/Math.min(.10,life*.2))*(1-smooth((age/life-(1-fade))/fade));
        return opacity*envelope/(1+.35*age);
    }
    private static Point cap(Point p,double max){return p.length()>max?p.scale(max/p.length()):p;}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
    private static double smooth(double v){v=clamp(v);return v*v*(3-2*v);}
    private double hash(int k){long x=variation+k*0x9E3779B97F4A7C15L;x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;x=(x^(x>>>27))*0x94D049BB133111EBL;return ((x^(x>>>31))>>>11)*0x1.0p-53;}
}
