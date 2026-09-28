#if !defined INCLUDE_LIGHTING_DIFFUSE_LIGHTING
#define INCLUDE_LIGHTING_DIFFUSE_LIGHTING

#include "/include/lighting/bsdf.glsl"
#include "/include/lighting/colors/blocklight_color.glsl"

#define INCLUDE_LIGHTING_CBC_MUZZLE_LIGHT

// ===========================================================================
//  CBC Muzzle Light bridge -- Photon patch
//
//  The "CBC Muzzle Light" mod supplies these uniforms every frame through
//  Iris' fixed-input uniform mechanism. With the mod absent every value is
//  zero and this file costs a few comparisons per pixel.
//
//  Required on the mod side:  lightingMode = "Advanced"
//  (the mod then stops drawing its screen-space overlay, so the light comes
//  from Photon's own lighting pass instead of being added on top of it)
//
//  Coordinates: cbcMuzzleLight*.xyz are scene space (camera-relative, world
//  axes), the same space as this function's scene_pos / normal arguments, so
//  N.L is a plain dot product and the shadow ray needs no conversion.
// ===========================================================================

#define CBC_MUZZLE_LIGHTS 16

// Shadow tuning. Which lights cast, how many taps they get and how soft the edge is all arrive from
// the mod at runtime (the three uniforms below), because those are the player's calls. What stays here
// is the compile-time bound of the tap loop and the geometry of the march, which a pack author retunes.
#define CBC_MUZZLE_SHADOW_STEPS_MAX 16   // compile-time bound of the tap loop; the mod picks 1..this
#define CBC_MUZZLE_SHADOW_STEPS_NEAR 8.0 // ray length in blocks up to which the full tap count is spent
#define CBC_MUZZLE_SHADOW_START 0.1      // distance of the first tap from the lit surface, in blocks
#define CBC_MUZZLE_SHADOW_THICKNESS 4.0  // assumed occluder thickness, in blocks
#define CBC_MUZZLE_SHADOW_BIAS 0.05      // rejects self-hits on the lit surface
// How far short of the light the march stops. It has to clear half a block, and this is not a detail: a
// registered lamp sits at the centre of the block it replaced, and that block's own model is in the
// depth buffer, so a ray allowed to reach the centre would find the lamp's own block and shadow the
// light out completely. 0.35 used to be enough only because nothing that was allowed to cast ever sat
// inside a block.
#define CBC_MUZZLE_SHADOW_CLEARANCE 0.6

uniform vec3 cbcMuzzleColor;            // legacy global tint; per-light cbcMuzzleTintN replaced it
uniform float cbcMuzzleIntensity;       // overall scale
uniform float cbcMuzzleNormalInfluence; // 0 = flat, 1 = full N.L
uniform int cbcMuzzleShadowMask;        // bit i set = light i casts a shadow
uniform int cbcMuzzleShadowSteps;       // default tap ceiling; a light may override it (see axis.y)
// Per-light shadow behaviour travels in `axis.xy` of every NON-BEAM light, because a sphere's axis
// vector is otherwise unused (axis.w == 0 is what marks a light as a sphere). So one lamp can be soft
// while the next is hard, and one can be given a bigger tap budget than the rest, without a single extra
// uniform. A beam keeps axis as its direction and is always hard: the sharp cone is the whole point of
// a searchlight, and one sweeping across a floor looks wrong blurred.
//   axis.x = penumbra radius in blocks, 0 = hard edges
//   axis.y = this light's own tap ceiling, 0 = use cbcMuzzleShadowSteps

// xyz = scene-space position, w = light level 0..1 (already includes the
// mod's temporal decay, so it flickers like the muzzle flash does)
uniform vec4 cbcMuzzleLight0;
uniform vec4 cbcMuzzleLight1;
uniform vec4 cbcMuzzleLight2;
uniform vec4 cbcMuzzleLight3;
uniform vec4 cbcMuzzleLight4;
uniform vec4 cbcMuzzleLight5;
uniform vec4 cbcMuzzleLight6;
uniform vec4 cbcMuzzleLight7;
uniform vec4 cbcMuzzleLight8;
uniform vec4 cbcMuzzleLight9;
uniform vec4 cbcMuzzleLight10;
uniform vec4 cbcMuzzleLight11;
uniform vec4 cbcMuzzleLight12;
uniform vec4 cbcMuzzleLight13;
uniform vec4 cbcMuzzleLight14;
uniform vec4 cbcMuzzleLight15;

// x = radius (or the beam's range), y = core fraction, z = middle fraction, w = tail brightness
uniform vec4 cbcMuzzleShape0;
uniform vec4 cbcMuzzleShape1;
uniform vec4 cbcMuzzleShape2;
uniform vec4 cbcMuzzleShape3;
uniform vec4 cbcMuzzleShape4;
uniform vec4 cbcMuzzleShape5;
uniform vec4 cbcMuzzleShape6;
uniform vec4 cbcMuzzleShape7;
uniform vec4 cbcMuzzleShape8;
uniform vec4 cbcMuzzleShape9;
uniform vec4 cbcMuzzleShape10;
uniform vec4 cbcMuzzleShape11;
uniform vec4 cbcMuzzleShape12;
uniform vec4 cbcMuzzleShape13;
uniform vec4 cbcMuzzleShape14;
uniform vec4 cbcMuzzleShape15;

// xyz = the spotlight's beam axis (unit, scene space), w = tan(half angle).
// w = 0 means "not a beam": the light is an omnidirectional sphere of radius shape.x.
// A beam is angular only -- brightness depends on the angle to the axis, never on the
// distance along it -- so shape.x is a hard range cut-off instead of a falloff radius.
uniform vec4 cbcMuzzleAxis0;
uniform vec4 cbcMuzzleAxis1;
uniform vec4 cbcMuzzleAxis2;
uniform vec4 cbcMuzzleAxis3;
uniform vec4 cbcMuzzleAxis4;
uniform vec4 cbcMuzzleAxis5;
uniform vec4 cbcMuzzleAxis6;
uniform vec4 cbcMuzzleAxis7;
uniform vec4 cbcMuzzleAxis8;
uniform vec4 cbcMuzzleAxis9;
uniform vec4 cbcMuzzleAxis10;
uniform vec4 cbcMuzzleAxis11;
uniform vec4 cbcMuzzleAxis12;
uniform vec4 cbcMuzzleAxis13;
uniform vec4 cbcMuzzleAxis14;
uniform vec4 cbcMuzzleAxis15;

// rgb = this lamp's own light colour (linear). Muzzle flashes and tracers carry the
// mod's configured colour; a registered lamp or a spotlight carries its own tint.
uniform vec4 cbcMuzzleTint0;
uniform vec4 cbcMuzzleTint1;
uniform vec4 cbcMuzzleTint2;
uniform vec4 cbcMuzzleTint3;
uniform vec4 cbcMuzzleTint4;
uniform vec4 cbcMuzzleTint5;
uniform vec4 cbcMuzzleTint6;
uniform vec4 cbcMuzzleTint7;
uniform vec4 cbcMuzzleTint8;
uniform vec4 cbcMuzzleTint9;
uniform vec4 cbcMuzzleTint10;
uniform vec4 cbcMuzzleTint11;
uniform vec4 cbcMuzzleTint12;
uniform vec4 cbcMuzzleTint13;
uniform vec4 cbcMuzzleTint14;
uniform vec4 cbcMuzzleTint15;

// ---------------------------------------------------------------------------
//  Shadowing
//
//  Deferred pass only: that is the single program where the scene depth buffer
//  is bound to depthtex1 (the other programs that include this file use a
//  different depth sampler or run at a different stage). Everywhere else this
//  compiles out and the light stays unshadowed rather than breaking the pack.
//
//  The march is bounded to the light's own distance, so geometry *behind* the
//  flash can never shadow it. Terrain and physical bodies share this depth
//  buffer, so both occlude without any extra work.
// ---------------------------------------------------------------------------
#if defined PROGRAM_DEFERRED4

vec3 cbc_muzzle_view_to_screen(vec3 view_pos) {
    vec4 clip = gbufferProjection * vec4(view_pos, 1.0);
    return (clip.xyz / clip.w) * 0.5 + 0.5;
}

float cbc_muzzle_screen_to_view_depth(float depth) {
    float ndc_depth = depth * 2.0 - 1.0;
    vec2 zw = ndc_depth * gbufferProjectionInverse[2].zw +
        gbufferProjectionInverse[3].zw;
    return -zw.x / zw.y;
}

/**
 * Three decorrelated randoms in [0,1) for this pixel and this frame.
 *
 * <p>The pack's own noise texture is the source, the same one the tap jitter already uses, with the
 * frame counter mixed in through three incommensurable constants so that consecutive frames are not
 * merely shifted versions of each other. Photon's TAA is what turns the per-frame change into an
 * average, and that is exactly what the soft edges below rely on.
 */
vec3 cbc_muzzle_random() {
    ivec2 p = ivec2(gl_FragCoord.xy) & 511;
    vec3 seed = texelFetch(noisetex, p, 0).rgb;
    return fract(seed + float(frameCounter) * vec3(0.6180339887, 0.7548776662, 0.5698402910));
}

/**
 * The visibility of one light from one lit surface.
 *
 * <p>{@code softness} is the radius of the light as far as this ray is concerned, in blocks. With it at
 * zero the ray aims at the light's centre and the answer is a hard edge. Above it the aim point is
 * moved to a random spot on a sphere of that radius, which is one sample of an area light -- and one
 * sample per pixel per frame is what a soft shadow is made of once TAA has averaged a few frames. That
 * is why this costs nothing: the same single ray, aimed slightly differently, and the error it leaves
 * behind changes from a spatially correlated band (which no amount of averaging removes) into noise
 * (which averaging eats).
 */
float cbc_muzzle_visibility(vec3 scene_pos, vec3 light_scene_pos, float strength, float softness, int ceiling_in) {
    vec3 origin_view = (gbufferModelView * vec4(scene_pos, 1.0)).xyz;
    // The sample point on the light's own disc. Done in scene space, before the view transform, so the
    // offset is a real distance in blocks wherever the camera happens to be.
    if (softness > 0.0) {
        vec3 dir = cbc_muzzle_random() * 2.0 - 1.0;
        float len = max(length(dir), 1.0E-4);
        light_scene_pos += (dir / len) * softness;
    }
    vec3 light_view = (gbufferModelView * vec4(light_scene_pos, 1.0)).xyz;
    vec3 to_light = light_view - origin_view;
    float light_distance = length(to_light);
    if (light_distance < 0.5) return 1.0;
    vec3 dir_view = to_light / light_distance;

    vec2 screen_size = vec2(textureSize(depthtex1, 0));
    vec3 origin_screen = cbc_muzzle_view_to_screen(origin_view);
    if (clamp(origin_screen.xy, 0.0, 1.0) != origin_screen.xy) return 1.0;

    // Anchor the ray to this fragment's own screen position so any TAA jitter
    // cancels out instead of shifting the whole ray by a pixel.
    vec2 jitter_offset = gl_FragCoord.xy / screen_size - origin_screen.xy;

    // Stop short of the light so it cannot occlude itself. See CBC_MUZZLE_SHADOW_CLEARANCE for why this
    // is half a block rather than a token amount.
    float march_distance = light_distance - CBC_MUZZLE_SHADOW_CLEARANCE;
    if (march_distance <= CBC_MUZZLE_SHADOW_START) return 1.0;

    // How many taps this particular ray is worth. A shadow ray costs its taps at every lit pixel, so they
    // are spent where they can still change the answer: a bright light close by gets the whole ceiling, a
    // dim one further off gets less. Tapering on the ray length is nearly free in quality, because the
    // taps are spread geometrically -- a long ray already has its sparsest spacing at the far end, so the
    // taps dropped are the ones least likely to hit anything.
    int ceiling = clamp(ceiling_in, 1, CBC_MUZZLE_SHADOW_STEPS_MAX);
    float near_taper = clamp(CBC_MUZZLE_SHADOW_STEPS_NEAR / max(light_distance, CBC_MUZZLE_SHADOW_STEPS_NEAR), 0.5, 1.0);
    float wanted = float(ceiling) * clamp(strength, 0.0, 1.0) * near_taper;
    int taps = int(clamp(wanted, float(max(2, ceiling / 2)), float(ceiling)));

    // Per-pixel, per-frame jitter. Photon's TAA integrates it into a smooth edge.
    // Without it the discrete taps pop from frame to frame as the light moves.
    float dither = texelFetch(noisetex, ivec2(gl_FragCoord.xy) & 511, 0).b;
    dither = fract(dither + float(frameCounter) * 0.618033988749895);

    // Geometric distribution over [START, march_distance], the same shape Photon
    // uses for its own screen-space rays: dense near the lit surface where contact
    // detail matters, coarser towards the light. Uniform steps put the first tap
    // far from the surface and miss everything close to it.
    const float ratio = 2.0;
    float step_count = float(taps);
    float span = march_distance - CBC_MUZZLE_SHADOW_START;
    float normalise = 1.0 / (pow(ratio, step_count) - 1.0);
    float series = 1.0;

    for (int i = 0; i < CBC_MUZZLE_SHADOW_STEPS_MAX; i++) {
        if (i >= taps) break;
        series *= ratio;
        float fraction = (series - 1.0) * normalise;
        float travelled = CBC_MUZZLE_SHADOW_START + span * fraction;
        // Jitter within the local step so the tap pattern changes every frame.
        travelled += dither * span * normalise * series * 0.5;
        if (travelled >= march_distance) break;

        vec3 ray_view = origin_view + dir_view * travelled;
        vec3 ray_screen = cbc_muzzle_view_to_screen(ray_view);

        vec2 uv = ray_screen.xy + jitter_offset;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;

        float sampled = texelFetch(depthtex1, ivec2(uv * screen_size), 0).x;
        if (sampled <= 0.0 || sampled >= 1.0) continue;

        // A positive delta means the ray is behind the surface stored at this tap.
        float delta = cbc_muzzle_screen_to_view_depth(ray_screen.z) -
            cbc_muzzle_screen_to_view_depth(sampled);
        if (delta > CBC_MUZZLE_SHADOW_BIAS &&
            delta < 2.0 * CBC_MUZZLE_SHADOW_THICKNESS) {
            return 0.0;
        }
    }

    return 1.0;
}

#endif // PROGRAM_DEFERRED4

// Same three-segment spatial curve the mod uses on its own CPU and GPU paths.
float cbc_muzzle_falloff(float q, float core, float middle, float tail) {
    if (q <= core) return 1.0;
    if (q >= 1.0) return 0.0;
    if (q <= middle) return 1.0 - (1.0 - tail) * (q - core) / (middle - core);
    return tail * (1.0 - q) / (1.0 - middle);
}

vec3 get_cbc_muzzle_lighting(vec3 scene_pos, vec3 normal, float ao) {
    vec4 lights[CBC_MUZZLE_LIGHTS] = vec4[CBC_MUZZLE_LIGHTS](
        cbcMuzzleLight0, cbcMuzzleLight1, cbcMuzzleLight2, cbcMuzzleLight3,
        cbcMuzzleLight4, cbcMuzzleLight5, cbcMuzzleLight6, cbcMuzzleLight7,
        cbcMuzzleLight8, cbcMuzzleLight9, cbcMuzzleLight10, cbcMuzzleLight11,
        cbcMuzzleLight12, cbcMuzzleLight13, cbcMuzzleLight14, cbcMuzzleLight15
    );
    vec4 shapes[CBC_MUZZLE_LIGHTS] = vec4[CBC_MUZZLE_LIGHTS](
        cbcMuzzleShape0, cbcMuzzleShape1, cbcMuzzleShape2, cbcMuzzleShape3,
        cbcMuzzleShape4, cbcMuzzleShape5, cbcMuzzleShape6, cbcMuzzleShape7,
        cbcMuzzleShape8, cbcMuzzleShape9, cbcMuzzleShape10, cbcMuzzleShape11,
        cbcMuzzleShape12, cbcMuzzleShape13, cbcMuzzleShape14, cbcMuzzleShape15
    );
    vec4 axes[CBC_MUZZLE_LIGHTS] = vec4[CBC_MUZZLE_LIGHTS](
        cbcMuzzleAxis0, cbcMuzzleAxis1, cbcMuzzleAxis2, cbcMuzzleAxis3,
        cbcMuzzleAxis4, cbcMuzzleAxis5, cbcMuzzleAxis6, cbcMuzzleAxis7,
        cbcMuzzleAxis8, cbcMuzzleAxis9, cbcMuzzleAxis10, cbcMuzzleAxis11,
        cbcMuzzleAxis12, cbcMuzzleAxis13, cbcMuzzleAxis14, cbcMuzzleAxis15
    );
    vec4 tints[CBC_MUZZLE_LIGHTS] = vec4[CBC_MUZZLE_LIGHTS](
        cbcMuzzleTint0, cbcMuzzleTint1, cbcMuzzleTint2, cbcMuzzleTint3,
        cbcMuzzleTint4, cbcMuzzleTint5, cbcMuzzleTint6, cbcMuzzleTint7,
        cbcMuzzleTint8, cbcMuzzleTint9, cbcMuzzleTint10, cbcMuzzleTint11,
        cbcMuzzleTint12, cbcMuzzleTint13, cbcMuzzleTint14, cbcMuzzleTint15
    );

    vec3 total = vec3(0.0);
    for (int i = 0; i < CBC_MUZZLE_LIGHTS; i++) {
        vec4 light = lights[i];
        if (light.w <= 0.0) continue;

        vec4 shape = shapes[i];
        vec4 axis = axes[i];
        // A registered lamp is not lit by its own light: the light starts inside the lamp block, so
        // additive light there washes the block's own texture out to white. tint.w marks the lights
        // this applies to. The outward-facing test keeps the neighbouring block's face, which lies
        // on the same boundary plane, out of the cull.
        if (tints[i].w > 0.5) {
            vec3 self = scene_pos - light.xyz;
            if (all(lessThan(abs(self), vec3(0.55))) && dot(normal, self) > 0.0) continue;
        }
        float radius = max(shape.x, 0.01);
        float falloff;
        vec3 to_light;

        if (axis.w > 0.0) {
            // Spotlight: pure angular profile. side/along cancels the range, so the cone
            // behaves like a real lamp -- same brightness near and far, only the angle counts.
            vec3 beam = scene_pos - light.xyz;
            float along = dot(beam, axis.xyz);
            // The range with its sign still on it. `shape.x` is negative for a light that also fades
            // along its length -- a hand torch -- and the sphere's `max(shape.x, 0.01)` above threw that
            // sign away, leaving 0.01. Every fragment past a centimetre then failed this cut-off, so the
            // torch drew nothing at all, in any pack that had the patch installed.
            float range = abs(shape.x);
            if (along <= 0.0 || along > range) continue;
            // The terminator uses the beam's own incident direction, not the direction to the
            // nearest point on the axis: the axis continues below the floor once the lamp tilts,
            // and a perpendicular terminator then reports "facing away" for everything past the
            // point where it dips under the surface -- a black sector that grows with the tilt.
            // -axis is uniform across the footprint and independent of distance, exactly like the
            // profile itself.
            to_light = -axis.xyz;
            // The angle to the axis sets the cross-section. A beam whose range arrives negative is one
            // that also fades along its length -- a hand torch; a ship's searchlight keeps the angle-only
            // profile it has always had, because it is meant to light the far end as brightly as the near
            // end. `max` is exactly what the CPU tier's AreaLight does for such a light.
            float across = length(beam - axis.xyz * along) / (along * axis.w);
            float down = along / range;
            falloff = cbc_muzzle_falloff(
                shape.x < 0.0 ? max(across, down) : across,
                shape.y,
                shape.z,
                shape.w
            );
        } else {
            vec3 offset = light.xyz - scene_pos;
            float distance = length(offset);
            falloff = cbc_muzzle_falloff(
                distance / radius,
                shape.y,
                shape.z,
                shape.w
            );
            to_light = distance > 0.0001 ? offset / distance : vec3(0.0, 1.0, 0.0);
        }
        if (falloff <= 0.0) continue;

        // A slightly wrapped terminator, but a surface facing away from the
        // flash still reaches exactly zero.
        float wrapped = clamp((dot(normal, to_light) + 0.2) / 1.2, 0.0, 1.0);
        float diffuse = mix(1.0, wrapped, cbcMuzzleNormalInfluence);
        // Follow the pack's ambient occlusion so the flash sits in the scene.
        float occlusion = mix(1.0, ao, 0.75);

        float visibility = 1.0;
#if defined PROGRAM_DEFERRED4
        // Who casts is the mod's call, one bit per light: it is the only side that knows which lights are
        // lamps, which are cannon fire, and what this particular player asked to spend their frame time on.
        // It used to be "the first N", which forced the mod to reorder its light list to put a chosen light
        // at a chosen index -- a mask has no such coupling. It is also what lets a registered lamp cast at
        // all: tints[i].w means "do not light this lamp's own block", and it only decided shadows before
        // because there was no other channel to carry the decision.
        if ((cbcMuzzleShadowMask & (1 << i)) != 0) {
            // Read per light, from the sphere's otherwise-unused axis vector (see the uniforms above).
            // A beam reads none of it: axis.w > 0 means its axis is its direction, and it stays hard.
            float softness = axis.w > 0.0 ? 0.0 : max(axis.x, 0.0);
            int own = axis.w > 0.0 ? 0 : int(axis.y + 0.5);
            int ceiling = own > 0 ? own : cbcMuzzleShadowSteps;
            visibility = cbc_muzzle_visibility(scene_pos, light.xyz, light.w, softness, ceiling);
            if (visibility <= 0.0) continue;
        }
#endif

        total += tints[i].rgb * (light.w * falloff * diffuse * occlusion * visibility);
    }

    // Scaled by the pack's own block-light magnitude so the light lands in the
    // same HDR range as everything else it lights. Each lamp already carries its
    // own colour, so no global tint is applied here.
    return (blocklight_scale * cbcMuzzleIntensity) * total;
}

#include "/include/misc/end_lighting_fix.glsl"
#include "/include/surface/material.glsl"
#include "/include/utility/fast_math.glsl"
#include "/include/utility/phase_functions.glsl"
#include "/include/utility/spherical_harmonics.glsl"

#ifdef COLORED_LIGHTS
#include "/include/lighting/lpv/blocklight.glsl"
#endif

#ifdef HANDHELD_LIGHTING
#include "/include/lighting/handheld_lighting.glsl"
#endif

#if !defined WORLD_OVERWORLD
#undef CLOUD_SHADOWS
#endif

const float sss_density = 14.0;
const float sss_scale = 5.0 * SSS_INTENSITY;
const float night_vision_scale = 1.5;
const float metal_diffuse_amount =
    0.5; // Scales diffuse lighting on metals, ideally this would be zero but
         // purely specular metals don't play well with SSR

float get_blocklight_falloff(float blocklight, float skylight, float ao) {
    float falloff = pow8(blocklight) + 0.18 * sqr(blocklight) +
        0.16 * dampen(blocklight); // Base falloff
    falloff *=
        mix(cube(ao),
            1.0,
            clamp01(falloff)); // Stronger AO further from the light source
    falloff *=
        mix(1.0,
            ao * dampen(abs(cos(2.0 * frameTimeCounter))) * 0.67 + 0.2,
            darknessFactor); // Pulsing blocklight with darkness effect
    falloff *= 1.0 - 0.2 * time_noon * skylight -
        0.2 * skylight; // Reduce blocklight intensity in daylight
    falloff += min(
        2.7 * pow12(blocklight),
        0.9
    ); // Strong highlight around the light source, visible even in the daylight
    falloff *= smoothstep(
        0.0,
        0.125,
        blocklight
    ); // Ease transition at edge of lightmap

    return falloff;
}

float get_skylight_falloff(float skylight) {
#if defined WORLD_OVERWORLD
    return sqr(skylight);
#else
    return 1.0;
#endif
}

#ifdef SHADOW_VPS
vec3 sss_approx(
    vec3 albedo,
    float sss_amount,
    float sheen_amount,
    float sss_depth,
    float LoV,
    float shadow
) {
    // Transmittance-based SSS
    if (sss_amount < eps) {
        return vec3(0.0);
    }

    vec3 coeff = albedo * inversesqrt(dot(albedo, luminance_weights) + eps);
    coeff = 0.75 * clamp01(coeff);
    coeff = (1.0 - coeff) * sss_density / sss_amount;

    float phase =
        mix(isotropic_phase, henyey_greenstein_phase(-LoV, 0.7), 0.33);

    vec3 sss =
        sss_scale * phase * exp2(-coeff * sss_depth) * dampen(sss_amount) * pi;

#ifdef SSS_SHEEN
    vec3 sheen = (0.8 * SSS_INTENSITY) * rcp(albedo + eps) *
        exp2(-1.0 * coeff * sss_depth) * henyey_greenstein_phase(-LoV, 0.5) *
        linear_step(-0.8, -0.2, -LoV);
    sss += sheen * sheen_amount;
#endif

    return sss;
}
#else
vec3 sss_approx(
    vec3 albedo,
    float sss_amount,
    float sheen_amount,
    float sss_depth,
    float LoV,
    float shadow
) {
    // Blur-based SSS
    float sss = 0.06 * sss_scale * pi;
    vec3 sheen = 0.8 * rcp(albedo + eps) * henyey_greenstein_phase(-LoV, 0.5) *
        linear_step(-0.8, -0.2, -LoV) * shadow;

    return sss + sheen * sheen_amount;
}
#endif

vec3 get_diffuse_lighting(
    Material material,
    vec3 scene_pos,
    vec3 normal,
    vec3 flat_normal,
    vec3 bent_normal,
    vec3 shadows,
    vec2 light_levels,
    float ao,
    float ambient_sss,
    float sss_depth,
#ifdef CLOUD_SHADOWS
    float cloud_shadows,
#endif
    float shadow_distance_fade,
    float NoL,
    float NoV,
    float NoH,
    float LoV
) {
#if defined PROGRAM_GBUFFERS_WATER
    // Small optimization, don't calculate diffuse lighting when albedo is 0 (eg
    // water)
    if (max_of(material.albedo) < eps) {
        return vec3(0.0);
    }
#endif

    vec3 lighting = vec3(0.0);

    // Arbitrary directional shading to make faces easier to distinguish
    float directional_lighting =
        (0.9 + 0.1 * normal.x) * (0.8 + 0.2 * abs(flat_normal.y)) +
        2.0 * ambient_sss * material.sss_amount;

    // Negative SSS depth => SSS blocked by occluder (SSRT SSS)
    bool sss_blocked = sss_depth < 0.0 || light_levels.y < 0.1;
    sss_depth = max0(sss_depth);

#if defined WORLD_OVERWORLD || defined WORLD_END

    // Sunlight/moonlight

#if defined SHADOW || defined SHADOW_SSRT
    vec3 diffuse = vec3(
        lift(max0(NoL), 0.25 * rcp(SHADING_STRENGTH)) *
        (1.0 - 0.5 * material.sss_amount)
    );
    vec3 bounced = 0.033 * (1.0 - shadows) * (1.0 - 0.1 * max0(normal.y)) *
        pow1d5(ao + eps) * pow4(light_levels.y) * BOUNCED_LIGHT_I;
    vec3 sss =
        sss_approx(
            material.albedo,
            material.sss_amount,
            material.sheen_amount,
            mix(sss_depth, 0.0, shadow_distance_fade),
            LoV,
            shadows.x
        ) *
        float(!sss_blocked);

    // Adjust SSS outside of shadow distance
    sss *=
        mix(1.0,
            (ao + pi * ambient_sss) * (clamp01(NoL) * 0.8 + 0.2),
            clamp01(shadow_distance_fade));

#ifdef AO_IN_SUNLIGHT
    diffuse *= sqr(ao);
#endif

#ifdef SHADOW_VPS
    // Add SSS and diffuse
    lighting += diffuse * shadows + bounced + sss;
#else
    // Blend SSS and diffuse
    lighting += mix(diffuse, sss, material.sss_amount) * shadows + bounced;
#endif
#else
    // Simple shading for when shadows are disabled
    vec3 sss = 0.08 * sss_scale * pi +
        0.5 * material.sheen_amount * rcp(material.albedo + eps) *
            henyey_greenstein_phase(-LoV, 0.5) * linear_step(-0.8, -0.2, -LoV);

    vec3 diffuse =
        vec3(lift(max0(NoL), 0.5 * rcp(SHADING_STRENGTH)) * 0.6 + 0.4) *
        (shadows * 0.8 + 0.2);
    diffuse = diffuse + max0(sss * lift(material.sss_amount, 5.0));
    diffuse *= 1.0 * (0.9 + 0.1 * normal.x) * (0.8 + 0.2 * abs(flat_normal.y));
    diffuse *= ao * pow4(light_levels.y) * (dampen(light_dir.y) * 0.5 + 0.5);

    lighting += diffuse;
#endif

    lighting *= light_color;

#ifdef CLOUD_SHADOWS
    lighting *= cloud_shadows;
#endif
#endif

    // Skylight

#if defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4 && defined SH_SKYLIGHT
    vec3 skylight = sh_evaluate_irradiance(sky_sh, bent_normal, ao);
    skylight = mix(skylight_up, skylight, sqr(light_levels.y));
#else
    vec3 skylight = ambient_color * ao;
    vec3 skylight_up = skylight;
#endif

    // Skylight SSS
    skylight = mix(skylight, 0.5 * skylight_up * ao, material.sss_amount);
    skylight += ambient_sss * skylight_up * material.sss_amount * 2.0;

#if defined WORLD_NETHER
    // Brighten + desaturate nether ambient
    skylight = 16.0 * directional_lighting *
        mix(skylight, vec3(dot(skylight, luminance_weights_rec2020)), 0.5);
#endif

    lighting += skylight * get_skylight_falloff(light_levels.y);

    // Blocklight

    float blocklight_falloff =
        get_blocklight_falloff(light_levels.x, light_levels.y, ao);
    vec3 mc_blocklight = (blocklight_falloff * directional_lighting) *
        (blocklight_scale * blocklight_color);

#ifdef COLORED_LIGHTS
    lighting += get_lpv_blocklight(
        scene_pos,
        flat_normal,
        mc_blocklight,
        ao * directional_lighting
    );
#else
    lighting += mc_blocklight;
#endif

#ifdef HANDHELD_LIGHTING
    lighting += get_handheld_lighting(scene_pos, ao);
#endif

    lighting += get_cbc_muzzle_lighting(scene_pos, normal, ao);

    lighting += material.emission * emission_scale;

#if defined WORLD_OVERWORLD
    // Cave lighting

    lighting += 0.15 * CAVE_LIGHTING_I * directional_lighting * ao *
        (1.0 - light_levels.y * light_levels.y) * (1.0 - 0.7 * darknessFactor);
    lighting += nightVision * night_vision_scale * directional_lighting * ao;
#endif

    return max0(lighting) * material.albedo * rcp_pi *
        mix(1.0, metal_diffuse_amount, float(material.is_metal));
}

#endif // INCLUDE_LIGHTING_DIFFUSE_LIGHTING
