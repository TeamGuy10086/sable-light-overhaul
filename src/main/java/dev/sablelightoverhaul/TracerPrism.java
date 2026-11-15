package dev.sablelightoverhaul;
import net.minecraft.world.phys.Vec3;
/** Closed, outward-wound CBC-style tracer prism. All six directions are visible with back-face culling. */
public final class TracerPrism {
 private TracerPrism(){}
 @FunctionalInterface public interface Sink{void vertex(Vec3 p);}
 private static final int[] FACES={2,3,1,0,4,5,7,6,0,4,6,2,1,3,7,5,0,1,5,4,2,6,7,3};
 public static void emit(Vec3 a,Vec3 b,double halfWidth,Sink sink){
  Vec3 axis=b.subtract(a).normalize();if(axis.lengthSqr()<.5||halfWidth<=0)return;
  Vec3 u=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(halfWidth);
  Vec3 v=axis.cross(u);
  Vec3[] points=new Vec3[8];
  for(int i=0;i<8;i++)points[i]=((i&4)==0?a:b).add(u.scale((i&1)==0?-1:1)).add(v.scale((i&2)==0?-1:1));
  for(int i:FACES)sink.vertex(points[i]);
 }
}
