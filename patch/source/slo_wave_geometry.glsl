// A camera-relative WORLD-space sphere. Return only a real, forward surface hit.
// An eye inside the sphere sees the exit, never the negative entry behind the eye.
float slo_wave_hit(vec3 ray, vec3 center, float radius) {
    float b = dot(ray, center);
    vec3 perpendicular = center - ray * b;
    float d = radius * radius - dot(perpendicular, perpendicular);
    if (d <= 0.0) return -1.0;
    float root = sqrt(d), entry = b - root, exit_t = b + root;
    return entry > 0.001 ? entry : (exit_t > 0.001 ? exit_t : -1.0);
}

// xy: displacement in pixels; z: condensation; w: stem scattering.
// pixel_angle is vertical radians/pixel from the actual projection, not world Y.
vec4 slo_wave_profile(vec3 ray, float scene_t, vec4 sphere, vec4 shape,
                      mat3 world_to_view, float pixel_angle, out float hit_t) {
    hit_t = -1.0;
    if (sphere.w <= 0.0 || shape.y <= 0.0) return vec4(0.0);
    hit_t = slo_wave_hit(ray, sphere.xyz, sphere.w);
    if (hit_t < 0.0 || hit_t >= scene_t) return vec4(0.0);
    float b = dot(ray, sphere.xyz);
    float radial = length(sphere.xyz - ray * b) / sphere.w;
    float edge = max(0.0, 1.0 - radial);
    float pixel_width = max(0.00001, hit_t * pixel_angle / sphere.w);
    float width = max(clamp(shape.z, 0.002, 1.0) * 0.50, pixel_width * 8.0);
    float feather = max(width * 0.75, pixel_width * 4.0);
    float stem = smoothstep(0.0, feather, edge)
        * (1.0 - smoothstep(width * 0.45, width, edge));
    float chord = sqrt(max(0.0, 1.0 - radial * radial));
    float progress = clamp(shape.w, 0.0, 1.0);
    float vapour = shape.y * 2.60 * mix(0.55, 1.0, chord)
        * (1.0 - smoothstep(0.30, 0.50, progress))
        * (1.0 - smoothstep(0.04, 0.42, progress) * chord)
        * smoothstep(0.0, max(0.025, pixel_width * 2.0), edge);
    float occlusion = smoothstep(0.0, max(0.05, sphere.w * 0.01), scene_t - hit_t);
    vec3 normal = normalize(ray * hit_t - sphere.xyz);
    vec3 tangent = normal - ray * dot(normal, ray);
    // Rotate into the actual camera axes; no cross(ray,worldUp) pole or roll ambiguity.
    vec2 push = (world_to_view * tangent).xy * (-32.0 * sqrt(clamp(shape.y, 0.0, 1.0)) * stem);
    return vec4(push, vapour, shape.y * stem) * occlusion;
}
