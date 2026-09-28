#version 150

uniform sampler2D DepthSampler;
uniform vec2 uScreenSize;
uniform vec3 uCameraPos;
uniform mat4 uInvProjMat;
uniform mat4 uInvViewMat;
uniform int uLightCount;
uniform vec3 uLightColor;
uniform float uIntensity;
uniform float uFalloff;
uniform float uNormalInfluence;
uniform float uLightData[640];
// Per-tile light lists: bit i is set when light i can reach this screen tile. Without them every
// fragment walks every active light, which is what made the cost rise with the light count on top of
// the area each light covers.
uniform int uUseTiles;
uniform usampler2D uTileMasks;
uniform float uTileSize;

out vec4 fragColor;

// Reconstruct the world-space position of the visible scene surface from the depth buffer.
vec3 reconstructWorld(vec2 fragCoord) {
    vec2 uv = fragCoord / uScreenSize;
    float depth = texture(DepthSampler, uv).r;
    vec3 ndc = vec3(uv * 2.0 - 1.0, depth * 2.0 - 1.0);
    vec4 view = uInvProjMat * vec4(ndc, 1.0);
    view.xyz /= view.w;
    return uCameraPos + (uInvViewMat * view).xyz;
}

float falloff(float q, float core, float middle, float tail) {
    if (q <= core) return 1.0;
    if (q >= 1.0) return 0.0;
    if (q <= middle) return 1.0 - (1.0 - tail) * (q - core) / (middle - core);
    return tail * (1.0 - q) / (1.0 - middle);
}

// Equivalent of AreaLight.at() for beams, directional (diamond) shapes and ellipsoids.
float areaLight(int i, vec3 p) {
    int b = i * 20;
    vec3 pos = vec3(uLightData[b], uLightData[b + 1], uLightData[b + 2]);
    float brightness = uLightData[b + 3];
    vec3 dir = vec3(uLightData[b + 4], uLightData[b + 5], uLightData[b + 6]);
    float axial = uLightData[b + 7];
    float lateral = uLightData[b + 8];
    float core = uLightData[b + 9];
    float middle = uLightData[b + 10];
    float tail = uLightData[b + 11];
    float rear = uLightData[b + 12];
    float vertical = uLightData[b + 13];
    float rearCutoff = uLightData[b + 14];
    float flags = uLightData[b + 15];
    vec3 uvec = vec3(uLightData[b + 16], uLightData[b + 17], uLightData[b + 18]);

    vec3 v = p - pos;
    float along = dot(v, dir);
    float q;
    if (flags >= 7.5) {
        // Spotlight: angular profile with a hard range cut-off. A beam whose range is packed negative is
        // one that also fades along its length -- a hand torch, as opposed to a ship's searchlight, which
        // is meant to be as bright at the far end as at the near end.
        float range = abs(axial);
        if (along <= 0.0 || along > range) return 0.0;
        float side = length(v - dir * along);
        float angle = side / (along * lateral);
        q = axial < 0.0 ? max(angle, along / range) : angle;
    } else if (flags >= 0.5) {
        // directional (autocannon/big-cannon muzzle light, diamond or ellipsoidal cross-section)
        if (along < -rearCutoff) return 0.0;
        float a = abs(along) / (along >= 0.0 ? axial : rear);
        float u = abs(dot(v, uvec)) / lateral;
        vec3 vvec = cross(dir, uvec);
        float vv = abs(dot(v, vvec)) / vertical;
        q = flags >= 1.5 ? (a + u + vv) : sqrt(a * a + u * u + vv * vv);
    } else {
        // ellipsoid
        float side = length(v - dir * along);
        q = sqrt(along * along / (axial * axial) + side * side / (lateral * lateral));
    }
    return brightness * falloff(q, core, middle, tail);
}

// Light source position for entry i (same layout areaLight() reads).
vec3 lightPosition(int i) {
    int b = i * 20;
    return vec3(uLightData[b], uLightData[b + 1], uLightData[b + 2]);
}

// Surface normal estimated from the depth buffer's screen-space derivatives.
//
// No normal buffer is available here, so the tangent frame is taken from the reconstructed world
// position. A footprint that is degenerate or spans a depth discontinuity (a silhouette edge) would
// yield garbage, so it falls back to a camera-facing normal; there the light degrades to the flat
// additive behaviour instead of producing artifacts.
vec3 surfaceNormal(vec3 world) {
    vec3 toCamera = uCameraPos - world;
    float distance = length(toCamera);
    vec3 facing = distance > 1.0E-4 ? toCamera / distance : vec3(0.0, 1.0, 0.0);
    vec3 dx = dFdx(world);
    vec3 dy = dFdy(world);
    vec3 n = cross(dx, dy);
    float lengthSquared = dot(n, n);
    float limit = 0.5 + 0.05 * distance;
    if (lengthSquared < 1.0E-12 || dot(dx, dx) > limit * limit || dot(dy, dy) > limit * limit) {
        return facing;
    }
    n *= inversesqrt(lengthSquared);
    // Only opaque front faces are visible here, so the normal always faces the camera.
    return dot(n, facing) < 0.0 ? -n : n;
}

void main() {
    vec3 world = reconstructWorld(gl_FragCoord.xy);
    vec3 normal = surfaceNormal(world);
    // Which lights can reach this fragment: the tile's mask when tiling is on, everything otherwise.
    uint mask = 0xFFFFFFFFu;
    if (uUseTiles == 1) {
        ivec2 tile = ivec2(floor(gl_FragCoord.xy / uTileSize));
        mask = texelFetch(uTileMasks, tile, 0).x;
    }
    float energy = 0.0;
    for (int i = 0; i < 32; i++) {
        if (i >= uLightCount) break;
        if (uUseTiles == 1 && (mask & (1u << i)) == 0u) continue;
        // The shape first: a light that cannot reach this fragment is rejected inside areaLight without
        // ever paying for the length, the normalise and the dot product below. The distance used to be
        // computed before this test, for every light in range of the tile or not.
        float lit = areaLight(i, world);
        if (lit <= 0.0) continue;
        vec3 toLight = lightPosition(i) - world;
        float distance = length(toLight);
        // A slightly wrapped terminator softens the falloff, but a surface facing away from the
        // source still reaches exactly zero, which is what rejects back faces and lights behind
        // geometry (the most visible form of "light through walls").
        float wrapped = distance > 1.0E-4
                ? clamp((dot(normal, toLight / distance) + 0.2) / 1.2, 0.0, 1.0)
                : 1.0;
        energy = max(energy, lit * mix(1.0, wrapped, uNormalInfluence));
    }
    if (energy <= 0.0001) discard;

    // energy is a block-light-like level (0..15). Shape it, then add it to the rendered scene.
    float level = pow(energy / 15.0, uFalloff) * uIntensity;
    vec3 add = uLightColor * level;
    // Soft knee: compress only the bright end so the core stays a warm colour instead of
    // clipping to a flat saturated blob.
    float peak = max(add.r, max(add.g, add.b));
    add /= 1.0 + max(peak - 0.75, 0.0);
    fragColor = vec4(add, 1.0);
}
