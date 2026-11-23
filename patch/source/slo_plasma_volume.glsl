// Camera-relative origin and unit axis. Integrate a parabolic radial density between two flat caps.
uniform float sloPlasmaVolumeMix;

float slo_plasma_envelope(float axial, vec2 phase) {
    return 0.65 + 0.23 * sin(axial * 0.85 + phase.x) + 0.12 * sin(axial * 1.71 + phase.y);
}

// Two long waves advect the contour; slow phase modulation varies the spacing.
float slo_plasma_edge_scale(float axial, vec3 radial, vec2 phase) {
    float side = dot(radial, vec3(0.63, -0.74, 0.48));
    float slow = sin(phase.y + axial * 0.065 + side * 0.16);
    float longWave = sin(axial * 0.20 + phase.x + 0.65 * slow + side * 0.19);
    float shoulder = sin(axial * 0.33 + phase.x + phase.y + side * 0.27);
    return 0.86 + 0.05 * longWave + (0.0175 + 0.006 * slow) * shoulder;
}

// Limit the apparent width near the camera as well as the physical radius in world space.
// Compare a visible point with the matching point on the finite beam axis.
float slo_plasma_axis_mask(vec3 ray, vec3 axisPoint, float edgeScale) {
    float forward = dot(ray, axisPoint);
    if (!(forward > 0.0)) return 0.0;
    vec3 perpendicular = axisPoint - ray * forward;
    float slope2 = dot(perpendicular, perpendicular) / (forward * forward);
    // Expand the near-camera contour along with the physical radius.
    return 1.0 - smoothstep(0.03109, 0.07180, slope2 / (edgeScale * edgeScale));
}

vec4 slo_plasma_light(vec3 ray, vec3 surface, vec4 source, vec4 beam, vec4 phase, float volumeGain) {
    vec3 axis = beam.xyz;
    if (!(source.w > 0.0 && beam.w > 0.0 && dot(axis, axis) > 0.99)) return vec4(0.0);
    float radius = beam.w * 0.46;
    vec3 end = source.xyz + source.w * axis;
    if (max(dot(ray, source.xyz), dot(ray, end)) <= 0.0) return vec4(0.0);
    vec3 offset = -source.xyz;
    float z = dot(offset, axis), dz = dot(ray, axis);
    vec3 p = offset - axis * z, v = ray - axis * dz;
    float vv = dot(v, v);
    // Angular masking confines near-eye light without a blind sphere around the emitter.
    float lo = 0.02, hi = min(length(surface), length(source.xyz) + source.w + radius);
    if (abs(dz) > 1e-6) {
        vec2 caps = vec2(-z, source.w - z) / dz;
        lo = max(lo, min(caps.x, caps.y));
        hi = min(hi, max(caps.x, caps.y));
    } else if (z < 0.0 || z > source.w) {
        hi = 0.0;
    }
    float rr = radius * radius;
    if (vv > 1e-8) {
        float nearest = -dot(p, v) / vv;
        vec3 radial = p + nearest * v;
        float chord2 = (rr - dot(radial, radial)) / vv;
        if (chord2 > 0.0) {
            float halfChord = sqrt(chord2);
            lo = max(lo, nearest - halfChord);
            hi = min(hi, nearest + halfChord);
        } else hi = lo;
    }
    float span = max(0.0, hi - lo);
    vec3 middle = p + v * (lo + 0.5 * span);
    float integrated = span * max(0.0, 1.0 - (dot(middle, middle) + vv * span * span / 12.0) / rr);
    if (!(integrated > 0.0)) return vec4(0.0);
    float midDistance = lo + 0.5 * span;
    float axialMid = clamp(z + dz * midDistance, 0.0, source.w);
    float edgeScale = slo_plasma_edge_scale(axialMid, middle, 18.0 * phase.zw);
    float edgeFade = 1.0 - smoothstep(edgeScale - 0.16, edgeScale, length(middle) / radius);
    float eyeFade = edgeFade * smoothstep(0.02, 0.35, midDistance)
                  * slo_plasma_axis_mask(ray, source.xyz + axis * axialMid, edgeScale);
    float density = volumeGain * 0.15 * min(integrated, 8.0) * eyeFade
                  * slo_plasma_envelope(z + dz * midDistance, 18.0 * phase.xy);
    density *= max(0.0, 1.0 - dot(middle, middle) / rr);
    float scatter = 1.0 - exp(-density);
    vec3 radiance = vec3(1.0, 0.017, 0.006) * (1.6 * scatter);
    // Side-on glare is additive only and confined to rays that see the beam interior.
    // Test the axis itself against scene depth; the near skin can peek around a wall.
    float axisDepth = dot(source.xyz + axis * axialMid, ray);
    float visible = smoothstep(0.0, 0.20, length(surface) - axisDepth);
    float side = smoothstep(0.20, 0.85, vv);
    float radial = length(middle) / radius;
    // Keep a faint inner shoulder, with the strongest glare outside the white core.
    float shoulder = mix(0.12, 1.0, smoothstep(0.29, 0.58, radial))
                   * (1.0 - smoothstep(0.62, 0.99, radial));
    float glare = side * visible * shoulder * eyeFade * smoothstep(0.0, 0.8, span);
    float crest = 0.95 + 0.05 * sin(axialMid * 0.08 + 144.0 * phase.z);
    radiance += vec3(2.4, 0.115, 0.023) * glare * crest;
    return vec4(radiance, scatter);
}
