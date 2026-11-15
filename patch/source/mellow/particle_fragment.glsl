#include "/lib/slo/depth.glsl"
#include "/lib/slo/gas.glsl"
#include "/lib/slo/smoke.glsl"

flat in int sloParticleKind;
in vec3 sloParticleLocal;
in vec3 sloParticleView;
flat in vec4 sloParticleParams;

vec3 slo_particle_linear(vec3 rgb) {
    rgb = clamp(rgb, 0.0, 1.0);
    return rgb * (rgb * (rgb * 0.305306011 + 0.682171111) + 0.012522878);
}

vec4 slo_render_particle(vec2 uv, vec2 atlasUV, vec4 tint) {
    float distanceToParticle = length(sloParticleView);
    float lodZ = slo_opaque_lod_z(uv);
    float visible = slo_cloud_visibility(uv, distanceToParticle);
    if (-sloParticleView.z > lodZ || visible < 0.002) discard;

    vec4 color = tint;
    if (sloParticleKind == 6) {
        // Opaque in clear air. Behind clouds, keep their contribution to the pixel.
        return vec4(tint.rgb * (SLO_MELLOW_EMISSION * SLO_MELLOW_TRACER_GAIN), visible);
    } else if (sloParticleKind <= 2) {
        color *= texture(gtexture, atlasUV);
        color.rgb = slo_particle_linear(color.rgb);
    } else if (sloParticleKind == 3) {
        float lodDistance = lodZ * (distanceToParticle / max(-sloParticleView.z, 1e-5));
        float opaque = min(lodDistance, min(slo_scene_distance(uv, false), slo_cloud_distance(uv)));
        color = slo_gas_volume(sloParticleLocal, sloParticleParams, tint.a, sloParticleView, opaque);
    } else if (sloParticleKind == 4) {
        float seed = round(sloParticleLocal.z);
        color = slo_smoke_tinted(sloParticleLocal.xy, seed, tint);
        if (abs(seed) >= 128.0) {
            color = slo_thermal_coverage(color);
            float heat = clamp(sloThermalHeat, 0.0, 1.0);
            float gain = max(sloSmokeOptions.w, 0.0);
            color.rgb = slo_particle_linear(color.rgb) * (0.36 * sloSmokeOptions.z)
                     + vec3(1.0, 0.32, 0.055) * heat * (3.0 * gain / (gain + 16.0))
                       * SLO_MELLOW_EMISSION;
        } else {
            color.rgb = slo_particle_linear(color.rgb);
        }
    } else if (sloParticleKind == 5) {
        color = slo_ribbon_sample(sloParticleLocal.xy) * tint;
        color.rgb = slo_particle_linear(color.rgb);
    }
    if (sloParticleKind == 1 || sloParticleKind == 3) color.rgb *= SLO_MELLOW_EMISSION;
    color.a *= visible;
    if (color.a < 0.002) discard;
    return color;
}

vec4 slo_particle_coverage(float alpha) {
    if (sloParticleKind == 6) return vec4(0.0);
    float coverage = clamp(alpha, 0.0, 1.0);
    return vec4(coverage, log2(1.0 + length(sloParticleView)) * coverage, 0.0, coverage);
}
