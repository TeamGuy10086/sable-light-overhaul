float slo_glow_visibility(vec3 source, vec2 uv, float tolerance) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    float blocker = min(slo_scene_distance(uv, false), slo_cloud_distance(uv));
    return smoothstep(-0.05, 0.05, blocker + tolerance - length(source));
}
