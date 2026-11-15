// Lens glare is an image-space response to the SOURCE's visibility. Comparing
// each halo pixel's ground distance with the light radius cuts a spherical arc.
float slo_glow_visibility(vec3 source, vec2 projected, float tolerance) {
    if (any(lessThan(projected, vec2(0.0))) || any(greaterThan(projected, vec2(1.0)))) return 0.0;
    float depth = texture(depthtex1, projected).r;
    bool lod = false;
#ifdef LOD_MOD_ACTIVE
    float lod_depth = texture(lod_depth_tex_solid, projected).r;
    lod = is_lod_terrain(depth, lod_depth);
    if (lod) depth = lod_depth;
#endif
    if (depth >= 1.0) return 1.0;
    float blocker = length(screen_to_view_space(vec3(projected, depth), true, lod));
    // A small physical tolerance admits the emitting fixture's front surface.
    return smoothstep(-0.05, 0.05, blocker + tolerance - length(source));
}
