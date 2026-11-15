#ifndef SLO_COMPLEMENTARY_DEPTH
#define SLO_COMPLEMENTARY_DEPTH

float slo_view_distance(mat4 inverseProjection, vec2 uv, float depth) {
    if (depth >= 1.0) return 1e20;
    vec4 point = inverseProjection * (vec4(uv, depth, 1.0) * 2.0 - 1.0);
    return length(point.xyz) / max(abs(point.w), 1e-8);
}

float slo_lod_distance(vec2 uv, bool translucent) {
#ifdef VOXY
    float depth = translucent ? texture(vxDepthTexTrans, uv).r : texture(vxDepthTexOpaque, uv).r;
    return slo_view_distance(vxProjInv, uv, depth);
#elif defined DISTANT_HORIZONS
    float depth = translucent ? texture(dhDepthTex, uv).r : texture(dhDepthTex1, uv).r;
    return slo_view_distance(dhProjectionInverse, uv, depth);
#else
    return 1e20;
#endif
}

// Particle occlusion uses the opaque LOD buffer, which is ready before the
// translucent particle pass. Compare view-space Z across the two projections.
float slo_particle_lod_z(ivec2 pixel, vec2 uv) {
#if defined VOXY
    ivec2 size = textureSize(vxDepthTexOpaque, 0);
    if (any(greaterThanEqual(pixel, size))) return 1e20;
    float depth = texelFetch(vxDepthTexOpaque, pixel, 0).r;
    mat4 inverseProjection = vxProjInv;
#elif defined DISTANT_HORIZONS
    ivec2 size = textureSize(dhDepthTex1, 0);
    if (any(greaterThanEqual(pixel, size))) return 1e20;
    float depth = texelFetch(dhDepthTex1, pixel, 0).r;
    mat4 inverseProjection = dhProjectionInverse;
#else
    return 1e20;
#endif
#if defined VOXY || defined DISTANT_HORIZONS
    if (!(depth > 0.0 && depth < 1.0)) return 1e20;
    vec4 point = inverseProjection * (vec4(uv, depth, 1.0) * 2.0 - 1.0);
    if (abs(point.w) < 1e-8) return 1e20;
    float viewZ = -point.z / point.w;
    return viewZ > 0.0 ? viewZ : 1e20;
#endif
}

float slo_scene_distance(vec2 uv, bool translucent) {
    float depth = translucent ? texture(depthtex0, uv).r : texture(depthtex1, uv).r;
    return min(slo_view_distance(gbufferProjectionInverse, uv, depth), slo_lod_distance(uv, translucent));
}

float slo_cloud_distance(vec2 uv) {
#ifdef VL_CLOUDS_ACTIVE
    // Complementary stores sqrt(first hit / renderDistance) in the deferred cloud buffer.
    float depth = texture(gaux2, uv).a;
    if (depth > 0.0 && depth < 1.0) return depth * depth * renderDistance;
#endif
    return 1e20;
}
#endif
