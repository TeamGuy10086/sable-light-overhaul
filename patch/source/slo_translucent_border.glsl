// RT13 can contain depth-tested objects which never write a separate front depth.
// Background/front depths can also belong to unrelated surfaces behind the smoke.
// Every foreground layer already receives its own fog in the geometry program.
float slo_translucent_border_weight(float weight, float front, float back,
        float lod_front, float lod_back, float coverage) {
    if (coverage < 0.0) return 1.0;
    if (coverage > 0.00001) return 1.0;
    return weight;
}

// Preserve the overlay contribution while fogging its background exactly as the
// neighbouring pixels. Simply skipping the whole mix would punch holes in fog.
vec3 slo_translucent_border_blend(vec3 original, vec3 background, vec3 composite,
        float weight, float layer_weight, float coverage) {
    if (layer_weight > weight) {
        return composite + (original-background)
            * (1.0-clamp(abs(coverage),0.0,1.0)) * (1.0-weight);
    }
    return mix(original,composite,weight);
}
