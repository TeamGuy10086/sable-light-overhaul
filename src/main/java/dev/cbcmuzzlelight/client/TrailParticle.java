package dev.cbcmuzzlelight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * One frame of a tracer shell's trail.
 *
 * <p>The old trail was the shell's own faint streak, which read as a thin line at best. This is the
 * opposite: a bright puff left hanging in the air at every frame of the flight, so the eye follows a
 * rope of light rather than a hair. The sprite comes from Mianbao's Modern Warfare rocket trail, taken
 * apart and pushed towards a hot glow, because that texture was drawn as smoke and shown unlit would
 * only have made a grey smudge.
 *
 * <p><b>It never falls.</b> Gravity is off and the drift passed in at spawn is damped every tick, so a
 * frame slows and then simply hangs where it was, which is what makes the trail hold its shape behind a
 * fast shell instead of raining away from it. Lifetime, size, opacity, tint and the damping are all
 * configurable, so how long a frame exists is a knob rather than a constant.
 */
public final class TrailParticle extends TextureSheetParticle {
 private final float birthSize;
 private final float peakOpacity;
 private final float damping;
 private TrailParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites){
  super(level,x,y,z);
  this.xd=dx;this.yd=dy;this.zd=dz;
  this.hasPhysics=false;
  this.gravity=0.0F;
  this.birthSize=ExtraConfig.TRAIL_SIZE.get().floatValue()*(0.75F+random.nextFloat()*0.5F);
  this.peakOpacity=ExtraConfig.TRAIL_OPACITY.get().floatValue();
  this.damping=0.9F;
  this.lifetime=Math.max(1,(int)Math.round(ExtraConfig.TRAIL_LIFETIME.get()*(0.8+random.nextDouble()*0.4)));
  this.quadSize=birthSize;
  this.roll=random.nextFloat()*(float)Math.PI*2.0F;
  this.oRoll=roll;
  int rgb=ExtraConfig.TRAIL_COLOR.get();
  this.setColor(((rgb>>16)&0xFF)/255.0F,((rgb>>8)&0xFF)/255.0F,(rgb&0xFF)/255.0F);
  this.setAlpha(0.0F);
  this.pickSprite(sprites);
 }
 @Override public void tick(){
  super.tick();
  // Gravity stays off and the drift bleeds away: the frame ends up parked in the air where it was
  // born, so the trail is a record of where the shell has been rather than a second projectile.
  this.xd*=damping;this.yd*=damping;this.zd*=damping;
  float progress=lifetime<=0?1.0F:Math.min(1.0F,(float)age/lifetime);
  // Quick fade in over the first tenth of the life, then a long fade out; without the fade in the
  // frames pop into existence and a fast shell looks like a dotted line.
  float in=Math.min(1.0F,progress*10.0F);
  this.setAlpha(peakOpacity*in*(1.0F-progress)*(1.0F-progress));
  this.quadSize=birthSize*(1.0F+0.35F*progress);
 }
 @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
 /** A trail is light, not smoke: it must not go dark in a shadow. */
 @Override public int getLightColor(float partialTick){return LightTexture.FULL_BRIGHT;}
 public static final class Provider implements ParticleProvider<SimpleParticleType> {
  private final SpriteSet sprites;
  public Provider(SpriteSet sprites){this.sprites=sprites;}
  @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,
                                           double x,double y,double z,double dx,double dy,double dz){
   if(!ExtraConfig.TRAIL_ENABLED.get())return null;
   return new TrailParticle(level,x,y,z,dx,dy,dz,sprites);
  }
 }
}
