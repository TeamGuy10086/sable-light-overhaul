// Same aperture / anamorphic glare as a spotlight, but omnidirectional and with a pixel-sized core.
// Inactive entries cost one uniform branch; there is no volume march or shadow-map allocation.
uniform float slo_flareGlowShrinkDistance;
vec3 slo_point_glow(vec4 source, vec4 tint, vec4 style, vec2 uv, vec2 resolution) {
    if (style.w <= 0.0 || (style.x <= 0.0 && style.y <= 0.0) || source.w <= 0.0 || tint.w <= 0.0) return vec3(0.0);
    float distanceToSource = length(source.xyz);
    vec4 clip = gbufferProjection * vec4(mat3(gbufferModelView) * source.xyz, 1.0);
    if (clip.w <= 0.001) return vec3(0.0);
    vec3 projected = clip.xyz / clip.w * 0.5 + 0.5;
    if (any(lessThan(projected.xy, vec2(0.0))) || any(greaterThan(projected.xy, vec2(1.0)))) return vec3(0.0);
    float visibility = slo_glow_visibility(source.xyz, projected.xy, 0.15);
    if (visibility <= 0.0) return vec3(0.0);
    vec2 d = (uv - projected.xy) * vec2(resolution.x / max(resolution.y,1.0),1.0);
    float r = length(d);
    float aperture = max(source.w / max(distanceToSource,0.5), 0.75/max(resolution.y,1.0));
    float ringScale = max(style.x,0.05);
    float core = exp(-r*r/(aperture*aperture*0.60*ringScale*ringScale));
    // Beyond the configured threshold the optical halo has a bounded world extent and shrinks with distance.
    // Only the burning core keeps a subpixel floor; that floor must never inflate the halo/streak.
    float farScale = min(1.0, max(slo_flareGlowShrinkDistance,1.0) / max(distanceToSource, 0.5));
    float opticalAperture = min(source.w / max(distanceToSource,0.5),0.035);
    float sy = (0.0045*farScale + opticalAperture*0.30)*max(style.z,0.05);
    float sx = min(0.34*farScale,0.055*farScale+opticalAperture*3.5)*max(style.y,0.05);
    float streak = exp(-d.y*d.y/(sy*sy))*exp(-d.x*d.x/(sx*sx*0.45));
    float line = exp(-d.y*d.y/(sy*sy*0.09))*exp(-d.x*d.x/(sx*sx*1.1));
    float halo = exp(-r/((0.011*farScale+opticalAperture*0.55)*ringScale));
    float wide = exp(-r/((0.050*farScale+opticalAperture*1.9)*ringScale));
    return tint.rgb*tint.w*visibility*((style.x>0.0?core+0.55*halo+0.20*wide:0.0)+(style.y>0.0?0.60*line+0.30*streak:0.0));
}

// Existing flares retain exactly their original appearance.
vec3 slo_flare_glow(vec4 source, vec4 tint, vec2 uv, vec2 resolution) {
    return slo_point_glow(source,tint,vec4(1.0),uv,resolution);
}
