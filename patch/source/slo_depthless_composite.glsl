// Inputs are separate until the final OVER: never reconstruct a dim background by
// subtracting a very bright smoke layer from an already transformed HDR composite.
vec3 slo_depthless_over(vec3 background, vec4 layer) {
    return background * (1.0 - clamp(layer.a, 0.0, 1.0)) + layer.rgb;
}
