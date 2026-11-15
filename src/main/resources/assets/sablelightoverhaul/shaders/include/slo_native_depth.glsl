uniform sampler2D LodDepthSampler;
uniform int uHasLod;
uniform int uLodZeroToOne;
uniform mat4 uInvLodViewProj;
uniform mat4 uViewProj;

vec4 slo_native_lod_point(vec2 uv) {
    if (uHasLod == 0) return vec4(0.0);
    float depth = texture(LodDepthSampler, uv).r;
    if (depth <= 0.0 || depth >= 1.0) return vec4(0.0);
    return uInvLodViewProj * vec4(uv * 2.0 - 1.0,
            uLodZeroToOne == 1 ? depth : depth * 2.0 - 1.0, 1.0);
}

float slo_native_projected_depth(vec4 point) {
    if (abs(point.w) < 1e-8) return 1.0;
    vec4 clip = uViewProj * (point / point.w);
    return clip.w > 0.0 ? clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0) : 1.0;
}
