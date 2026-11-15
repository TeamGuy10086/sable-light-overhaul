// RT13 has coverage and premultiplied radiance, but no per-layer front depth.
// Preserve this foreground layer while changing the underlying scene with a wave.
// Conservative approximation: a wave nearer than a depthless layer is attenuated too.
vec3 slo_wave_over_translucency(vec3 before_wave,vec3 after_wave,float coverage){
    return mix(before_wave,after_wave,1.0-clamp(coverage,0.0,1.0));
}
