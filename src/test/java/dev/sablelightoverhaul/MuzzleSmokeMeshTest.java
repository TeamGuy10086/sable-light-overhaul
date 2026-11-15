package dev.sablelightoverhaul;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.client.MuzzleSmokeMesh;
import net.minecraft.client.Camera;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuzzleSmokeMeshTest {
    @Test void explicitQuadPreservesInterpolationAtlasCoolingAndMaterialBits() {
        var positions=new ArrayList<float[]>();
        var uv=new ArrayList<float[]>();
        var colors=new ArrayList<float[]>();
        var lights=new ArrayList<Integer>();
        VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
            new Class<?>[]{VertexConsumer.class},(proxy,method,args)-> {
                if(method.getName().equals("addVertex")) positions.add(new float[]{(float)args[0],(float)args[1],(float)args[2]});
                if(method.getName().equals("setUv")) uv.add(new float[]{(float)args[0],(float)args[1]});
                if(method.getName().equals("setColor")) colors.add(new float[]{(float)args[0],(float)args[1],(float)args[2],(float)args[3]});
                if(method.getName().equals("setLight")) lights.add((int)args[0]);
                return proxy;
            });
        int light=GasVertexData.materialLight(0x00F000A0,false);
        MuzzleSmokeMesh.draw(out,new Camera(),2,4,6,0,0,0,.5f,2,0,.2f,.4f,.6f,.8f,.3f,.4f,.5f,.7f,light);
        assertEquals(4,positions.size());
        float[] a=positions.get(0),b=positions.get(1),c=positions.get(2);
        float faceZ=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
        assertTrue(faceZ>0,"Billboard must face the camera under GL_CCW backface culling");
        assertArrayEquals(new float[]{3,0,3},positions.get(0),1e-6f);
        assertArrayEquals(new float[]{3,4,3},positions.get(1),1e-6f);
        assertArrayEquals(new float[]{-1,4,3},positions.get(2),1e-6f);
        assertArrayEquals(new float[]{-1,0,3},positions.get(3),1e-6f);
        assertArrayEquals(new float[]{.4f,.8f},uv.get(0));
        assertArrayEquals(new float[]{.4f,.6f},uv.get(1));
        assertArrayEquals(new float[]{.2f,.6f},uv.get(2));
        assertArrayEquals(new float[]{.2f,.8f},uv.get(3));
        assertEquals(4,colors.size());assertEquals(4,lights.size());
        for(int i=0;i<4;i++) {
            assertArrayEquals(new float[]{.3f,.4f,.5f,.7f},colors.get(i));
            assertEquals(light,lights.get(i));
        }
    }

    @Test void softPuffUsesThreeDistinctOffsetQuads() {
        var positions=new ArrayList<float[]>();
        var uv=new ArrayList<float[]>();
        VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
            new Class<?>[]{VertexConsumer.class},(proxy,method,args)-> {
                if(method.getName().equals("addVertex")) positions.add(new float[]{(float)args[0],(float)args[1],(float)args[2]});
                if(method.getName().equals("setUv")) uv.add(new float[]{(float)args[0],(float)args[1]});
                return proxy;
            });
        MuzzleSmokeMesh.drawPuff(out,new Camera(),2,4,6,0,0,0,.5f,2,0,3,.8f,.8f,.8f,.6f,0xF000F0);
        assertEquals(12,positions.size(),"three quads, not one disc");
        assertEquals(12,uv.size());
        assertEquals(3,java.util.Set.of(uv.get(0)[0],uv.get(4)[0],uv.get(8)[0]).size());
        float[] a=positions.get(0),b=positions.get(4),c=positions.get(8);
        assertTrue(Math.abs(a[0]-b[0])+Math.abs(a[1]-b[1])+Math.abs(a[2]-b[2])>1.0e-3,
            "first lobe must not be stamped on top of the main quad");
        assertTrue(Math.abs(a[0]-c[0])+Math.abs(a[1]-c[1])+Math.abs(a[2]-c[2])>1.0e-3,
            "second lobe must not be stamped on top of the main quad");
    }

    @Test void tankFallbackDeclaresAnOverrideBeforeInheritedFastPath()throws Exception {
        var n=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
            "build/classes/java/main/dev/sablelightoverhaul/mixin/MuzzleFallbackSmokeMixin.class"))).accept(n,0);
        var render=n.methods.stream().filter(m->m.name.equals("render")).findFirst().orElseThrow();
        var calls=java.util.Arrays.stream(render.instructions.toArray())
            .filter(i->i instanceof org.objectweb.asm.tree.MethodInsnNode)
            .map(i->(org.objectweb.asm.tree.MethodInsnNode)i).toList();
        assertTrue(calls.stream().anyMatch(c->c.name.equals("slo$tankControlled")));
        assertEquals(1,calls.stream().filter(c->c.owner.endsWith("/MuzzleSmokeMesh")&&c.name.equals("draw")).count());
        assertEquals(1,calls.stream().filter(c->c.name.equals("render")&&c.getOpcode()==org.objectweb.asm.Opcodes.INVOKESPECIAL).count(),
            "Uncontrolled CBC smoke must retain its inherited render path");
    }
}
