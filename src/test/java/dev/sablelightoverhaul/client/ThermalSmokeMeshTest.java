package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.BigSmokeModel;
import dev.sablelightoverhaul.GasVertexData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalSmokeMeshTest {
 @Test void thermalClusterIsOneQuadCarryingItsOwnSeedAndRecoversTheFrame(){
  for(int frame:new int[]{0,1,37,255})for(int seed:new int[]{0,47,63}){
   int encoded=BigSmokeModel.encodedSeed(seed,frame/32.0),negative=-encoded;
   var uvs=new ArrayList<float[]>();var positions=new ArrayList<Vec3>();var alphas=new ArrayList<Float>();var reds=new ArrayList<Float>();
   VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
    if(method.getName().equals("setUv"))uvs.add(new float[]{(float)args[0],(float)args[1]});
    if(method.getName().equals("setColor")){reds.add((float)args[0]);alphas.add((float)args[3]);}
    if(method.getName().equals("addVertex"))positions.add(new Vec3((float)args[0],(float)args[1],(float)args[2]));return proxy;
   });
   MuzzleSmokeMesh.drawThermalCluster(out,new Camera(),0,0,0,0,0,0,0,2,3,.2f,encoded,.6f,.48f,.25f,GasVertexData.smokeLight(0xF000F0));
   assertEquals(4,uvs.size(),"a cluster is exactly one quad");assertEquals(4,positions.size());assertEquals(4,alphas.size());
   for(float red:reds)assertEquals(.48f,red,1e-6f,"red carries the steady opacity for the coverage cut");
   int recovered=Math.round(-uvs.get(0)[0]/4);
   assertEquals(encoded,recovered,"negative encoding round-trips exactly");
   assertEquals(frame,(recovered-128)/64,"shared thermal frame survives the negative encode");
   assertEquals(seed,(recovered-128)&63,"lobe identity survives the negative encode");
   for(float[] uv:uvs){assertEquals(negative,(int)Math.floor((uv[0]+1.5)/4.0));assertEquals(.8f,Math.abs(uv[0]-negative*4.0f),.004f);assertEquals(.8f,Math.abs(uv[1]),1e-6f);}
   double area=positions.get(1).subtract(positions.get(0)).cross(positions.get(2).subtract(positions.get(0))).z;
   assertTrue(area>0,"front winding is counter-clockwise facing the camera");
   for(float alpha:alphas)assertEquals(.6f,alpha,1e-6f,"one material means one unfaded alpha");
  }
 }
 @Test void eachLobeKeepsItsOwnSeedAndSharedThermalAgeAcrossAllVertices(){
  for(int frame:new int[]{0,1,32,255})for(int seed:new int[]{0,47,63}){
   var uv=new ArrayList<float[]>();var positions=new ArrayList<Vec3>();var colors=new ArrayList<float[]>();
   VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
    if(method.getName().equals("setUv"))uv.add(new float[]{(float)args[0],(float)args[1]});
    if(method.getName().equals("setColor"))colors.add(new float[]{(float)args[0],(float)args[3]});
    if(method.getName().equals("addVertex"))positions.add(new Vec3((float)args[0],(float)args[1],(float)args[2]));return proxy;
   });
   MuzzleSmokeMesh.drawThermalPuff(out,new Camera(),0,0,0,0,0,0,0,2,3,.2f,BigSmokeModel.encodedSeed(seed,frame/32.0),.7f,.48f,.25f,GasVertexData.smokeLight(0xF000F0));
   assertEquals(12,uv.size());assertEquals(12,positions.size());
   for(int i=0;i<12;i+=4){
    float weight=i==0?.58f:i==4?.44f:.36f;
    for(int j=0;j<4;j++){
     assertEquals(.48f*weight,colors.get(i+j)[0],1e-5f,"red = steady opacity * lobe weight, so the shader recovers the lifecycle factor");
     assertEquals(.7f*weight,colors.get(i+j)[1],1e-5f,"alpha keeps the existing per-lobe weighting");
    }
   }
   for(int i=0;i<12;i++){
    int decoded=(int)Math.floor((uv.get(i)[0]+1.5)/4);
    assertEquals(frame,(decoded-128)/64,"age must not carry when the lobe seed wraps");
    assertEquals((seed+i/4*17)&63,(decoded-128)&63);
    assertEquals(1,Math.abs(uv.get(i)[1]));
    double local=uv.get(i)[0]-decoded*4;assertEquals(1,Math.abs(local));
   }
   for(int i=0;i<12;i+=4)assertTrue(positions.get(i+1).subtract(positions.get(i)).cross(positions.get(i+2).subtract(positions.get(i))).z>0,"front winding");
  }
 }
 @Test void configIsRegisteredInItsOwnGroupWithoutChangingFollowingKeys(){
  var source=new dev.sablelightoverhaul.client.ui.TomlSource(MuzzleClientConfig.SPEC,java.util.Set.of());
  var group=source.groups().stream().filter(g->g.topic().equals("大炮热烟团")).findFirst().orElseThrow();
  assertEquals("大炮",group.section());assertEquals(30,group.fields().size());
  assertTrue(group.fields().stream().anyMatch(field->field.key().equals("particles.muzzle.bigCannon.thermalSmoke.densityDetail")));
  assertTrue(source.read().containsKey("particles.muzzle.tankCannon.afterSmoke.exitSpeed"));
  assertTrue(BigSmokeConfig.SCALE.getSpec().test(16.0));assertTrue(BigSmokeConfig.COUNT.getSpec().test(256));
  assertFalse(BigSmokeConfig.COUNT.getSpec().test(257));assertTrue(BigSmokeConfig.LIMIT.getSpec().test(4096));
  assertEquals(true,(Boolean)BigSmokeConfig.SINGLE_PASS.getDefault());
  assertEquals(4.0,(Double)BigSmokeConfig.CHARGE_REFERENCE.getDefault(),1e-9);
  assertEquals(.5,(Double)BigSmokeConfig.CHARGE_EXPONENT.getDefault(),1e-9);
  assertEquals(6.0,(Double)BigSmokeConfig.CHARGE_MAX.getDefault(),1e-9,"chargeMaxScale replaces the old 1.8 ceiling");
  assertFalse(BigSmokeConfig.CHARGE_EXPONENT.getSpec().test(-.1));assertTrue(BigSmokeConfig.CHARGE_EXPONENT.getSpec().test(0.0));
  assertTrue(BigSmokeConfig.EMISSION_GAIN.getSpec().test(128.0));
  assertTrue(BigSmokeConfig.ROOT_HOT_SCALE.getSpec().test(.05));
  assertTrue(BigSmokeConfig.TIP_HOT_SCALE.getSpec().test(8.0));
  assertFalse((Boolean)ExtraConfig.FLARE_SHADOWS.getDefault());
 }

 @Test void cachedTankRangeIsOneQuadAndCannotBecomeThermal(){
  for(int seed=0;seed<64;seed++){
   var uvs=new ArrayList<float[]>();var colors=new ArrayList<float[]>();
   VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
    if(method.getName().equals("setUv"))uvs.add(new float[]{(float)args[0],(float)args[1]});
    if(method.getName().equals("setColor"))colors.add(new float[]{(float)args[0],(float)args[3]});return proxy;
   });
   MuzzleSmokeMesh.drawCachedPuff(out,new Camera(),0,0,0,0,0,0,0,2,3,.5f,seed,.46f,.46f,.46f,.7f,GasVertexData.smokeLight(0xF000F0));
   assertEquals(4,uvs.size());
   for(float[] uv:uvs){int id=(int)Math.floor((uv[0]+1.5)/4);assertEquals(-64-seed,id);assertTrue(Math.abs(id)<128);}
   for(float[] c:colors){assertEquals(.46f,c[0]);assertEquals(.7f,c[1]);}
  }
 }
}
