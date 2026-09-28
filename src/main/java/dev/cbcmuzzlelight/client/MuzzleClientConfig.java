package dev.cbcmuzzlelight.client;
import dev.cbcmuzzlelight.*;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class MuzzleClientConfig {
 public static final ModConfigSpec SPEC;
 public static final ModConfigSpec.EnumValue<LightingMode> MODE;
 public static final LightProfile AUTO_PROFILE,BLAST_PROFILE,SMOKE_PROFILE;
 public static final ModConfigSpec.DoubleValue AUTO_SIZE;
 public static final ModConfigSpec.DoubleValue AUTO_LENGTH_SCALE;
 public static final ModConfigSpec.DoubleValue AUTO_WIDTH_SCALE;
 public static final ModConfigSpec.DoubleValue AUTO_OPACITY;
 public static final ModConfigSpec.IntValue AUTO_FLAME_DURATION;
 public static final ModConfigSpec.IntValue AUTO_DURATION;
 public static final ModConfigSpec.IntValue AUTO_COUNT;
 public static final ModConfigSpec.DoubleValue AUTO_LENGTH;
 public static final ModConfigSpec.DoubleValue AUTO_WIDTH;
 public static final ModConfigSpec.IntValue BIG_DURATION;
 public static final ModConfigSpec.IntValue BIG_HOLD;
 public static final ModConfigSpec.DoubleValue BIG_MINIMUM;
 public static final ModConfigSpec.DoubleValue BIG_DECAY;
 public static final ModConfigSpec.IntValue BIG_COUNT;
 public static final ModConfigSpec.DoubleValue BIG_LENGTH;
 public static final ModConfigSpec.DoubleValue BIG_WIDTH;
 public static final ModConfigSpec.DoubleValue FALLBACK_SPEED;
 public static final ModConfigSpec.DoubleValue HOT_FRACTION;
 public static final ModConfigSpec.IntValue TRACKED_PARTICLES;
 public static final ModConfigSpec.IntValue MAX_SOURCES;
 public static final ModConfigSpec.IntValue SABLE_MAX_SOURCES;
 public static final ModConfigSpec.BooleanValue SABLE_ENABLED;
 public static final ModConfigSpec.BooleanValue SABLE_DEBUG;
 public static final ModConfigSpec.IntValue ADV_MAX;
 public static final ModConfigSpec.IntValue ADV_RECEIVERS;
 public static final ModConfigSpec.BooleanValue ADV_DEBUG;
 static {
  var b=new ModConfigSpec.Builder();
  MODE=b.defineEnum("lightingMode",LightingMode.Compatible);
  AUTO_SIZE=b.defineInRange("visuals.autocannon.sizeMultiplier",9.0,0.1,16.0);
  AUTO_LENGTH_SCALE=b.defineInRange("visuals.autocannon.flameLengthScale",0.5,0.1,3.0);
  AUTO_WIDTH_SCALE=b.defineInRange("visuals.autocannon.flameWidthScale",2.0,0.1,3.0);
  AUTO_OPACITY=b.defineInRange("visuals.autocannon.flameOpacity",1.0,0.1,1.0);
  AUTO_FLAME_DURATION=b.defineInRange("visuals.autocannon.flameDurationTicks",3,1,20);
  AUTO_DURATION=b.defineInRange("legacy.autocannon.lightDurationTicks",5,1,40);
  AUTO_COUNT=b.defineInRange("legacy.autocannon.lightSourceCount",5,1,7);
  AUTO_LENGTH=b.defineInRange("legacy.autocannon.lightLength",3.0,0.0,24.0);
  AUTO_WIDTH=b.defineInRange("legacy.autocannon.lightWidth",1.5,0.0,24.0);
  BIG_DURATION=b.defineInRange("legacy.bigCannon.durationTicks",30,1,1200);
  BIG_HOLD=b.defineInRange("legacy.bigCannon.peakHoldTicks",4,0,1200);
  BIG_MINIMUM=b.defineInRange("legacy.bigCannon.minimumBrightness",0.0,0.0,1.0);
  BIG_DECAY=b.defineInRange("legacy.bigCannon.decayPerTick",0.02,0.0,1.0);
  BIG_COUNT=b.defineInRange("legacy.bigCannon.lightSourceCount",7,1,7);
  BIG_LENGTH=b.defineInRange("legacy.bigCannon.lightLength",6.0,0.0,24.0);
  BIG_WIDTH=b.defineInRange("legacy.bigCannon.lightWidth",6.0,0.0,24.0);
  FALLBACK_SPEED=b.defineInRange("tracking.fallbackSpeed",0.9,0.0,4.0);
  HOT_FRACTION=b.defineInRange("tracking.hotLifetimeFraction",0.6,0.05,1.0);
  TRACKED_PARTICLES=b.defineInRange("tracking.particlesPerShot",8,1,24);
  MAX_SOURCES=b.defineInRange("legacy.performance.maxLightSources",256,7,448);
  SABLE_MAX_SOURCES=b.defineInRange("legacy.sable.maxProjectedSources",96,1,448);
  SABLE_ENABLED=b.define("legacy.sable.enabled",true);
  SABLE_DEBUG=b.define("legacy.sable.debugLogging",false);
  ADV_MAX=b.defineInRange("advanced.performance.maxAreaLights",32,1,64);
  ADV_RECEIVERS=b.defineInRange("advanced.performance.maxReceiverAreas",64,1,128);
  ADV_DEBUG=b.define("advanced.performance.debugLogging",false);
  b.push("advanced").push("autocannon");
  AUTO_PROFILE=new LightProfile(b,true,32,16,16,12,5,1,0.5,1);
  b.pop().push("bigCannon").push("blast");
  BLAST_PROFILE=new LightProfile(b,false,25,8,12,8,2,2,0.02,2);
  b.pop().push("smoke");
  SMOKE_PROFILE=new LightProfile(b,false,25,8,12,8,28,2,0.02,2);
  b.pop().pop().pop(); ExtraConfig.define(b); SPEC=b.build();
 }
 public static LightCurve bigLight() { return new LightCurve(BIG_DURATION.get(),BIG_MINIMUM.get(),BIG_DECAY.get(),BIG_HOLD.get()); }
 public static LightCurve autoLight() { return new LightCurve(AUTO_DURATION.get(),0,0.25,2); }
 public static LightLayout bigLayout() { return new LightLayout(BIG_COUNT.get(),BIG_LENGTH.get(),BIG_WIDTH.get(),2,FALLBACK_SPEED.get()); }
 public static LightLayout autoLayout() { return new LightLayout(AUTO_COUNT.get(),AUTO_LENGTH.get(),AUTO_WIDTH.get(),0,0); }
 public static LightCurve advancedBigLifetime() { return new LightCurve(BLAST_PROFILE.duration.get()+SMOKE_PROFILE.duration.get(),0,0,0); }
 public static LightLayout advancedBigLayout() { return new LightLayout(1,0,0,BLAST_PROFILE.duration.get(),FALLBACK_SPEED.get()); }
}
