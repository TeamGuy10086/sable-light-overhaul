package dev.sablelightoverhaul.lighting;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Particle options written exactly the way {@code /particle} writes them.
 *
 * <p>Every particle choice in the config and on the block pages is the text a player would type
 * after {@code /particle}, so anything that works in the command works here and back: the string
 * is split at its first brace, parsed with the same tag parser and decoded with that particle
 * type's own codec. A string that stops decoding (a mod removed, a typo) falls back instead of
 * killing the emitter, and says so once per distinct string.
 */
public final class ParticleSpec {
 private static final Map<String,ParticleOptions> CACHE=new HashMap<>();
 private static final Set<String> WARNED=new HashSet<>();
 /** Specs whose rejection has already been explained, so typing does not spam the log. */
 private static final Set<String> WHY=new HashSet<>();
 private ParticleSpec(){}
 /**
  * Decode one spec string.
  * @param level - level whose registry access decodes the options.
  * @param text - {@code "id"} or {@code "id{snbt}"}, as typed after {@code /particle}.
  * @returns the options, or null when this string cannot be decoded right now.
  */
 @SuppressWarnings("unchecked")
 public static ParticleOptions parse(Level level,String text){
  if(level==null||text==null||text.isBlank()) return null;
  String key=text.trim();
  ParticleOptions cached=CACHE.get(key);
  if(cached!=null) return cached;
  ParticleOptions parsed=decode(level,key);
  if(parsed==null){
   if(WARNED.add(key)) LogUtils.getLogger().warn("Sable Light Overhaul: cannot decode particle \"{}\", the configured fallback is used instead",key);
   return null;
  }
  if(CACHE.size()<512) CACHE.put(key,parsed);
  return parsed;
 }
 private static ParticleOptions decode(Level level,String text){
  int brace=text.indexOf('{');
  ResourceLocation location=ResourceLocation.tryParse((brace<0?text:text.substring(0,brace)).trim());
  if(location==null) { why(text,"the part before '{' is not a resource location"); return null; }
  ParticleType<?> type=BuiltInRegistries.PARTICLE_TYPE.get(location);
  if(type==null) { why(text,"no particle type is registered as "+location); return null; }
  CompoundTag tag;
  try { tag=brace<0?new CompoundTag():TagParser.parseTag(text.substring(brace)); }
  catch(CommandSyntaxException ex){ why(text,"the parameters do not parse: "+ex.getMessage()); return null; }
  // A type without a codec cannot be built from a spec at all, and the catch below used to swallow the null with
  // everything else: "this particle cannot be described as text" looked the same as "you typed it wrong".
  var codec=type.codec();
  if(codec==null) { why(text,location+" has no codec, so it cannot be described as text"); return null; }
  try {
   return (ParticleOptions)codec.codec().parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow();
  } catch(RuntimeException ex){ why(text,"the codec rejected it: "+ex); return null; }
 }
 /** One line per distinct spec that failed, so a page that refuses a particle says why instead of only turning red. */
 private static void why(String text,String reason){
  if(!WHY.add(text))return;
  com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul particle spec \"{}\" was rejected: {}",text,reason);
 }
 /**
  * Whether a spec decodes right now, without caching or warning: a configuration page calls this
  * on every keystroke, so an unfinished string must just read as invalid.
  */
 public static boolean valid(Level level,String text){
  if(level==null||text==null||text.isBlank())return false;
  return decode(level,text.trim())!=null;
 }
 /**
  * The same options with the one colour a spec can carry replaced, so ground dust can take the
  * colour of the block it was kicked off. Only meanings this mod understands are rewritten; every
  * other particle is returned untouched.
  * @param options - decoded options.
  * @param argb - the replacement colour.
  * @returns options to spawn instead.
  */
 public static ParticleOptions retint(ParticleOptions options,int argb){
  if(options instanceof DustParticleOptions dust){
   return new DustParticleOptions(new Vector3f(((argb>>16)&0xFF)/255.0F,((argb>>8)&0xFF)/255.0F,(argb&0xFF)/255.0F),dust.getScale());
  }
  if(options instanceof GroundDustParticleData dust) return dust.withColor(argb&0xFFFFFF);
  return options;
 }
 /**
  * Send one spawn call to every player that can see it.
  * @param count - particle count; 0 spawns a single particle whose velocity is exactly the delta.
  */
 public static void spawn(ServerLevel level,List<ServerPlayer> players,ParticleOptions options,
                          double x,double y,double z,int count,double dx,double dy,double dz,double speed){
  // Vanilla drops every player past 32 blocks even here, which is why this does not call it directly.
  ForcedParticles.send(level,players,options,x,y,z,count,dx,dy,dz,speed);
 }
}
