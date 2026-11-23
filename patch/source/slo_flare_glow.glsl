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

// Zero with older mods: existing signal/illumination flares keep their original path.
uniform int sloMissileGlowCount;
// Missile exhaust is a small source, not a searchlight lens. Separate the core from optical haze
// so clustered halos saturate locally without dimming distant unrelated missiles or their cores.
void slo_missile_glow(vec4 source, vec4 tint, vec2 uv, vec2 resolution,
                      inout vec3 cores, inout vec3 halos) {
    if(source.w<=0.0 || tint.w<=0.0) return;
    vec4 clip=gbufferProjection*vec4(mat3(gbufferModelView)*source.xyz,1.0);
    if(clip.w<=0.001) return;
    vec2 center=clip.xy/clip.w*.5+.5;
    if(any(lessThan(center,vec2(0.0))) || any(greaterThan(center,vec2(1.0)))) return;
    float visibility=slo_glow_visibility(source.xyz,center,0.15);
    if(visibility<=0.0) return;
    float pixel=1.0/max(resolution.y,1.0);
    float angular=source.w/max(length(source.xyz),.5);
    // Half the ORIGINAL flare's Gaussian radius, not half the compact 655 profile.
    // Keep the size2 pixel floor where it already exceeds that target.
    float oldAperture=max(angular,.75*pixel);
    float coreSize=max(2.0*max(.65*pixel,min(.003,angular*.12)),.5*sqrt(.60)*oldAperture);
    float farScale=min(1.0,max(slo_flareGlowShrinkDistance,1.0)/max(length(source.xyz),.5));
    float opticalAperture=min(angular,.035);
    // Quarter-size diffuse halo. The halo has no pixel floor and continues shrinking
    // beyond the existing distance threshold; only the separate core retains its floor.
    float haloSize=.125*(.011*farScale+opticalAperture*.55);
    float wideSize=.125*(.050*farScale+opticalAperture*1.9);
    float sy=.5*(.0045*farScale+opticalAperture*.30);
    float sx=.5*min(.34*farScale,.055*farScale+opticalAperture*3.5);
    vec2 delta=(uv-center)*vec2(resolution.x/max(resolution.y,1.0),1.0);
    float radius=length(delta);
    float core=exp(-dot(delta,delta)/(coreSize*coreSize));
    // Finite support prevents hundreds of imperceptible tails accumulating over the whole screen.
    float halo=exp(-radius/haloSize)*(1.0-smoothstep(2.0*haloSize,4.0*haloSize,radius));
    float wide=exp(-radius/wideSize)*(1.0-smoothstep(2.0*wideSize,4.0*wideSize,radius));
    float line=exp(-delta.y*delta.y/(sy*sy*.09))*exp(-delta.x*delta.x/(sx*sx*1.1));
    float streak=exp(-delta.y*delta.y/(sy*sy))*exp(-delta.x*delta.x/(sx*sx*.45));
    float starSupport=1.0-smoothstep(2.0,4.0,max(abs(delta.x)/sx,abs(delta.y)/sy));
    line*=starSupport;
    streak*=starSupport;
    vec3 energy=max(tint.rgb,vec3(0.0))*max(tint.w,0.0)*visibility;
    // The sharp star stays readable; only diffuse haze participates in the soft limit.
    cores+=energy*(1.6*core+.60*line);
    halos+=energy*(.55*halo+.20*wide+.30*streak);
}
vec3 slo_missile_halo_resolve(vec3 halos) {
    float peak=max(halos.r,max(halos.g,halos.b));
    return halos/(1.0+peak/.30); // hue-preserving, per-pixel soft limit; cores are not limited
}
