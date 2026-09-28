// Contract v1. xyz = world-axis camera-relative position, w = normalized flash intensity.
// Provided by the experimental Iris bridge, zero when no flash. Radius is 16 blocks.
uniform vec4 cbcMuzzleLight0;
uniform vec4 cbcMuzzleLight1;
uniform vec4 cbcMuzzleLight2;
uniform vec4 cbcMuzzleLight3;
uniform vec4 cbcMuzzleLight4;
uniform vec4 cbcMuzzleLight5;
uniform vec4 cbcMuzzleLight6;
uniform vec4 cbcMuzzleLight7;
float cbcContribution(vec3 p, vec4 light) {
    float falloff = max(0.0, 1.0 - length(p - light.xyz) / 16.0);
    return light.w * falloff * falloff;
}
vec3 cbcMuzzleLighting(vec3 cameraRelativeWorldPosition) {
    vec3 p = cameraRelativeWorldPosition;
    float energy = cbcContribution(p, cbcMuzzleLight0) + cbcContribution(p, cbcMuzzleLight1)
        + cbcContribution(p, cbcMuzzleLight2) + cbcContribution(p, cbcMuzzleLight3)
        + cbcContribution(p, cbcMuzzleLight4) + cbcContribution(p, cbcMuzzleLight5)
        + cbcContribution(p, cbcMuzzleLight6) + cbcContribution(p, cbcMuzzleLight7);
    return vec3(1.0, 0.48, 0.12) * min(energy, 4.0);
}
