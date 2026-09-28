package dev.cbcmuzzlelight;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpecialModelTargetTest {
 ClassNode read(String jar,String name)throws Exception{try(var z=new JarFile("libs/"+jar+".jar")){var n=new ClassNode();new ClassReader(z.getInputStream(z.getJarEntry(name+".class"))).accept(n,0);return n;}}
 @Test void sableTaskAndCommitDescriptorsAreExact()throws Exception{
  String root="dev/ryanhcode/sable/sublevel/render/fancy/";
  var n=read("sable",root+"task/FancySubLevelTaskScheduler");
  var m=n.methods.stream().filter(x->x.name.equals("scheduleCompile")).findFirst().orElseThrow();
  assertEquals("(L"+root+"FancySubLevelSectionCompiler$RenderSection;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;DLjava/util/function/Consumer;)V",m.desc);
  int calls=0;for(var insn:m.instructions)if(insn instanceof MethodInsnNode c&&c.name.equals("schedule")){assertEquals(root+"task/FancySubLevelTaskScheduler",c.owner);assertEquals("(L"+root+"task/SubLevelTask;DLjava/lang/Runnable;)V",c.desc);calls++;}assertEquals(1,calls);
  n=read("sable",root+"FancySubLevelSectionCompiler$RenderSection");assertTrue(n.methods.stream().anyMatch(x->x.name.equals("setCompiledSection")&&x.desc.equals("(L"+root+"FancySubLevelSectionCompiler$CompiledSection;)V")));
 }
 @Test void copycatCoordinateAdaptersMatchIncludingForgeSubclass()throws Exception{
  String root="com/copycatsplus/copycats/foundation/copycat/model/";
  var n=read("copycats",root+"ScaledBlockAndTintGetter");assertTrue(n.methods.stream().anyMatch(m->m.name.equals("getTruePos")&&m.desc.equals("(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;")));
  assertTrue(n.methods.stream().anyMatch(m->m.name.equals("getWrapped")&&m.desc.equals("()Lnet/minecraft/world/level/BlockAndTintGetter;")));
  assertEquals(root+"ScaledBlockAndTintGetter",read("copycats",root+"neoforge/ScaledBlockAndTintGetterForge").superName);
  n=read("copycats",root+"FilteredBlockAndTintGetter");assertTrue(n.fields.stream().anyMatch(f->f.name.equals("wrapped")&&f.desc.equals("Lnet/minecraft/world/level/BlockAndTintGetter;")));
 }
 @Test void yuushyaCacheHooksMatchUploadedVersion()throws Exception{
  String root="com/yuushya/modelling/client/anvilcraft/rendering/";
  var n=read("yuushya",root+"CacheableBERenderingPipeline");assertTrue(n.fields.stream().anyMatch(f->f.name.equals("regions")&&f.desc.equals("Ljava/util/Map;")));assertTrue(n.methods.stream().anyMatch(m->m.name.equals("runTasks")&&m.desc.equals("()V")));
  n=read("yuushya",root+"CachedRegion");assertTrue(n.fields.stream().anyMatch(f->f.name.equals("blockEntities")&&f.desc.equals("Ljava/util/Set;")));assertTrue(n.methods.stream().anyMatch(m->m.name.equals("forcedUpdate")&&m.desc.equals("()V")));
 }
 @Test void copyfoxiesModelsUseCopycatsAssemblyBase()throws Exception{
  var n=read("copyfoxies","com/verr1/copyfoxies/content/copycat/center_variable_cube/CopyFoxCenterVariableCubeModelCore");assertTrue(n.superName.startsWith("com/copycatsplus/copycats/"));
 }
}
