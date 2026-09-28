package dev.cbcmuzzlelight;

import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedTargetTest {
    private ClassNode read(String jar,String path) throws Exception {
        try(var zip=new JarFile(jar)) {var n=new ClassNode();new ClassReader(zip.getInputStream(zip.getJarEntry(path+".class"))).accept(n,0);return n;}
    }
    @Test void lambHasExactShortPulseHookAndApi() throws Exception {
        var n=read("libs/lamb-runtime.jar","dev/lambdaurora/lambdynlights/LambDynLights");
        assertTrue(n.methods.stream().anyMatch(m -> m.name.equals("shouldTick") && m.desc.equals("(Ldev/lambdaurora/lambdynlights/engine/source/DeferredDynamicLightSource;)Z")));
        n=read("libs/lamb-api.jar","dev/lambdaurora/lambdynlights/api/DynamicLightsInitializer");
        assertEquals(2,n.methods.stream().filter(m -> m.name.equals("onInitializeDynamicLights")).count());
    }
    @Test void flywheelFastAndSlowUseInterceptedWriter() throws Exception {
        var n=read("libs/flywheel.jar","dev/engine_room/flywheel/backend/engine/LightDataCollector");
        assertTrue(n.methods.stream().anyMatch(m -> m.name.equals("collectSection") && m.desc.equals("(JJ)V")));
        assertTrue(n.methods.stream().anyMatch(m -> m.name.equals("write") && m.desc.equals("(JIIIII)V") && (m.access&Opcodes.ACC_STATIC)!=0));
        for(String suffix:new String[]{"$Fast","$Slow"}) {
            n=read("libs/flywheel.jar","dev/engine_room/flywheel/backend/engine/LightDataCollector"+suffix);
            boolean writer=false;
            for(var m:n.methods) for(var insn:m.instructions) if(insn instanceof MethodInsnNode c) {
                writer|=c.name.equals("write") && c.desc.equals("(JIIIII)V");
                assertFalse(c.owner.equals("org/lwjgl/system/MemoryUtil") && c.name.startsWith("memPut"),"Light writes must use the scoped writer");
            }
            assertTrue(writer);
        }
    }
    @Test void twinCenterLocalsAreAliveAtHook() throws Exception {
        var n=read("libs/cbcat.jar","com/dsvv/cbcat/cannon/twin_autocannon/contraption/MountedTwinAutocannonContraption");
        var m=n.methods.stream().filter(x -> x.name.equals("fireShot")).findFirst().orElseThrow();
        int hook=-1;
        for(int i=0;i<m.instructions.size();i++) if(m.instructions.get(i) instanceof MethodInsnNode c && c.name.equals("players")) hook=i;
        assertTrue(hook>=0); int at=hook;
        for(String local:new String[]{"offset","factor","entity"}) assertEquals(1,m.localVariables.stream().filter(v -> v.name.equals(local)
                && m.instructions.indexOf(v.start)<=at && m.instructions.indexOf(v.end)>at).count(),local);
    }
    @Test void sizedBigPlumeConstructorAndLocalsMatchUploadedCbc() throws Exception {
        String path="rbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticleData";
        var n=read("libs/cbc.jar","rbasamoyai/createbigcannons/cannon_control/contraption/MountedBigCannonContraption");
        var m=n.methods.stream().filter(x -> x.name.equals("fireShot")).findFirst().orElseThrow();
        int count=0;
        for(int i=0;i<m.instructions.size();i++) if(m.instructions.get(i) instanceof TypeInsnNode c && c.getOpcode()==Opcodes.NEW && c.desc.equals(path)) {
            count++; int at=i;
            for(String name:new String[]{"plumePos","vec","level","entity"}) assertEquals(1,m.localVariables.stream().filter(v -> v.name.equals(name)
                    && m.instructions.indexOf(v.start)<=at && m.instructions.indexOf(v.end)>at).count(),name);
        }
        assertEquals(1,count);
        int calls=0;
        for(var insn:m.instructions) if(insn instanceof MethodInsnNode c && c.owner.equals(path) && c.name.equals("<init>")) {
            assertEquals("(FFI)V",c.desc); calls++;
        }
        assertEquals(1,calls);
        n=read("libs/cbc.jar","rbasamoyai/createbigcannons/effects/particles/smoke/CannonSmokeParticle");
        assertTrue(n.methods.stream().anyMatch(x -> x.name.equals("getQuadSize") && x.desc.equals("(F)F")));
    }
    @Test void tracerQueriesMatchUploadedCbcAndSableAnchorApis() throws Exception {
        var auto=read("libs/cbc.jar","rbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile");
        assertTrue(auto.methods.stream().anyMatch(m->m.name.equals("isTracer")&&m.desc.equals("()Z")));
        var big=read("libs/cbc.jar","rbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile");
        assertTrue(big.methods.stream().anyMatch(m->m.name.equals("hasTracer")&&m.desc.equals("()Z")));
        var container=read("libs/sable.jar","dev/ryanhcode/sable/api/sublevel/SubLevelContainer");
        assertTrue(container.methods.stream().anyMatch(m->m.name.equals("getSubLevel")&&m.desc.equals("(Ljava/util/UUID;)Ldev/ryanhcode/sable/sublevel/SubLevel;")));
        var plot=read("libs/sable.jar","dev/ryanhcode/sable/sublevel/plot/LevelPlot");
        assertTrue(plot.methods.stream().anyMatch(m->m.name.equals("toGlobal")&&m.desc.equals("(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/ChunkPos;")));
    }
    @Test void advancedNeverEnqueuesVanillaLightPropagation() throws Exception {
        for(String cls:new String[]{"AdvancedLights","AdvancedSable","LambAreaBackend"}) {
            var n=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/cbcmuzzlelight/client/"+cls+".class"))).accept(n,0);
            for(var m:n.methods) for(var insn:m.instructions) if(insn instanceof MethodInsnNode c)
                assertFalse(java.util.Set.of("checkBlock","updateSectionStatus","setStoredLevel","setBlock").contains(c.name),cls+" must remain a render-only backend");
        }
    }
}
