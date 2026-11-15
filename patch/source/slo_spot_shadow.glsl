// R16_UNORM radial distance, zero means no hit within the map range.
// Compare BEFORE filtering. Interpolating depths would invent occluders between surfaces.
float slo_spot_compare(float stored, float distanceToLamp, float range) {
    float code = floor(stored * 65535.0 + 0.5);
    if (code < 0.5) return 1.0;
    float hit = (code - 1.0) / 65534.0 * range;
    return 1.0 - smoothstep(hit + 0.04, hit + 0.12, distanceToLamp);
}

float slo_spot_visibility(vec3 p, vec4 rightSlot, vec4 upRange, vec4 offsetTan, float aperture) {
    vec3 mapPoint = p + offsetTan.xyz;
    vec3 mapAxis = cross(upRange.xyz, rightSlot.xyz);
    float axial = dot(mapPoint, mapAxis);
    if (axial <= 0.0001) return 0.0;
    vec2 uv = vec2(dot(mapPoint, rightSlot.xyz), dot(mapPoint, upRange.xyz))
        / max(aperture + axial * offsetTan.w, 0.0001);
    if (any(greaterThan(abs(uv), vec2(1.0)))) return 0.0;
    vec2 grid = (uv * 0.5 + 0.5) * float(SLO_SPOT_SHADOW_RAYS) - 0.5;
    ivec2 base = ivec2(floor(grid));
    vec2 f = fract(grid);
    int row = int(rightSlot.w - 0.5) * SLO_SPOT_SHADOW_RAYS;
    float result = 0.0;
    float distanceToLamp = length(mapPoint);
    for (int y=0; y<2; ++y) for (int x=0; x<2; ++x) {
        ivec2 cell = clamp(base + ivec2(x,y), ivec2(0), ivec2(SLO_SPOT_SHADOW_RAYS-1));
        cell.y += row; // clamp locally: never bleed into the neighbouring lamp's rows
        float stored = texelFetch(sloSpotShadow, cell, 0).r;
        float weight = (x==0 ? 1.0-f.x : f.x) * (y==0 ? 1.0-f.y : f.y);
        result += weight * slo_spot_compare(stored,distanceToLamp,upRange.w);
    }
    return result;
}
