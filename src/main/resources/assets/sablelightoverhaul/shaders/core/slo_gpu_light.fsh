#version 150

#moj_import <sablelightoverhaul:slo_native_depth.glsl>

uniform sampler2D DepthSampler;
uniform vec2 uScreenSize;
uniform vec3 uCameraPos;
uniform mat4 uInvProjMat;
uniform mat4 uInvViewMat;
uniform mat4 uInvViewProj;
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
uniform int uNativeOnly;
uniform int uProbeLight;
uniform vec2 uProbePixel;
uniform int uProbeMainDepth;
uniform int uLodBlitZeroToOne;
uniform mat4 uLodBlitViewProj;
uniform float uSourceTint[96];

out vec4 fragColor;
// Debug readback only: q/profile/brightness/angular gain, before color mapping.
vec4 probeShape = vec4(0.0);

// Reconstruct the world-space position of the visible scene surface from the depth buffer.
vec3 reconstructWorld(vec2 uv, float depth) {
    vec3 ndc = vec3(uv * 2.0 - 1.0, depth * 2.0 - 1.0);
    vec4 view = uInvProjMat * vec4(ndc, 1.0);
    return (uInvViewMat * vec4(view.xyz / view.w, 1.0)).xyz;
}

float falloff(float q, float core, float middle, float tail) {
    if (q <= core) return 1.0;
    if (q >= 1.0) return 0.0;
    if (q <= middle) return 1.0 - (1.0 - tail) * (q - core) / (middle - core);
    return tail * (1.0 - q) / (1.0 - middle);
}

// Equivalent of AreaLight.at() for beams, directional (diamond) shapes and ellipsoids.
// Signal sphere opening in world space; zero/360 keeps legacy omnidirectional lighting.
float slo_signal_angle_gain(vec3 from_source, float angle) {
    float distance = length(from_source);
    if (angle <= 0.0 || angle >= 360.0 || distance < 0.0001) return 1.0;
    float half_angle = radians(angle * 0.5);
    return smoothstep(cos(half_angle), cos(half_angle * 0.9), -from_source.y / distance);
}

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
        // Spotlight: the cross-section is the angle from the axis on its own curve, and a beam whose range
        // is packed negative -- a hand torch -- multiplies in a second, separate ramp over the distance it
        // actually travels. Both used to be one profile through max(angle, along/range): that coupled how
        // wide the cone read to how far it reached, and it measured the length on the depth along the axis,
        // so a point on the cone's rim was dimmed as if it sat on the centre line. A rim point is
        // 1/cos(angle) further from the apex, which is the ratio used here. This mirrors AreaLight.at() and
        // the pack's slo_slot, all three of which have to agree.
        float range = abs(axial);
        if (along <= 0.0 || along > range) return 0.0;
        float side = length(v - dir * along);
        float angle = side / (along * lateral);
        float value = falloff(angle, core, middle, tail);
        if (uvec.x < 0.0) {
            value *= falloff(along / range, 0.75, 1.0, 0.0);
        } else if (axial < 0.0) {
            // For a beam the first lane of `uvec` carries the ramp's knee, i.e. AreaLight.distanceKnee().
            float knee = clamp(uvec.x, 0.0, 0.95);
            if (knee > 0.0) {
                value *= falloff(sqrt(along * along + side * side) / range, 0.0, knee, knee);
            }
        }
        probeShape = vec4(angle, value, brightness, 1.0);
        return brightness * value;
    } else if (flags >= 0.5) {
        // directional (autocannon/big-cannon muzzle light, diamond or ellipsoidal cross-section)
        if (along < -rearCutoff) return 0.0;
        // Keep conditional selection separate from arithmetic. Runtime shader rewriting
        // can drop expression parentheses; the rear branch then becomes the radius itself.
        float axialRadius = rear;
        if (along >= 0.0) axialRadius = axial;
        float a = abs(along) / axialRadius;
        float u = abs(dot(v, uvec)) / lateral;
        vec3 vvec = cross(dir, uvec);
        float vv = abs(dot(v, vvec)) / vertical;
        q = sqrt(a * a + u * u + vv * vv);
        if (flags >= 1.5) q = a + u + vv;
    } else {
        // ellipsoid
        float side = length(v - dir * along);
        q = sqrt(along * along / (axial * axial) + side * side / (lateral * lateral));
    }
    float profile = falloff(q, core, middle, tail);
    float angular = 1.0;
    if (flags < 0.5) angular = slo_signal_angle_gain(v, uvec.x);
    probeShape = vec4(q, profile, brightness, angular);
    return brightness * profile * angular;
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
    vec3 toCamera = (uNativeOnly == 1 ? vec3(0.0) : uCameraPos) - world;
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
    vec2 pixel = uProbeLight < 0 ? gl_FragCoord.xy : uProbePixel;
    // Which lights can reach this fragment: the tile's mask when tiling is on, everything otherwise.
    uint mask = 0xFFFFFFFFu;
    if (uUseTiles == 1) {
        ivec2 tile = ivec2(floor(pixel / uTileSize));
        mask = texelFetch(uTileMasks, tile, 0).x;
        if (mask == 0u) discard;
    }
    vec2 uv = pixel / uScreenSize;
    float sceneDepth = texelFetch(DepthSampler, ivec2(pixel), 0).r;
    vec4 lodPoint = uProbeMainDepth == 0 ? slo_native_lod_point(uv) : vec4(0.0);
    bool useLod = false;
    vec4 lodClip = vec4(0.0);
    if (abs(lodPoint.w) > 1e-8) {
        vec4 clip = uViewProj * lodPoint;
        if (clip.w / lodPoint.w > 0.0) {
            float ndcDepth = clip.z / clip.w;
            float lodDepth = ndcDepth * 0.5 + 0.5;
            // Voxy can put its own reprojected depth in the vanilla attachment. Preserve
            // the precise LOD position when that value matches, including the far clamp.
            vec4 blitClip = uLodBlitViewProj * lodPoint;
            float blitDepth = min(blitClip.z / blitClip.w, 1.0 - 2.0 / 16777215.0);
            if (uLodBlitZeroToOne == 0) blitDepth = blitDepth * 0.5 + 0.5;
            useLod = sceneDepth >= 1.0 || lodDepth <= sceneDepth + 0.00000015
                    || (blitClip.w / lodPoint.w > 0.0 && abs(sceneDepth - blitDepth) <= 0.000001);
            if (useLod) lodClip = clip;
        }
    }
    if (!(sceneDepth > 0.0 && sceneDepth < 1.0) && !useLod) {
        if (uProbeMainDepth == 2) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        discard;
    }
    vec3 world;
    if (uNativeOnly == 1 || useLod) {
        // Use the same pixel ray used to project the light bounds. The LOD projection
        // supplies depth only, so a different viewport projection cannot shift the receiver sideways.
        vec2 ndc = uv * 2.0 - 1.0;
        if (useLod) {
            lodClip.xy = ndc * lodClip.w;
            world = (uInvViewProj * lodClip).xyz / lodPoint.w;
        } else {
            vec4 point = uInvViewProj * vec4(ndc, sceneDepth * 2.0 - 1.0, 1.0);
            world = point.xyz / point.w;
        }
    } else {
        world = reconstructWorld(uv, sceneDepth);
    }
    if (uNativeOnly == 0) world += uCameraPos;
    if (uProbeMainDepth == 3) {
        fragColor = vec4(world, 1.0);
        return;
    }
    vec3 normal = uNativeOnly == 0 ? surfaceNormal(world) : vec3(0.0);
    float energy = 0.0;
    vec3 colored = vec3(0.0);
    for (int i = 0; i < 32; i++) {
        if (i >= uLightCount) break;
        if (uProbeLight >= 0 && i != uProbeLight) continue;
        if (uUseTiles == 1 && (mask & (1u << i)) == 0u) continue;
        // The shape first: a light that cannot reach this fragment is rejected inside areaLight without
        // ever paying for the length, the normalise and the dot product below. The distance used to be
        // computed before this test, for every light in range of the tile or not.
        float lit = areaLight(i, world);
        if (uProbeMainDepth == 4) {
            fragColor = probeShape;
            return;
        }
        if (uProbeMainDepth == 2) {
            int b = i * 20;
            fragColor = vec4(clamp(lit / 15.0, 0.0, 1.0),
                    clamp(uLightData[b + 3] / 15.0, 0.0, 1.0), 1.0, 1.0);
            return;
        }
        if (!(lit > 0.0)) continue;
        if (uNativeOnly == 1) {
            // SLO Only has depth but no material normal buffer. Use spatial attenuation
            // directly so noisy depth derivatives cannot modulate the light.
            int offset = i * 3;
            vec3 tint = vec3(uSourceTint[offset], uSourceTint[offset + 1], uSourceTint[offset + 2]);
            colored = max(colored, tint * pow(lit / 15.0, uFalloff));
            continue;
        }
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

    // energy is a block-light-like level (0..15). Shape it, then add it to the rendered scene.
    vec3 add = colored;
    if (uNativeOnly == 0) add = uLightColor * pow(energy / 15.0, uFalloff);
    add *= uIntensity;
    // Soft knee: compress only the bright end so the core stays a warm colour instead of
    // clipping to a flat saturated blob.
    float peak = max(add.r, max(add.g, add.b));
    if (peak <= 0.0001) discard;
    if (uNativeOnly == 1) {
        // C = scene + (1-scene)*add. Keep at least 25% of scene contrast even
        // at extreme intensity; no hard clipping to flat white patches.
        add = 0.75 * (vec3(1.0) - exp(-2.0 * max(add, vec3(0.0))));
    } else {
        add /= 1.0 + max(peak - 0.75, 0.0);
    }
    fragColor = vec4(add, 0.0);
}
