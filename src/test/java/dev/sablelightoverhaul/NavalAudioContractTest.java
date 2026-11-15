package dev.sablelightoverhaul;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class NavalAudioContractTest {
    @Test void reportAndImpactKeepTheirProfileAcrossTheOptionalChannel() {
        var profile = NavalSoundPayload.Profile.forWeapon(ResourceLocation.parse("sablehostile:twin_76mm"));
        assertEquals(NavalSoundPayload.Profile.MM76, profile);
        assertEquals("sablehostile:naval.76mm.near", profile.reportEvent(false).toString());
        assertEquals("sablehostile:naval.76mm.far", profile.reportEvent(true).toString());
        assertNull(NavalSoundPayload.Profile.forWeapon(ResourceLocation.parse("other:twin_76mm")));

        for (var kind : NavalSoundPayload.Kind.values()) {
            var message = new NavalSoundPayload(ResourceLocation.parse("minecraft:overworld"), new Vec3(2, 8.25, -3), kind, profile);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                NavalSoundPayload.CODEC.encode(buffer, message);
                assertEquals(message, NavalSoundPayload.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test void allNavalImpactEventsUseBundledGameEventReferences() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/sablelightoverhaul/sounds.json")) {
            assertNotNull(stream);
            var events = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(9, events.size());
            for (var model : HostileShellPayload.Model.values()) {
                var profile = NavalSoundPayload.Profile.forShell(model);
                for (var kind : NavalSoundPayload.Kind.values()) {
                    if (kind == NavalSoundPayload.Kind.REPORT) continue;
                    var event = events.getAsJsonObject(profile.impactEvent(kind).getPath());
                    assertNotNull(event);
                    for (var sample : event.getAsJsonArray("sounds")) {
                        var sound = sample.getAsJsonObject();
                        assertEquals("event", sound.get("type").getAsString());
                        assertTrue(sound.get("name").getAsString().startsWith("minecraft:"));
                    }
                }
            }
        }
    }

    @Test void nativeHookMatchesExactlyOneCbcCollisionCall() throws Exception {
        String owner = "rbasamoyai/createbigcannons/munitions/AbstractCannonProjectile";
        try (var jar = new JarFile("libs/cbc.jar")) {
            var node = new ClassNode();
            try (var stream = jar.getInputStream(jar.getJarEntry(owner + ".class"))) {
                new ClassReader(stream).accept(node, 0);
            }
            var tickCollision = node.methods.stream().filter(method -> method.name.equals("clipAndDamage")).findFirst().orElseThrow();
            int count = 0;
            for (var instruction : tickCollision.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                        && call.name.equals("calculateBlockPenetration")) {
                    assertEquals("(Lrbasamoyai/createbigcannons/munitions/ProjectileContext;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/BlockHitResult;)L"
                        + owner + "$ImpactResult;", call.desc);
                    count++;
                }
            }
            assertEquals(1, count);
        }
    }
}
