package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.client.ui.TomlSource;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class RetiredHullSettingsTest {
    @Test void legacyValuesStillLoadButCannotPolluteEitherMenu() {
        assertFalse(RetiredHullSettings.ENABLED);
        for(var spec:List.of(MuzzleClientConfig.SPEC,LightingCommon.SPEC)) {
            var source=new TomlSource(spec,java.util.Set.of());
            for(var group:source.groups())for(var field:group.fields())assertFalse(RetiredHullSettings.contains(field.key()),field.key());
        }
        var source=new TomlSource(LightingCommon.SPEC,java.util.Set.of());
        assertNotNull(source.spec("muzzle.tankCannon.hullDust.flow.density"));
        assertNull(source.spec("muzzle.tankCannon.hullDust.flow.surfaceCoupling"));
        assertTrue(LightingCommon.HULL_SURFACE_COUPLING.getSpec().getComment().startsWith(RetiredHullSettings.NOTICE));
        assertTrue(LightingCommon.HULL_SURFACE_COUPLING.getSpec().test(true)); // old files remain valid
    }
    @Test void legacyPayloadCannotConstructSurfaceSolver() throws Exception {
        try(var stream=getClass().getResourceAsStream("/dev/sablelightoverhaul/client/HullDustParticle.class")) {
            var node=new ClassNode();new ClassReader(stream).accept(node,0);
            var spawn=node.methods.stream().filter(m->m.name.equals("spawn")).findFirst().orElseThrow();
            for(var ins:spawn.instructions)if(ins instanceof MethodInsnNode call && call.name.equals("<init>")) {
                assertFalse(call.owner.endsWith("HullSurfaceAnchor"),"legacy payload activates anchor");
            }
        }
    }
}
