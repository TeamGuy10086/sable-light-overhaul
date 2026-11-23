package dev.sablelightoverhaul;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Check signatures against the actual shipped dependency bytecode, without starting a game or server. */
class SpotlightRangeHooksTest {
    private ClassNode jar(String file,String name) throws Exception {
        try(var jar=new JarFile(file);var stream=jar.getInputStream(jar.getJarEntry(name+".class"))){
            var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    private ClassNode own(String name) throws Exception {
        var node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/"+name+".class"))).accept(node,0);return node;
    }
    private MethodNode method(ClassNode node,String name) {
        var methods=node.methods.stream().filter(m->m.name.equals(name)).toList();assertEquals(1,methods.size(),node.name+"."+name);return methods.getFirst();
    }
    @Test void rawMigrationHookArgumentsMatchTheirTargets() throws Exception {
        String mc="build/moddev/artifacts/neoforge-21.1.228-merged.jar";
        String[][] hooks={
            {mc,"net/minecraft/world/level/chunk/storage/ChunkSerializer","read","SpotlightChunkMigrationMixin"},
            {"libs/create.jar","com/simibubi/create/content/contraptions/Contraption","readBlocksCompound","SpotlightContraptionMigrationMixin"},
            {"libs/sable.jar","dev/ryanhcode/sable/sublevel/plot/ServerLevelPlot","load","SpotlightSableMigrationMixin"}
        };
        for(String[] hook:hooks) {
            MethodNode target=method(jar(hook[0],hook[1]),hook[2]),handler=method(own("mixin/"+hook[3]),"slo$migrateRange");
            Type[] expected=Type.getArgumentTypes(target.desc),actual=Type.getArgumentTypes(handler.desc);
            assertEquals(expected.length+1,actual.length);
            for(int i=0;i<expected.length;i++)assertEquals(expected[i],actual[i],hook[3]);
            assertEquals(target.access&Opcodes.ACC_STATIC,handler.access&Opcodes.ACC_STATIC);
            var inject=handler.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            int atIndex=inject.values.indexOf("at");
            var at=(AnnotationNode)((java.util.List<?>)inject.values.get(atIndex+1)).getFirst();
            assertEquals("HEAD",at.values.get(at.values.indexOf("value")+1));
        }
        MethodNode template=method(jar(mc,"net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate"),"loadPalette");
        assertEquals("(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/ListTag;Lnet/minecraft/nbt/ListTag;)V",template.desc);
        var handler=method(own("mixin/SpotlightStructureMigrationMixin"),"slo$migrateRange");
        assertEquals("(Lnet/minecraft/nbt/ListTag;Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/ListTag;Lnet/minecraft/nbt/ListTag;)Lnet/minecraft/nbt/ListTag;",handler.desc);
    }
    @Test void worldEditReaderStillUsesTheInterceptedParserAndCoordinates() throws Exception {
        var reader=jar("libs/worldedit.jar","com/sk89q/worldedit/extent/clipboard/io/sponge/ReaderUtil");
        var decode=method(reader,"decodePalette");
        assertEquals("(Lorg/enginehub/linbus/tree/LinCompoundTag;Lcom/sk89q/worldedit/extent/clipboard/io/sponge/VersionedDataFixer;)Ljava/util/Map;",decode.desc);
        boolean found=false;
        for(var insn:decode.instructions)if(insn instanceof MethodInsnNode call&&call.name.equals("parseFromInput")) {
            assertEquals("com/sk89q/worldedit/extension/factory/BlockFactory",call.owner);
            assertEquals("(Ljava/lang/String;Lcom/sk89q/worldedit/extension/input/ParserContext;)Ljava/lang/Object;",call.desc);found=true;
        }
        assertTrue(found);
        assertEquals("(Lcom/sk89q/worldedit/extent/clipboard/Clipboard;Ljava/util/Map;[BLorg/enginehub/linbus/tree/LinListTag;Lcom/sk89q/worldedit/extent/clipboard/io/sponge/VersionedDataFixer;Z)V",
            method(reader,"initializeClipboardFromBlocks").desc);
        // Compare our coordinate reconstruction to WorldEdit's actual implementation over several planes.
        var clazz=Class.forName("com.sk89q.worldedit.extent.clipboard.io.sponge.ReaderUtil");
        var position=clazz.getDeclaredMethod("decodePositionFromDataIndex",int.class,int.class,int.class);position.setAccessible(true);
        for(int i=0;i<315;i++){
            var actual=(com.sk89q.worldedit.math.BlockVector3)position.invoke(null,7,9,i);
            assertEquals(com.sk89q.worldedit.math.BlockVector3.at(i%7,i/(7*9),(i/7)%9),actual);
        }
    }
    @Test void spotlightNoLongerBuildsAThousandWayStateProperty() throws Exception {
        var block=own("lighting/SpotlightBlock");
        assertFalse(block.fields.stream().anyMatch(f->f.name.equals("RANGE")));
        var definition=method(block,"createBlockStateDefinition");Set<String> properties=new HashSet<>();
        for(var insn:definition.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC)properties.add(field.name);
        assertEquals(Set.of("FACING","MODE","VISIBLE"),properties);
        var entity=own("lighting/SpotlightBlockEntity");
        assertTrue(entity.fields.stream().anyMatch(f->f.name.equals("range")&&f.desc.equals("I")));
    }
}
