package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.jar.*;
class FlakSmokeHooksTest {
 private ClassNode read(String name)throws Exception{try(var jar=new JarFile("libs/cbc.jar")){var n=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(n,0);return n;}}
 @Test void installedCbcHasExactlyTheSmokeAndFlameCallsitesWeWrap()throws Exception{
  var c=read("rbasamoyai/createbigcannons/effects/particles/explosions/FlakCloudParticle");
  int list=0,forced=0,flash=0;
  for(var m:c.methods)if(m.name.equals("tick"))for(var ins:m.instructions)if(ins instanceof MethodInsnNode call){
   if(call.owner.equals("java/util/List")&&call.name.equals("iterator"))list++;
   if(call.owner.equals("net/minecraft/client/multiplayer/ClientLevel")&&call.name.equals("addParticle")){
    if(call.desc.equals("(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))forced++;
    if(call.desc.equals("(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))flash++;
   }
  }
  assertEquals(1,list);assertEquals(3,forced);assertEquals(1,flash);
 }
 @Test void radialRecordFieldsMatchAccessors()throws Exception{
  var c=read("rbasamoyai/createbigcannons/effects/particles/explosions/FlakCloudParticle$TrailSubparticle");
  for(String field:new String[]{"displacement","vel","drag","lifetime"})assertTrue(c.fields.stream().anyMatch(f->f.name.equals(field)),field);
 }
}
