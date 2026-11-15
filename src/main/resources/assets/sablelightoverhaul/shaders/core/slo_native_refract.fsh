#version 150
#moj_import <sablelightoverhaul:slo_native_depth.glsl>

uniform sampler2D Background;
uniform sampler2D DepthSampler;
uniform mat4 InverseProjection;
uniform vec2 ScreenSize;
uniform vec4 Sphere;
uniform float Refraction;
out vec4 fragColor;

float sceneDepth(vec2 uv) {
    return min(texture(DepthSampler, uv).r, slo_native_projected_depth(slo_native_lod_point(uv)));
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec4 view = InverseProjection * vec4(uv * 2.0 - 1.0, 0.9, 1.0);
    vec3 ray = normalize(view.xyz / view.w);
    float axial = dot(ray, Sphere.xyz);
    float radial = length(Sphere.xyz - ray * axial) / Sphere.w;
    if (radial >= 1.0 || radial < 0.72) discard;
    float shell = Sphere.w * sqrt(1.0 - radial * radial);
    float hit = axial - shell;
    if (hit <= 0.01) hit = axial + shell;
    if (hit <= 0.01) discard;
    vec4 clip = uViewProj * vec4(ray * hit, 1.0);
    float depth = clip.z / clip.w * 0.5 + 0.5;
    if (depth >= sceneDepth(uv)) discard;
    float edge = smoothstep(0.72, 0.94, radial) * (1.0 - smoothstep(0.98, 1.0, radial));
    vec2 offset = (uv * 2.0 - 1.0) * clip.w;
    vec4 center = uViewProj * vec4(Sphere.xyz, 1.0);
    vec2 direction = offset * center.w - center.xy * clip.w;
    vec2 shift = 10.0 * edge * Refraction * direction / max(length(direction), 0.001) / ScreenSize;
    vec2 sampleUV = clamp(uv + shift, 0.0, 1.0);
    if (depth >= sceneDepth(sampleUV)) discard;
    fragColor = texture(Background, sampleUV);
}
