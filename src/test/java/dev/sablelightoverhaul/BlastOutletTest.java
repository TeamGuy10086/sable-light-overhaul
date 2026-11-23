package dev.sablelightoverhaul;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlastOutletTest {
    @Test void anActuallyOpenPassageIsUsedAndAnIntactOneIsRejected() {
        var center = new Vec3(.5, .5, .5);
        var upward = new BlastDebugItem.Outlet(new Vec3(.5, 4, .5), new Vec3(0, 1, 0), 0);
        assertEquals(upward, BlastDebugItem.firstOpenOutlet(List.of(upward), center, (a, b) -> true));
        assertNull(BlastDebugItem.firstOpenOutlet(List.of(upward), center,
            (from, to) -> from.y >= 2 || to.y < 2));
    }

    @Test void aPassageAroundASolidLedgeStillVents() {
        Vec3 center = new Vec3(.5, .5, .5);
        Vec3 exit = new Vec3(.5, 3.9, .5);
        var outlet = new BlastDebugItem.Outlet(exit, new Vec3(0, 1, 0), 0);
        var ledge = new net.minecraft.world.phys.AABB(0, 1, 0, 1, 3, 1);
        assertEquals(outlet, BlastDebugItem.firstOpenOutlet(List.of(outlet), center,
            (from, to) -> !ledge.contains(from) && !ledge.contains(to) && ledge.clip(from, to).isEmpty()));
    }
}
