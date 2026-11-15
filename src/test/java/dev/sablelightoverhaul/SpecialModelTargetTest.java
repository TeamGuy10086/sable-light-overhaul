package dev.sablelightoverhaul;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpecialModelTargetTest {
 ClassNode read(String jar,String name)throws Exception{try(var z=new JarFile("libs/"+jar+".jar")){var n=new ClassNode();new ClassReader(z.getInputStream(z.getJarEntry(name+".class"))).accept(n,0);return n;}}
 @Test void copyfoxiesModelsUseCopycatsAssemblyBase()throws Exception{
  var n=read("copyfoxies","com/verr1/copyfoxies/content/copycat/center_variable_cube/CopyFoxCenterVariableCubeModelCore");assertTrue(n.superName.startsWith("com/copycatsplus/copycats/"));
 }
}
