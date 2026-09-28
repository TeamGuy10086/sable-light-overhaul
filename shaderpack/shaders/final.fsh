#version 150 compatibility
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
in vec2 texcoord;
out vec4 fragColor;
#include "/lib/cbc_muzzle.glsl"
void main() {
    vec4 scene = texture(colortex0, texcoord);
    float depth = texture(depthtex0, texcoord).r;
    if (depth < 0.999999) {
        vec4 view = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        view /= view.w;
        vec3 cameraRelative = (gbufferModelViewInverse * view).xyz;
        // Diagnostic distance-only lighting. No point-light shadows, normals, GI or material reconstruction.
        scene.rgb += (scene.rgb + vec3(0.12)) * cbcMuzzleLighting(cameraRelative);
    }
    fragColor = vec4(scene.rgb, scene.a);
}
