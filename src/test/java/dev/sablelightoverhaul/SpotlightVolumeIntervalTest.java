package dev.sablelightoverhaul;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the shipped scalar GLSL algebra as Java floats, against independent point-in-cone checks. */
class SpotlightVolumeIntervalTest {
    @TempDir static Path compiled;
    private static URLClassLoader loader;
    private static Method intersect;
    private static final float C = (float) Math.cos(Math.toRadians(20));

    @BeforeAll static void compileActualShaderFunction() throws Exception {
        String glsl = Files.readString(Path.of("patch/source/slo_beam_interval.glsl"));
        String body = glsl.replace("vec2 slo_beam_interval(", "public static float[] intersect(")
                .replaceAll("return vec2\\(([^,()]+), ([^,()]+)\\);", "return new float[] {$1, $2};")
                .replaceAll("(?<![\\w.])(\\d+\\.\\d+(?:e[+-]?\\d+)?)(?![\\w.])", "$1f");
        Path source = compiled.resolve("BeamIntervalShader.java");
        Files.writeString(source, "public class BeamIntervalShader {\n"
                + "static float min(float a,float b){return Math.min(a,b);}\n"
                + "static float max(float a,float b){return Math.max(a,b);}\n"
                + "static float sqrt(float a){return (float)Math.sqrt(a);}\n" + body + "\n}");
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the test must run on a JDK");
        assertEquals(0, compiler.run(null, null, null, "-d", compiled.toString(), source.toString()));
        loader = new URLClassLoader(new java.net.URL[] {compiled.toUri().toURL()});
        intersect = loader.loadClass("BeamIntervalShader").getMethod("intersect",
                float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class);
    }

    @AfterAll static void closeCompilerOutput() throws Exception {
        if (loader != null) loader.close();
    }

    private static float[] interval(float x,float y,float z,float dx,float dy,float dz,
                                    float cos,float reach,float scene) throws Exception {
        return interval(x,y,z,dx,dy,dz,cos,reach,scene,0);
    }
    private static float[] interval(float x,float y,float z,float dx,float dy,float dz,
                                    float cos,float reach,float scene,float aperture) throws Exception {
        return (float[]) intersect.invoke(null,dx,x,dx*x+dy*y+dz*z,
            x*x+y*y+z*z,cos*cos,reach,scene,aperture);
    }
    @Test void finiteLensHasLitAirAtItsRimAndNoRearNappe() throws Exception {
        float[] span=interval(0.01f,.2f,0,0,1,0,C,40,100,.25f);
        assertEquals(.02f,span[0],1e-5);
        assertEquals(.25+.01*Math.tan(Math.toRadians(20))-.2,span[1],1e-5);
        assertArrayEquals(new float[]{0,0},interval(-.01f,0,0,0,1,0,C,40,100,.25f));
    }
    @Test void frustumRaysAgreeWithIndependentMembership() throws Exception {
        Random r=new Random(558);
        for(int n=0;n<3000;n++){
            float x=r.nextFloat()*40-10,y=r.nextFloat()*20-10,z=r.nextFloat()*20-10;
            float dx=r.nextFloat()*2-1,dy=r.nextFloat()*2-1,dz=r.nextFloat()*2-1;
            float norm=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=norm;dy/=norm;dz/=norm;
            float a=.05f+r.nextFloat()*2,reach=5+r.nextFloat()*50;
            float[] span=interval(x,y,z,dx,dy,dz,C,reach,80,a);
            for(int k=0;k<100;k++){
                double t=(k+.37)*.8,ax=x+t*dx;
                double side=Math.hypot(y+t*dy,z+t*dz),radius=a+ax*Math.tan(Math.acos(C));
                if(Math.abs(side-radius)<.002||Math.abs(ax)<.002||Math.abs(ax-reach)<.002)continue;
                assertEquals(ax>0&&ax<reach&&side<radius,span[1]>span[0]&&t>span[0]&&t<span[1]);
            }
        }
    }

    @Test void sideCameraDoesNotPaintTheTwoFalseDiscs() throws Exception {
        float[] forward = interval(10, 10, 0, 1, 0, 0, C, 40, 100);
        assertEquals(10 / Math.tan(Math.toRadians(20)) - 10, forward[0], 0.0001);
        assertEquals(30, forward[1], 0.0001);
        // Looking away from the real shaft used to accept [0.02, 10], all of it outside.
        assertArrayEquals(new float[] {0, 0}, interval(10, 10, 0, -1, 0, 0, C, 40, 100));
    }

    @Test void aCameraInsideTheBeamKeepsItsVisibleSegment() throws Exception {
        assertArrayEquals(new float[] {0.02f, 30}, interval(10, 0, 0, 1, 0, 0, C, 40, 100), 0.0001f);
        assertArrayEquals(new float[] {0.02f, 10}, interval(10, 0, 0, -1, 0, 0, C, 40, 100), 0.0001f);
        assertArrayEquals(new float[] {0.02f, 5}, interval(10, 0, 0, 1, 0, 0, C, 40, 5), 0.0001f);
    }

    @Test void theRearConeNeverBecomesAVisibleBeam() throws Exception {
        assertArrayEquals(new float[] {0, 0}, interval(-10, 0, 0, -1, 0, 0, C, 40, 100));
        assertArrayEquals(new float[] {10, 50}, interval(-10, 0, 0, 1, 0, 0, C, 40, 100), 0.0001f);
        assertArrayEquals(new float[] {0, 0}, interval(-10, 0, 0, 1, 0, 0, C, 40, 5));
    }

    @Test void parallelAndGeneratorDirectionsHaveNoMissingStrip() throws Exception {
        assertArrayEquals(new float[] {0, 0}, interval(-1, 0, 0, 0, 1, 0, C, 40, 100));
        float[] transverse = interval(10, 0, 0, 0, 1, 0, C, 40, 100);
        assertEquals(0.02f, transverse[0]);
        assertEquals(10 * Math.tan(Math.toRadians(20)), transverse[1], 0.0001);
        float dy = (float) Math.sin(Math.toRadians(20));
        for (float dx : new float[] {C - 0.00001f, C, C + 0.00001f}) {
            float norm = (float) Math.hypot(dx, dy);
            checkPoints(10, 0, 0, dx/norm, dy/norm, 0, C, 40, 100);
        }
    }

    @Test void randomRaysAgreeWithIndependentPointMembership() throws Exception {
        Random random = new Random(491);
        for (int n = 0; n < 3000; n++) {
            float x = random.nextFloat() * 100 - 30;
            float y = random.nextFloat() * 80 - 40;
            float z = random.nextFloat() * 80 - 40;
            float dx = random.nextFloat() * 2 - 1;
            float dy = random.nextFloat() * 2 - 1;
            float dz = random.nextFloat() * 2 - 1;
            float norm = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
            float cos = (float) Math.cos(Math.toRadians(5 + random.nextFloat()*65));
            checkPoints(x, y, z, dx/norm, dy/norm, dz/norm, cos,
                    5 + random.nextFloat()*90, 1 + random.nextFloat()*180);
        }
    }

    private static void checkPoints(float x, float y, float z, float dx, float dy, float dz,
                                     float cos, float reach, float scene) throws Exception {
        float[] span = interval(x, y, z, dx, dy, dz, cos, reach, scene);
        assertTrue(Float.isFinite(span[0]) && Float.isFinite(span[1]));
        double tan2 = (1.0 - (double) cos*cos) / ((double) cos*cos);
        for (int k = 0; k < 100; k++) {
            double t = scene * (k + 0.37) / 100;
            double px = x + t*dx, py = y + t*dy, pz = z + t*dz;
            double boundary = px*px*tan2 - py*py - pz*pz;
            // Ignore only points at numerical precision of the cone/plane boundaries.
            if (Math.abs(boundary) < 0.002 || Math.abs(px) < 0.002
                    || Math.abs(px-reach) < 0.002 || Math.abs(t-0.02) < 0.0001) continue;
            boolean inside = t > 0.02 && px > 0 && px < reach && boundary > 0;
            boolean accepted = span[1] > span[0] && t > span[0] && t < span[1];
            assertEquals(inside, accepted, "ray origin=(" + x + "," + y + "," + z
                    + ") direction=(" + dx + "," + dy + "," + dz + ") t=" + t);
        }
    }
}
