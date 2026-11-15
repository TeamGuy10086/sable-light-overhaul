// colortex0 precedes this pass's smoke/fog composition. Refract it HERE, before
// those foreground layers, instead of mixing that raw buffer over the final scene.
vec3 slo_wave_sample(vec2 coord) {
    vec2 size = vec2(textureSize(colortex0, 0));
    vec2 pixel = coord * taau_render_scale * size - 0.5;
    ivec2 base = ivec2(floor(pixel)), last = ivec2(size) - 1;
    vec2 f = fract(pixel);
    return mix(mix(texelFetch(colortex0, clamp(base, ivec2(0), last), 0).rgb,
                   texelFetch(colortex0, clamp(base + ivec2(1,0), ivec2(0), last), 0).rgb, f.x),
               mix(texelFetch(colortex0, clamp(base + ivec2(0,1), ivec2(0), last), 0).rgb,
                   texelFetch(colortex0, clamp(base + ivec2(1,1), ivec2(0), last), 0).rgb, f.x), f.y);
}

float slo_wave_scene_distance(vec2 coord) {
    float depth = texture(depthtex0, coord).r;
    bool lod = false;
#ifdef LOD_MOD_ACTIVE
    float lod_depth = texture(lod_depth_tex, coord).r;
    lod = is_lod_terrain(depth, lod_depth);
    if (lod) depth = lod_depth;
#endif
    if (depth >= 1.0) return 1e20;
    return length(screen_to_view_space(vec3(coord, depth), true, lod));
}

vec3 slo_wave_refract_scene(vec2 coord, vec2 offset, float nearest_wave) {
    // Keep foreground terrain/hand from being dragged onto a wave behind it.
    vec2 safe_min = view_pixel_size * 0.5;
    vec2 safe_max = vec2(1.0) - safe_min;
    float dispersion = clamp(sloWaveDispersion, 0.0, 1.0);
    vec2 far_uv = coord + offset * (1.0 + dispersion);
    if (any(lessThan(far_uv, safe_min)) || any(greaterThan(far_uv, safe_max)))
        return texture(colortex0, coord * taau_render_scale).rgb;
    float sample_t = slo_wave_scene_distance(far_uv);
    if (dispersion >= 0.001) {
        sample_t = min(sample_t, slo_wave_scene_distance(coord + offset));
        sample_t = min(sample_t, slo_wave_scene_distance(coord + offset * (1.0 - dispersion)));
    }
    float visibility = smoothstep(0.0, 0.5, sample_t - nearest_wave);
    offset *= visibility;
    if (dispersion < 0.001) return slo_wave_sample(coord + offset);
    return vec3(slo_wave_sample(coord + offset * (1.0 - dispersion)).r,
                slo_wave_sample(coord + offset).g,
                slo_wave_sample(coord + offset * (1.0 + dispersion)).b);
}
