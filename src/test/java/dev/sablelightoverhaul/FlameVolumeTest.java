package dev.sablelightoverhaul;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlameVolumeTest {
    @Test void animatedTextureCoordinatesStayWithinTheirAtlasTile() {
        for(double phase=0;phase<10;phase+=0.3) for(double t=-0.5;t<1.5;t+=0.05) for(double side:new double[]{-2,0,2}) {
            float u=FlameVolume.textureU(t,side,phase),v=FlameVolume.textureV(t,side,phase);
            assertTrue(u>=0.002f && u<=0.998f);assertTrue(v>=0.002f && v<=0.998f);
        }
        assertNotEquals(FlameVolume.textureU(0.5,0,0),FlameVolume.textureU(0.5,0,0.3));
    }
    @Test void axialTextureFlowDoesNotFoldTheFlameBackwards() {
        for(double phase=0;phase<6;phase+=0.5) {
            float previous=0;
            for(int i=0;i<=100;i++) {
                float next=FlameVolume.textureU(i/100.0,0.3,phase);
                assertTrue(next>=previous);previous=next;
            }
        }
    }
    @Test void deploymentRecognizesBothOldLodPatchAndNewMuzzlePrograms() throws Exception {
        String deploy=Files.readString(Path.of("patch/deploy-to-instance.ps1"));
        var definition=java.util.regex.Pattern.compile("\\$oursMarker = '([^']+)'").matcher(deploy);
        assertTrue(definition.find());
        var marker=java.util.regex.Pattern.compile(definition.group(1));
        assertTrue(marker.matcher("float slo_z_lod = screen_to_view_space_depth").find());
        for(String p:new String[]{"gbuffers_all_solid","gbuffers_all_translucent"}) for(String ext:new String[]{"vsh","fsh"}) {
            assertTrue(marker.matcher(Files.readString(Path.of("patch/photon/shaders/program/"+p+"."+ext))).find());
        }
        assertFalse(marker.matcher(Files.readString(Path.of("patch/baselines/photon/shaders/program/gbuffers_all_translucent.fsh"))).find());
    }
    @Test void flameIsBoundedAndHasAnInteriorRatherThanAShell() {
        assertEquals(0,FlameVolume.density(-0.1,0,0));
        assertEquals(0,FlameVolume.density(1.1,0,0));
        assertEquals(0,FlameVolume.density(0.5,1.1,0));
        assertTrue(FlameVolume.density(0.5,0,0)>FlameVolume.density(0.5,0.8,0));
    }
    @Test void opticalIntegrationDoesNotGetBrighterByAddingSlices() {
        double a=1-Math.pow(1-FlameVolume.opacity(0.8,0.4,1,0.85f),5);
        double b=1-Math.pow(1-FlameVolume.opacity(0.8,0.2,1,0.85f),10);
        assertEquals(a,b,1e-6);assertEquals(0,FlameVolume.opacity(0,1,1,1));
    }
    @Test void texturedFallbackIsIncludedWhenSeparateEntityDrawsAreDisabled() throws Exception {
        String properties=Files.readString(Path.of("patch/photon/shaders/shaders.properties"));
        assertTrue(properties.contains("gbuffers_particles_translucent.enabled"));
        for(String ext:new String[]{"vsh","fsh"}) {
            String shader=Files.readString(Path.of("patch/photon/shaders/program/gbuffers_all_translucent."+ext));
            int a=shader.indexOf("#if ",shader.indexOf(ext.equals("vsh")?"out vec2 uv;":"in vec2 uv;"));
            String condition=shader.substring(a,shader.indexOf('\n',a));
            assertTrue(condition.contains("PROGRAM_GBUFFERS_TEXTURED"),"The real fallback must declare and execute the gas material");
        }
    }
    @Test void bothPhotonParticleProgramsKeepTheMaterialAndSoftAlphaContract() throws Exception {
        for(String p:new String[]{"gbuffers_all_solid","gbuffers_all_translucent"}) {
            String v=Files.readString(Path.of("patch/photon/shaders/program/"+p+".vsh"));
            String f=Files.readString(Path.of("patch/photon/shaders/program/"+p+".fsh"));
            assertTrue(v.contains("sloMuzzleTag == 12 && sloMuzzleRaw > 255 ? 3 :"));
            assertTrue(v.contains("gl_MultiTexCoord1.x - float(sloMuzzleTag)"));
            assertTrue(v.contains("if (sloMuzzleKind == 1 || sloMuzzleKind == 3 || sloMuzzleKind == 6) material_mask = 32u"));
            assertTrue(v.contains("sloMuzzleTag == 9 ? 7 : 0"));
            assertTrue(v.contains("if (sloMuzzleKind == 7) material_mask = 32u"));
            if (p.endsWith("translucent")) {
                assertTrue(f.contains("if (sloMuzzleKind == 7) material.emission = vec3(1.25)"));
            }
            assertTrue(f.contains("return sloMuzzleKind > 0 ? 0.002 : 0.1"));
            assertTrue(f.contains("< slo_muzzle_alpha_cutoff()"));
        }
    }
}
