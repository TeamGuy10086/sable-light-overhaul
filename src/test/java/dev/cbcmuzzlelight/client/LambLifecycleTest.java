package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.AreaLight;
import dev.lambdaurora.lambdynlights.api.behavior.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambLifecycleTest {
    @Test void updateReusesBehaviorAndClearZerosOldWorkerReferences() {
        var registered=new ArrayList<DynamicLightBehavior>();
        LambAreaBackend.install(new DynamicLightBehaviorManager() {
            public void add(DynamicLightBehavior b) { registered.add(b); }
            public boolean remove(DynamicLightBehavior b) { return registered.remove(b); }
        });
        var l=new AreaLight(0,0,0,1,0,0,30,12,0.25,15);
        var full=new AreaSnapshot(l,AreaSnapshot.bounds(l),null);
        LambAreaBackend.sync(Map.of("gun/1",full));
        var retained=registered.getFirst();
        assertEquals(15,retained.lightAtPos(BlockPos.ZERO,1.935));
        assertTrue(retained.hasChanged()); assertFalse(retained.hasChanged());
        LambAreaBackend.sync(Map.of("gun/1",full)); assertFalse(retained.hasChanged());
        var dim=new AreaLight(0,0,0,1,0,0,30,12,0.25,7.5);
        LambAreaBackend.sync(Map.of("gun/1",new AreaSnapshot(dim,AreaSnapshot.bounds(dim),null)));
        assertSame(retained,registered.getFirst()); assertEquals(1,registered.size());
        assertTrue(retained.hasChanged()); assertEquals(7.5,retained.lightAtPos(BlockPos.ZERO,1.935));
        LambAreaBackend.clear();
        assertTrue(registered.isEmpty()); assertTrue(retained.isRemoved());
        assertEquals(0,retained.lightAtPos(BlockPos.ZERO,1.935),"Stale spatial entries must not retain removed light");
    }
}
