package dev.sablelightoverhaul.lighting;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
public final class RegisteredSources extends SavedData {
 /**
  * @param color palette index, or {@link SourceColor#AUTO} when the block's own id decides it
  * @param radius light radius in blocks, or {@link #RADIUS_AUTO} to derive it from the brightness
  * @param shadowMode the lamp's own answer on whether its shadow is drawn at all and how: 0 none,
  *                   1 a hard edge, 2 a soft one. It is a wish, not a decision -- the client's own
  *                   config caps the tap count and can force hard edges, so a lamp cannot spend a
  *                   visitor's frame time without their leave. Spheres only; a beam ignores it and
  *                   stays hard.
  * @param shadowSteps this lamp's own tap ceiling, or {@link #SHADOW_STEPS_AUTO} for the client's.
  *                    Only ever lowers it, for the same reason.
  * @param cloudReach this lamp's own cloud reach in blocks, or {@link #CLOUD_REACH_GLOBAL} to leave it
  *                   to the client's global setting. This is the one field that is a *reach* rather than
  *                   a look: the cloud term is a volume term and a lamp under a deck is 140 blocks from
  *                   it, so "does this lamp light the clouds" is a question only its owner can answer
  *                   (CLOUD-LIGHTING.zh-CN.md, decision 1).
  * @param flood whether this lamp is lit the way vanilla block light is (omni-directional, no ambient
  *              occlusion, no shadow) instead of by this mod's shaped term. True by default: the shaped
  *              term is what a lamp looks like when it is *not* standing in for a block light, and the
  *              first thing anyone notices about a taken-over lamp was that it had gone dim and lost the
  *              glow, because that is what replacing an omni-directional pool with a terminator and AO
  *              does. The flood mode keeps the lamp's own colour and radius while matching the look.
  * @param faces which of the six block faces this lamp emits through, bit i for {@link #FACE_BITS}: a
  *              lamp on a wall must not light the wall it is mounted on, and Minecraft has no sloped
  *              surfaces to hide the fixture behind. Samples inside the lamp's own block always receive
  *              light, so an excluded face cannot leave the fixture dark. Faces apply to spheres only --
  *              a beam already says where it points.
  * @param emission this lamp's own brightness, or {@link #EMISSION_FROM_BLOCK} to use the level of the
  *                 block it stands for. The override exists for the lamps we never replaced: their block
  *                 emits what it emits and nothing in a page can change that, so before this there was no
  *                 way to dim one at all.
  * @param cones the directions this lamp may cast in, as the text the page was given; empty means every
  *              direction. Kept as text rather than as numbers because the text is the only form that
  *              survives a round trip to the page unchanged, and because the numbers are derived from it
  *              in one place ({@link Cones#parse}).
  * @param blink the lamp's on/off pattern as the page wrote it (see {@link BlinkPattern}); empty means
  *              steady. It lives in the registration as well as in the stand-in's block entity because a
  *              lamp we only registered has no block entity of ours to keep it.
  */
 public record Entry(int id,SourceAnchor anchor,int mode,boolean spotlight,int color,int radius,
                     int shadowMode,int shadowSteps,int cloudReach,boolean flood,int faces,int emission,
                     String cones,String blink) {}
 /** No radius of its own: the radius follows the block's brightness, as it always did. */
 public static final int RADIUS_AUTO=0;
 /** The lamp's shadow is soft unless its page says otherwise: the shipped default for a new lamp. */
 public static final int SHADOW_SOFT=2;
 /** No tap ceiling of its own; the client's applies. */
 public static final int SHADOW_STEPS_AUTO=0;
 /**
  * No cloud reach of its own: the client's global {@code clouds.sphereReach} applies, which itself
  * defaults to zero. So a lamp nobody has given a reach lights no clouds, which is both the shipped
  * behaviour and what the design asks for (lamps are the most numerous lights in a scene and they sit
  * under the clouds).
  */
 public static final int CLOUD_REACH_GLOBAL=0;
 /** Every face emits: the default, and what a lamp that nobody has configured keeps. */
 public static final int FACES_ALL=63;
 /** No brightness of its own: the block's own light level is the answer, as it always was. */
 public static final int EMISSION_FROM_BLOCK=0;
 /** The face order the mask uses, as the page labels them and the shader tests them. */
 public static final String[] FACE_BITS={"下","上","北","南","西","东"};
 public final Map<Integer,Entry> entries=new LinkedHashMap<>();private int nextId=1;
 /**
  * Every change to the registry goes through here, so this is where the take-over is told that its cached
  * positions are stale. Without it the cache would have to be rebuilt on a timer -- which is what it used to do,
  * paying for a walk of this map every two seconds whether anything had changed or not.
  */
 @Override public void setDirty() {
  super.setDirty();
  ServerTakeOver.invalidate();
 }
 public static RegisteredSources get(ServerLevel level) {return level.getDataStorage().computeIfAbsent(new Factory<>(RegisteredSources::new,RegisteredSources::load),"sablelightoverhaul_sources");}
 public Entry find(SourceAnchor a,boolean spot) {return entries.values().stream().filter(e->e.spotlight==spot && e.anchor.equals(a)).findFirst().orElse(null);}
 /**
  * The registration whose lamp is <em>at this world position</em>, whether or not the position that
  * produced it is the one the registration is keyed by.
  *
  * <p>This exists because a lamp has two positions that describe the same thing: its block, which on a
  * physics body sits in that body's plot, and the spot in the world where the player sees and clicks it.
  * A page or an edit that arrives with one of them used to look only for an exact key match on the other,
  * find nothing, and either report every value as zero or register a second lamp at the world position --
  * which is one lamp drawing two lights, one of them nailed to a place the hull has already left.
  *
  * @param world the position in the world, as the player clicks it
  * @param tolerance how far a registration may be from that position and still be that lamp
  */
 public Entry findNear(net.minecraft.world.level.Level level,net.minecraft.world.phys.Vec3 world,double tolerance) {
  Entry best=null;
  double bestDistance=tolerance*tolerance;
  for(var e:entries.values()){
   var resolved=e.anchor.resolve(level);
   if(resolved==null)continue;
   // The block's own position, put back where the ship is drawn: this is the point the player aimed at.
   var placed=e.anchor.world(level,resolved,net.minecraft.world.phys.Vec3.atCenterOf(resolved));
   if(placed==null)continue;
   double distance=placed.distanceToSqr(world);
   if(distance<=bestDistance){bestDistance=distance;best=e;}
  }
  return best;
 }
 /**
  * Cycle one ground registration: none → enabled → forced → none. A new entry is
  * stored as {@link SourceColor#AUTO}, so the block's own id keeps deciding its colour until a dye
  * overrides it; that way a config or name change lands without re-registering anything.
  * @param a - the block's anchor.
  * @returns the new mode.
  */
 public int cycle(SourceAnchor a) {
  var e=find(a,false);int mode=e==null?1:(e.mode+1)%3;
  if(e!=null)entries.remove(e.id);if(mode!=0){int id=e==null?nextId++:e.id;entries.put(id,new Entry(id,a,mode,false,e==null?SourceColor.AUTO:e.color(),e==null?RADIUS_AUTO:e.radius(),e==null?SHADOW_SOFT:e.shadowMode(),e==null?SHADOW_STEPS_AUTO:e.shadowSteps(),e==null?CLOUD_REACH_GLOBAL:e.cloudReach(),e==null||e.flood(),e==null?FACES_ALL:e.faces(),e==null?EMISSION_FROM_BLOCK:e.emission(),e==null?"":e.cones(),e==null?"":e.blink()));}setDirty();return mode;
 }
 /** Set one registration's palette index explicitly, the way a dye item does. */
 public boolean setColor(SourceAnchor a,int color) {
  var e=find(a,false);if(e==null)return false;
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,color,e.radius(),e.shadowMode(),e.shadowSteps(),e.cloudReach(),e.flood(),e.faces(),e.emission(),e.cones(),e.blink()));setDirty();return true;
 }
 /**
  * Set one registration's own light radius in blocks, or {@link #RADIUS_AUTO} to leave it to the
  * brightness. Only a registered source has a radius to set, so an unregistered anchor is refused.
  */
 public boolean setRadius(SourceAnchor a,int radius) {
  var e=find(a,false);if(e==null)return false;
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),radius,e.shadowMode(),e.shadowSteps(),e.cloudReach(),e.flood(),e.faces(),e.emission(),e.cones(),e.blink()));setDirty();return true;
 }
 /**
  * Set one registration's shadow behaviour: whether it casts, and how many taps it may spend.
  *
  * <p>It is the lamp's own wish. The client applies it only as far as its own budget allows, which is
  * what stops a lamp on somebody else's vehicle from deciding a visitor's frame time.
  */
 public boolean setShadow(SourceAnchor a,int shadowMode,int shadowSteps) {
  var e=find(a,false);if(e==null)return false;
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),e.radius(),shadowMode,shadowSteps,e.cloudReach(),e.flood(),e.faces(),e.emission(),e.cones(),e.blink()));setDirty();return true;
 }
 /**
  * Set one lamp's light style: whether it is lit like vanilla block light, and which of its six faces it
  * emits through.
  *
  * <p>Written as a pair for the same reason the shadow answer is: the page saves whichever of the two it
  * did not just edit by reading it back, so editing one can never silently drop the other.
  */
 public boolean setLightStyle(SourceAnchor a,boolean flood,int faces) {
  var e=find(a,false);if(e==null)return false;
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),e.radius(),e.shadowMode(),e.shadowSteps(),e.cloudReach(),flood,faces,e.emission(),e.cones(),e.blink()));setDirty();return true;
 }
 /**
  * Set one lamp's own brightness, or {@link #EMISSION_FROM_BLOCK} to go back to the block's own level.
  *
  * <p>Only the lamps we did not replace need this: a stand-in carries the level in its block state, which
  * is what the server's light pass reads, while a block we left alone emits whatever it was built to emit.
  */
 /**
  * Set one lamp's shadow cones from the text the page was given, or clear them with an empty string.
  *
  * <p>The text is parsed here rather than stored unchecked: a string that does not parse keeps the lamp
  * exactly as it was, and the caller gets the reason to show. An empty string is not an error -- it is
  * how "every direction casts" is spelled.
  */
 public boolean setCones(SourceAnchor a,String cones) throws IllegalArgumentException {
  var e=find(a,false);if(e==null)return false;
  String text=cones==null?"":cones.trim();
  Cones.parse(text);
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),e.radius(),e.shadowMode(),e.shadowSteps(),e.cloudReach(),e.flood(),e.faces(),e.emission(),text,e.blink()));setDirty();return true;
 }
 /**
  * Set one lamp's blink pattern from the text the page was given, or clear it with an empty string.
  *
  * <p>Stored on the registration as well as on the stand-in, because a lamp that was only registered has
  * no block entity of ours to carry it. The text is validated by the caller ({@link LightSourceServer#setBlink}).
  */
 public boolean setBlink(SourceAnchor a,String blink) {
  var e=find(a,false);if(e==null)return false;
  String text=blink==null?"":blink.trim();
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),e.radius(),e.shadowMode(),e.shadowSteps(),e.cloudReach(),e.flood(),e.faces(),e.emission(),e.cones(),text));setDirty();return true;
 }
 public boolean setEmissionOverride(SourceAnchor a,int emission) {
  var e=find(a,false);if(e==null)return false;
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),e.radius(),e.shadowMode(),e.shadowSteps(),e.cloudReach(),e.flood(),e.faces(),emission,e.cones(),e.blink()));setDirty();return true;
 }
 /**
  * Set one registration's own cloud reach in blocks, or {@link #CLOUD_REACH_GLOBAL} to follow the
  * client's global setting.
  *
  * <p>A lamp's reach is stored rather than derived because the cloud term is the one place where the
  * light's own radius is not an answer: a registered lamp spends its life under the deck, so the only
  * person who can say whether it lights the clouds is the person who placed it.
  */
 public boolean setCloudReach(SourceAnchor a,int cloudReach) {
  var e=find(a,false);if(e==null)return false;
  entries.put(e.id,new Entry(e.id,e.anchor,e.mode,false,e.color(),e.radius(),e.shadowMode(),e.shadowSteps(),cloudReach,e.flood(),e.faces(),e.emission(),e.cones(),e.blink()));setDirty();return true;
 }
 /**
  * Set one registration's mode directly, the way a cycle does: 0 unregisters, 1 and 2 register or
  * update. A source that is already registered keeps its colour, its radius and its shadow behaviour,
  * because the page writes one field at a time and changing the mode must not silently drop the rest.
  * @param a - the block's anchor.
  * @param mode - the new mode.
  * @returns whether anything changed; unregistering an unregistered anchor changes nothing.
  */
 public boolean setMode(SourceAnchor a,int mode) {
  var e=find(a,false);
  if(mode<=0){if(e==null)return false;entries.remove(e.id);setDirty();return true;}
  if(e==null){register(a,mode);return true;}
  entries.put(e.id,new Entry(e.id,e.anchor,mode,false,e.color(),e.radius(),e.shadowMode(),e.shadowSteps(),e.cloudReach(),e.flood(),e.faces(),e.emission(),e.cones(),e.blink()));setDirty();return true;
 }
 public void spotlight(SourceAnchor a,boolean add) {var e=find(a,true);if(add&&e==null){int id=nextId++;entries.put(id,new Entry(id,a,2,true,SourceColor.UNTINTED,RADIUS_AUTO,SHADOW_SOFT,SHADOW_STEPS_AUTO,CLOUD_REACH_GLOBAL,true,FACES_ALL,EMISSION_FROM_BLOCK,"",""));setDirty();}else if(!add&&e!=null){entries.remove(e.id);setDirty();}}
 /** Batch path: sets a ground registration's mode directly, adding it when it does not exist yet. */
 public Entry register(SourceAnchor a,int mode) {
  var e=find(a,false);int id=e==null?nextId++:e.id;
  var entry=new Entry(id,a,mode,false,e==null?SourceColor.AUTO:e.color(),e==null?RADIUS_AUTO:e.radius(),e==null?SHADOW_SOFT:e.shadowMode(),e==null?SHADOW_STEPS_AUTO:e.shadowSteps(),e==null?CLOUD_REACH_GLOBAL:e.cloudReach(),e==null||e.flood(),e==null?FACES_ALL:e.faces(),e==null?EMISSION_FROM_BLOCK:e.emission(),e==null?"":e.cones(),e==null?"":e.blink());entries.put(id,entry);setDirty();return entry;
 }
 /**
  * Registers an anchor only when it has no registration yet, so an existing mode and colour survive.
  *
  * <p>This is what a stand-in placed by hand uses: it must become a light, but a lamp that was already
  * registered keeps the mode and dye colour its owner chose.
  * @returns true when a registration was added.
  */
 public boolean ensure(SourceAnchor a,int mode) {
  if(find(a,false)!=null)return false;
  register(a,mode);return true;
 }
 /** Batch path: drops a ground registration by anchor, so a range command does not need its id. */
 public boolean removeAnchor(SourceAnchor a) {
  var e=find(a,false);if(e==null)return false;entries.remove(e.id);setDirty();return true;
 }
 public boolean remove(int id) {if(entries.remove(id)==null)return false;setDirty();return true;}
 public static RegisteredSources load(CompoundTag tag,HolderLookup.Provider registries) {
  var data=new RegisteredSources();data.nextId=Math.max(1,tag.getInt("NextId"));
  var list=tag.getList("Sources",Tag.TAG_COMPOUND);
  for(var raw:list){var t=(CompoundTag)raw;int id=t.getInt("Id"),mode=t.getInt("Mode");if(id<=0||mode<1||mode>2)continue;
   var a=new SourceAnchor(t.hasUUID("Ship")?t.getUUID("Ship"):null,BlockPos.of(t.getLong("Pos")));
   // A world written before these two existed gets the shipped defaults, not zeroes: shadowMode 0 would
   // silently turn every existing lamp's shadow off on the first load after the update.
   int shadowMode=t.contains("ShadowMode")?t.getInt("ShadowMode"):SHADOW_SOFT;
   int shadowSteps=t.contains("ShadowSteps")?t.getInt("ShadowSteps"):SHADOW_STEPS_AUTO;
   // A world written before the cloud field existed gets CLOUD_REACH_GLOBAL, which is also what a lamp
   // with no opinion holds: the client's global setting decides, exactly as it did before the field.
   int cloudReach=t.contains("CloudReach")?t.getInt("CloudReach"):CLOUD_REACH_GLOBAL;
   // A world written before the light style existed gets the shipped default: flood, every face. That is
   // also what makes every lamp in an existing world stop being dim the moment it is loaded.
   boolean flood=!t.contains("Flood")||t.getBoolean("Flood");
   int faces=t.contains("Faces")?t.getInt("Faces"):FACES_ALL;
   int emission=t.contains("Emission")?t.getInt("Emission"):EMISSION_FROM_BLOCK;
   String cones=t.getString("Cones");
   data.entries.put(id,new Entry(id,a,mode,t.getBoolean("Spotlight"),t.contains("Color")?t.getInt("Color"):SourceColor.AUTO,t.contains("Radius")?t.getInt("Radius"):RADIUS_AUTO,shadowMode,shadowSteps,cloudReach,flood,faces,emission,cones,t.getString("Blink")));data.nextId=Math.max(data.nextId,id+1);
  }return data;
 }
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
  tag.putInt("NextId",nextId);var list=new ListTag();for(var e:entries.values()) {var t=new CompoundTag();t.putInt("Id",e.id);t.putInt("Mode",e.mode);t.putBoolean("Spotlight",e.spotlight);t.putInt("Color",e.color);t.putInt("Radius",e.radius);t.putInt("ShadowMode",e.shadowMode);t.putInt("ShadowSteps",e.shadowSteps);t.putInt("CloudReach",e.cloudReach);t.putBoolean("Flood",e.flood);t.putInt("Faces",e.faces);t.putInt("Emission",e.emission);t.putString("Cones",e.cones);t.putString("Blink",e.blink);t.putLong("Pos",e.anchor.pos().asLong());if(e.anchor.ship()!=null)t.putUUID("Ship",e.anchor.ship());list.add(t);}tag.put("Sources",list);return tag;
 }
}
