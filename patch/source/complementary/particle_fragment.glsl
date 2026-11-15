#include "/lib/slo/depth.glsl"
#include "/lib/slo/gas.glsl"
#include "/lib/slo/smoke.glsl"

vec4 slo_particle_color(int kind, vec3 local, vec4 params, vec4 tint, vec3 viewPos, vec2 uv, vec2 atlasUV, float lodZ) {
    float distanceToParticle = length(viewPos);
    float cloud = slo_cloud_distance(uv);
    if (distanceToParticle > cloud) discard;

    if (kind == 6) {
        return vec4(tint.rgb * (SLO_COMPLEMENTARY_EMISSION * SLO_COMPLEMENTARY_TRACER_GAIN), 1.0);
    }

    vec4 color = tint;
    if (kind <= 2) {
        color *= texture(tex, atlasUV);
    } else if (kind == 3) {
        float lodDistance = lodZ * (distanceToParticle / max(-viewPos.z, 1e-5));
        color = slo_gas_volume(local, params, tint.a, viewPos, min(cloud, lodDistance));
    } else if (kind == 4) {
        color = slo_smoke_tinted(local.xy, round(local.z), tint);
        if (abs(round(local.z)) >= 128.0) {
            // Build a dense core from the same material mask before applying the lifecycle.
            // Leave the thin tail and late erosion on their own curves.
            float body = smoothstep(0.55 * sloThermalCut, sloThermalCut + 0.11, color.a);
            float fringe = 0.35 * smoothstep(0.0, sloThermalCut + 0.20, color.a);
            float life = sloThermalLife;
            float denseLife = life * (1.0 + (1.0 - life) * (0.8 + 0.2 * life));
            color.a = sloThermalIslands * mix(fringe * life, denseLife, body);
            color.rgb += vec3(2.2, 0.72, 0.12) * sloThermalHeat * sloSmokeOptions.w * SLO_COMPLEMENTARY_EMISSION;
        }
    } else if (kind == 5) {
        color = slo_ribbon_sample(local.xy) * tint;
    }
    if (kind == 1 || kind == 3) color.rgb *= SLO_COMPLEMENTARY_EMISSION;
    if (color.a < 0.002) discard;
    return color;
}
