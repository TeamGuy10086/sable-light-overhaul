package dev.sablelightoverhaul.client;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullClusterMeshTest {
    @Test void allThreeLobesFitTheSupportDiskIncludingTheirOffsets(){
        var camera=new Camera();double radius=.4;
        for(int angle=0;angle<360;angle+=15){
            var vertices=new ArrayList<Vec3>();
            VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
                if(method.getName().equals("addVertex"))vertices.add(new Vec3((float)args[0],(float)args[1],(float)args[2]));return proxy;
            });
            MuzzleSmokeMesh.drawHullCluster(out,camera,0,0,0,0,0,0,1,(float)radius,(float)radius,(float)Math.toRadians(angle),3,1,1,1,.5f,GasVertexData.smokeLight(0xF000F0),true,0,1,0,1);
            assertEquals(12,vertices.size());
            for(var p:vertices)assertTrue(Math.hypot(p.x,p.y)<=radius+1e-6,"outer lobe must fit too");
        }
    }
    @Test void actualThreeLobesRemainVisibleBeforeReleaseAndExportExactMesh()throws Exception{
        for(int angle:new int[]{0,85}){
            var camera=new Camera();camera.rotation().rotationX((float)Math.toRadians(angle));
            double a=Math.toRadians(angle);var normal=new Vec3(0,0,1);
            var shape=HullDustProjection.project(normal,new Vec3(1,0,0),new Vec3(0,Math.cos(a),Math.sin(a)),1,.3,.64,0);
            var positions=new ArrayList<Vec3>();var uv=new ArrayList<float[]>();var alpha=new ArrayList<Float>();
            VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
                switch(method.getName()){
                    case "addVertex"->positions.add(new Vec3((float)args[0],(float)args[1],(float)args[2]));
                    case "setUv"->uv.add(new float[]{(float)args[0],(float)args[1]});
                    case "setColor"->alpha.add((float)args[3]);
                }return proxy;
            });
            double z=3+shape.lift();MuzzleSmokeMesh.drawHullCluster(out,camera,1,2,z,1,2,z,1,shape.halfWidth(),shape.halfHeight(),shape.roll(),3,.7f,.6f,.5f,.4f,GasVertexData.smokeLight(0xF000F0),true,0,1,0,1);
            assertEquals(12,positions.size());assertEquals(12,alpha.size());assertTrue(alpha.stream().allMatch(v->v>.1f));
            var facing=new Vec3(0,-Math.sin(a),Math.cos(a));
            for(int i=0;i<12;i+=4)assertTrue(positions.get(i+1).subtract(positions.get(i)).cross(positions.get(i+2).subtract(positions.get(i))).dot(facing)>0);
            var lines=new ArrayList<String>();for(int i=0;i<12;i++){var p=positions.get(i);var t=uv.get(i);lines.add(p.x+"\t"+p.y+"\t"+p.z+"\t"+alpha.get(i)+"\t"+t[0]+"\t"+t[1]);}
            Files.createDirectories(Path.of("build/reports"));Files.write(Path.of("build/reports/hull-cluster-546-"+angle+".tsv"),lines);
        }
    }
    @Test void diagnosticCaptureForwardsVerticesAndAttributesWithoutMutation(){
        var calls=new ArrayList<String>();
        VertexConsumer target=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{calls.add(method.getName()+Arrays.toString(args));return proxy;});
        var capture=new HullDustDebug.Capture(target,new Vec3(100,20,-30));
        capture.addVertex(1,2,3).setColor(20,30,40,50).setUv(.2f,.4f).setLight(0xF000F0).setNormal(0,1,0);
        assertEquals(List.of(new Vec3(101,22,-27)),capture.vertices);
        assertEquals(List.of("addVertex[1.0, 2.0, 3.0]","setColor[20, 30, 40, 50]","setUv[0.2, 0.4]","setUv2[240, 240]","setNormal[0.0, 1.0, 0.0]"),calls);
    }
}
