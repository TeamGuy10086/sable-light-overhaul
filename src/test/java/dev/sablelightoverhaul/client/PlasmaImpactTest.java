package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.lighting.SourceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlasmaImpactTest {
    private final Vec3 up = new Vec3(0, 1, 0);
    private final SourceAnchor anchor = new SourceAnchor(null, BlockPos.ZERO);

    @Test void grazingSprayStillTravelsDownstreamAtItsWidestSpread() {
        var strike = PlasmaImpact.Contact.of(anchor, Vec3.ZERO, up, new Vec3(.96, -.28, 0));
        var frame = strike.frame(null, 0);
        var velocity = frame.sparkVelocity(frame.tangent().scale(-1), strike.skew(), .34, .12);
        assertTrue(velocity.x > .4 && velocity.y > 0);
        var direct = PlasmaImpact.Contact.of(anchor, Vec3.ZERO, up, up.scale(-1));
        assertEquals(0, direct.skew(), 1e-7);
    }

    @Test void oldContactUsesTheNewSurfacePoseForPositionAndOrientation() {
        var mark = PlasmaImpact.Contact.of(anchor, Vec3.ZERO, up, new Vec3(.96, -.28, 0));
        var rotation = new Quaternionf().rotateZ((float)(Math.PI / 2));
        var moved = mark.frameAt(new float[]{12, 3, 8, rotation.x, rotation.y, rotation.z, rotation.w});
        assertEquals(new Vec3(12, 3, 8), moved.point());
        assertTrue(moved.normal().distanceTo(new Vec3(-1, 0, 0)) < 1e-5);
        assertTrue(moved.tangent().distanceTo(up) < 1e-5);
    }
}
