package dev.sablelightoverhaul;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Guards the actual crash boundary without initializing a Minecraft window in unit tests. */
class MissileExhaustAtlasTest {
    @Test void bothExhaustBranchesUseParticleEngineRegisteredAtlasWithoutStaleSpriteCache() throws Exception {
        var node=new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/api/ClientMissileEffectsApi.class"))).accept(node,0);
        var method=node.methods.stream().filter(m->m.name.equals("exhaust")).findFirst().orElseThrow();
        int manager=0,texture=0,sprites=0;
        for(var instruction:method.instructions) if(instruction instanceof MethodInsnNode call) {
            assertFalse(call.owner.endsWith("ModelManager"),"particle atlas is not a model atlas");
            assertFalse(call.owner.equals("net/minecraft/client/Minecraft")&&call.name.equals("getTextureAtlas"),"first launch NPE regression");
            if(call.owner.equals("net/minecraft/client/Minecraft")&&call.name.equals("getTextureManager"))manager++;
            if(call.owner.endsWith("/TextureManager")&&call.name.equals("getTexture"))texture++;
            if(call.owner.endsWith("/TextureAtlas")&&call.name.equals("getSprite"))sprites++;
        }
        assertEquals(1,manager);assertEquals(1,texture);assertEquals(2,sprites,"baseline and gas must resolve current sprites");
        for(var field:node.fields) {
            assertFalse(field.desc.contains("TextureAtlas"),"do not retain atlas/sprite across reloads");
        }
        assertTrue(method.tryCatchBlocks.isEmpty(),"do not hide broken exhaust by swallowing the exception");
    }

    @Test void bothSpritesAreStitchedByRegisteredAutocannonParticle() throws Exception {
        var root=Path.of("src/main/resources/assets/sablelightoverhaul");
        String definition=Files.readString(root.resolve("particles/auto_muzzle.json"));
        for(String name:new String[]{"auto_muzzle","volume_white"}) {
            assertTrue(definition.contains("\"sablelightoverhaul:"+name+"\""));
            assertTrue(Files.isRegularFile(root.resolve("textures/particle/"+name+".png")));
        }
    }
}
