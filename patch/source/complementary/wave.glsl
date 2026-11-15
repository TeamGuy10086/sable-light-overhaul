#include "/lib/slo/depth.glsl"
#include "/lib/slo/wave_geometry.glsl"

uniform sampler2D colortex13;
uniform float sloWaveRefract;
uniform float sloWaveDispersion;

void slo_accumulate_wave(vec4 sphere, vec4 shape, vec3 ray, float sceneDistance, float pixelAngle,
                         inout vec4 effect, inout float firstHit) {
    float hit;
    vec4 profile = slo_wave_profile(ray, sceneDistance, sphere, shape,
                                   mat3(gbufferModelView), pixelAngle, hit);
    effect += profile;
    if (any(notEqual(profile, vec4(0.0)))) firstHit = min(firstHit, hit);
}

float slo_foreground_coverage(vec2 uv, float waveDistance) {
    vec2 smoke = texture(colortex13, uv).rg;
    if (smoke.x < 0.002) return 0.0;
    float smokeDistance = exp2(smoke.y / smoke.x) - 1.0;
    return clamp(smoke.x, 0.0, 1.0) * smoothstep(-0.25, 0.25, waveDistance - smokeDistance);
}

vec3 slo_apply_wave(vec3 color, vec2 uv, vec4 effect, float firstHit) {
    if (all(equal(effect, vec4(0.0)))) return color;
    vec3 beforeWave = color;
    float foreground = slo_foreground_coverage(uv, firstHit);
    vec2 pixel = 1.0 / vec2(textureSize(colortex0, 0));
    vec2 displacement = effect.xy * max(0.0, sloWaveRefract);
    displacement *= min(1.0, 24.0 / max(length(displacement), 0.001));
    vec2 offset = displacement * pixel;
    if (dot(offset, offset) > 1e-12) {
        float dispersion = clamp(sloWaveDispersion, 0.0, 1.0);
        vec2 farUV = uv + offset * (1.0 + dispersion);
        vec2 edge = pixel * 0.5;
        if (all(greaterThanEqual(farUV, edge)) && all(lessThanEqual(farUV, 1.0 - edge))) {
            float sampleDistance = slo_scene_distance(farUV, true);
            if (dispersion > 0.001) {
                sampleDistance = min(sampleDistance, slo_scene_distance(uv + offset, true));
                sampleDistance = min(sampleDistance, slo_scene_distance(uv + offset * (1.0 - dispersion), true));
            }
            float shiftedForeground = slo_foreground_coverage(farUV, firstHit);
            offset *= smoothstep(0.0, 0.5, sampleDistance - firstHit)
                    * (1.0 - max(0.0, shiftedForeground - foreground));
            if (dispersion > 0.001) {
                color = vec3(texture(colortex0, uv + offset * (1.0 - dispersion)).r,
                             texture(colortex0, uv + offset).g,
                             texture(colortex0, uv + offset * (1.0 + dispersion)).b);
            } else {
                color = texture(colortex0, uv + offset).rgb;
            }
        }
    }
    float gain = 0.05 + 3.8 * clamp(dot(color, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    float vapour = effect.z * (1.0 + 0.20 * noonFactor);
    color = mix(color, vec3(0.90, 0.91, 0.94), clamp(vapour * 1.55, 0.0, 0.88));
    color += vec3(0.95, 0.90, 0.82) * gain * (vapour * 0.35 + effect.w);
    return mix(color, beforeWave, foreground);
}
