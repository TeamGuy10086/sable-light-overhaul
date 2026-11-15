package dev.sablelightoverhaul.client;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.GasVertexData;
import dev.sablelightoverhaul.BigSmokeModel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
/** A single flak cloud expands only after its hot phase. */
public final class FlakCloudPuffParticle extends WindborneParticle {
 private static SpriteSet sprites;
 private static final int HOT_TICKS=8;
 private static final LinkedHashSet<Ticket> LIVE=new LinkedHashSet<>();
 private static WeakReference<ClientLevel> owner=new WeakReference<>(null);
 private record Ticket(long expires,WeakReference<FlakCloudPuffParticle> particle){}
 private final Ticket ticket;private final int seed;private final float size;
 public static void spawn(ClientLevel level,double x,double y,double z,int life){
  if(sprites==null)return;
  if(owner.get()!=level){LIVE.clear();owner=new WeakReference<>(level);}
  LIVE.removeIf(t->t.expires()<=level.getGameTime()||t.particle().get()==null);
  int limit=ExtraConfig.FLAK_SMOKE_LIMIT.get();
  while(LIVE.size()>=limit){
   var oldest=LIVE.removeFirst().particle().get();
   if(oldest!=null)oldest.remove();
  }
  var p=new FlakCloudPuffParticle(level,x,y,z,life);
  LIVE.add(p.ticket);
  Minecraft.getInstance().particleEngine.add(p);
 }
 private FlakCloudPuffParticle(ClientLevel level,double x,double y,double z,int life){
  super(level,x,y,z);yd=.022;
  hasPhysics=false;gravity=0;friction=.985f;
  lifetime=Math.clamp(life+18,48,72);ticket=new Ticket(level.getGameTime()+lifetime+2,new WeakReference<>(this));seed=random.nextInt(64);
  size=1.5f*(1.6f+random.nextFloat()*.5f);roll=random.nextFloat()*6.283185f;oRoll=roll;pickSprite(sprites);
 }
 @Override public void remove(){super.remove();LIVE.remove(ticket);}
 @Override public ParticleRenderType getRenderType(){return MuzzleParticleLayer.TYPE;}
 @Override public AABB getRenderBoundingBox(float p){float r=size*1.6f;return new AABB(x-r,y-r,z-r,x+r,y+r,z+r);}
 /** Trim a flak streak that ends inside a dense burst, without changing its flight samples. */
 static Vec3 clipStreakHead(ClientLevel level,Vec3 tail,Vec3 head,float partial){
  if(owner.get()!=level||LIVE.isEmpty())return head;
  Vec3 travel=head.subtract(tail);
  double lengthSquared=travel.lengthSqr(),visible=1;
  if(lengthSquared<1e-10)return head;
  for(var ticket:LIVE){
   var cloud=ticket.particle().get();
   if(cloud==null||!cloud.isAlive()||cloud.age>=HOT_TICKS)continue;
   double radius=cloud.size*.48;
   double dx=tail.x-net.minecraft.util.Mth.lerp(partial,cloud.xo,cloud.x);
   double dy=tail.y-net.minecraft.util.Mth.lerp(partial,cloud.yo,cloud.y);
   double dz=tail.z-net.minecraft.util.Mth.lerp(partial,cloud.zo,cloud.z);
   double hx=dx+travel.x,hy=dy+travel.y,hz=dz+travel.z;
   if(hx*hx+hy*hy+hz*hz>=radius*radius)continue;
   double along=dx*travel.x+dy*travel.y+dz*travel.z;
   double offset=dx*dx+dy*dy+dz*dz-radius*radius;
   double entry=(-along-Math.sqrt(Math.max(0,along*along-lengthSquared*offset)))/lengthSquared;
   visible=Math.min(visible,Math.max(0,entry));
   if(visible==0)return tail;
  }
  return visible<1?tail.add(travel.scale(visible)):head;
 }
 @Override public void render(VertexConsumer out,Camera camera,float partial){
  float time=age+partial;
  float coldTime=Math.max(0,time-HOT_TICKS);
  float t=Math.clamp(coldTime/(lifetime-HOT_TICKS),0,1);
  float expansion=(float)-Math.expm1(-coldTime/25.0);
  float radius=size*(.7f+.9f*expansion);
  float a=(float)(.98*Math.min(1,time/1.5)*(1-t*t*(3-2*t))/(1+.45*expansion));
  float thermalAge=time/HOT_TICKS;
  float breakup=(float)BigSmokeModel.breakup(t);
  int packed=super.getLightColor(partial);
  if(MuzzleParticleLayer.proceduralSmoke()){
   float detail=ExtraConfig.FLAK_DENSITY_DETAIL.get().floatValue()*Math.clamp((t-.30f)/.45f,0,1);
   MuzzleSmokeMesh.drawThermalCluster(out,camera,x,y,z,xo,yo,zo,partial,radius,radius,roll,
    BigSmokeModel.encodedSeed(seed,thermalAge),a,.98f,breakup,detail,GasVertexData.smokeLight(packed));
  }else{
   float heat=(float)Math.max(0,1-thermalAge);
   MuzzleSmokeMesh.drawAtlasPuff(out,camera,x,y,z,xo,yo,zo,partial,radius,radius,roll,getU0(),getU1(),getV0(),getV1(),
    .28f+.55f*heat,.29f+.32f*heat,.31f+.10f*heat,a,MuzzleParticleLayer.markLight(packed,heat>0));
  }
 }
 public static final class Provider implements ParticleProvider<SimpleParticleType>{
  public Provider(SpriteSet s){sprites=s;}
  @Override public Particle createParticle(SimpleParticleType t,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return null;}
 }
}
