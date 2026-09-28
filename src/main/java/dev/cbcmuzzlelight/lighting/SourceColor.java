package dev.cbcmuzzlelight.lighting;

import java.util.*;
import net.minecraft.world.item.DyeColor;

/**
 * Colour palette for registered light sources, shared by both sides.
 *
 * <p>Vanilla's dye ids run {@code white(0) .. black(15)}. Two of those slots are
 * useless as a light tint, so the palette reuses the ends:
 *
 * <ul>
 *   <li>{@code 0} untinted — the configured default light colour
 *   <li>{@code 1..14} — DyeColor ids 1..14 (orange .. red) used as a tint
 *   <li>{@code 15} flashing — cycles ids 1..14 every {@link #FLASH_TICKS} ticks
 * </ul>
 *
 * <p>A registered block's colour is resolved once per registration: the config
 * override by block id first, then the id's own path scanned left to right for
 * the first colour word, then untinted. A dye item on the block overrides the
 * result for good.
 */
public final class SourceColor {
 /** Palette size; an index is a {@link DyeColor} id. */
 public static final int COUNT=16;
 /** Untinted: whatever the client's default light colour is. */
 public static final int UNTINTED=0;
 /** Flashing: rotates the tint through ids 1..14. */
 public static final int FLASHING=15;
 /** Ticks between two flashing steps. */
 public static final int FLASH_TICKS=10;
 /** Stored marker for "no explicit colour": re-resolved from the block on each cycle. */
 public static final int AUTO=-1;
 /** Layout names, in DyeColor id order; the two ends are the repurposed slots. */
 private static final String[] NAMES={"透明","橙","品红","淡蓝","黄","黄绿","粉","灰","淡灰","青","紫","蓝","棕","绿","红","闪烁"};
 /** Dye words in DyeColor id order, so a name scan needs no lookup table. */
 private static final String[] WORDS={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
 /** Second word of the two-word dye names, indexed by the first word's id. */
 private static final Map<String,Integer> TWO_WORD=Map.of("light_blue",3,"light_gray",8);
 /**
 * Unambiguous colour synonyms, so a pack that says "scarlet" still lands on red.
 * Deliberately narrow: a wrong tint is worse than no tint, so only words that
 * mean exactly one dye colour are listed.
 */
 private static final Map<String,Integer> SYNONYMS=Map.ofEntries(
  Map.entry("scarlet",14),Map.entry("crimson",14),Map.entry("vermilion",14),Map.entry("ruby",14),Map.entry("cherry",14),
  Map.entry("amber",1),Map.entry("tangerine",1),
  Map.entry("gold",4),Map.entry("golden",4),Map.entry("lemon",4),
  Map.entry("emerald",13),Map.entry("jade",13),Map.entry("mint",13),Map.entry("olive",13),Map.entry("verdant",13),
  Map.entry("turquoise",9),Map.entry("teal",9),Map.entry("aqua",9),Map.entry("aquamarine",9),
  Map.entry("azure",3),Map.entry("sky",3),
  Map.entry("sapphire",11),Map.entry("cobalt",11),Map.entry("navy",11),Map.entry("lapis",11),
  Map.entry("violet",10),Map.entry("amethyst",10),Map.entry("plum",10),Map.entry("lavender",10),
  Map.entry("fuchsia",2),Map.entry("orchid",2),
  Map.entry("coral",6),Map.entry("salmon",6),Map.entry("peach",6),
  Map.entry("chocolate",12),Map.entry("cocoa",12),
  Map.entry("silver",7),Map.entry("ash",7),
  Map.entry("snow",0),Map.entry("pearl",0),Map.entry("ivory",0),Map.entry("cream",0));
 /** Parsed override table plus the raw list it came from, so a config edit re-parses. */
 private static List<?> parsedFrom;
 private static Map<String,Integer> parsed=Map.of();
 private static final Map<String,Integer> RESOLVED=new HashMap<>();
 private SourceColor(){}
 /** Layout name for one palette index, for chat feedback. */
 public static String name(int index){return NAMES[index<0||index>=COUNT?UNTINTED:index];}
 /** Whether a value is a usable palette index. */
 public static boolean valid(int index){return index>=UNTINTED&&index<COUNT;}
 /** The palette index a dye word names, or {@code null} when the word is not a colour. */
 public static Integer word(String token){
  for(int i=0;i<WORDS.length;i++) if(WORDS[i].equals(token)) return i;
  return SYNONYMS.get(token);
 }
 /** Whether one config row is a usable {@code namespace:block=colour} entry. */
 public static boolean overrideRow(Object value){
  if(!(value instanceof String row)) return false;
  int split=row.lastIndexOf('=');
  return split>0&&split<row.length()-1&&word(row.substring(split+1).trim().toLowerCase(Locale.ROOT))!=null;
 }
 /** Parse {@code "namespace:block=colour"} entries; unparsable rows are ignored. */
 public static Map<String,Integer> parse(List<?> entries){
  if(entries==parsedFrom) return parsed;
  invalidate();
  Map<String,Integer> map=new HashMap<>();
  for(Object raw:entries){
   if(!(raw instanceof String row)) continue;
   int split=row.lastIndexOf('=');
   if(split<=0||split==row.length()-1) continue;
   Integer index=word(row.substring(split+1).trim().toLowerCase(Locale.ROOT));
   if(index==null) continue;
   map.put(row.substring(0,split).trim().toLowerCase(Locale.ROOT),index);
  }
  parsed=Map.copyOf(map);parsedFrom=entries;
  return parsed;
 }
 /**
 * Palette index for a block, cached per id: an override wins, otherwise the id's
 * own path is scanned left to right for the first colour word.
 * @param blockId - block registry id, e.g. {@code create:red_ramp}.
 * @param overrides - parsed override table.
 * @param nameScan - whether the id's name may decide the colour at all.
 * @returns a palette index, {@link #UNTINTED} when nothing matched.
 */
 public static int resolve(String blockId,Map<String,Integer> overrides,boolean nameScan){
  // The override table is checked before the cache, so a newly added row wins immediately.
  Integer override=overrides.get(blockId);
  if(override!=null) return override;
  if(!nameScan) return UNTINTED;
  Integer cached=RESOLVED.get(blockId);
  if(cached!=null) return cached;
  int index=scan(blockId);
  RESOLVED.put(blockId,index);
  return index;
 }
 /** Drop the per-id cache; called when the override table or the toggle changes. */
 public static void invalidate(){RESOLVED.clear();}
 /** First colour word in the block id's path, which is the part after the namespace. */
 private static int scan(String blockId){
  int colon=blockId.indexOf(':');
  String[] tokens=(colon<0?blockId:blockId.substring(colon+1)).split("_");
  for(int i=0;i<tokens.length;i++){
   // Two-word dye names win at the same starting word, so light_blue is not read as blue.
   if(i+1<tokens.length){
    Integer joined=TWO_WORD.get(tokens[i]+"_"+tokens[i+1]);
    if(joined!=null) return joined;
   }
   Integer one=word(tokens[i]);
   if(one!=null) return one;
  }
  return UNTINTED;
 }
 /**
 * Linear RGB for a palette index.
 * @param index - palette index; out of range means untinted.
 * @param phase - flashing step, in {@link #FLASH_TICKS}-tick units.
 * @param out - three-element output array.
 * @param dr - default light colour, red.
 * @param dg - default light colour, green.
 * @param db - default light colour, blue.
 * @returns {@code out}, filled with linear components.
 */
 public static float[] rgb(int index,int phase,float[] out,float dr,float dg,float db){
  int slot=slot(index,phase);
  if(slot==UNTINTED){out[0]=dr;out[1]=dg;out[2]=db;return out;}
  int argb=DyeColor.values()[slot].getTextureDiffuseColor();
  out[0]=linear((argb>>16)&0xFF);out[1]=linear((argb>>8)&0xFF);out[2]=linear(argb&0xFF);
  return out;
 }
 /** Palette index after flashing is resolved; untinted for anything out of range. */
 public static int slot(int index,int phase){
  int i=valid(index)?index:UNTINTED;
  return i==FLASHING?1+Math.floorMod(phase,14):i;
 }
 /** One sRGB channel (0..255) as a linear component; the shader works in linear space. */
 private static float linear(int channel){
  float value=channel/255.0F;
  return (float)Math.pow(value,2.2);
 }
}
