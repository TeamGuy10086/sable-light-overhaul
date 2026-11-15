package dev.sablelightoverhaul;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class CbcatTargetTest {
    private ClassNode target() throws Exception {
        Path jar=Path.of("libs/cbcat.jar");
        assumeTrue(Files.exists(jar),"Optional CBCAT binary check requires libs/cbcat.jar");
        assertEquals("c7a08eea5ef2d69eb8052b9da2a5628f10a78cb786896352891d203f36a890e7",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        try(var zip=new JarFile(jar.toFile()); var in=zip.getInputStream(zip.getJarEntry(
                "com/dsvv/cbcat/cannon/twin_autocannon/contraption/MountedTwinAutocannonContraption.class"))) {
            var node=new ClassNode(); new ClassReader(in).accept(node,0); return node;
        }
    }
    private MethodNode fire(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals("fireShot") && m.desc.equals(
                "(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V")).findFirst().orElseThrow();
    }
    @Test void oneHookOutsidePlayerLoopWithBothSelectedMuzzleLocalsAlive() throws Exception {
        var node=target(); var m=fire(node); int count=0, hook=-1;
        assertEquals("rbasamoyai/createbigcannons/cannon_control/contraption/AbstractMountedCannonContraption",node.superName);
        for(int i=0;i<m.instructions.size();i++) if(m.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/server/level/ServerLevel") && call.name.equals("players") && call.desc.equals("()Ljava/util/List;")) {count++; hook=i;}
        assertEquals(1,count); final int location=hook;
        for(String local:new String[]{"particlePos","vec1"}) {
            assertEquals(1,m.localVariables.stream().filter(v -> v.name.equals(local) && v.desc.equals("Lnet/minecraft/world/phys/Vec3;")
                    && m.instructions.indexOf(v.start)<=location && m.instructions.indexOf(v.end)>location).count(),local);
        }
    }
    @Test void nativeAlternationAndOffsetPrecedeHookAndNoBaseAutoShotIsCalled() throws Exception {
        var m=fire(target()); int flip=-1, hook=-1; boolean minus=false,plus=false;
        for(int i=0;i<m.instructions.size();i++) {
            var insn=m.instructions.get(i);
            if(insn instanceof FieldInsnNode f && f.name.equals("firstFire") && f.getOpcode()==Opcodes.PUTFIELD) flip=i;
            if(insn instanceof LdcInsnNode c && c.cst instanceof Float v) {minus|=v==-0.25f; plus|=v==0.25f;}
            if(insn instanceof MethodInsnNode c) {
                if(c.name.equals("players")) hook=i;
                assertFalse(c.owner.endsWith("/MountedAutocannonContraption") && c.name.equals("fireShot"),"Do not double-fire existing integration");
                assertFalse(c.owner.endsWith("/CBCCompatTransformers") && c.name.equals("transformVec3"),"Coordinates must already be native world positions");
            }
        }
        assertTrue(minus && plus); assertTrue(flip>=0 && hook>flip,"The boolean is already flipped; use captured particlePos instead");
    }
    @Test void integrationHasNoDirectCbcatClassLinkageAndIsOptional() throws Exception {
        var n=new ClassNode(); new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/mixin/CbcatTwinMixin.class"))).accept(n,0);
        for(var m:n.methods) for(var i:m.instructions) {
            if(i instanceof MethodInsnNode call) assertFalse(call.owner.startsWith("com/dsvv/cbcat/"));
            if(i instanceof FieldInsnNode field) assertFalse(field.owner.startsWith("com/dsvv/cbcat/"));
        }
        assertTrue(Files.readString(Path.of("src/main/resources/sablelightoverhaul.cbcat.mixins.json")).contains("CbcatMixinPlugin"));
    }
}
