package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.nio.file.*;
class TracerPrismTest {
 @Test void allSixFacesPointOutwardAcrossAxisOrientations() throws Exception {
  for(Vec3 b:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,-1),new Vec3(1,2,3))){
   Vec3 a=b.scale(-1),centre=a.lerp(b,.5);var vertices=new ArrayList<Vec3>();TracerPrism.emit(a,b,.2,vertices::add);
   assertEquals(24,vertices.size());
   for(int i=0;i<24;i+=4){Vec3 v0=vertices.get(i),v1=vertices.get(i+1),v2=vertices.get(i+2),v3=vertices.get(i+3);
    Vec3 normal=v1.subtract(v0).cross(v2.subtract(v0));
    assertTrue(normal.dot(v0.add(v1).add(v2).add(v3).scale(.25).subtract(centre))>0,"back-facing prism face "+i);
    assertEquals(0,normal.dot(v3.subtract(v0)),1e-8);
   }
  }
 }
 @Test void exportActualProductionVerticesForCullEnabledGpuProbe() throws Exception {
  var vertices=new ArrayList<Vec3>();TracerPrism.emit(new Vec3(-.7,0,0),new Vec3(.7,0,0),.18,vertices::add);
  Path out=Path.of("work/tracers-569/prism-vertices.txt");Files.createDirectories(out.getParent());
  var lines=new ArrayList<String>();for(var v:vertices)lines.add(v.x+" "+v.y+" "+v.z);Files.write(out,lines);
  assertEquals(24,vertices.size());
 }
 @Test void zeroLengthHasNoDegenerateFaces(){var v=new ArrayList<Vec3>();TracerPrism.emit(Vec3.ZERO,Vec3.ZERO,1,v::add);assertTrue(v.isEmpty());}
}
