package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.BigSmokeModel;
import dev.sablelightoverhaul.GasVertexData;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Big-cannon-only world-space lobes; one bounded burst, no continually moving smoke generator. */
public final class BigSmokeParticle extends TextureSheetParticle implements TrackedSmoke {
 private static SpriteSet sprites;
 private static java.lang.ref.WeakReference<ClientLevel> world=new java.lang.ref.WeakReference<>(null);
 // Weak references cannot retain particles evicted by the vanilla engine; expiry also bounds admission.
 private record Live(java.lang.ref.WeakReference<BigSmokeParticle> particle,long expires){}
 private static final ArrayDeque<Live> live=new ArrayDeque<>();
 private static int worldEpoch;
 private final Vec3 origin,axis,side,up;
 private final double along,lateralU,lateralV,reach,radius,hotTicks,rise,phase,expansion,fadeFrom;
 private final float opacity;
 private final boolean cold;
 private final double coldSize,curlStrength,densityDetail;
 private final int seed,epoch,birthTick;
 static void sprites(SpriteSet value){sprites=value;}
 public static boolean available(){return sprites!=null&&ClientFlashes.effectsEnabled()&&BigSmokeConfig.ENABLED.get();}
 public static int lobeCount(float charge){
  return qualityCount((double)charge*BigSmokeConfig.COUNT.get());
 }
 static int qualityCount(double count){
  int quality=switch(Minecraft.getInstance().options.particles().get()){case ALL->3;case DECREASED->2;case MINIMAL->1;};
  return Math.clamp(Math.round(count*quality/3),1,256);
 }
 record Appearance(double reach,double radius,int life,int heat,double rootHeat,double tipHeat,
                   double opacity,double rise,double spread,double expansion,double fadeFrom,
                   double coldFraction,double coldSize,double curl,double densityDetail,boolean varyLifetime) {}
 /** Each emission batch evicts the oldest cannon lobes to fit its own budget. */
 public static void emit(ClientLevel level,Vec3 origin,Vec3 direction,SmokeTracker.Group group,int tick,int total,int release,float power){
  if(!available())return;
  double charge=BigSmokeModel.chargeScale(power,BigSmokeConfig.CHARGE_REFERENCE.get(),BigSmokeConfig.CHARGE_EXPONENT.get(),BigSmokeConfig.CHARGE_MAX.get());
  double scale=BigSmokeConfig.SCALE.get()*charge,base=BigSmokeConfig.DIAMETER.get()*scale*.5;
  double flashWidth=MuzzleClientConfig.BIG_FIREBALL_ENABLED.get()?FireballParticle.bigWidth(charge):base;
  var appearance=new Appearance(BigSmokeConfig.LENGTH.get()*scale,BigSmokeModel.linkedRadius(base,flashWidth,BigSmokeConfig.FLASH_LINK.get()),
      BigSmokeConfig.LIFE.get(),BigSmokeConfig.HOT_TICKS.get(),BigSmokeConfig.ROOT_HOT_SCALE.get(),BigSmokeConfig.TIP_HOT_SCALE.get(),
      BigSmokeConfig.OPACITY.get()*ExtraConfig.BIG_PLUME_ALPHA.get(),BigSmokeConfig.RISE.get(),BigSmokeConfig.SPREAD.get(),
      BigSmokeConfig.EXPANSION.get(),BigSmokeConfig.FADE_FROM.get(),BigSmokeConfig.COLD_FRACTION.get(),BigSmokeConfig.COLD_SIZE.get(),
      BigSmokeConfig.CURL.get(),BigSmokeConfig.DENSITY_DETAIL.get(),true);
  emit(level,origin,direction,group,tick,total,release,appearance);
 }
 static void emit(ClientLevel level,Vec3 origin,Vec3 direction,SmokeTracker.Group group,int tick,int total,int release,Appearance appearance){
  if(!available())return;
  int nowEpoch=ClientFlashes.visualEpoch();long now=level.getGameTime();
  if(world.get()!=level||worldEpoch!=nowEpoch){live.clear();world=new java.lang.ref.WeakReference<>(level);worldEpoch=nowEpoch;}
  live.removeIf(e->e.expires<=now||dead(e));
  int limit=BigSmokeConfig.LIMIT.get();
  int count=Math.min(BigSmokeModel.children(total,release,tick),limit);
  while(live.size()>limit-count){
   var oldest=live.removeFirst().particle().get();
   if(oldest!=null)oldest.remove();
  }
  int start=(int)(((long)tick*total+release-1)/release);
  for(int i=0;i<count;i++){
   var p=new BigSmokeParticle(level,origin,direction,group,start+i,tick,appearance);
   live.add(new Live(new java.lang.ref.WeakReference<>(p),now+p.lifetime+2));
   Minecraft.getInstance().particleEngine.add(p);
  }
 }
 private static boolean dead(Live e){var p=e.particle.get();return p==null||p.removed;}
 private BigSmokeParticle(ClientLevel level,Vec3 origin,Vec3 direction,SmokeTracker.Group group,int index,int birthTick,Appearance appearance){
  super(level,origin.x,origin.y,origin.z);
  this.origin=origin;axis=direction.normalize();this.birthTick=birthTick;
  double fraction=BigSmokeModel.station(index);
  cold=dev.sablelightoverhaul.BigSmokeBirthModel.isCold(index,appearance.coldFraction());
  coldSize=appearance.coldSize();curlStrength=appearance.curl();densityDetail=appearance.densityDetail();
  side=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();up=side.cross(axis);
  epoch=ClientFlashes.visualEpoch();seed=random.nextInt(64);phase=random.nextDouble()*Math.PI*2;
  reach=appearance.reach();along=.12+.88*fraction;
  double theta=index*2.3999632297+origin.x*.17+origin.z*.11;
  double spread=Math.sqrt(random.nextDouble())*appearance.spread()*(.25+.75*Math.sqrt(fraction));
  lateralU=Math.cos(theta)*spread;lateralV=Math.sin(theta)*spread;
  radius=appearance.radius()*(.72+.28*fraction)*(.9+random.nextDouble()*.2);
  expansion=appearance.expansion();fadeFrom=appearance.fadeFrom();
  lifetime=appearance.varyLifetime()?Math.max(1,(int)(appearance.life()*(.85+random.nextDouble()*.3))):appearance.life();
  hotTicks=BigSmokeModel.thermalTicks(appearance.heat(),fraction,appearance.rootHeat(),appearance.tipHeat(),
      appearance.varyLifetime()?.85+random.nextDouble()*.3:1);
  rise=appearance.rise();opacity=(float)appearance.opacity();
  hasPhysics=false;gravity=0;roll=random.nextFloat()*Mth.TWO_PI;oRoll=roll;
  alpha=0;pickSprite(sprites);positionAt(birthTick/20.0);xo=x;yo=y;zo=z;quadSize=getQuadSize(0);
  if(group!=null)group.capture(this);
 }
 /** Fixture puffs use the production mesh/material at a fixed cold age and bypass the live smoke budget. */
 static List<BigSmokeParticle> benchmarkPuffs(ClientLevel level,Vec3 eye,Vec3 look,int count){
  Vec3 origin=eye.add(look.scale(13));
  var appearance=new Appearance(4,2.4,160,20,1,1,.72,0,3.8,1.6,.95,1,1,0,
      BigSmokeConfig.DENSITY_DETAIL.get(),false);
  var result=new ArrayList<BigSmokeParticle>(count);
  for(int index=0;index<count;index++){
   var puff=new BigSmokeParticle(level,origin,look,null,index,0,appearance);
   puff.benchmark=true;
   puff.age=112;
   puff.positionAt(puff.age/20.0);
   puff.xo=puff.x;puff.yo=puff.y;puff.zo=puff.z;
   puff.quadSize=puff.getQuadSize(0);
   double size=puff.quadSize;
   puff.setBoundingBox(new AABB(puff.x-size,puff.y-size,puff.z-size,puff.x+size,puff.y+size,puff.z+size));
   puff.alpha=puff.coverage(0);
   result.add(puff);
   Minecraft.getInstance().particleEngine.add(puff);
  }
  return result;
 }
 private boolean benchmark;
 private double windX,windZ;
 @Override public void tick(){
  if(benchmark)return;
  xo=x;yo=y;zo=z;
  if(!available()||epoch!=ClientFlashes.visualEpoch()||age++>=lifetime){remove();return;}
  var wind=ParticleWind.step();
  windX+=wind.x();windZ+=wind.z();
  positionAt((age+birthTick)/20.0);
  quadSize=getQuadSize(0);setBoundingBox(new AABB(x-quadSize,y-quadSize,z-quadSize,x+quadSize,y+quadSize,z+quadSize));
  alpha=coverage(0);
  if(SmokeTrace.on())SmokeTrace.row("tick",age,0,alpha,0,x,y,z,quadSize);
 }
 private int proceduralEncoded(float partial){return BigSmokeModel.encodedSeed(seed,(cold?1:0)+(age+partial)/hotTicks);}
 private float coverage(float partial){
  double seconds=(age+partial)/20.0;
  double spread=BigSmokeModel.spreadProgress((age+birthTick+partial)/20.0);
  // Let openings appear while their surrounding smoke still has body.
  double dilution=1+expansion*spread*BigSmokeModel.breakup(Math.max(0,spread-.18));
  return (float)(opacity*BigSmokeModel.coverage(seconds,lifetime/20.0,dilution,fadeFrom));
 }
 private float breakup(float partial){
  double progress=expansion>0 ? BigSmokeModel.spreadProgress((age+birthTick+partial)/20.0)
      : (age+partial)/lifetime;
  return (float)BigSmokeModel.breakup(progress);
 }
 private void positionAt(double seconds){
  double front=BigSmokeModel.front(seconds);
  double radial=(.18+.82*front)*BigSmokeModel.expansion(seconds,expansion)+BigSmokeModel.radialTravel(seconds,expansion);
  // Stable per-lobe variation grows slowly; no per-frame random motion.
  double drift=-Math.expm1(-Math.max(0,seconds-.3)/4.0);
  radial+=.28*(.5+.5*Math.cos(phase))*drift*drift;
  // Related low-frequency deformation, not spinning a rigid smoke card. Velocity naturally decays.
  double curl=curlStrength*(cold?1.15:1);
  // The outer plume loses axial reach gradually after the initial push.
  double axialDecay=-Math.expm1(-Math.max(0,seconds-1.0)/4.0);
  double axialReach=along*front*(1-.30*along*axialDecay);
  Vec3 at=origin.add(axis.scale(reach*(axialReach+BigSmokeModel.outwardDrift(seconds))))
    .add(side.scale(radius*(lateralU*radial+dev.sablelightoverhaul.BigSmokeBirthModel.curlSide(seconds,phase,curl))))
    .add(up.scale(radius*(lateralV*radial+dev.sablelightoverhaul.BigSmokeBirthModel.curlUp(seconds,phase,curl))))
    .add(0,rise*seconds*(1-Math.exp(-seconds)),0);
  setPos(at.x+windX,at.y,at.z+windZ);
 }
 @Override public float getQuadSize(float partial){return (float)(radius*dev.sablelightoverhaul.BigSmokeBirthModel.growth((age+birthTick+partial)/20.0,expansion,cold,coldSize));}
 @Override public ParticleRenderType getRenderType(){return MuzzleParticleLayer.TYPE;}
 @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer out,net.minecraft.client.Camera camera,float partial){
  if(benchmark)partial=0;
  float size=getQuadSize(partial),a=coverage(partial);
  if(SmokeTrace.on())SmokeTrace.row("render",age,partial,a,proceduralEncoded(partial),x,y,z,size);
  if(a<.002)return;
  if(MuzzleParticleLayer.proceduralSmoke()){
   if(benchmark)SmokeBenchmark.noteDraw();
   int encoded=proceduralEncoded(partial);
   float detail=(float)(densityDetail*Math.clamp(((age+partial)/lifetime-.30)/.45,0,1));
   if(BigSmokeConfig.FLIPBOOK.get()||(BigSmokeConfig.SINGLE_PASS.get()&&ClientFlashes.inPackLighting()))MuzzleSmokeMesh.drawThermalCluster(out,camera,x,y,z,xo,yo,zo,partial,size,size,roll,encoded,
    a,opacity,breakup(partial),detail,GasVertexData.smokeLight(super.getLightColor(partial)));
   else MuzzleSmokeMesh.drawThermalPuff(out,camera,x,y,z,xo,yo,zo,partial,size,size,roll,encoded,
    a,opacity,breakup(partial),detail,GasVertexData.smokeLight(super.getLightColor(partial)));
  }else{
   float heat=cold?0:(float)BigSmokeModel.heat(age+partial,hotTicks);
   MuzzleSmokeMesh.drawAtlasPuff(out,camera,x,y,z,xo,yo,zo,partial,size,size,roll,getU0(),getU1(),getV0(),getV1(),
    .28f+.55f*heat,.29f+.32f*heat,.31f+.10f*heat,a,MuzzleParticleLayer.markLight(super.getLightColor(partial),false));
  }
 }
 public double slo$x(){return x;}public double slo$y(){return y;}public double slo$z(){return z;}
 public double slo$px(){return xo;}public double slo$py(){return yo;}public double slo$pz(){return zo;}
 public double slo$size(){return getQuadSize(0);}public boolean slo$alive(){return !removed;}
 public double slo$heat(){return cold?0:BigSmokeModel.heat(age,hotTicks);}
 public boolean slo$muzzleManaged(){return true;}public boolean slo$tankControlled(){return false;}
}
