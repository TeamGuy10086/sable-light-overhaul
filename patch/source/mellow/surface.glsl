const float blocklight_scale = 1.0;
const float emission_scale = SLO_MELLOW_EMISSION;
const float time_noon = 0.0;
float cube(float value) { return value * value * value; }
float clamp01(float value) { return clamp(value, 0.0, 1.0); }
#include "/lib/slo/light.glsl"

vec3 slo_surface_light(vec3 position, vec3 normal, vec3 flatNormal, float ao,
                       float skylight, vec3 albedo) {
    vec3 irradiance = get_slo_lighting(position, normal, flatNormal, ao, skylight, albedo);
    return irradiance * albedo * SLO_MELLOW_LIGHT
         + slo_mellow_lamp_emission * slo_mellow_lamp_emission;
}
