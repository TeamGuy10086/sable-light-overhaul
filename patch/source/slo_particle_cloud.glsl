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
