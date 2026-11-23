// Photon lens response. Work in screen-height units so the aperture stays round.
vec3 slo_plasma_lens(vec4 source, vec4 beam, vec2 uv, float aspect, float sceneDepth) {
    vec3 a = source.xyz;
    vec3 b = a + beam.xyz * source.w;
    mat4 project = gbufferProjection;
    vec4 ca = project * vec4(mat3(gbufferModelView) * a, 1.0);
    vec4 cb = project * vec4(mat3(gbufferModelView) * b, 1.0);
    if (max(ca.w, cb.w) <= 0.05) return vec3(0.0);
    if (min(ca.w, cb.w) < 0.05) {
        float t = (0.05 - ca.w) / (cb.w - ca.w);
        if (ca.w < cb.w) { a = mix(a, b, t); ca = mix(ca, cb, t); }
        else { b = mix(a, b, t); cb = mix(ca, cb, t); }
    }
    vec2 scale = vec2(aspect, 1.0);
    vec2 start = 0.5 * ca.xy / ca.w * scale;
    vec2 span = 0.5 * cb.xy / cb.w * scale - start;
    vec2 pixel = (uv - 0.5) * scale;
    float invLen2 = 1.0 / max(dot(span, span), 1e-10);
    float t = clamp(dot(pixel - start, span) * invLen2, 0.0, 1.0);
    vec2 foot = start + t * span;
    float depth = 1.0 / mix(1.0 / ca.w, 1.0 / cb.w, t);
    // A 0.9-block bright core drives the angular width of both scattering lobes.
    float core = 0.45 * abs(gbufferProjection[1][1]) / depth;
    float width = min(5.5 * core, 0.19);
    float r = length(pixel - foot) / max(width, 1e-7);
    if (r >= 1.0 || sceneDepth + 1.0 < depth) return vec3(0.0);
    vec3 pos = mix(a, b, t * depth / cb.w);
    float visible = slo_glow_visibility(pos, foot / scale + 0.5, 0.9);
    float focus = clamp(-dot(start, span) * invLen2, 0.0, 1.0);
    float along = length((t - focus) * span) / max(width, 1e-7);
    float shoulder = exp(-18.0 * r * r);
    float veil = exp(-3.5 * r * r) * (1.0 - smoothstep(0.65, 1.0, r));
    return visible * (1.0 + 0.22 * exp(-along * along)) *
           (vec3(0.52, 0.053, 0.012) * shoulder + vec3(0.24, 0.044, 0.006) * veil);
}
