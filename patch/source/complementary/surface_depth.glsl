#ifndef SLO_COMPLEMENTARY_SURFACE_DEPTH
#define SLO_COMPLEMENTARY_SURFACE_DEPTH
float slo_depth_along_view(mat4 inverseProjection, vec2 uv, float depth) {
    if (depth <= 0.0 || depth >= 1.0) return 1e20;
    vec4 point = inverseProjection * (vec4(uv, depth, 1.0) * 2.0 - 1.0);
    return -point.z / point.w;
}

float slo_shadow_depth(vec2 uv) {
    float depth = slo_depth_along_view(gbufferProjectionInverse, uv, texture(depthtex0, uv).r);
#ifdef VOXY
    depth = min(depth, slo_depth_along_view(vxProjInv, uv, texture(vxDepthTexOpaque, uv).r));
#elif defined DISTANT_HORIZONS
    depth = min(depth, slo_depth_along_view(dhProjectionInverse, uv, texture(dhDepthTex, uv).r));
#endif
    return depth;
}
#endif
