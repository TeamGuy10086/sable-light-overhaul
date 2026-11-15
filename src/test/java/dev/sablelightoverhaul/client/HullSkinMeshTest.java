package dev.sablelightoverhaul.client;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.GasVertexData;
import java.lang.reflect.Proxy;
import java.util.*;
import java.nio.file.*;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullSkinMeshTest {
 @Test void skinStaysOnRoofAndSidesWithCorrectWindingAndFeatheredBounds()throws Exception{
  var export=new ArrayList<String>();
  for(float thickness:new float[]{0,.2f,2}){export.clear();
  for(Vec3 normal:List.of(new Vec3(0,1,0),new Vec3(1,0,0),new Vec3(0,0,1))){
   var u=Math.abs(normal.x)>.5?new Vec3(0,0,1):new Vec3(1,0,0);var v=normal.cross(u);
   var skin=new HullSurfaceAnchor.Skin(new Vec3(1,2,3),u,v,.5f,.5f,-.4f,.4f,-.4f,.4f);
   var vertices=new ArrayList<Vec3>();var colors=new ArrayList<float[]>();var coords=new ArrayList<float[]>();var lights=new ArrayList<Integer>();
   VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
    switch(method.getName()){
     case "addVertex"->vertices.add(new Vec3((float)args[0],(float)args[1],(float)args[2]));
     case "setUv"->coords.add(new float[]{(float)args[0],(float)args[1]});
     case "setColor"->colors.add(new float[]{(float)args[0],(float)args[1],(float)args[2],(float)args[3]});
     case "setLight"->lights.add((int)args[0]);
    }return proxy;
   });
   int light=GasVertexData.smokeLight(0xF000F0);
   MuzzleSmokeMesh.drawSurfaceSkin(out,new Camera(),skin,3,.7f,.6f,.5f,.4f,light,true,0,1,0,1,thickness);
   assertEquals(36,vertices.size());boolean visible=false,feather=false;
   for(int i=0;i<36;i++){
    var local=vertices.get(i).subtract(skin.center());assertTrue(local.dot(normal)>=-1e-6&&local.dot(normal)<=Math.min(thickness,.325)+1e-6);
    if(colors.get(i)[3]==0)assertEquals(0,local.dot(normal),1e-6);
    else if(thickness>0)assertTrue(local.dot(normal)>.69*Math.min(thickness,.325));assertTrue(Math.abs(local.dot(u))<=.50001&&Math.abs(local.dot(v))<=.50001);
    assertTrue(coords.get(i)[0]>=11.59&&coords.get(i)[0]<=12.41);assertTrue(Math.abs(coords.get(i)[1])<=.40001);
    assertEquals(light,lights.get(i));visible|=colors.get(i)[3]>.39;feather|=colors.get(i)[3]==0;
   }
   assertTrue(visible&&feather);
   for(int i=0;i<36;i+=4)assertTrue(vertices.get(i+1).subtract(vertices.get(i)).cross(vertices.get(i+2).subtract(vertices.get(i))).dot(normal)>0);
   if(normal.z==1)for(int i=0;i<36;i++){var q=vertices.get(i);var c=colors.get(i);var uv=coords.get(i);export.add(q.x+"\t"+q.y+"\t"+q.z+"\t"+c[3]+"\t"+(uv[0]-12)+"\t"+uv[1]);}
  }
  Files.createDirectories(Path.of("build/reports"));Files.write(Path.of("build/reports/hull-skin-"+(thickness==0?"flat":thickness==.2f?"quads":"capped")+".tsv"),export);
  }
 }
}
