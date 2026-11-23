package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.jar.*;
class ShellExplosionTrailHookTest {
 @Test void cbcCloudEmitterStillHasSeparatePrimaryAndSecondaryLoops()throws Exception{
  try(var jar=new JarFile("libs/cbc.jar")){
   var c=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry("rbasamoyai/createbigcannons/effects/particles/explosions/ShellExplosionCloudParticle.class"))).accept(c,0);
   assertTrue(c.fields.stream().anyMatch(f->f.name.equals("power")&&f.desc.equals("F")));
   assertTrue(c.fields.stream().anyMatch(f->f.name.equals("isPlume")&&f.desc.equals("Z")));
   int iterators=0,spawns=0;
   for(var m:c.methods)if(m.name.equals("tick"))for(var i:m.instructions)if(i instanceof MethodInsnNode call){
    if(call.owner.equals("java/util/List")&&call.name.equals("iterator"))iterators++;
    if(call.name.equals("addParticle"))spawns++;
   }
   assertEquals(1,iterators,"one secondary trail loop, separate from main plume loop");
   assertEquals(2,spawns,"primary and secondary smoke spawning are separate callsites");
  }
 }
 @Test void cbcBlastWaveRemainsOutsideTheReplaceableCloudEmitter() throws Exception {
  try(var jar=new JarFile("libs/cbc.jar")){
   var c=new ClassNode();
   var shell=jar.getJarEntry("rbasamoyai/createbigcannons/munitions/ShellExplosion.class");
   new ClassReader(jar.getInputStream(shell)).accept(c,0);
   var spawn=c.methods.stream().filter(m->m.name.equals("spawnParticles")).findFirst().orElseThrow();
   var effects=new java.util.ArrayList<String>();
   int particleCalls=0;
   for(var instruction:spawn.instructions){
    if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/level/Level")
            && call.name.equals("addParticle") && call.desc.equals("(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V")) particleCalls++;
    if(instruction instanceof TypeInsnNode allocation && allocation.getOpcode()==Opcodes.NEW){
     effects.add(allocation.desc.substring(allocation.desc.lastIndexOf('/')+1));
    }
   }
   assertTrue(effects.contains("ShellBlastWaveEffectParticleData"));
   assertEquals(1, java.util.Collections.frequency(effects,"ShellExplosionCloudParticleData"));
   assertEquals(2, particleCalls, "local shockwave hook sees the blast emitter and the single cloud");
  }
 }
}
