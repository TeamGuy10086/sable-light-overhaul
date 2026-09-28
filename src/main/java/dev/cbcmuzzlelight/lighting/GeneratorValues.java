package dev.cbcmuzzlelight.lighting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * One generator's edited values, keyed by {@link GeneratorFields} ids.
 *
 * <p>A missing key means "still the config default", so a world saved before a field existed, or a
 * block copied from a blueprint, keeps working with no migration. Reading and writing by id is also
 * what lets the configuration page render any field without knowing what it does.
 *
 * <p>Kept apart from the block entity so the whole value model is testable without a live registry.
 */
public final class GeneratorValues {
 private static final String DATA="Data";
 private final CompoundTag data=new CompoundTag();
 public double number(GeneratorKind kind,String id){
  return data.contains(id)?data.getDouble(id):GeneratorFields.defaultNumber(kind,id);
 }
 public boolean flag(GeneratorKind kind,String id){
  return data.contains(id)?data.getBoolean(id):GeneratorFields.defaultFlag(kind,id);
 }
 public String text(GeneratorKind kind,String id){
  return data.contains(id)?data.getString(id):GeneratorFields.defaultText(kind,id);
 }
 /** @returns whether the value changed. */
 public boolean setNumber(GeneratorKind kind,String id,double value){
  var field=GeneratorFields.find(kind,id);
  if(field==null||field.type()!=GeneratorFields.Type.NUMBER)return false;
  double clamped=GeneratorFields.clamp(kind,id,value);
  if(data.contains(id)&&data.getDouble(id)==clamped)return false;
  data.putDouble(id,clamped);
  return true;
 }
 public boolean setFlag(GeneratorKind kind,String id,boolean value){
  var field=GeneratorFields.find(kind,id);
  if(field==null||field.type()!=GeneratorFields.Type.TOGGLE)return false;
  // Compared against the stored value, never against the effective one: an explicit write is kept
  // even when it matches the config default, so a later config edit cannot silently re-decide it.
  if(data.contains(id)&&data.getBoolean(id)==value)return false;
  data.putBoolean(id,value);
  return true;
 }
 /** Store an edited string; the emitter falls back to the config default if it stops decoding. */
 public boolean setText(GeneratorKind kind,String id,String value){
  var field=GeneratorFields.find(kind,id);
  if(field==null||field.type()!=GeneratorFields.Type.TEXT)return false;
  if(data.contains(id)&&data.getString(id).equals(value))return false;
  data.putString(id,value);
  return true;
 }
 /** Horizontal offset from the block, in body space: the height always comes from the ground. */
 public Vec3 offset(GeneratorKind kind){
  return new Vec3(number(kind,GeneratorFields.OFFSET_X),0,number(kind,GeneratorFields.OFFSET_Z));
 }
 /** Move the emission point one step towards a face; vertical faces are refused at the caller. */
 public void nudge(GeneratorKind kind,int dx,int dz,double step){
  setNumber(kind,GeneratorFields.OFFSET_X,number(kind,GeneratorFields.OFFSET_X)+dx*step);
  setNumber(kind,GeneratorFields.OFFSET_Z,number(kind,GeneratorFields.OFFSET_Z)+dz*step);
 }
 public void save(CompoundTag tag){tag.put(DATA,data.copy());}
 public void load(CompoundTag tag){
  for(String key:java.util.List.copyOf(data.getAllKeys()))data.remove(key);
  data.merge(tag.getCompound(DATA));
 }
}
