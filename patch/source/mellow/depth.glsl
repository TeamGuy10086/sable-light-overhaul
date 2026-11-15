#ifndef SLO_MELLOW_DEPTH
#define SLO_MELLOW_DEPTH

uniform sampler2D colortex12;
#ifdef VOXY
uniform sampler2D colortex15;
#endif

vec3 slo_view_position(mat4 inverseProjection, vec2 uv, float depth) {
    vec4 point = inverseProjection * (vec4(uv, depth, 1.0) * 2.0 - 1.0);
    return point.xyz / point.w;
}

vec3 slo_pixel_ray(mat4 inverseProjection, vec2 uv) {
    vec3 point = slo_view_position(inverseProjection, uv, 0.75);
    return point / max(-point.z, 1e-5);
}

float slo_opaque_lod_z(vec2 uv) {
#ifdef VOXY
    ivec2 size = textureSize(colortex15, 0);
    ivec2 pixel = clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1);
    float z = texelFetch(colortex15, pixel, 0).r;
    if (z > 0.0) return z;
#elif defined DISTANT_HORIZONS
    ivec2 size = textureSize(dhDepthTex1, 0);
    if (any(lessThanEqual(size, ivec2(1)))) return 1e20;
    ivec2 pixel = clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1);
    float depth = texelFetch(dhDepthTex1, pixel, 0).r;
    if (depth > 0.0 && depth < 1.0) {
        float viewZ = -slo_view_position(dhProjectionInverse, uv, depth).z;
        if (viewZ > 0.0) return viewZ;
    }
#endif
    return 1e20;
}

float slo_lod_distance(vec2 uv, bool translucent) {
#ifdef VOXY
    return slo_opaque_lod_z(uv) * length(slo_pixel_ray(vxProjInv, uv));
#elif defined DISTANT_HORIZONS
    float depth = translucent ? texture(dhDepthTex0, uv).r : texture(dhDepthTex1, uv).r;
    if (depth > 0.0 && depth < 1.0) return length(slo_view_position(dhProjectionInverse, uv, depth));
    return 1e20;
#else
    return 1e20;
#endif
}

float slo_scene_distance(vec2 uv, bool translucent) {
    float depth = translucent ? texture(depthtex0, uv).r : texture(depthtex1, uv).r;
    float vanilla = depth > 0.0 && depth < 1.0
        ? length(slo_view_position(gbufferProjectionInverse, uv, depth)) : 1e20;
    return min(vanilla, slo_lod_distance(uv, translucent));
}

vec3 slo_deferred_depth_at(vec2 uv) {
    ivec2 size = textureSize(colortex12, 0);
    ivec2 pixel = clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1);
    return texelFetch(colortex12, pixel, 0).rgb;
}

float slo_cloud_distance(vec2 uv) {
    vec2 cloud = slo_deferred_depth_at(uv).xy;
    return cloud.x > 0.0 && cloud.y > 0.5 ? cloud.x : 1e20;
}

float slo_cloud_visibility(vec2 uv, float distanceToEffect) {
    vec2 cloud = slo_deferred_depth_at(uv).xy;
    if (cloud.x <= 0.0) return 1.0;
    return 1.0 - clamp(cloud.y, 0.0, 1.0)
                 * smoothstep(cloud.x, cloud.x + 2.0, distanceToEffect);
}

float slo_glow_visibility(vec3 source, vec2 uv, float tolerance) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return smoothstep(-0.05, 0.05, slo_scene_distance(uv, false) + tolerance - length(source))
           * slo_cloud_visibility(uv, length(source));
}
#endif
