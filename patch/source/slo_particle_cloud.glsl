// SLO depthless particles: approximate cloud extinction at the fragment's actual distance.
// The opaque/background pass already contains cloud scattering. Scale BOTH premultiplied
// radiance and coverage so fully opaque clouds reveal that background, never black cutouts.
// Two cached texture samples, no cloud raymarch or new framebuffer.
#if defined WORLD_OVERWORLD && (defined PROGRAM_GBUFFERS_PARTICLES || defined PROGRAM_GBUFFERS_PARTICLES_TRANSLUCENT || defined PROGRAM_GBUFFERS_TEXTURED)
uniform sampler2D colortex11;
uniform sampler2D colortex12;
float slo_particle_cloud_transmission(vec2 screen_uv, float particle_distance) {
    vec2 cloud_uv=screen_uv*taau_render_scale;
    float cloud_distance=textureLod(colortex12,cloud_uv,0.0).x;
    if(cloud_distance<=0.0 || particle_distance<=cloud_distance) return 1.0;
    return clamp(textureLod(colortex11,cloud_uv,0.0).a,0.0,1.0);
}
#else
float slo_particle_cloud_transmission(vec2 screen_uv, float particle_distance) { return 1.0; }
#endif

// Keep diagnostic changes scoped to thermal smoke. Normal mode retains the original blend.
vec4 slo_apply_particle_cloud(vec4 color, vec2 screen_uv, float particle_distance, bool thermal) {
#if defined WORLD_OVERWORLD && (defined PROGRAM_GBUFFERS_PARTICLES || defined PROGRAM_GBUFFERS_PARTICLES_TRANSLUCENT || defined PROGRAM_GBUFFERS_TEXTURED)
#if SLO_SMOKE_CLOUD_DEBUG == 1
    if (thermal) return color;
#elif SLO_SMOKE_CLOUD_DEBUG == 2
    if (thermal) {
        vec2 uv = screen_uv * taau_render_scale;
        float point_distance = textureLod(colortex12, uv, 0.0).x;
        vec4 neighbors = textureGather(colortex12, uv, 0);
        float gathered_distance = min(min(neighbors.x, neighbors.y), min(neighbors.z, neighbors.w));
        bool smoke_sees_front = point_distance > 0.0 && particle_distance > point_distance;
        bool composite_sample_sees_front = gathered_distance < particle_distance;
        vec3 state = vec3(0.05, 0.7, 0.12); // neither
        if (smoke_sees_front) {
            state = composite_sample_sees_front ? vec3(1.0, 0.55, 0.03) : vec3(1.0, 0.03, 0.03);
        } else if (composite_sample_sees_front) {
            state = gathered_distance <= 0.0 ? vec3(0.8, 0.03, 0.8) : vec3(0.03, 0.35, 1.0);
        }
        return vec4(state * color.a, color.a);
    }
#endif
#endif
    return color * slo_particle_cloud_transmission(screen_uv, particle_distance);
}
