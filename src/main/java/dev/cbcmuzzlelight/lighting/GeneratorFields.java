package dev.cbcmuzzlelight.lighting;

import java.util.*;

/**
 * The editable values of a generator, in one table.
 *
 * <p>The table is the single source of truth for three consumers: the block page (which renders
 * one widget per field without knowing what any of them mean), the block entity (which stores
 * them by id and clamps them to the table's range) and the emitter (which reads them by id).
 * A value is absent until a player edits it, and then the config default applies, so a freshly
 * placed generator follows the config and an edited one keeps its own numbers.
 */
public final class GeneratorFields {
 /** Widget kinds a page knows how to draw. */
 public enum Type { TEXT, NUMBER, TOGGLE }
 /** One value; {@code owner} is null for fields both kinds have. */
 public record Field(String id,Type type,double min,double max,GeneratorKind owner) {
  public String labelKey(){return "cbcmuzzlelight.generator.field."+id;}
 }
 /** What a generator starts with. The spec is deliberately not a config value: a string that looks like a resource location breaks the config screens other mods provide, and the block page is where a player is meant to type one anyway. */
 public static final String DEFAULT_DUST="cbcmuzzlelight:ground_dust{size:2.4,color:16777215,lifetime:60,stretch:2.0,track:true}";
 public static final String DEFAULT_SMOKE="minecraft:campfire_cosy_smoke";
 public static final String ENABLED="enabled",FORCE="forceEmit",OFFSET_X="offsetX",OFFSET_Z="offsetZ",PARTICLE="particle";
 private static final double OFFSET_LIMIT=16;
 private GeneratorFields(){}
 private static final List<Field> FIELDS=List.of(
  new Field(ENABLED,Type.TOGGLE,0,1,null),
  new Field(FORCE,Type.TOGGLE,0,1,null),
  new Field(OFFSET_X,Type.NUMBER,-OFFSET_LIMIT,OFFSET_LIMIT,null),
  new Field(OFFSET_Z,Type.NUMBER,-OFFSET_LIMIT,OFFSET_LIMIT,null),
  new Field(PARTICLE,Type.TEXT,0,0,null),
  // Dust: the emitter scales the amount with the body's horizontal speed.
  new Field("rate",Type.NUMBER,0,200,GeneratorKind.DUST),
  new Field("maxPerScan",Type.NUMBER,0,200,GeneratorKind.DUST),
  new Field("searchDepth",Type.NUMBER,1,128,GeneratorKind.DUST),
  new Field("minSpeed",Type.NUMBER,0,4,GeneratorKind.DUST),
  new Field("lifetimeMin",Type.NUMBER,1,400,GeneratorKind.DUST),
  new Field("lifetimeMax",Type.NUMBER,1,400,GeneratorKind.DUST),
  // Size and stretch are rolled per particle inside these ranges, so one refresh is a field of
  // clouds of different sizes rather than copies of one sprite.
  new Field("sizeMin",Type.NUMBER,0.1,16,GeneratorKind.DUST),
  new Field("sizeMax",Type.NUMBER,0.1,16,GeneratorKind.DUST),
  // The patch the dust is stirred over, plus its own motion, so the track dust can be tuned without
  // touching the shared defaults.
  new Field("scatter",Type.NUMBER,0,16,GeneratorKind.DUST),
  new Field("damping",Type.NUMBER,0.1,1,GeneratorKind.DUST),
  new Field("gravity",Type.NUMBER,0,4,GeneratorKind.DUST),
  new Field("stretchMin",Type.NUMBER,1,6,GeneratorKind.DUST),
  new Field("stretchMax",Type.NUMBER,1,6,GeneratorKind.DUST),
  new Field("upSpeedMin",Type.NUMBER,0,4,GeneratorKind.DUST),
  new Field("upSpeedMax",Type.NUMBER,0,4,GeneratorKind.DUST),
  new Field("tintFromBlock",Type.TOGGLE,0,1,GeneratorKind.DUST),
  // Smoke: redstone picks the cadence, so both ends of the curve are editable.
  new Field("intervalAtMaxSignal",Type.NUMBER,1,200,GeneratorKind.SMOKE),
  new Field("intervalAtMinSignal",Type.NUMBER,1,200,GeneratorKind.SMOKE),
  new Field("countAtMaxSignal",Type.NUMBER,0,64,GeneratorKind.SMOKE),
  new Field("countAtMinSignal",Type.NUMBER,0,64,GeneratorKind.SMOKE),
  // The jet itself: two angles and a speed, so a vent can be aimed. Nothing about the body's own
  // motion takes part: particles live in world space, and the cloud is left standing where it was.
  new Field("directionYaw",Type.NUMBER,0,360,GeneratorKind.SMOKE),
  new Field("directionPitch",Type.NUMBER,-90,90,GeneratorKind.SMOKE),
  new Field("speed",Type.NUMBER,0,4,GeneratorKind.SMOKE),
  // Only the vent may move vertically: where the ground is, the code decides.
  new Field("offsetY",Type.NUMBER,-OFFSET_LIMIT,OFFSET_LIMIT,GeneratorKind.SMOKE),
  new Field("riseSpeed",Type.NUMBER,0,2,GeneratorKind.SMOKE),
  new Field("lifetime",Type.NUMBER,1,600,GeneratorKind.SMOKE),
  new Field("size",Type.NUMBER,0.05,16,GeneratorKind.SMOKE));
 /** Every field of one kind, shared ones first. */
 public static List<Field> of(GeneratorKind kind){
  var result=new ArrayList<Field>();
  for(var field:FIELDS) if(field.owner()==null||field.owner()==kind) result.add(field);
  return List.copyOf(result);
 }
 /** One field by id, or null when this kind has no such field. */
 public static Field find(GeneratorKind kind,String id){
  for(var field:FIELDS) if(field.id().equals(id)&&(field.owner()==null||field.owner()==kind)) return field;
  return null;
 }
 /** Clamp a number into the field's declared range; unknown ids and types pass through. */
 public static double clamp(GeneratorKind kind,String id,double value){
  var field=find(kind,id);
  return field==null||field.type()!=Type.NUMBER?value:Math.min(Math.max(value,field.min()),field.max());
 }
 /** Config default of a numeric field, used until a player edits it. */
 public static double defaultNumber(GeneratorKind kind,String id){
  return switch(id){
   case OFFSET_X,OFFSET_Z->0;
   case "rate"->LightingCommon.DUST_RATE.get();
   case "maxPerScan"->LightingCommon.DUST_MAX_PER_SCAN.get();
   case "searchDepth"->LightingCommon.DUST_DEPTH.get();
   case "minSpeed"->LightingCommon.DUST_MIN_SPEED.get();
   case "lifetimeMin"->LightingCommon.DUST_LIFE_MIN.get();
   case "lifetimeMax"->LightingCommon.DUST_LIFE_MAX.get();
   case "size"->LightingCommon.SMOKE_SIZE.get();
   case "sizeMin"->LightingCommon.DUST_SIZE_MIN.get();
   case "sizeMax"->LightingCommon.DUST_SIZE_MAX.get();
   case "stretchMin"->LightingCommon.DUST_STRETCH_MIN.get();
   case "stretchMax"->LightingCommon.DUST_STRETCH_MAX.get();
   case "scatter"->LightingCommon.DUST_SCATTER.get();
   case "damping"->LightingCommon.DUST_DAMPING.get();
   case "gravity"->LightingCommon.DUST_GRAVITY.get();
   case "upSpeedMin"->LightingCommon.DUST_UP_MIN.get();
   case "upSpeedMax"->LightingCommon.DUST_UP_MAX.get();
   case "offsetY"->LightingCommon.SMOKE_OFFSET_Y.get();
   case "intervalAtMaxSignal"->LightingCommon.SMOKE_INTERVAL_MAX.get();
   case "intervalAtMinSignal"->LightingCommon.SMOKE_INTERVAL_MIN.get();
   case "countAtMaxSignal"->LightingCommon.SMOKE_COUNT_MAX.get();
   case "countAtMinSignal"->LightingCommon.SMOKE_COUNT_MIN.get();
   case "riseSpeed"->LightingCommon.SMOKE_RISE.get();
   case "directionYaw"->LightingCommon.SMOKE_YAW.get();
   case "directionPitch"->LightingCommon.SMOKE_PITCH.get();
   case "speed"->LightingCommon.SMOKE_SPEED.get();
   case "lifetime"->LightingCommon.SMOKE_LIFE.get();
   default->0;
  };
 }
 /** Config default of a toggle. */
 public static boolean defaultFlag(GeneratorKind kind,String id){
  return switch(id){
   case ENABLED->true;
   case FORCE->false;
   case "tintFromBlock"->LightingCommon.DUST_TINT.get();
   default->false;
  };
 }
 /** Config default of a text field. */
 public static String defaultText(GeneratorKind kind,String id){
  return PARTICLE.equals(id)?(kind==GeneratorKind.DUST?DEFAULT_DUST:DEFAULT_SMOKE):"";
 }
}
