package dev.sablelightoverhaul.lighting;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record PointLightAppearance(double x,double y,double z,boolean glow,boolean ring,boolean axis,
                                   double ringSize,double axisLength,double axisWidth) {
 public static final List<String> FIELDS=List.of("offsetX","offsetY","offsetZ","glow","glowRing","glowAxis",
   "glowRingSize","glowAxisLength","glowAxisWidth");
 public static final double OFFSET_MIN=-64,OFFSET_MAX=64,SIZE_MIN=0.05,SIZE_MAX=32;
 public static final PointLightAppearance DEFAULT=new PointLightAppearance(0,0,0,false,true,true,1,1,1);

 public PointLightAppearance{
  x=legalize(x,OFFSET_MIN,OFFSET_MAX);y=legalize(y,OFFSET_MIN,OFFSET_MAX);z=legalize(z,OFFSET_MIN,OFFSET_MAX);
  ringSize=legalize(ringSize,SIZE_MIN,SIZE_MAX);axisLength=legalize(axisLength,SIZE_MIN,SIZE_MAX);
  axisWidth=legalize(axisWidth,SIZE_MIN,SIZE_MAX);
 }

 static double legalize(double v,double min,double max){
  if(!Double.isFinite(v))return min<0?0:1;
  return Math.max(min,Math.min(max,v));
 }

 public static boolean owns(String key){return key!=null&&FIELDS.contains(key);}

 public double number(String key){
  return switch(key){
   case "offsetX"->x;case "offsetY"->y;case "offsetZ"->z;
   case "glow"->glow?1:0;case "glowRing"->ring?1:0;case "glowAxis"->axis?1:0;
   case "glowRingSize"->ringSize;case "glowAxisLength"->axisLength;case "glowAxisWidth"->axisWidth;
   default->throw new IllegalArgumentException("unknown point light field: "+key);
  };
 }

 public PointLightAppearance with(String key,double v){
  if(!owns(key))throw new IllegalArgumentException("unknown point light field: "+key);
  return switch(key){
   case "offsetX"->new PointLightAppearance(offset(v),y,z,glow,ring,axis,ringSize,axisLength,axisWidth);
   case "offsetY"->new PointLightAppearance(x,offset(v),z,glow,ring,axis,ringSize,axisLength,axisWidth);
   case "offsetZ"->new PointLightAppearance(x,y,offset(v),glow,ring,axis,ringSize,axisLength,axisWidth);
   case "glow"->new PointLightAppearance(x,y,z,flag(v),ring,axis,ringSize,axisLength,axisWidth);
   case "glowRing"->new PointLightAppearance(x,y,z,glow,flag(v),axis,ringSize,axisLength,axisWidth);
   case "glowAxis"->new PointLightAppearance(x,y,z,glow,ring,flag(v),ringSize,axisLength,axisWidth);
   case "glowRingSize"->new PointLightAppearance(x,y,z,glow,ring,axis,size(v),axisLength,axisWidth);
   case "glowAxisLength"->new PointLightAppearance(x,y,z,glow,ring,axis,ringSize,size(v),axisWidth);
   default->new PointLightAppearance(x,y,z,glow,ring,axis,ringSize,axisLength,size(v));
  };
 }

 private static double offset(double v){
  if(!Double.isFinite(v))throw new IllegalArgumentException("offset not finite");
  if(v<OFFSET_MIN||v>OFFSET_MAX)throw new IllegalArgumentException("offset out of range: "+v);
  return v;
 }

 private static double size(double v){
  if(!Double.isFinite(v))throw new IllegalArgumentException("size not finite");
  if(v<SIZE_MIN||v>SIZE_MAX)throw new IllegalArgumentException("size out of range: "+v);
  return v;
 }

 private static boolean flag(double v){
  if(v==0)return false;
  if(v==1)return true;
  throw new IllegalArgumentException("flag must be 0 or 1: "+v);
 }

 public boolean shifted(){return x!=0||y!=0||z!=0;}

 public CompoundTag save(){
  CompoundTag t=new CompoundTag();
  t.putDouble("offsetX",x);t.putDouble("offsetY",y);t.putDouble("offsetZ",z);
  t.putBoolean("glow",glow);t.putBoolean("glowRing",ring);t.putBoolean("glowAxis",axis);
  t.putDouble("glowRingSize",ringSize);t.putDouble("glowAxisLength",axisLength);t.putDouble("glowAxisWidth",axisWidth);
  return t;
 }

 public static PointLightAppearance load(CompoundTag t){
  if(t==null)return DEFAULT;
  return new PointLightAppearance(
    num(t,"offsetX",DEFAULT.x,OFFSET_MIN,OFFSET_MAX),num(t,"offsetY",DEFAULT.y,OFFSET_MIN,OFFSET_MAX),
    num(t,"offsetZ",DEFAULT.z,OFFSET_MIN,OFFSET_MAX),
    bool(t,"glow",DEFAULT.glow),bool(t,"glowRing",DEFAULT.ring),bool(t,"glowAxis",DEFAULT.axis),
    num(t,"glowRingSize",DEFAULT.ringSize,SIZE_MIN,SIZE_MAX),
    num(t,"glowAxisLength",DEFAULT.axisLength,SIZE_MIN,SIZE_MAX),
    num(t,"glowAxisWidth",DEFAULT.axisWidth,SIZE_MIN,SIZE_MAX));
 }

 private static double num(CompoundTag t,String key,double fallback,double min,double max){
  if(!t.contains(key,net.minecraft.nbt.Tag.TAG_ANY_NUMERIC))return fallback;
  try{
   double v=t.getDouble(key);
   return Double.isFinite(v)&&v>=min&&v<=max?v:fallback;
  }catch(RuntimeException e){return fallback;}
 }

 private static boolean bool(CompoundTag t,String key,boolean fallback){
  if(!t.contains(key,net.minecraft.nbt.Tag.TAG_BYTE))return fallback;
  try{byte v=t.getByte(key);return v==0?false:v==1?true:fallback;}catch(RuntimeException e){return fallback;}
 }

 public void write(FriendlyByteBuf b){
  b.writeFloat((float)x);b.writeFloat((float)y);b.writeFloat((float)z);
  b.writeByte((glow?1:0)|(ring?2:0)|(axis?4:0));
  b.writeFloat((float)ringSize);b.writeFloat((float)axisLength);b.writeFloat((float)axisWidth);
 }

 public static PointLightAppearance read(FriendlyByteBuf b){
  float x=b.readFloat(),y=b.readFloat(),z=b.readFloat();int bits=b.readUnsignedByte();
  float rs=b.readFloat(),al=b.readFloat(),aw=b.readFloat();
  return new PointLightAppearance(clampOr(x,0),clampOr(y,0),clampOr(z,0),
    (bits&1)!=0,(bits&2)!=0,(bits&4)!=0,
    clampOr(rs,DEFAULT.ringSize),clampOr(al,DEFAULT.axisLength),clampOr(aw,DEFAULT.axisWidth));
 }

 private static double clampOr(double v,double fallback){
  return Double.isNaN(v)||Double.isInfinite(v)?fallback:v;
 }
}
