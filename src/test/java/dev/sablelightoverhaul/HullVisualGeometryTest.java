package dev.sablelightoverhaul;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullVisualGeometryTest {
    static final Vec3 UP=new Vec3(0,1,0);
    static HullVisualGeometry.Triangle roof(double y){return new HullVisualGeometry.Triangle(new Vec3(-1,y,-1),new Vec3(1,y,1),new Vec3(1,y,-1));}
    @Test void outwardRayPicksExteriorInsteadOfClosestAbsoluteDistance(){
        var hit=HullVisualGeometry.project(List.of(roof(.1),roof(.4)),Vec3.ZERO,UP,.75);
        assertNotNull(hit);assertEquals(.4,hit.shift(),1e-8);assertEquals(UP,hit.normal());
        assertEquals(0,HullVisualGeometry.project(List.of(roof(0)),Vec3.ZERO,UP,.75).shift(),1e-8);
    }
    @Test void plotCoordinatesPreserveProjection(){
        var base=roof(.2);var offset=new Vec3(30000000,100,30000000);
        var far=new HullVisualGeometry.Triangle(base.a().add(offset),base.b().add(offset),base.c().add(offset));
        var hit=HullVisualGeometry.project(List.of(far),offset,UP,.75);
        assertNotNull(hit);assertEquals(.2,hit.shift(),1e-7);assertEquals(.2,hit.point().subtract(offset).y,1e-7);
    }
    @Test void backfacesAndDegenerateTrianglesAreNotSurfaces(){
        var front=roof(.3);var back=new HullVisualGeometry.Triangle(front.a(),front.c(),front.b());
        var degenerate=new HullVisualGeometry.Triangle(Vec3.ZERO,UP,UP.scale(2));
        assertNull(HullVisualGeometry.project(List.of(back,degenerate),Vec3.ZERO,UP,.75));
    }
    @Test void slopedRenderPanelCorrectsBothPositionAndNormalAtSharedEdge(){
        var a=new Vec3(-1,-.2,-1);var b=new Vec3(1,.6,-1);var c=new Vec3(1,.6,1);var d=new Vec3(-1,-.2,1);
        var hit=HullVisualGeometry.project(List.of(new HullVisualGeometry.Triangle(a,c,b),new HullVisualGeometry.Triangle(a,d,c)),Vec3.ZERO,UP,.75);
        assertNotNull(hit);assertEquals(.2,hit.point().y,1e-8);assertEquals(new Vec3(-.4,1,0).normalize().x,hit.normal().x,1e-8);
    }
    @Test void holesOutOfReachAndInvalidInputsFallBack(){
        assertNull(HullVisualGeometry.project(List.of(roof(2)),Vec3.ZERO,UP,.75));
        assertNull(HullVisualGeometry.project(List.of(roof(.2)),new Vec3(4,0,0),UP,.75));
        assertNull(HullVisualGeometry.project(List.of(roof(.2)),Vec3.ZERO,Vec3.ZERO,.75));
        assertNull(HullVisualGeometry.project(List.of(roof(.2)),Vec3.ZERO,UP,Double.NaN));
        assertNull(HullVisualGeometry.project(List.of(new HullVisualGeometry.Triangle(new Vec3(Double.NaN,0,0),UP,Vec3.ZERO)),Vec3.ZERO,UP,.75));
    }
}
