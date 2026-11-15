package dev.sablelightoverhaul;
import java.nio.file.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
class Tracer568ContractTest {
    private ClassNode cbc(String name)throws Exception{
        try(var zip=new ZipFile("libs/cbc.jar")){var n=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(name+".class"))).accept(n,0);return n;}
    }
    @Test void flakReplacementHooksOnlyDiscardedBurstReturn()throws Exception{
        var node=cbc("rbasamoyai/createbigcannons/munitions/autocannon/flak/FlakAutocannonProjectile");
        int bursts=0,explosions=0;
        for(var m:node.methods)if(m.name.equals("detonate"))for(var i:m.instructions)if(i instanceof MethodInsnNode c){
            if(c.name.equals("handleCustomExplosion"))explosions++;
            if(c.name.equals("spawnConeBurst")){
                bursts++;assertEquals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;ID)Lrbasamoyai/ritchiesprojectilelib/projectile_burst/ProjectileBurst;",c.desc);
                var next=i.getNext();while(next.getOpcode()<0)next=next.getNext();assertEquals(Opcodes.POP,next.getOpcode());
            }
        }
        assertEquals(1,bursts);assertEquals(1,explosions);
        var config=Files.readString(Path.of("src/main/resources/sablelightoverhaul.cbc.mixins.json"));assertTrue(config.contains("FlakConeMixin"));
    }
    @Test void bigCannonSmokeHookHasExactlyOneCallSite()throws Exception{
        int sites=0;for(var m:cbc("rbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile").methods)
            if(m.name.equals("tick"))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.name.equals("addAlwaysVisibleParticle")&&c.desc.equals("(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))sites++;
        assertEquals(1,sites);
    }
    @Test void discardedSmokeParticleFactoryRemainsAvailableForMissiles()throws Exception{
        String route=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/TrailTrack.java"));
        int a=route.indexOf("private static void tracerTrail"),b=route.indexOf("public static void receive",a);
        assertTrue(route.substring(a,b).contains("ProjectileStreaks.accept"));assertFalse(route.substring(a,b).contains("missileTrail("));
        assertTrue(route.contains("public static void missileTrail("));
        assertTrue(Files.exists(Path.of("src/main/resources/assets/sablelightoverhaul/particles/trail.json")));
    }
}
