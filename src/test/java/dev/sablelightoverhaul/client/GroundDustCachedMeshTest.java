package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GasVertexData;
import dev.sablelightoverhaul.GroundDustFieldModel;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.client.Camera;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundDustCachedMeshTest {
 @Test void cachedCloudKeepsNegativeMaterialIdThroughWorldCorridorClipping(){
  var settings=new GroundDustFieldModel.Settings(6,1,4,.1,1,0,.5,.8,1,1,1,2,.6,1,1);
  var patch=new GroundDustFieldModel.Patch(new Point(1.2,0,3),new Point(0,1,0),0xBEA98C);
  var model=new GroundDustFieldModel(new Point(0,0,0),new Point(0,0,1),List.of(patch),settings,7);
  var node=model.nodes().get(0);var camera=new Camera();
  for(int seed:new int[]{0,31,63}){
   var positions=new ArrayList<float[]>();var uvs=new ArrayList<float[]>();var alphas=new ArrayList<Float>();
   VertexConsumer target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
    switch(method.getName()){
     case "addVertex" -> positions.add(new float[]{(float)args[0],(float)args[1],(float)args[2]});
     case "setUv" -> uvs.add(new float[]{(float)args[0],(float)args[1]});
     case "setColor" -> alphas.add((float)args[3]);
    }return proxy;
   });
   var out=new CorridorVertexConsumer().bind(target,camera.getPosition(),node);
   MuzzleSmokeMesh.drawCachedPuff(out,camera,1.2,0,3,1.2,0,3,1,2,.6f,.4f,seed,.6f,.5f,.3f,.7f,GasVertexData.smokeLight(0xF000F0));
   assertFalse(positions.isEmpty());assertEquals(positions.size(),uvs.size());assertEquals(positions.size(),alphas.size());
   boolean feather=false;
   for(int n=0;n<positions.size();n++){
    var p=positions.get(n);
    assertEquals(-64-seed,(int)Math.floor((uvs.get(n)[0]+1.5)/4),"clipping must not turn cached smoke into hot material");
    if(Math.hypot(p[0],p[1])<.75)assertEquals(0,alphas.get(n),1e-6);
    assertTrue(alphas.get(n)>=0 && alphas.get(n)<=.7f);
    if(alphas.get(n)<.69)feather=true;
   }
   assertTrue(feather,"test must cross the corridor's feather");
  }
 }
}
