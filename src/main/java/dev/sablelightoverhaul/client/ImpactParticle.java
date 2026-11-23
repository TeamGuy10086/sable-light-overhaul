package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.lighting.ImpactParticleData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A spark, a streak or a chip thrown by an impact.
 *
 * <p>All three use the same physics — a burst velocity, drag, gravity, and a bounce that reflects
 * whichever axis a surface stopped — because that is what makes a splash read as one event. Only the
 * drawing differs: a spark is a small bright square, a streak is a scratch drawn along the direction
 * of travel, a chip is a longer tumbling fragment that dies after its first bounce. Everything is
 * drawn with a vanilla render type and an atlas sprite, which is what makes it visible under shader
 * packs, unlike particles that draw themselves through a custom render type.
 */
public final class ImpactParticle extends TextureSheetParticle {
 private final int epoch=ClientFlashes.visualEpoch();
 private final boolean chip;
 private final boolean streak;
 private final float streakWidthScale;
 private final float streakLengthScale;
 private final boolean shellDebris;
 private final boolean shellSpark;
 private final int tailTicks;
 private static SpriteSet impactSprites;
 private final float tailOpacity;
 private float tailDistance;
 private final double downwardAcceleration;
 private final float bounce;
 private final float baseAlpha;
 private final float glow;
 private int bounces;
 /** Ticks this particle has spent going nowhere; two in a row and it is removed. */
 private int resting;
 private ImpactParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,ImpactParticleData data,SpriteSet sprites){
  super(level,x,y,z);
  this.chip=data.isChip();
  this.streak=data.isStreak();
  this.shellDebris=data.isShellDebris();
  this.shellSpark=data.mode()==ImpactParticleData.SHELL_SPARK;
  this.tailOpacity=shellDebris?ImpactExplosionConfig.debrisTailOpacity.get().floatValue():0;
  this.tailTicks=tailOpacity>0?ImpactExplosionConfig.debrisTailTicks.get():0;
  this.streakWidthScale=shellSpark?.14F:data.mode()==ImpactParticleData.FLAK_STREAK?2F:1F;
  this.streakLengthScale=shellSpark?.48F*ImpactExplosionConfig.sparkLength.get().floatValue():1F;
  // A chip is heavier and duller, a spark light and quick: same motion model, different numbers.
  this.downwardAcceleration=data.mode()==ImpactParticleData.AIR_DEBRIS ? 9.8/(20*20)
    : .04*(shellDebris?(float)(Math.max(0,dy)/(.04*Math.max(1.5,data.lifetime()*.46)))
    :this.chip?0.10F:shellSpark?.35F:0.05F);
  this.bounce=Math.min(Math.max(data.bounce(),0.0F),1.0F);
  this.baseAlpha=shellDebris?1F:this.chip?0.7F:1.0F;
  this.glow=Math.min(Math.max(data.glow(),0.0F),4.0F);
  this.lifetime=Math.max(2,data.lifetime());
  this.gravity=1.0F;
  this.friction=this.chip?0.94F:0.97F;
  this.hasPhysics=true;
  this.quadSize=Math.max(0.02F,data.size());
  int rgb=data.color();
  this.setColor(((rgb>>16)&0xFF)/255.0F,((rgb>>8)&0xFF)/255.0F,(rgb&0xFF)/255.0F);
  this.setAlpha(baseAlpha);
  this.setParticleSpeed(dx,dy,dz);
  this.roll=random.nextFloat()*6.2832F;
  this.oRoll=this.roll;
  this.setSprite(sprites.get(this.random));
 }
 @Override public void tick(){
  if(!ClientFlashes.effectsEnabled()||epoch!=ClientFlashes.visualEpoch()){remove();return;}
  this.xo=this.x;this.yo=this.y;this.zo=this.z;
  double wasX=xd,wasY=yd,wasZ=zd;
  this.yd-=downwardAcceleration;
  if(shellDebris)wasY=yd;
  this.move(this.xd,this.yd,this.zd);
  if(tailTicks>0){
   var start=new Vec3(xo,yo,zo);
   var end=new Vec3(x,y,z);
   float nextDistance=tailDistance+(float)start.distanceTo(end);
   ExplosionTrailMesh.addDebrisWake(start,end,quadSize,tailTicks,tailOpacity,tailDistance,nextDistance,getLightColor(0));
   tailDistance=nextDistance;
  }
  // move() zeroes whichever axis a surface stopped, so a bounce is that axis reflected. A chip stops
  // after its first bounce and a spark keeps skipping, which is the whole difference between them.
  //
  // "Stopped" is not "reached exactly zero": the ground puts Y to zero outright, but a wall and the floor under
  // a sliding particle only take seven tenths of the speed off, so a chip that landed and slid was never
  // counted as having bounced at all -- it simply stayed where it stopped, and a machine gun left them strewn
  // across the ground. A significant loss on any axis is what a bounce actually is.
  boolean hit=false;
  if(wasX!=0.0&&Math.abs(xd)<Math.abs(wasX)*0.6){xd=-wasX*bounce;hit=true;}
  if(wasY!=0.0&&Math.abs(yd)<Math.abs(wasY)*0.6){yd=-wasY*bounce;hit=true;}
  if(wasZ!=0.0&&Math.abs(zd)<Math.abs(wasZ)*0.6){zd=-wasZ*bounce;hit=true;}
  if(hit&&++bounces>(shellDebris?0:chip?1:3)){remove();return;}
  // And the belt to that brace: whatever stopped it, a particle that is going nowhere has no reason to keep
  // being drawn. Two ticks rather than one, so the single tick spent at the apex of a slow arc is not read as
  // rest.
  if(Math.abs(xd)+Math.abs(yd)+Math.abs(zd)<1.0E-3){if(++resting>2){remove();return;}}else{resting=0;}
  this.oRoll=this.roll;
  if(chip)this.roll+=0.45F;
  this.age++;
  if(!shellDebris&&!shellSpark){
   float progress=(float)this.age/this.lifetime;
   this.setAlpha(baseAlpha*(1.0F-progress*progress));
  }
  // No emissive-buffer consumer is installed; visible emission comes from this particle's renderer.
  if(this.age>=this.lifetime)remove();
 }
 @Override public ParticleRenderType getRenderType(){return shellDebris || shellSpark ? ParticleRenderType.PARTICLE_SHEET_OPAQUE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
 /** Sparks and ricochet chips are self-lit. Shell debris uses world light. */
 @Override public int getLightColor(float partialTick){
  return shellDebris?super.getLightColor(partialTick):LightTexture.FULL_BRIGHT;
 }
 @Override public void render(VertexConsumer buffer,Camera camera,float partialTick){
  if(!ClientFlashes.effectsEnabled()||epoch!=ClientFlashes.visualEpoch())return;
  var cameraPos=camera.getPosition();
  float ox=(float)(net.minecraft.util.Mth.lerp(partialTick,xo,x)-cameraPos.x);
  float oy=(float)(net.minecraft.util.Mth.lerp(partialTick,yo,y)-cameraPos.y);
  float oz=(float)(net.minecraft.util.Mth.lerp(partialTick,zo,z)-cameraPos.z);
  int light=this.getLightColor(partialTick);
  float half=this.getQuadSize(partialTick)*0.5F;
  if(streak){
   renderStreak(buffer,camera,ox,oy,oz,half,light);
   return;
  }
  Quaternionf rotation=camera.rotation();
  Vector3f right=new Vector3f(1.0F,0.0F,0.0F).rotate(rotation);
  Vector3f up=new Vector3f(0.0F,1.0F,0.0F).rotate(rotation);
  float halfWidth=shellDebris?half*1.5F:chip?half*2.2F:half;
  if(chip){
   float cos=(float)Math.cos(roll),sin=(float)Math.sin(roll);
   Vector3f spunRight=new Vector3f(right.x*cos+up.x*sin,right.y*cos+up.y*sin,right.z*cos+up.z*sin);
   Vector3f spunUp=new Vector3f(up.x*cos-right.x*sin,up.y*cos-right.y*sin,up.z*cos-right.z*sin);
   right=spunRight;up=spunUp;
  }
  quad(buffer,ox,oy,oz,right,up,halfWidth,shellDebris ? half*.68F : chip ? half*ExtraConfig.AP_CHIP_WIDTH.get().floatValue() : half,light);
 }

 /** A single narrow, camera-visible ribbon along velocity; no crossed planes or star core. */
 private void renderStreak(VertexConsumer buffer,Camera camera,float ox,float oy,float oz,float half,int light){
  double dx=this.x-this.xo,dy=this.y-this.yo,dz=this.z-this.zo;
  double speed=Math.sqrt(dx*dx+dy*dy+dz*dz);
  if(speed<1.0E-5){dx=xd;dy=yd;dz=zd;speed=Math.sqrt(dx*dx+dy*dy+dz*dz);}
  if(speed<1.0E-5)return;
  Vector3f forward=new Vector3f((float)(dx/speed),(float)(dy/speed),(float)(dz/speed));
  Vector3f side=new Vector3f(forward).cross(new Vector3f(-ox,-oy,-oz));
  if(side.lengthSquared()<1.0E-6)side.set(forward).cross(new Vector3f(0,1,0).rotate(camera.rotation()));
  if(side.lengthSquared()<1.0E-6)side.set(forward).cross(new Vector3f(1,0,0).rotate(camera.rotation()));
  side.normalize();
  float length=((float)Math.min(speed,1.0)*1.6F+half)*streakLengthScale;
  // Tail trails behind the current head instead of extending ahead of the spark. The ribbon width is a
  // client setting: the 0.1.28 shape was a single very thin scratch (0.12 of the quad half) and read as
  // too fine in game, so the default is wider and can be tuned without a rebuild.
  float width=shellSpark ? streakWidthScale : streakWidthScale*ExtraConfig.AP_STREAK_WIDTH.get().floatValue();
  streakQuad(buffer,ox,oy,oz,forward.negate(),side,length,half*width,light);
 }
 private void streakQuad(VertexConsumer buffer,float ox,float oy,float oz,Vector3f forward,Vector3f width,float length,float half,int light){
  Vector3f a=new Vector3f(width).mul(-half);
  Vector3f b=new Vector3f(width).mul(half);
  // One winding is chosen by which side the camera is on, so a crossed pair is visible from anywhere.
  boolean front=forward.cross(width,new Vector3f()).dot(-ox,-oy,-oz)>=0.0F;
  if(front){
   vertex(buffer,ox,oy,oz,forward,0.0F,a,this.getU0(),this.getV1(),light);
   vertex(buffer,ox,oy,oz,forward,length,a,this.getU1(),this.getV1(),light);
   vertex(buffer,ox,oy,oz,forward,length,b,this.getU1(),this.getV0(),light);
   vertex(buffer,ox,oy,oz,forward,0.0F,b,this.getU0(),this.getV0(),light);
  }else{
   vertex(buffer,ox,oy,oz,forward,0.0F,b,this.getU0(),this.getV0(),light);
   vertex(buffer,ox,oy,oz,forward,length,b,this.getU1(),this.getV0(),light);
   vertex(buffer,ox,oy,oz,forward,length,a,this.getU1(),this.getV1(),light);
   vertex(buffer,ox,oy,oz,forward,0.0F,a,this.getU0(),this.getV1(),light);
  }
 }
 private void quad(VertexConsumer buffer,float ox,float oy,float oz,Vector3f right,Vector3f up,float halfWidth,float halfHeight,int light){
  vertex(buffer,ox,oy,oz,right,up,-halfWidth,-halfHeight,this.getU0(),this.getV1(),light);
  vertex(buffer,ox,oy,oz,right,up,halfWidth,-halfHeight,this.getU1(),this.getV1(),light);
  vertex(buffer,ox,oy,oz,right,up,halfWidth,halfHeight,this.getU1(),this.getV0(),light);
  vertex(buffer,ox,oy,oz,right,up,-halfWidth,halfHeight,this.getU0(),this.getV0(),light);
 }
 private void vertex(VertexConsumer buffer,float ox,float oy,float oz,Vector3f right,Vector3f up,float dx,float dy,float u,float v,int light){
  buffer.addVertex(ox+right.x()*dx+up.x()*dy,oy+right.y()*dx+up.y()*dy,oz+right.z()*dx+up.z()*dy)
    .setUv(u,v).setColor(rCol,gCol,bCol,alpha).setLight(light);
 }
 /** One corner of a streak, placed by moving along {@code forward} and then sideways. */
 private void vertex(VertexConsumer buffer,float ox,float oy,float oz,Vector3f forward,float along,Vector3f side,float u,float v,int light){
  buffer.addVertex(ox+forward.x()*along+side.x(),oy+forward.y()*along+side.y(),oz+forward.z()*along+side.z())
    .setUv(u,v).setColor(rCol,gCol,bCol,alpha).setLight(light);
 }
 record DebrisFrame(double previousX, double previousY, double previousZ, double x, double y, double z,
                    float previousRoll, float roll, float size, int light,
                    Vec3 trailingDirection, float lineLength, float lineHalfWidth) {}
 DebrisFrame debrisFrame(){
  Vec3 trail = Vec3.ZERO;
  float length = 0;
  float halfWidth = 0;
  if (shellSpark) {
   Vec3 motion = new Vec3(x-xo,y-yo,z-zo);
   if (motion.lengthSqr() < 1.0E-10) motion = new Vec3(xd,yd,zd);
   double speed = motion.length();
   if (speed >= 1.0E-5) trail = motion.scale(-1 / speed);
   float half = getQuadSize(0) * .5F;
   length = ((float)Math.min(speed,1.0)*1.6F+half)*streakLengthScale;
   halfWidth = half*streakWidthScale;
  }
  return new DebrisFrame(xo,yo,zo,x,y,z,oRoll,roll,quadSize,getLightColor(0),trail,length,halfWidth);
 }
 static SpriteSet fragmentSprites(){return impactSprites;}
 public static final class Provider implements ParticleProvider<ImpactParticleData> {
  private final SpriteSet sprites;
  public Provider(SpriteSet sprites){this.sprites=sprites;impactSprites=sprites;}
  @Override public Particle createParticle(ImpactParticleData data,ClientLevel level,double x,double y,double z,double dx,double dy,double dz){
   var particle = new ImpactParticle(level,x,y,z,dx,dy,dz,data,sprites);
   if ((data.isShellDebris() || particle.shellSpark) && net.neoforged.fml.ModList.get().isLoaded("flywheel")
       && FlywheelDebris.submit(level, particle, data.color(), particle.shellSpark)) return null;
   return particle;
  }
 }
}
