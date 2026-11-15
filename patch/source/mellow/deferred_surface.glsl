#if SLO_MELLOW_LIGHT_SHADOWS == 1
vec3 slo_receiver_normal(sampler2D depths, mat4 inverseProjection, vec2 uv, vec3 position) {
    vec2 pixel = 1.0 / vec2(textureSize(depths, 0));
    vec2 left = clamp(uv - vec2(pixel.x, 0.0), 0.0, 1.0);
    vec2 right = clamp(uv + vec2(pixel.x, 0.0), 0.0, 1.0);
    vec2 down = clamp(uv - vec2(0.0, pixel.y), 0.0, 1.0);
    vec2 up = clamp(uv + vec2(0.0, pixel.y), 0.0, 1.0);
    vec3 dx0 = position - slo_view_position(inverseProjection, left, texture(depths, left).r);
    vec3 dx1 = slo_view_position(inverseProjection, right, texture(depths, right).r) - position;
    vec3 dy0 = position - slo_view_position(inverseProjection, down, texture(depths, down).r);
    vec3 dy1 = slo_view_position(inverseProjection, up, texture(depths, up).r) - position;
    vec3 normal = cross(abs(dx0.z) < abs(dx1.z) ? dx0 : dx1,
                        abs(dy0.z) < abs(dy1.z) ? dy0 : dy1);
    float lengthSquared = dot(normal, normal);
    if (!(lengthSquared > 1e-10)) return vec3(0.0);
    normal *= inversesqrt(lengthSquared);
    return mat3(gbufferModelViewInverse) * (dot(normal, -position) < 0.0 ? -normal : normal);
}

bool slo_in_shadow_light(vec3 position, vec4 light, vec4 shape, vec4 axis) {
    vec3 offset = position - light.xyz;
    float range = max(abs(shape.x), 0.01);
    float tangent = max(axis.w, 0.0);
    return light.w > 0.0 && dot(offset, offset) <= range * range * (1.0 + tangent * tangent);
}

float slo_receiver_footprint(vec3 position) {
    return 2.0 * length(position) / max(abs(gbufferProjection[1][1]) * resolution.y, 1.0);
}

vec3 slo_deferred_shadow(vec3 color, vec2 uv, vec3 viewPosition, vec3 position, bool lod) {
    if (SLO_MELLOW_LIGHT <= 0.0 || sloShadowMask == 0 || sloLightCount <= 0) return color;
    vec3 normal;
    vec2 unjittered = uv - 0.5 * taaJitter;
    #if defined DISTANT_HORIZONS && !defined VOXY
        if (lod) normal = slo_receiver_normal(dhDepthTex1, dhProjectionInverse, uv, viewPosition);
        else
    #endif
    {
        float depth = texelFetch(depthtex1, ivec2(gl_FragCoord.xy), 0).r;
        normal = slo_receiver_normal(depthtex1, gbufferProjectionInverse, uv,
                                     slo_view_position(gbufferProjectionInverse, uv, depth));
        viewPosition = slo_view_position(gbufferProjectionInverse, unjittered, depth);
        position = (gbufferModelViewInverse * vec4(viewPosition, 1.0)).xyz;
    }
    if (dot(normal, normal) < 0.5 || !slo_has_shadow_light(position)) return color;

    // The base pass has already applied texture color and ambient light.
    vec3 reflectance = clamp(sqrt(max(color, vec3(0.0))), 0.08, 1.0);
    slo_shadow_pixel_footprint = slo_receiver_footprint(viewPosition);
    slo_mellow_shadow_jitter = 0.5 * taaJitter;
    slo_shadow_only = true;
    slo_shadow_lost = vec3(0.0);
    get_slo_lighting(position, normal, normal, 1.0, 0.0, reflectance);
    slo_shadow_only = false;
    return max(color - slo_shadow_lost * reflectance * SLO_MELLOW_LIGHT, color * 0.36);
}
#endif

#ifdef VOXY
#include "/lib/slo/voxy_material.glsl"
uniform sampler2D colortex14;

vec3 slo_light_voxy(vec3 color, ivec2 pixel, vec2 uv) {
    if (sloLightCount <= 0) return color;
    vec4 material = texelFetch(colortex14, pixel, 0);
    int code = int(round(material.a * 255.0)) - 1;
    if (code < 0) return color;
    float depth = slo_opaque_lod_z(uv);
    if (depth >= 1e19) return color;
    vec3 viewPosition = slo_pixel_ray(vxProjInv, uv - 0.5 * taaJitter) * depth;
    vec3 position = mat3(vxModelViewInv) * viewPosition + vxModelViewInv[3].xyz;
    vec3 normal = slo_voxy_face_normal(code % 6);
    int lightmap = code / 6;
    float skylight = float(lightmap % 8) / 7.0;
    float ao = float(lightmap / 8) / 4.0;
    #if SLO_MELLOW_LIGHT_SHADOWS == 1
        slo_shadow_pixel_footprint = slo_receiver_footprint(viewPosition);
        slo_mellow_shadow_jitter = 0.5 * taaJitter;
    #endif
    return color + slo_surface_light(position, normal, normal, ao, skylight, material.rgb)
                   * (1.0 - darknessLightFactor);
}
#endif
