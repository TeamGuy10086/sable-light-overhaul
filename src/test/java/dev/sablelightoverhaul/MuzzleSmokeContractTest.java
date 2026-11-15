package dev.sablelightoverhaul;

import java.nio.file.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Verify the supported CBC inheritance/method contracts that the muzzle-only integration hooks. */
class MuzzleSmokeContractTest {
    private ClassNode cbcPath(String path)throws Exception {
        try(var jar=new ZipFile("libs/cbc.jar")) {
            var n=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry(path+".class"))).accept(n,0);return n;
        }
    }
    @Test void disabledAutoEmitterHasOnlyTheTwoRequestedVanillaChildTypes()throws Exception {
        String path="rbasamoyai/createbigcannons/effects/particles/plumes/AutocannonPlumeParticle";
        var emitter=cbcPath(path);
        var kinds=new java.util.HashSet<String>();
        for(var m:emitter.methods)if(m.name.equals("tick"))for(var i:m.instructions)
            if(i instanceof org.objectweb.asm.tree.FieldInsnNode f && f.owner.equals("net/minecraft/core/particles/ParticleTypes"))kinds.add(f.name);
        assertEquals(java.util.Set.of("FLAME","CLOUD"),kinds,"Suppress only this emitter, never global vanilla particle factories");
        var provider=cbcPath(path+"$Provider");
        String descriptor="(L"+path+"Data;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;";
        assertEquals(1,provider.methods.stream().filter(m->m.name.equals("createParticle")&&m.desc.equals(descriptor)).count());
        String mixin=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/mixin/AutocannonPlumeSuppressMixin.java"));
        assertTrue(mixin.contains("createParticle"+descriptor));
        assertTrue(mixin.contains("ci.setReturnValue(null)"));
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/sablelightoverhaul.mixins.json"))).getAsJsonObject();
        assertTrue(config.getAsJsonArray("client").toString().contains("AutocannonPlumeSuppressMixin"));
        assertFalse(config.getAsJsonArray("mixins").toString().contains("AutocannonPlumeSuppressMixin"));
    }
    @Test void autocannonTrailCallsAreExactlyTheTwoBatchSites()throws Exception {
        String path="rbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile";
        var projectile=cbcPath(path);
        assertTrue(projectile.fields.stream().anyMatch(f->f.name.equals("displacement")&&f.desc.equals("D")),
            "the near-muzzle gate reads CBC's own travelled distance");
        int calls=0;
        for(var m:projectile.methods)if(m.name.equals("tick"))for(var i:m.instructions)
            if(i instanceof org.objectweb.asm.tree.MethodInsnNode c
                && c.owner.equals("net/minecraft/world/level/Level")
                && c.name.equals("addAlwaysVisibleParticle")
                && c.desc.equals("(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))calls++;
        assertEquals(2,calls,"CBC spawns the current and next trail segments in two batches");
        String mixin=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/mixin/AutocannonTrailMixin.java"));
        assertFalse(mixin.contains("AUTO_TRAIL_START"),"all tracer smoke is suppressed, not only launch smoke");
        assertTrue(mixin.contains("TrailSmokeParticleData"));
        assertTrue(mixin.contains("require = 2"));
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/sablelightoverhaul.mixins.json"))).getAsJsonObject();
        assertTrue(config.getAsJsonArray("client").toString().contains("AutocannonTrailMixin"));
        assertFalse(config.getAsJsonArray("mixins").toString().contains("AutocannonTrailMixin"));
    }

    @Test void thereIsOneMuzzleLayerAndItNeverWritesDepth()throws Exception {
        // 0.1.520-0.1.533 had a second SMOKE type that wrote depth and blended straight alpha into
        // Photon's premultiplied RT13. That is what put a transparent rim on every puff, so the
        // single layer is a contract, not a preference: the rim must never become an occluder and
        // the layer must never multiply an already-premultiplied colour by alpha again.
        var layer=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/MuzzleParticleLayer.java"));
        assertFalse(layer.contains("ParticleRenderType SMOKE"),"SMOKE was removed; smoke and flame share TYPE");
        assertTrue(layer.contains("private static volatile Mode mode = Mode.FIXED;"),"the shipped state is the fixed contract");
        assertTrue(layer.contains("RenderSystem.depthMask(mode == Mode.DEPTH)"),"depth writing exists only as the DEPTH A/B case");
        assertTrue(layer.contains("mode != Mode.BLEND"),"the premultiplied blend is the default; BLEND is the A/B case");
        assertTrue(layer.contains("ONE_MINUS_SRC_ALPHA"),"Photon RT13 is premultiplied");
        // The custom shader is only bound without a pack; under Iris the pack's own particle program
        // reads the lightmap marker and draws it. Always binding it made the batch disappear (0.1.527).
        assertTrue(layer.contains("if (!IrisCompat.shaderPackInUse() && shader != null)"));
        var fallback=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/mixin/MuzzleFallbackSmokeMixin.java"));
        assertTrue(fallback.contains("MuzzleParticleLayer.TYPE"));
        assertFalse(fallback.contains("MuzzleParticleLayer.SMOKE"));
        // One lobe is a diagnostic, and the command has to expose all four states to be useful.
        var mesh=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/MuzzleSmokeMesh.java"));
        assertTrue(mesh.contains("MuzzleParticleLayer.Mode.SINGLE"));
        var command=Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/SmokeDebugCommand.java"));
        assertTrue(command.contains("Mode.values()"),"the command iterates every A/B mode");
        for(var mode:new String[]{"FIXED","BLEND","DEPTH","SINGLE"})
            assertTrue(layer.contains(mode+"("),"mode "+mode+" is part of the A/B switch");
        assertTrue(Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/ClientBootstrap.java"))
            .contains("SmokeDebugCommand::register"));
    }
    @Test void controlledTankReleaseCreatesOwnedCloudSmokeWithoutClientLevelCulling()throws Exception {
        var emitter=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/client/PlumeEmitterParticle.class"))).accept(emitter,0);
        var allocated=new java.util.HashSet<String>();
        for(var m:emitter.methods)if(m.name.equals("tick"))for(var i:m.instructions)
            if(i instanceof org.objectweb.asm.tree.TypeInsnNode t && t.getOpcode()==org.objectweb.asm.Opcodes.NEW)allocated.add(t.desc);
        assertFalse(allocated.contains("rbasamoyai/createbigcannons/effects/particles/smoke/FallbackCannonSmokeParticleData"));
        assertTrue(allocated.contains("dev/sablelightoverhaul/lighting/PlumeSmokeParticleData"));
        for(var m:emitter.methods)for(var i:m.instructions)if(i instanceof org.objectweb.asm.tree.MethodInsnNode c) {
            assertFalse(c.owner.equals("net/minecraft/client/multiplayer/ClientLevel")&&c.name.equals("addParticle"),"A forced emitter must not lose its children to the 32-block client limit");
            assertFalse(c.name.equals("attachBigPlume"),"Tank smoke must not hijack a nearby big-cannon smoke light");
        }
        var nativeSmoke=cbc("CannonSmokeParticle");
        assertTrue(nativeSmoke.methods.stream().anyMatch(m->m.name.equals("getQuadSize")&&m.desc.equals("(F)F")));
        // Its constructor still runs for fallback particles; that is where the group captures alpha/growth.
        var fallback=cbc("FallbackCannonSmokeParticle");
        assertTrue(fallback.methods.stream().filter(m->m.name.equals("<init>")).anyMatch(m->java.util.Arrays.stream(m.instructions.toArray())
            .anyMatch(i->i instanceof org.objectweb.asm.tree.MethodInsnNode c && c.name.equals("<init>") && c.owner.equals(nativeSmoke.name))));
    }
    private ClassNode cbc(String name)throws Exception {
        try(var jar=new ZipFile("libs/cbc.jar")) {
            var n=new ClassNode();new ClassReader(jar.getInputStream(jar.getEntry("rbasamoyai/createbigcannons/effects/particles/smoke/"+name+".class"))).accept(n,0);return n;
        }
    }
    @Test void compatibleSmokeOwnsItsLayerButInheritsCoolingLight()throws Exception {
        var fallback=cbc("FallbackCannonSmokeParticle");
        assertTrue(fallback.superName.endsWith("/CannonSmokeParticle"));
        assertTrue(fallback.methods.stream().anyMatch(m->m.name.equals("getRenderType")&&m.desc.equals("()Lnet/minecraft/client/particle/ParticleRenderType;")));
        assertFalse(fallback.methods.stream().anyMatch(m->m.name.equals("getLightColor")));
        assertTrue(cbc("CannonSmokeParticle").methods.stream().anyMatch(m->m.name.equals("getLightColor")&&m.desc.equals("(F)I")));
    }
    @Test void spawnCommandBuildsOneIsolatedPuffInAFreshGroup() throws Exception {
        var command = Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/SmokeDebugCommand.java"));
        assertTrue(command.contains("Commands.literal(\"spawn\")"), "the isolated-puff repro command");
        assertTrue(command.contains("SmokeTracker.create("), "each spawn gets its own group, never a shared one");
        assertTrue(command.contains("BigSmokeParticle.emit("), "spawn goes through the same admission path as a real shot");
        assertTrue(command.contains("0, count, 1,"), "one tick, one release batch: exactly count particles");
        assertTrue(command.contains("Commands.literal(\"trace\")"), "the render trace toggle");
        var particle = Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/BigSmokeParticle.java"));
        assertTrue(particle.contains("public static void emit("), "emit stays the entry point the command relies on");
        assertTrue(particle.contains("SmokeTrace.row(\"render\"") && particle.contains("SmokeTrace.row(\"tick\""),
            "both the tick and the render side answer the trace");
    }
    @Test void referenceAndVolumeSpritesAreAvailableForBothWeaponModes()throws Exception {
        for(String name:new String[]{"auto_muzzle","fireball"}) {
            String json=Files.readString(Path.of("src/main/resources/assets/sablelightoverhaul/particles/"+name+".json"));
            assertTrue(json.indexOf("auto_muzzle")<json.indexOf("volume_white"));
        }
        assertEquals(dev.sablelightoverhaul.client.MuzzleClientConfig.FlameRenderer.VOLUME,
            dev.sablelightoverhaul.client.MuzzleClientConfig.FLAME_RENDERER.getDefault());
    }
}
