package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrailMuzzleGuardTest {
    @Test void launchSegmentIsClippedOutsideTheGrowingBillboardRadius() {
        var g=new TrailMuzzleGuard(new Vec3(10,20,30),new Vec3(0,0,1),1);
        var start=g.clipStart(new Vec3(10,20,28),new Vec3(10,20,40));
        assertEquals(31,start.z,1e-9);assertEquals(0,g.ahead(start),1e-9);
        assertNull(g.clipStart(new Vec3(10,20,28),new Vec3(10,20,30)));
        var ahead=new Vec3(10,20,35);
        assertSame(ahead,g.clipStart(ahead,new Vec3(10,20,40)));
    }
    @Test void rotatedDriftCanNeverRetreatThroughTheLaunchPlane() {
        var axis=new Vec3(.6,0,.8);var g=new TrailMuzzleGuard(new Vec3(100,80,-20),axis,1.2);
        var at=g.origin().add(axis.scale(3));
        var velocity=g.drift(at,axis.scale(-20).add(0,.1,0));
        assertEquals(.1,velocity.y,1e-9);
        for(int tick=0;tick<200;tick++) {
            at=at.add(velocity);velocity=velocity.scale(.9);
            assertTrue(g.ahead(at)>=-1e-8,"Even conservative .9 damping must keep the frame outside");
        }
    }
    @Test void shortFlashExpiryDoesNotLoseLaunchGeometryButOldShotsDo() {
        var store=new FlashStore(4);
        store.add(0,0,0,1,0,0,2,100,new LightCurve(2,0,0),new LightLayout(0,0));
        store.expire(103);assertTrue(store.areaFlashes().isEmpty());
        assertNotNull(store.trailGuard(new Vec3(0,0,0),new Vec3(20,0,0),103,1,.06));
        assertNull(store.trailGuard(new Vec3(0,10,0),new Vec3(20,10,0),103,1,.06));
        assertNull(store.trailGuard(new Vec3(0,0,0),new Vec3(-20,0,0),103,1,.06));
        assertNull(store.trailGuard(new Vec3(100,0,0),new Vec3(101,0,0),103,1,.06));
        assertNull(store.trailGuard(new Vec3(0,0,0),new Vec3(20,0,0),109,1,.06));
        store.clear();assertNull(store.trailGuard(new Vec3(0,0,0),new Vec3(20,0,0),103,1,.06));
    }
    @Test void worldFeedDoesNotPublishAnUntransformedLocalVelocity()throws Exception {
        var n=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
            "build/classes/java/main/dev/sablelightoverhaul/TracerFeed.class"))).accept(n,0);
        var tick=n.methods.stream().filter(m->m.name.equals("tick")).findFirst().orElseThrow();
        for(var i:tick.instructions)if(i instanceof org.objectweb.asm.tree.MethodInsnNode c)
            assertFalse(c.name.equals("getDeltaMovement"),"World endpoints must not be mixed with an entity-local vector");
    }
}
