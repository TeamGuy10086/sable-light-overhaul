package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
class MissileSmokeRenderContractTest {
    @Test void meshCreatesNoParticlesAndNeverEnablesDepthWrites() throws Exception {
        var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/client/MissileSmokeMesh.class"))).accept(n,0);
        boolean draw=false,depth=false;
        for(var m:n.methods)for(var i:m.instructions)if(i instanceof MethodInsnNode c){
            assertFalse(c.owner.equals("net/minecraft/client/particle/ParticleEngine"),"No particle engine queue operations");
            assertFalse(c.name.equals("addParticle"));
            if(c.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&c.name.equals("depthMask")){
                var previous=i.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
                assertNotNull(previous);assertEquals(Opcodes.ICONST_0,previous.getOpcode());depth=true;
            }
            if(c.owner.equals("com/mojang/blaze3d/vertex/BufferUploader")&&c.name.equals("drawWithShader"))draw=true;
        }
        assertTrue(draw);assertTrue(depth);
    }
    @Test void incomingIdsCannotEvictExistingTracks() throws Exception {
        var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/client/MissileSmokeMesh.class"))).accept(n,0);
        var sample=n.methods.stream().filter(m->m.name.equals("sample")).findFirst().orElseThrow();
        boolean checksCapacity=false;
        for(var i:sample.instructions){
            if(i instanceof IntInsnNode constant&&constant.operand==128)checksCapacity=true;
            if(i instanceof MethodInsnNode call)assertFalse(call.name.equals("remove")||call.name.equals("clear"),
                "New IDs must not churn the retained trail map at capacity");
        }
        assertTrue(checksCapacity,"Admission remains bounded");
    }
    @Test void sharedParticlePassHasExactlyOneCleanupInjectionPoint() throws Exception {
        try(var jar=new JarFile("build/moddev/artifacts/neoforge-21.1.228-merged.jar")){
            var n=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry("net/minecraft/client/particle/ParticleEngine.class"))).accept(n,0);
            var m=n.methods.stream().filter(x->x.name.equals("render")&&x.desc.equals("(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V")).findFirst().orElseThrow();
            int count=0;for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&c.name.equals("depthMask")&&c.desc.equals("(Z)V"))count++;
            assertEquals(1,count);
        }
    }
}
