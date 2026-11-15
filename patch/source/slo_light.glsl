#ifndef INCLUDE_LIGHTING_SLO_LIGHT
#define INCLUDE_LIGHTING_SLO_LIGHT

// ===========================================================================
//  Sable Light Overhaul bridge -- Photon patch
//
//  The "Sable Light Overhaul" mod supplies these uniforms every frame through
//  Iris' fixed-input uniform mechanism. With the mod absent every value is
//  zero and this file costs a few comparisons per pixel.
//
//  Required on the mod side:  lightingMode = "Advanced"
//  (the mod then stops drawing its screen-space overlay, so the light comes
//  from Photon's own lighting pass instead of being added on top of it)
//
//  Coordinates: sloLight*.xyz are scene space (camera-relative, world
//  axes), the same space as this function's scene_pos / normal arguments, so
//  N.L is a plain dot product and the shadow ray needs no conversion.
// ===========================================================================

#define SLO_LIGHTS 32             // most lights ONE FRAME can carry -- not how many exist in the
                                         // world; 32 is the width of sloShadowMask, one bit per slot

// Shadow tuning. Which lights cast, how many taps they get and how soft the edge is all arrive from
// the mod at runtime (the three uniforms below), because those are the player's calls. What stays here
// is the compile-time bound of the tap loop and the geometry of the march, which a pack author retunes.
#define SLO_SHADOW_STEPS_MAX 255  // compile-time bound of the tap loop; the mod picks 1..this.
                                         // 255 because the per-light wish travels in a byte, and because a
                                         // quality-mode ray is spaced by sloStepSize: the cap is what
                                         // turns that spacing into a reach (0.1 blocks x 255 = 25 blocks).
#define SLO_SHADOW_STEPS_NEAR 8.0 // ray length in blocks up to which the full tap count is spent
#define SLO_SHADOW_START 0.1      // distance of the first tap from the lit surface, in blocks
#define SLO_SHADOW_THICKNESS 4.0  // assumed occluder thickness, in blocks
#define SLO_SHADOW_BIAS 0.05      // rejects self-hits on the lit surface
// How far short of the light the march stops. It has to clear half a block, and this is not a detail: a
// registered lamp sits at the centre of the block it replaced, and that block's own model is in the
// depth buffer, so a ray allowed to reach the centre would find the lamp's own block and shadow the
// light out completely. 0.35 used to be enough only because nothing that was allowed to cast ever sat
// inside a block.
// How far short of the light's own centre the ray stops, in blocks. It has to clear the block the lamp is
// *mounted* on, not just the lamp's own cell: the mounting face sits half a block from the centre, so a ray
// that stops 0.6 out ends 0.1 INSIDE that face, the depth difference there is a tenth of a block -- far under
// the thickness tolerance -- and the lamp is judged to be shadowed by the very surface it is attached to. That
// is why a lamp throws a shadow of its own mounting plane at some angles. One block clears every neighbour of
// the lamp's cell; the cost is roughly a one block bubble around the lamp where nothing can cast a shadow.
#define SLO_SHADOW_CLEARANCE 1.0

uniform vec3 sloColor;            // legacy global tint; per-light sloTintN replaced it
uniform float sloIntensity;       // overall scale
uniform float sloNormalInfluence; // 0 = flat, 1 = full N.L
uniform int sloShadowMask;        // bit i set = light i casts a shadow
uniform int sloShadowSteps;       // default tap ceiling; a light may override it (see axis.y)
uniform sampler2D sloField;          // SLO: the vanilla light field, 1024 x 512, one byte per cell
// How many slots actually carry a light this frame. The loop below is bounded by this rather than by
// SLO_LIGHTS, so a frame with three lights pays for three instead of walking 32 dead slots.
// 0 means the mod has no lights this frame: it publishes this count every frame from the same record
// that carries the arrays, so 0 is a fact rather than a missing value. The function returns on it
// before touching anything -- see the guard at its head, the most important line in this block for a
// scene with nothing lit.
// ---------------------------------------------------------------------------
// Per-light style, one int per slot: bits 0..5 are the block faces this light emits through, bit 6 is
// the flood mode.
//
// Faces exist because Minecraft has no sloped surfaces -- a lamp on a wall or in a corner is a block
// against other blocks, and a light at the block's centre therefore illuminates the wall it is mounted
// on, which no real fixture does. Turning a face off is far cheaper than any light path that would have
// to discover the wall: the shader only has to ask which face the sample lies beyond.
//
// The face test is skipped for samples inside the lamp's own block, and that is the point of the whole
// arrangement: the lamp's model sits in that cell, and a face that does not emit must still not leave
// the fixture itself in the dark.
//
// Flood mode is the pack's own look: block light is omni-directional and ignores ambient occlusion, and it
// is what vanilla light feels like. What it is NOT is unoccluded -- vanilla block light is stopped by a
// wall, so a flood light still casts and still marches; only the terminator (N.L) and the AO are dropped.
// Dropping the march as well was wrong: it made the "vanilla-like" lamp the one light in the scene that
// shines through a wall, which is the single thing vanilla block light never does. Flood lights are also
// scaled by the pack's own block-light magnitude alone -- see the end of slo_slot -- so that a level 15
// lamp matches the block light it replaced instead of doubling it.
// ---------------------------------------------------------------------------
#define SLO_FACE_PX 1
#define SLO_FACE_NX 2
#define SLO_FACE_PY 4
#define SLO_FACE_NY 8
#define SLO_FACE_PZ 16
#define SLO_FACE_NZ 32
// ---------------------------------------------------------------------------
// Shadow cones: the directions a lamp is allowed to cast in.
//
// A lamp on the outside of a hull does not want shadows from the half-metre bumps of the surface it is
// bolted to, but it does want to shadow the engine, and it must not light the cabin behind it. Those are
// three different directions, and an emit-face mask cannot express them -- faces say where light goes,
// this says where shadows are computed. Outside every cone the shadow test is skipped entirely, so the
// term passes straight through and, incidentally, costs nothing: a narrow cone is cheaper than no cone.
//
// One cone is four angles packed into one float's bits, low byte first: horizontal start, horizontal end,
// vertical start, vertical end. Horizontal runs 0..360 over 0..255; vertical runs -90..+90 over 0..255,
// so both quantise to about 1.4 degrees, which is all a region to cover needs. A cone whose packed word
// is zero is absent, so the count of cones is the number of leading non-zero words and no separate count
// field is needed.
//
// The angles are measured in the frame of the physics body the lamp is mounted on -- a ship turns, and a
// cone painted in world angles would stay behind. Body 0 is the world, which is exactly right for a lamp
// on the ground; the mod fills the rest from Sable's poses, one quaternion per body, shared by every lamp
// on it. The body index rides in the style word (see the slo_style_* numbering below).
// ---------------------------------------------------------------------------
#define SLO_CONE_COUNT_SHIFT 7
#define SLO_CONE_COUNT_MASK 7
#define SLO_BODY_SHIFT 10
#define SLO_BODY_MASK 7
#define SLO_FACES_ALL 63
#define SLO_STYLE_FLOOD 64
// SLO: bit 13. The sixth falloff profile: a point light the owner asked to read as a physical source, so its
// brightness falls with the square of the distance as well as its curve. It is a profile rather than a rule
// because it is measured in blocks and is merciless -- ten blocks out a light keeps one percent -- and because
// every other profile here is already chosen per light.
#define SLO_STYLE_PHYSICAL 8192
// Bit 14: this lamp's light field was flooded in its hull's own plot, so the sample point has to be read in
// that frame. The rotation is NOT taken from the shared body table -- a table lookup that comes back as the
// world slot leaves the sample unrotated, which is indistinguishable from "the hull's shading changes when
// the hull turns". It rides in this light's own shape vector instead (see the field branch).
#define SLO_STYLE_FIELD_BODY 16384
// Bit 15: tint.w is an emissive ratio, so this lamp's own block glows like the block it replaced. Set for
// every lamp that stands in for an emissive block, on either model.
#define SLO_STYLE_EMISSION 32768


uniform int sloStyle0;
uniform int sloStyle1;
uniform int sloStyle2;
uniform int sloStyle3;
uniform int sloStyle4;
uniform int sloStyle5;
uniform int sloStyle6;
uniform int sloStyle7;
uniform int sloStyle8;
uniform int sloStyle9;
uniform int sloStyle10;
uniform int sloStyle11;
uniform int sloStyle12;
uniform int sloStyle13;
uniform int sloStyle14;
uniform int sloStyle15;
uniform int sloStyle16;
uniform int sloStyle17;
uniform int sloStyle18;
uniform int sloStyle19;
uniform int sloStyle20;
uniform int sloStyle21;
uniform int sloStyle22;
uniform int sloStyle23;
uniform int sloStyle24;
uniform int sloStyle25;
uniform int sloStyle26;
uniform int sloStyle27;
uniform int sloStyle28;
uniform int sloStyle29;
uniform int sloStyle30;
uniform int sloStyle31;

uniform vec4 sloCone0;
uniform vec4 sloCone1;
uniform vec4 sloCone2;
uniform vec4 sloCone3;
uniform vec4 sloCone4;
uniform vec4 sloCone5;
uniform vec4 sloCone6;
uniform vec4 sloCone7;
uniform vec4 sloCone8;
uniform vec4 sloCone9;
uniform vec4 sloCone10;
uniform vec4 sloCone11;
uniform vec4 sloCone12;
uniform vec4 sloCone13;
uniform vec4 sloCone14;
uniform vec4 sloCone15;
uniform vec4 sloCone16;
uniform vec4 sloCone17;
uniform vec4 sloCone18;
uniform vec4 sloCone19;
uniform vec4 sloCone20;
uniform vec4 sloCone21;
uniform vec4 sloCone22;
uniform vec4 sloCone23;
uniform vec4 sloCone24;
uniform vec4 sloCone25;
uniform vec4 sloCone26;
uniform vec4 sloCone27;
uniform vec4 sloCone28;
uniform vec4 sloCone29;
uniform vec4 sloCone30;
uniform vec4 sloCone31;

uniform vec4 sloBody0;
uniform vec4 sloBody1;
uniform vec4 sloBody2;
uniform vec4 sloBody3;
uniform vec4 sloBody4;
uniform vec4 sloBody5;
uniform vec4 sloBody6;
uniform vec4 sloBody7;

uniform int sloLightCount;
// Cloud lighting (CLOUD-LIGHTING.zh-CN.md). Declared here as well as in slo_clouds.glsl because that
// block skips its own declarations whenever this one is present, and the cloud function is compiled
// into every program that has this file -- including the blocky-cloud pass.
//   sloCloudMask  = bit i set for a light that takes part in cloud lighting
//   sloCloudParams = (master switch, phase lane, multi-scatter lane, intensity)
//   sloCloudSplit  = how much of the term stays integrated in the cloud march (1) instead of being
//                    evaluated per pixel after the pack's temporal upsampling (0)
//   sloCloudFalloff = multiplier on a light's own size that sets the width of its cloud patch and how
//                    fast it fades; see slo_cloud_fade in slo_clouds.glsl for why the falloff cannot be
//                    measured on the 3D distance to the light
uniform int sloCloudMask;
uniform vec4 sloCloudParams;
uniform float sloCloudSplit;
uniform float sloCloudFalloff;
// Per-light shadow behaviour travels in `axis.xy` of every NON-BEAM light, because a sphere's axis
// vector is otherwise unused (axis.w == 0 is what marks a light as a sphere). So one lamp can be soft
// while the next is hard, and one can be given a bigger tap budget than the rest, without a single extra
// uniform. A beam keeps axis as its direction and is always hard: the sharp cone is the whole point of
// a searchlight, and one sweeping across a floor looks wrong blurred.
//   axis.x = penumbra radius in blocks, 0 = hard edges
//   axis.y = this light's own tap ceiling, 0 = use sloShadowSteps

// xyz = scene-space position, w = light level 0..1 (already includes the
// mod's temporal decay, so it flickers like the muzzle flash does)
uniform vec4 sloLight0;
uniform vec4 sloLight1;
uniform vec4 sloLight2;
uniform vec4 sloLight3;
uniform vec4 sloLight4;
uniform vec4 sloLight5;
uniform vec4 sloLight6;
uniform vec4 sloLight7;
uniform vec4 sloLight8;
uniform vec4 sloLight9;
uniform vec4 sloLight10;
uniform vec4 sloLight11;
uniform vec4 sloLight12;
uniform vec4 sloLight13;
uniform vec4 sloLight14;
uniform vec4 sloLight15;
uniform vec4 sloLight16;
uniform vec4 sloLight17;
uniform vec4 sloLight18;
uniform vec4 sloLight19;
uniform vec4 sloLight20;
uniform vec4 sloLight21;
uniform vec4 sloLight22;
uniform vec4 sloLight23;
uniform vec4 sloLight24;
uniform vec4 sloLight25;
uniform vec4 sloLight26;
uniform vec4 sloLight27;
uniform vec4 sloLight28;
uniform vec4 sloLight29;
uniform vec4 sloLight30;
uniform vec4 sloLight31;

// x = radius (or the beam's range), y = core fraction, z = middle fraction, w = tail brightness
uniform vec4 sloShape0;
uniform vec4 sloShape1;
uniform vec4 sloShape2;
uniform vec4 sloShape3;
uniform vec4 sloShape4;
uniform vec4 sloShape5;
uniform vec4 sloShape6;
uniform vec4 sloShape7;
uniform vec4 sloShape8;
uniform vec4 sloShape9;
uniform vec4 sloShape10;
uniform vec4 sloShape11;
uniform vec4 sloShape12;
uniform vec4 sloShape13;
uniform vec4 sloShape14;
uniform vec4 sloShape15;
uniform vec4 sloShape16;
uniform vec4 sloShape17;
uniform vec4 sloShape18;
uniform vec4 sloShape19;
uniform vec4 sloShape20;
uniform vec4 sloShape21;
uniform vec4 sloShape22;
uniform vec4 sloShape23;
uniform vec4 sloShape24;
uniform vec4 sloShape25;
uniform vec4 sloShape26;
uniform vec4 sloShape27;
uniform vec4 sloShape28;
uniform vec4 sloShape29;
uniform vec4 sloShape30;
uniform vec4 sloShape31;

// xyz = the spotlight's beam axis (unit, scene space), w = tan(half angle).
// w = 0 means "not a beam": the light is an omnidirectional sphere of radius shape.x.
// A beam is angular only -- brightness depends on the angle to the axis, never on the
// distance along it -- so shape.x is a hard range cut-off instead of a falloff radius.
uniform vec4 sloAxis0;
uniform vec4 sloAxis1;
uniform vec4 sloAxis2;
uniform vec4 sloAxis3;
uniform vec4 sloAxis4;
uniform vec4 sloAxis5;
uniform vec4 sloAxis6;
uniform vec4 sloAxis7;
uniform vec4 sloAxis8;
uniform vec4 sloAxis9;
uniform vec4 sloAxis10;
uniform vec4 sloAxis11;
uniform vec4 sloAxis12;
uniform vec4 sloAxis13;
uniform vec4 sloAxis14;
uniform vec4 sloAxis15;
uniform vec4 sloAxis16;
uniform vec4 sloAxis17;
uniform vec4 sloAxis18;
uniform vec4 sloAxis19;
uniform vec4 sloAxis20;
uniform vec4 sloAxis21;
uniform vec4 sloAxis22;
uniform vec4 sloAxis23;
uniform vec4 sloAxis24;
uniform vec4 sloAxis25;
uniform vec4 sloAxis26;
uniform vec4 sloAxis27;
uniform vec4 sloAxis28;
uniform vec4 sloAxis29;
uniform vec4 sloAxis30;
uniform vec4 sloAxis31;

// rgb = this lamp's own light colour (linear). Muzzle flashes and tracers carry the
// mod's configured colour; a registered lamp or a spotlight carries its own tint.
uniform vec4 sloTint0;
uniform vec4 sloTint1;
uniform vec4 sloTint2;
uniform vec4 sloTint3;
uniform vec4 sloTint4;
uniform vec4 sloTint5;
uniform vec4 sloTint6;
uniform vec4 sloTint7;
uniform vec4 sloTint8;
uniform vec4 sloTint9;
uniform vec4 sloTint10;
uniform vec4 sloTint11;
uniform vec4 sloTint12;
uniform vec4 sloTint13;
uniform vec4 sloTint14;
uniform vec4 sloTint15;
uniform vec4 sloTint16;
uniform vec4 sloTint17;
uniform vec4 sloTint18;
uniform vec4 sloTint19;
uniform vec4 sloTint20;
uniform vec4 sloTint21;
uniform vec4 sloTint22;
uniform vec4 sloTint23;
uniform vec4 sloTint24;
uniform vec4 sloTint25;
uniform vec4 sloTint26;
uniform vec4 sloTint27;
uniform vec4 sloTint28;
uniform vec4 sloTint29;
uniform vec4 sloTint30;
uniform vec4 sloTint31;

// One float per light: the knee of the ramp a beam fades along its LENGTH by, or 0 for a beam whose length
// is even. Four to a vec4, because a beam's four vectors are all spoken for -- shape is range/core/middle/
// tail, axis is direction and half-angle, light is position and brightness, tint is colour and self-cull --
// and this is the one number the angular profile cannot carry. See the matching packing in the bridge.
uniform vec4 sloDecay0;
uniform vec4 sloDecay1;
uniform vec4 sloDecay2;
uniform vec4 sloDecay3;
uniform vec4 sloDecay4;
uniform vec4 sloDecay5;
uniform vec4 sloDecay6;
uniform vec4 sloDecay7;

// 1 = keep drawing shadows that reach further than the ray can honestly resolve (the shipped behaviour).
// 0 = stop each ray at its own honest reach: about 1.25 blocks per tap, capped at 15. One depth tap
// resolves about 1.25 blocks, so a ray with more length than taps*1.25 has its taps spread so thin that a
// wall or a pillar in between is stepped straight over -- the shadow then has holes rather than an edge.
// Stopping the ray is what removes those holes; the cost is that a shadow needing a longer ray is not
// drawn at all, which is the honest answer rather than a wrong one.
uniform int sloShadowLongRange;
// Quality mode: one tap every sloStepSize blocks instead of the tapered march. The mod publishes
// both every frame; see the config's warning for what the equal spacing costs.
uniform int sloQualitySteps;
uniform float sloStepSize;
// Quality mode's assumed occluder thickness. See the depth test below for why tight is right there.
uniform float sloQualityThickness;

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

vec3 slo_view_to_screen(vec3 view_pos) {
    vec4 clip = gbufferProjection * vec4(view_pos, 1.0);
    return (clip.xyz / clip.w) * 0.5 + 0.5;
}

float slo_screen_to_view_depth(float depth) {
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
vec3 slo_random() {
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
float slo_visibility(vec3 scene_pos, vec3 light_scene_pos, float strength, float softness, int ceiling_in) {
    vec3 origin_view = (gbufferModelView * vec4(scene_pos, 1.0)).xyz;
    // The sample point on the light's own disc. Done in scene space, before the view transform, so the
    // offset is a real distance in blocks wherever the camera happens to be.
    if (softness > 0.0) {
        vec3 dir = slo_random() * 2.0 - 1.0;
        float len = max(length(dir), 1.0E-4);
        light_scene_pos += (dir / len) * softness;
    }
    vec3 light_view = (gbufferModelView * vec4(light_scene_pos, 1.0)).xyz;
    vec3 to_light = light_view - origin_view;
    float light_distance = length(to_light);
    if (light_distance < 0.5) return 1.0;
    vec3 dir_view = to_light / light_distance;

    vec2 screen_size = vec2(textureSize(depthtex1, 0));
    vec3 origin_screen = slo_view_to_screen(origin_view);
    if (clamp(origin_screen.xy, 0.0, 1.0) != origin_screen.xy) return 1.0;

    // Anchor the ray to this fragment's own screen position so any TAA jitter
    // cancels out instead of shifting the whole ray by a pixel.
    vec2 jitter_offset = gl_FragCoord.xy / screen_size - origin_screen.xy;

    // Stop short of the light so it cannot occlude itself. See SLO_SHADOW_CLEARANCE for why this
    // is half a block rather than a token amount.
    float march_distance = light_distance - SLO_SHADOW_CLEARANCE;
    if (march_distance <= SLO_SHADOW_START) return 1.0;

    // How many taps this particular ray is worth. A shadow ray costs its taps at every lit pixel, so a dim
    // light still gets fewer than a bright one -- but the floor is three quarters of the ceiling rather than
    // half, and the length taper is a square root rather than a straight ratio. Both were tuned for muzzle
    // flashes a couple of blocks away; a lamp lighting a room asks the ray to cross the whole room, and
    // every tap dropped there is an occluder that goes unnoticed.
    int ceiling = clamp(ceiling_in, 1, SLO_SHADOW_STEPS_MAX);
    int taps;
    if (sloQualitySteps != 0) {
        // Quality mode: the step size is the specification, not a budget. One tap every
        // sloStepSize blocks, so an occluder thinner than the tapered march's far-field gaps -- an
        // antenna, a railing -- is found wherever it stands. The tap count follows from the step size and
        // this ray's length, and the compile-time cap turns into a reach: step size x cap, 0.1 x 255 being
        // about 25 blocks. Past that reach this ray casts no shadow rather than a coarser one, which is the
        // honest answer and the one worth photographing.
        float step = max(sloStepSize, 0.01);
        taps = int(clamp(ceil(march_distance / step), 1.0, float(SLO_SHADOW_STEPS_MAX)));
    } else {
        float near_taper = clamp(sqrt(SLO_SHADOW_STEPS_NEAR / max(light_distance, SLO_SHADOW_STEPS_NEAR)), 0.75, 1.0);
        float wanted = float(ceiling) * clamp(strength, 0.0, 1.0) * near_taper;
        int floor_taps = min(ceiling, max(2, (ceiling * 3) / 4));
        taps = int(clamp(wanted, float(floor_taps), float(ceiling)));

        // Long-range shadow simulation off: the ray may only travel as far as these taps can honestly
        // resolve. 1.25 blocks per tap, never more than 15 whatever the tap budget is. What this buys is the
        // near field -- inside the reach the taps now sit a block apart instead of five, so an occluder in
        // between is found. What it costs is every shadow that would have needed to march further.
        if (sloShadowLongRange == 0) {
            march_distance = min(march_distance, min(15.0, max(4.0, float(taps) * 1.25)));
        }
    }
    if (march_distance <= SLO_SHADOW_START) return 1.0;

    // Per-pixel, per-frame jitter. Photon's TAA integrates it into a smooth edge.
    // Without it the discrete taps pop from frame to frame as the light moves.
    float dither = texelFetch(noisetex, ivec2(gl_FragCoord.xy) & 511, 0).b;
    dither = fract(dither + float(frameCounter) * 0.618033988749895);

    // Jittered uniform march with a mild bias towards the lit surface. This used to be a geometric series
    // (ratio 2), which left a gap of more than seven blocks in the far half of a fifteen block ray: a pillar
    // standing there was stepped straight over, so a lamp's shadow stopped a couple of blocks behind whatever
    // cast it instead of stretching across the room. Bounded gaps are what make a point light behave like a
    // point light. The bias keeps the taps denser near the surface, where contact detail is what the eye checks.
    // Even spacing in quality mode, the tapered march otherwise. `step_count` is what makes the taps equal
    // when it is on: t runs 0..1 across the whole ray, so the gap is span / taps, which is the step size the
    // tap count above was derived from.
    const float bias = 1.35;
    float step_count = float(taps);
    float span = march_distance - SLO_SHADOW_START;
    // The assumed occluder thickness, and the one number quality mode changes about the geometry. The
    // window in the depth test is twice this: a surface the ray has passed behind counts as an occluder
    // only while the ray is that far behind it. Four blocks was sized for the tapered march, whose
    // far-field gaps are several blocks wide, and it had to be generous to catch a thin wall at all.
    // With one tap every sloStepSize blocks the sampling is no longer the limit, so the tolerance
    // can be -- and a wide tolerance there is what paints a grazing floor black, because at a shallow
    // angle the neighbouring pixel's depth sits several blocks away from the ray.
    float thickness = sloQualitySteps != 0
        ? max(sloQualityThickness, 0.0)
        : SLO_SHADOW_THICKNESS;

    for (int i = 0; i < SLO_SHADOW_STEPS_MAX; i++) {
        if (i >= taps) break;
        // One tap per step, placed anywhere inside it: the pattern changes every frame and TAA averages it.
        float t = (float(i) + dither) / step_count;
        float travelled = SLO_SHADOW_START + span * (sloQualitySteps != 0 ? t : pow(t, bias));
        if (travelled >= march_distance) break;

        vec3 ray_view = origin_view + dir_view * travelled;
        vec3 ray_screen = slo_view_to_screen(ray_view);

        vec2 uv = ray_screen.xy + jitter_offset;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;

        float sampled = texelFetch(depthtex1, ivec2(uv * screen_size), 0).x;
        if (sampled <= 0.0 || sampled >= 1.0) continue;

        // A positive delta means the ray is behind the surface stored at this tap.
        float delta = slo_screen_to_view_depth(ray_screen.z) -
            slo_screen_to_view_depth(sampled);
        if (delta > SLO_SHADOW_BIAS &&
            delta < 2.0 * thickness) {
            return 0.0;
        }
    }

    return 1.0;
}

#endif // PROGRAM_DEFERRED4

// Same three-segment spatial curve the mod uses on its own CPU and GPU paths.
float slo_falloff(float q, float core, float middle, float tail) {
    if (q <= core) return 1.0;
    if (q >= 1.0) return 0.0;
    if (q <= middle) return 1.0 - (1.0 - tail) * (q - core) / (middle - core);
    return tail * (1.0 - q) / (1.0 - middle);
}

// One light's contribution, taking that light's uniforms as parameters.
//
// This is the whole point of the shape below: the caller names each slot's uniforms directly, so the
// slot index is a compile-time constant and no array is involved. The block used to copy all 32 slots
// into five local arrays and index them with the loop variable; a variable index cannot live in
// registers, so every lit pixel paid for five 32-entry arrays in local memory -- 544 floats a pixel,
// whether one light was on or thirty-two. With the slots named one by one the cost of a frame is the
// cost of the lights that are actually in it, and a single lamp is a single lamp.
// One cone's worth of test, straight out of the packed word.
//
// The parameter names here are load-bearing and were once wrong. This function first took its word as
// "packed", and the pack then failed to compile on the NVIDIA driver:
//
//     0(870) : error C0000: syntax error, unexpected identifier, expecting ')' at token "azimuth"
//
// The driver tokenises "packed" as the layout qualifier it is in the GLSL grammar (the spec lists it
// with shared/std140/std430/row_major/column_major as a layout qualifier identifier rather than as a
// reserved word), and then reports the syntax error at the *next* identifier -- which is why the log
// blamed "azimuth" while the culprit was the name before it. glslang accepts all of these as plain
// identifiers, so the offline validator cannot see this class of bug; validate-photon-patch.ps1 now
// lints for it by name instead. Pipeline creation aborted and Iris switched shaders off entirely, so
// no part of 0.1.44..0.1.46 ever ran in game. See HANDOFF 8.39.
bool slo_in_cone(float cone_word, float yaw_deg, float pitch_deg) {
    if (cone_word == 0.0) return false;
    uint bits = floatBitsToUint(cone_word);
    float h1 = float(bits & 255u) * (360.0 / 255.0);
    float h2 = float((bits >> 8) & 255u) * (360.0 / 255.0);
    float v1 = float((bits >> 16) & 255u) * (180.0 / 255.0) - 90.0;
    float v2 = float((bits >> 24) & 255u) * (180.0 / 255.0) - 90.0;
    return yaw_deg >= h1 && yaw_deg <= h2 && pitch_deg >= v1 && pitch_deg <= v2;
}

// The rotation of one body, or the identity for anything that is not on one. Named uniforms cannot be
// indexed at run time, so this is a chain: eight comparisons, and the compiler sees one quaternion.
vec4 slo_body_quat(int slot) {
    if (slot == 1) return sloBody1;
    if (slot == 2) return sloBody2;
    if (slot == 3) return sloBody3;
    if (slot == 4) return sloBody4;
    if (slot == 5) return sloBody5;
    if (slot == 6) return sloBody6;
    if (slot == 7) return sloBody7;
    return sloBody0;
}

// World direction into the body's frame. A quaternion and its conjugate are the same rotation the other
// way, so one function does both jobs. A whole point never needs the body's origin here: a field lamp's
// sample is expressed as the difference from the lamp itself, taken in scene space where both are small
// numbers. See the field branch for why that order -- difference first, rotate second -- is not optional.
vec3 slo_rotate(vec3 v, vec4 q) {
    return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);
}

/**
 * Whether this sample lies in any of the light's cones.
 *
 * <p>False also means "the light has no cones", which is the documented meaning of an empty list: every
 * direction casts, as it always did. That is why the caller only calls this when the count is non-zero.
 */
bool slo_in_any_cone(vec4 cones, int count, int body, vec3 to_light) {
    vec3 dir = normalize(-to_light);
    vec4 q = slo_body_quat(body);
    // The body's rotation takes world into its own frame; the conjugate is the inverse.
    dir = slo_rotate(dir, vec4(-q.xyz, q.w));
    float yaw_deg = degrees(atan(dir.z, dir.x));
    if (yaw_deg < 0.0) yaw_deg += 360.0;
    float pitch_deg = degrees(asin(clamp(dir.y, -1.0, 1.0)));
    if (count > 0 && slo_in_cone(cones.x, yaw_deg, pitch_deg)) return true;
    if (count > 1 && slo_in_cone(cones.y, yaw_deg, pitch_deg)) return true;
    if (count > 2 && slo_in_cone(cones.z, yaw_deg, pitch_deg)) return true;
    if (count > 3 && slo_in_cone(cones.w, yaw_deg, pitch_deg)) return true;
    return false;
}
// How much sky reaches this fragment, published for slo_slot to read. It is a file-scope global because
// slo_slot is called from thirty-two generated call sites and threading one more argument through all of
// them would make every future signature change thirty-two edits.
float sloFieldSkylight = 0.0;
// And this fragment's albedo, for the same reason: the lamp block's own emission is a function of it.
vec3 sloFieldAlbedo = vec3(1.0);

/**
 * A lamp's own block, glowing the way the block it replaced glowed.
 *
 * <p>Taking a lamp's vanilla light over means its block reports emission 0 to the light engine -- that is what
 * stops every lamp lighting the world twice -- and the casualty was the block's own emissive look. This rebuilds
 * it out of the ratio the mod publishes in tint.w, in the same shape the pack uses for its own emissive list:
 *
 *     vec3 albedo_sqrt = sqrt(material.albedo);
 *     material.emission = albedo_sqrt * hsl.y * linear_step(0.33, 0.5, hsl.z);
 *
 * The square root compresses the range so a mid-tone emits relatively more than its brightness suggests, and
 * the lightness mask means the dark parts of the texture emit nothing -- which is why a glowstone's grout lines
 * stay dark and only its bright cells blaze. A flat colour has neither and read flat beside the block it
 * replaced.
 *
 * <p>Both models call this. A lamp used to lose its glow the moment its page was switched from 光场 to 射线光,
 * because the ratio only ever reached the field branch.
 */
vec3 slo_lamp_emission(vec3 tint_rgb, float ratio) {
    vec3 albedo = sloFieldAlbedo;
    float lightness = 0.5 * (max(max(albedo.r, albedo.g), albedo.b)
                             + min(min(albedo.r, albedo.g), albedo.b));
    float mask = smoothstep(0.33, 0.5, lightness);
    // 5.0 is where the per-block ratios finally lined up with the blocks they stand in for: at 1.0 every lamp
    // read darker than the block it replaced, and the ratio table was being pushed to compensate one block at
    // a time. The ratios are the shape and this is the level.
    const float SLO_LAMP_EMISSION_SCALE = 5.0;
    return sqrt(albedo) * mask * tint_rgb * emission_scale * max(0.0, ratio) * SLO_LAMP_EMISSION_SCALE;
}

// Signal sphere opening in world space; zero/360 keeps legacy omnidirectional lighting.
float slo_signal_angle_gain(vec3 from_source, float angle) {
    float distance = length(from_source);
    if (angle <= 0.0 || angle >= 360.0 || distance < 0.0001) return 1.0;
    float half_angle = radians(angle * 0.5);
    return smoothstep(cos(half_angle), cos(half_angle * 0.9), -from_source.y / distance);
}

vec3 slo_slot(vec4 light, vec4 shape, vec4 axis, vec4 tint, float decay, int slot,
                 vec3 scene_pos, vec3 normal, vec3 flat_normal, float ao, float selfFootprint, int style,
                 vec4 cones) {
        if (light.w <= 0.0) return vec3(0.0);

        // A registered lamp is not lit by its own light: the light starts inside the lamp block, so
        // additive light there washes the block's own texture out to white. tint.w marks the lights
        // this applies to. The outward-facing test keeps the neighbouring block's face, which lies
        // on the same boundary plane, out of the cull.
        // A lamp does not light its own block: the additive light starts inside it and would wash the block's
        // own model out to the light's colour. The test sits on the lamp's own block boundary, so it is made a
        // ramp whose width is never narrower than one pixel's footprint in the world -- see selfFootprint.
        float selfFade = 1.0;
        if (tint.w > 0.0) {
            vec3 self = scene_pos - light.xyz;
            if (dot(normal, self) > 0.0) {
                float own = max(max(abs(self.x), abs(self.y)), abs(self.z));
                float width = max(0.085, selfFootprint);
                selfFade = smoothstep(0.5 - width, 0.5 + width, own);
            }
        }
        float radius = max(shape.x, 0.01);
        // Initialised rather than merely declared. They belong to the ray model below, and a light on the
        // field model returns before it needs either -- but the compiler cannot see that, and an uninitialised
        // read is undefined behaviour in GLSL even on a path that cannot be taken.
        float falloff = 1.0;
        vec3 to_light = vec3(0.0, 1.0, 0.0);
        bool flood = (style & SLO_STYLE_FLOOD) != 0;
        // Everything from here to the field branch is the ray model, and a field is not a ray. It is skipped
        // for a field lamp rather than computed and then overruled, because two of those terms return black
        // outright and both used to sit ABOVE the field branch:
        //
        //     if (falloff <= 0.0) return vec3(0.0);        -- past the page's radius, whatever it is
        //     if ((faces & (1 << face)) == 0) return 0.0;  -- past a face the page does not emit from
        //
        // The first cut a field short of its own fifteen cells whenever the page's radius was smaller. The
        // second cuts by DIRECTION, which is exactly why one side of a corner could be lit and the opposite
        // side black while the field held the same level in both -- and "the same level in both" is what the
        // crosshair probe measured, cell by cell, before this line was written. A field spreads everywhere it
        // can reach; that is what being like vanilla block light means, and LightFields never reads the radius
        // or the emit-face rows for a lamp on this model.
        if (!flood) {
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
            if (along <= 0.0 || along > range) return vec3(0.0);
            // The terminator uses the beam's own incident direction, not the direction to the
            // nearest point on the axis: the axis continues below the floor once the lamp tilts,
            // and a perpendicular terminator then reports "facing away" for everything past the
            // point where it dips under the surface -- a black sector that grows with the tilt.
            // -axis is uniform across the footprint and independent of distance, exactly like the
            // profile itself.
            to_light = -axis.xyz;
            // The angle to the axis sets the cross-section: tan(angle) / tan(half angle), so the cone
            // keeps its angular width at any distance from the apex.
            vec3 off_axis = beam - axis.xyz * along;
            float across = length(off_axis) / (along * axis.w);
            falloff = slo_falloff(across, shape.y, shape.z, shape.w);
            // The length ramp is measured on the distance the light actually travels to this sample,
            // |sample - apex|, and not on its depth along the axis. `along` is where the sample
            // *projects* onto the centre line, so a ramp on it dims a sample sitting on the cone's rim
            // exactly as if it sat on the centre -- the pool's edge came out as bright as its middle,
            // which is the report that the flashlight's distance falloff is decided by its centre point.
            // A rim sample is 1/cos(angle) further from the torch, and that ratio is what is used now.
            // It also settles the cut-off for free: with a knee the ramp is already zero past `range`, so
            // a torch reaches a sphere around its apex instead of a disc at the axial depth, while a
            // searchlight (no knee, no ramp) keeps the hard axial cut it is meant to have.
            float knee = decay;
            // SLO: a searchlight used to keep full brightness to the very end of its range, which left the
            // beam as a hard disc of light with no sense of distance. It now stays at full output for the first
            // three quarters and fades linearly to nothing over the last quarter, so a beam has a far end.
            // Profiles that already ramp along their length (a torch, a lamp) keep their own ramp.
            if (knee < 0.0) {
                // Wide flare cone: keep the footprint proportional to altitude, not a fixed sphere.
                falloff *= slo_falloff(along / range, 0.75, 1.0, 0.0);
            } else if (knee > 0.0) {
                falloff *= slo_falloff(length(beam) / range, 0.0, knee, knee);
            } else {
                falloff *= slo_falloff(length(beam) / range, 0.75, 1.0, 0.0);
            }
        } else {
            vec3 offset = light.xyz - scene_pos;
            float distance = length(offset);
            float q = distance / radius;
            falloff = slo_falloff(
                q,
                shape.y,
                shape.z,
                shape.w
            );
            falloff *= slo_signal_angle_gain(-offset, -decay);
            // Photon's windowed inverse square, borrowed here for the two things the three-segment curve
            // cannot say. It is a plain physical law with a window on it -- window = 1 - q^4 squared, so the
            // light reaches exactly zero at its range instead of being cut off mid-slope -- and the law itself
            // is 1/(d^2+1), which has no singularity at the centre and makes a light read as a source rather
            // than as a bright patch with a soft edge. It is deliberately NOT normalised by the radius: an
            // earlier version multiplied by (radius^2+1) to "keep the brightness where the old curve put it",
            // which is not what that factor does -- at five blocks from a twenty block light it is a fifteen
            // fold amplification, and it turned distant terrain into a lit sheet. Both factors here are at most
            // one, so the term can only ever dim a light, never brighten it.
            //
            // Beams are deliberately left alone: a spotlight here is angular by construction -- the same
            // brightness near and far, only the angle counting -- and an inverse square on its length would
            // contradict the thing that makes it a searchlight.
            float window = clamp(1.0 - q * q * q * q, 0.0, 1.0);
            // SLO: the physical inverse square is the sixth falloff profile, not a law imposed on every point
            // light. It arrives as a per-light flag, because every other profile here is per light too (a
            // lamp's own page, the spotlight and disco keys), and a global switch would contradict that. The
            // term is measured in blocks and is merciless -- ten blocks out a light keeps one percent -- which
            // is exactly why it is a profile an owner picks instead of the default every light used to get.
            // Without the flag this is the three-segment curve with the window alone: a light reads by its own
            // shape and still reaches exactly zero at its range.
            falloff *= ((style & SLO_STYLE_PHYSICAL) != 0)
                ? (window * window) / (distance * distance + 1.0)
                : window;
            to_light = distance > 0.0001 ? offset / distance : vec3(0.0, 1.0, 0.0);
        }
        if (falloff <= 0.0) return vec3(0.0);
        falloff *= selfFade;

        // Which face of the light's own block this sample lies beyond. Only spheres: a beam already has
        // a direction, and its cone is a stricter statement than any subset of faces. Samples inside the
        // light's block are exempt, so a face that does not emit cannot leave the fixture dark.
        int faces = style & SLO_FACES_ALL;
        if (axis.w <= 0.0 && faces != SLO_FACES_ALL) {
            vec3 outward = scene_pos - light.xyz;
            if (any(notEqual(floor(scene_pos), floor(light.xyz)))) {
                vec3 a = abs(outward);
                int face = a.x >= a.y && a.x >= a.z ? (outward.x > 0.0 ? 0 : 1)
                         : a.y >= a.z ? (outward.y > 0.0 ? 2 : 3)
                         : (outward.z > 0.0 ? 4 : 5);
                if ((faces & (1 << face)) == 0) return vec3(0.0);
            }
        }
        }

        // SLO: the vanilla light-field model. This lamp's light is a field the mod floods through the world
        // one cell at a time, exactly as vanilla block light spreads, and it arrives as a 3D texture: one
        // 32x32x32 slab per slot with an always-empty guard cell at each end, so the trilinear fetch can
        // never mix two lamps. Level 15 is the vanilla ceiling, which is why a lamp on this model reaches
        // fifteen cells and why the level is divided by fifteen here: a cell at full strength then matches
        // the flat look this model replaced. Geometry work -- the shape term, the cones, the emit faces, the
        // terminator, the AO and the shadow march -- belongs to the ray model and is deliberately skipped:
        // a field already knows what it reached, because it spread there.
        if (flood && decay > 0.0) {
            // SLO field model. The lamp's own world block is the field's anchor: the field is a 32^3 slab centred
            // on it, one byte per cell, and the layer it lives in arrives as `decay` -- the fourth component of
            // the lamp's sloDecayN, which a sphere light does not otherwise use. No origin uniform is involved and
            // none is needed: the origin was tried in three shapes and never reached the shader, and a value that
            // does not arrive is indistinguishable in game from a lamp that was never switched on.
            //
            // World space, not scene space: light.xyz is camera-relative and cameraPosition is the camera, so
            // adding them cancels the camera exactly and the sample position becomes a property of the world.
            // A lamp on a physics body then goes one step further: its field lives in the body's plot, so both
            // the lamp's cell and the sample are carried into that frame before any address is computed.
            // The lamp's own cell centre in world space -- and it is the cell's centre, not the block's corner.
            // The field is built around the light's centre (ExtraLights takes Vec3.atCenterOf(pos) and stores
            // that), so cell (15,15,15) of the slab sits at center + 0.5 in each axis. An anchor taken from
            // floor(center) lands half a block low, which shifts every sample by half a cell: the field is then
            // read one cell out in every direction.
            vec3 field_lamp = light.xyz + cameraPosition;
            vec3 field_sample = scene_pos + cameraPosition;
            vec3 field_pos;
            // A lamp on a physics body keeps its blocks in that body's plot, so its field was flooded there and
            // has to be read there. The whole trap is *how* the sample is moved into that frame.
            //
            // The lamp's own position is the only reference needed, and the difference has to be taken BEFORE
            // anything is rotated: scene_pos and light.xyz are both camera-relative, their difference is a few
            // blocks, and that difference is exactly the sample relative to the lamp's cell centre. Rotating it
            // by the body's conjugate puts it in the ship's frame, where the field lives.
            //
            // What must never happen is doing the arithmetic in plot coordinates. Sable allocates plots far out
            // -- this world's is around x = 20,481,000 -- and a float has 24 bits of mantissa, so above 2^24 the
            // step is two blocks: a plot coordinate in a float is quantised to ±1 cell before it is even rotated.
            // That is what "the shading flickers and the ground will not stay lit" was: every sample landed two
            // cells away from where it belonged and moved as the camera did.
            //
            // The rotation comes from this light's own shape vector, not from the shared body table: a table
            // entry that comes back as the world slot is an identity rotation, and an identity here reads the
            // hull's private grid in world axes -- the report "the same face shades differently at a different
            // attitude". Three components are enough because the fourth of a unit quaternion is recoverable and
            // its sign names the same rotation.
            vec4 field_quat = vec4(0.0, 0.0, 0.0, 1.0);
            bool field_body = (style & SLO_STYLE_FIELD_BODY) != 0;
            if (field_body) {
                field_quat = vec4(shape.yzw, sqrt(max(0.0, 1.0 - dot(shape.yzw, shape.yzw))));
                field_pos = slo_rotate(scene_pos - light.xyz, vec4(-field_quat.xyz, field_quat.w));
            } else {
                vec3 field_anchor = floor(field_lamp) + 0.5;
                field_pos = field_sample - field_anchor;
            }
            float field_layer = decay - 1.0;
            // Read the field at a point, or -1 outside the slab.
            //
            // The slab is 32 cells wide and is centred on the lamp, so cell i has its centre at field_pos = i - 15
            // and covers field_pos in [i - 15.5, i - 14.5). The lamp's own cell is i = 15, which is field_pos = 0.
            //
            // The field is a 2D image now, 1024 x 512, one byte per cell: a layer's cells are laid out exactly as
            // the array's own index order, x + y*32 + z*1024, so column = x + y*32 and row = z, and the sixteen
            // layers stack vertically. That is the same order the upload writes and the same order index() builds,
            // which is why this needs no reshaping step anywhere.
            //
            // It was a 3D texture before, and that 3D texture is what never arrived: a dynamic sampler reported a
            // bind in 22 programs and sampled zero, and an external sampler on this mod's own unit did the same,
            // even with every texel filled to the maximum. A 2D image can be registered as a Minecraft dynamic
            // texture and named in shaders.properties as customTexture.sloField, which is the route Iris documents
            // and maintains itself -- no mod-side sampler bookkeeping at all.
            #define SLO_FIELD_CELL(q) (floor((q) + 15.5))
            // The multiply by 255 is not decoration. The field is an R8 texture, so texture() returns the stored
            // byte divided by 255 -- a light level of 15 arrives as 0.0588. Feeding that straight into the vanilla
            // brightness curve put every sample at about a tenth of a percent of full: the lamp lit, in its own
            // colour, and was almost black, with only the few cells nearest the lamp visible at all. Every earlier
            // measurement of "the shader reads zero" was consistent with a value this small, which is why the fault
            // looked like a missing texture for so long.
            // A cell's byte carries two things now: the level in bits 0..3, and an "opaque" flag in bit 4. The
            // level alone could not tell the two kinds of zero apart -- a wall, and an air cell the flood never
            // reached -- and they have to be treated in opposite ways: a wall is replaced by the face's own
            // level, an unlit air cell is left at zero, because that is where a light fades out. Replacing both
            // flattened the edge of every lit pool into one constant value per block, and a flat patch has no
            // gradient left -- its contour collapses onto the cell grid, which is exactly the sawtooth that ran
            // along the edge of the light. Four bits of every byte had been carrying nothing.
            //
            // This returns the raw byte (0..31) or -1 outside the slab; SLO_FIELD_LEVEL turns it into a level.
            #define SLO_FIELD_AT(q) (all(greaterThanEqual((q), vec3(-15.5))) && all(lessThan((q), vec3(16.5))) \
                ? texture(sloField, (vec2( \
                    SLO_FIELD_CELL(q).x + SLO_FIELD_CELL(q).y * 32.0 + 0.5, \
                    SLO_FIELD_CELL(q).z + field_layer * 32.0 + 0.5) \
                    / vec2(1024.0, 512.0))).r * 255.0 \
                : -1.0)
            // 16 or more means the flag is set, because a level is at most 15.
            #define SLO_FIELD_LEVEL(v) ((v) < 0.0 ? -1.0 : ((v) >= 15.5 ? (v) - 16.0 : (v)))
            // Step off the face along its own normal -- the flat normal -- to reach the cell the face looks
            // into, which is the cell vanilla calls "the block in front of the face".
            //
            // Two earlier versions tried to avoid trusting the normal's space and both were worse. Snapping to
            // the largest component and then guessing which side is air by comparing brightness made the choice
            // flip at cell boundaries and between neighbouring pixels -- the flashing stripes and black patches
            // -- and because the offset was constrained to one axis the sample crowded into a narrow band, which
            // is why the lit area on a corridor floor collapsed into a line. The version before that took the
            // axis from the shading normal, which Photon replaces with a per-pixel normal-mapped perturbation
            // under NORMAL_MAPPING.
            //
            // The flat normal is the world-space geometric face direction with its sign intact: the gbuffer is
            // written as mat3(gbufferModelViewInverse) * (mat3(gl_ModelViewMatrix) * gl_Normal), and flat ground
            // staying stable while the camera turns is the evidence that it arrives in the same space as
            // scene_pos. So there is nothing to infer -- offset by it a bit over half a block and the sample is
            // inside the air cell the face looks into, on every face, every frame, with no comparison that could
            // flip.
            vec3 field_dir = length(flat_normal) > 1.0E-4 ? normalize(flat_normal) : vec3(0.0, 1.0, 0.0);
            // The normal is a world direction and the field is not always read in the world, so it follows the
            // sample into the body's frame. Nothing below cares which frame it is in: the probe, the in-plane
            // basis and the host test all live in the field's own lattice.
            if (field_body) {
                field_dir = normalize(slo_rotate(field_dir, vec4(-field_quat.xyz, field_quat.w)));
            }

            // The lamp's own block glows again, and blooms again.
            //
            // Taking a lamp's vanilla light over means its block reports emission 0 to the light engine -- that
            // is what stops every lamp lighting the world twice -- and the casualty was the block's own
            // emissive look. Its face used to be written by the pack's emission term, which is emission_scale
            // here: 40x the tint, which is what makes a glowing block bright enough to bloom. A lamp on the
            // field model ended up a dull cube with light falling on it rather than a source, which is exactly
            // the report "our replacement block is dimmer and has lost its bloom".
            //
            // The host block is found by stepping INTO the surface along the face normal rather than by
            // flooring the fragment: a face lies exactly on a cell boundary, so flooring it is a coin flip
            // between the lamp and the block beside it. On a body the same statement is a plain containment
            // test, because field_pos is already measured from the lamp's own cell centre.
            bool field_on_lamp = field_body
                    ? all(lessThan(abs(field_pos - field_dir * 0.5), vec3(0.5)))
                    : all(equal(floor(field_sample - field_dir * 0.5), floor(field_lamp)));
            if (field_on_lamp) {
                // The lamp's own block glows again, and blooms again. The look is shared with the ray model --
                // see slo_lamp_emission -- so a lamp keeps it when its page switches models.
                return slo_lamp_emission(tint.rgb, tint.w) * max(0.15, light.w);
            }
            // Interpolate the way vanilla interpolates its own block light: as a vertex attribute.
            //
            // Minecraft bakes a light value per face vertex and the rasteriser lerps it across the quad, which
            // is where the smoothness of vanilla block light comes from -- Photon itself does no smoothing of
            // its own (d4_deferred_shading reads the level straight out of the gbuffer). The value at a vertex
            // is not the cell under it: it is an average of the four cells that touch that vertex, taken in the
            // layer the face looks into. This reproduces both halves of that.
            //
            // The average is not a plain one, and this file got the exception wrong. Vanilla's
            // AmbientOcclusionFace.blend() takes the two in-plane edge cells and their shared diagonal, plus
            // the cell in front of the face, and does:
            //
            //     if (x == 0) x = front;                    // for each of the first three
            //     return (x0 + x1 + x2 + front) / 4;
            //
            // That substitution is the whole difference. What was here before dropped the zero samples and
            // renormalised by the weight that survived, so one surviving corner became the entire value: at a
            // corner where three walls meet, the fragments that keep a single lit sample jump to that sample's
            // level (bright) while the fragments whose corners all sit inside the walls collapse to zero
            // (black). Two artifacts, one line. Vanilla can produce neither -- a zero is replaced by the face's
            // own level, so the result is never brighter than the brightest cell that contributed and never
            // darker than the cell directly in front of the face.
            vec3 field_probe = field_pos + field_dir * 0.5;
            vec3 field_t1 = abs(field_dir.x) > abs(field_dir.y) ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
            vec3 field_t2 = cross(field_dir, field_t1);
            // Cell centres are at integer coordinates in field space (cell i's centre is i - 15), so the cell
            // the probe is in is its nearest integer and the distance from that centre is the interpolation
            // weight. Snapped in the plane only: the normal axis is left alone, so a face normal that is not
            // perfectly axis-aligned cannot silently move the sample into the next layer.
            float field_pu = dot(field_probe, field_t1);
            float field_pv = dot(field_probe, field_t2);
            float field_cu = floor(field_pu + 0.5);
            float field_cv = floor(field_pv + 0.5);
            vec3 field_centre = field_probe
                    + field_t1 * (field_cu - field_pu)
                    + field_t2 * (field_cv - field_pv);
            float field_fa = field_pu - field_cu + 0.5;
            float field_fb = field_pv - field_cv + 0.5;
            // Nine cells: the one in front of the face and its eight neighbours in the plane. Four vertices
            // times four cells covers all nine, and the diagonal ones are only ever reached through a vertex,
            // so every one of them is needed. A sample outside the slab reads as unlit, and the substitution
            // below turns that into the face's own level -- the same answer vanilla gives for a cell it cannot
            // read, and the reason a lamp's reach fades out instead of ending in a shell.
            // The nine cells, and what to do with each of them.
            //
            // Vanilla's rule is `if (x == 0) x = front`, and it has to be adapted twice over. Vanilla's "zero"
            // means the neighbour is a solid block, because the cell on the far side of a wall is solid and
            // holds no light. Two cells here also read zero for a reason vanilla never has -- the wall itself,
            // and an air cell the flood never reached -- and treating them alike is what made the edge of the
            // light a staircase. So:
            //
            //   opaque (flag set)          -> the face's own level. Nothing to contribute; this is vanilla's case.
            //   outside the slab (-1)      -> the face's own level, the same answer vanilla gives for a cell it
            //                                 cannot read.
            //   lit, but more than 2 below -> the face's own level. A flood drops one level per cell, so a cell
            //   the face cell                  within these two steps cannot be that far down; it got its light
            //                                 around a corner, past a wall, and averaging it in drags the corner
            //                                 down. Measured once as [4 0 0|0 12 13|4 0 0], where the two 4s
            //                                 pulled two vertices from 12 to 10.
            //   zero, not opaque           -> LEFT AT ZERO. This is the pool's edge, and it is where a gradient
            //                                 has to survive. Replacing it flattens the neighbourhood and the
            //                                 contour collapses onto the cell grid.
            float r_c = SLO_FIELD_AT(field_centre);
            float f_c = SLO_FIELD_LEVEL(r_c);
            #define SLO_FIELD_SUB(r) ((r) < 0.0 || (r) >= 15.5 || ((r) > 0.0 && (r) < f_c - 2.0) ? f_c : (r))
            f_c    = SLO_FIELD_SUB(r_c);
            float f_m10  = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre - field_t1));
            float f_p10  = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre + field_t1));
            float f_0m1  = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre - field_t2));
            float f_0p1  = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre + field_t2));
            float f_m1m1 = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre - field_t1 - field_t2));
            float f_p1m1 = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre + field_t1 - field_t2));
            float f_m1p1 = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre - field_t1 + field_t2));
            float f_p1p1 = SLO_FIELD_SUB(SLO_FIELD_AT(field_centre + field_t1 + field_t2));
            #undef SLO_FIELD_SUB
            // Reconstruct with a C1 kernel instead of a bilinear one.
            //
            // Bilinear is continuous but its SLOPE jumps at every cell boundary -- the derivative of a tent is
            // a step. The eye is far more sensitive to a slope discontinuity than to a brightness one, so the
            // field's own falloff, one level per cell (which is what vanilla does and what the flood is),
            // reads as facets along the contour; an edge drawn along one of those contours comes out as a
            // staircase of single blocks. That is the report this replaces.
            //
            // A quadratic B-spline over the same nine cells is C1, so the slope is continuous across every
            // boundary and the facets go away. It costs nothing: the same nine taps, a different weighting. It
            // also smooths slightly by construction, which is what makes a one-level-per-cell field read as a
            // gradient rather than as a set of steps.
            //
            // The kernel, for a query at u in [-0.5, 0.5] from the nearest cell centre:
            //     w(-1) = 0.5 (0.5 - u)^2
            //     w( 0) = 0.75 - u^2
            //     w(+1) = 0.5 (0.5 + u)^2
            // and it sums to one for every u, so this is still an interpolation and not a rescale.
            float field_ua = field_fa - 0.5;
            float field_ub = field_fb - 0.5;
            float field_wa0 = 0.5 * (0.5 - field_ua) * (0.5 - field_ua);
            float field_wa1 = 0.75 - field_ua * field_ua;
            float field_wa2 = 0.5 * (0.5 + field_ua) * (0.5 + field_ua);
            float field_wb0 = 0.5 * (0.5 - field_ub) * (0.5 - field_ub);
            float field_wb1 = 0.75 - field_ub * field_ub;
            float field_wb2 = 0.5 * (0.5 + field_ub) * (0.5 + field_ub);
            float field_level =
                field_wa0 * (field_wb0 * f_m1m1 + field_wb1 * f_m10  + field_wb2 * f_m1p1)
              + field_wa1 * (field_wb0 * f_0m1  + field_wb1 * f_c    + field_wb2 * f_0p1)
              + field_wa2 * (field_wb0 * f_p1m1 + field_wb1 * f_p10  + field_wb2 * f_p1p1);
            #undef SLO_FIELD_AT
            // Nothing to add where the flood reached nothing. A cell the light could not reach is dark, and that is
            // what makes a wall cast and a gap leak; a negative value means outside the slab, which is past the
            // lamp's reach.
            // SLO DIAGNOSTIC, temporary. Paint the value that is actually shaded -- `field_level`, the result
            // of the four-vertex blend -- in five saturated bands, because a number cannot survive tone
            // mapping but a hue can:
            //
            //   red    <= 0.5   nothing at all
            //   orange  1..4
            //   yellow  5..8
            //   green   9..12
            //   cyan   13..15
            //
            // Read against the normal render from the same spot, this says which band every dark pixel fell
            // into: the field's own falloff (a smooth ring) or the blend trailing a lit cell down (a single
            // cell of a lower band inside a higher one). Off again now that it has been read.
            const bool SLO_FIELD_PAINT = false;
            if (SLO_FIELD_PAINT) {
                vec3 band = field_level <= 0.5 ? vec3(3.0, 0.0, 0.0)
                          : field_level <= 4.0 ? vec3(3.0, 1.4, 0.0)
                          : field_level <= 8.0 ? vec3(2.4, 2.4, 0.0)
                          : field_level <= 12.0 ? vec3(0.0, 3.0, 0.0)
                          : vec3(0.0, 2.0, 3.0);
                return band;
            }
            if (field_level <= 0.0) return vec3(0.0);
            // Vanilla's own brightness curve: pow8 + 0.18*sqr + 0.16*(x*(2-x)).
            float lv = field_level * (1.0 / 15.0);
            float lv2 = lv * lv;
            float lv4 = lv2 * lv2;
            float lv8 = lv4 * lv4;
            float shaped = lv8 + 0.18 * lv2 + 0.16 * lv * (2.0 - lv);
            // Everything vanilla block light is multiplied by, because this IS vanilla block light.
            //
            // The hue comes from the lamp's own palette slot, and the palette's first slot is vanilla block
            // light's colour. That is what settled "ours is clearly more orange": the overlay colour this used
            // to multiply by is (1.00, 0.58, 0.25) linear against vanilla's (1.00, 0.523, 0.355) -- a tenth more
            // green and thirty percent less blue. A lamp left on 原版 now matches the torch beside it, a lamp
            // asked for 暖光 gets that amber back, and a dyed lamp still gets its dye.
            //
            // The pack's BLOCKLIGHT_R/G/B no longer reach this light, and they cannot: the mod has no way to
            // read a shader constant, so the vanilla tint is baked into the palette on the Java side from
            // Photon's defaults. Tuning the pack's block light moves the torches and leaves our lamps where
            // they are -- stated here rather than left as a surprise.
            //
            // The two directional factors are Photon's own, and they were the second half of "too bright": a
            // face pointing -x keeps 0.9 of the light and a vertical face 0.8 to 1.0 of it. Without them every
            // surface received the full value, a lit floor and the wall beside it came out identical, and the
            // face distinction that makes a build read as blocks was gone.
            //
            // The two lines of vanilla's falloff that make a torch read WHITE at its core, which is what was
            // missing when the same tint at the same brightness still read as salmon:
            //
            //     falloff += min(2.7 * pow12(blocklight), 0.9);   // a highlight around the source
            //     falloff *= smoothstep(0.0, 0.125, blocklight);  // an eased edge at the lightmap's rim
            //
            // The highlight is the one that decides the hue. It adds up to 0.9 on a base of 1.34, so the cells
            // nearest the lamp are pushed more than half again, and tone mapping then bleaches them toward
            // white -- a torch's pool is pale in the middle and only warm at its edge. Without it the tint is
            // applied flat and the whole pool stays saturated, which is exactly the report "原版 has turned
            // red": the tint was the pack's own block-light colour to three decimals, and it still looked wrong
            // because the brightness shape around it was not vanilla's.
            //
            // pow12 is pow8 * pow4, already computed above as lv8 and lv4.
            //
            // AO, in vanilla's own place and with vanilla's own strength:
            //
            //     falloff *= mix(cube(ao), 1.0, clamp01(falloff));
            //
            // This was left out on the argument that the field IS the occlusion -- it spread cell by cell and
            // stopped where it could not pass -- and that was wrong in a way only the picture could show: with
            // the highlight above but no AO, the joins between blocks came out bright and flat, and a surface
            // that is uniformly bright right up to the corner it meets reads as floating rather than resting
            // on anything. The field's occlusion is at the scale of a block; contact shadow is at the scale of
            // a pixel, and they are not the same information.
            //
            // Note where it sits: BEFORE the highlight, because that is where vanilla puts it. The mix is
            // weighted by the falloff itself, so the core of the pool is left almost untouched and the darkening
            // grows towards the rim -- which is the whole shape of the effect.
            float fieldFalloff = shaped;
            fieldFalloff *= mix(cube(ao), 1.0, clamp01(fieldFalloff));
            fieldFalloff += min(2.7 * lv8 * lv4, 0.9);
            fieldFalloff *= smoothstep(0.0, 0.125, lv);
            // The last line of vanilla's falloff, the one that keeps a lamp from competing with daylight:
            //     falloff *= 1.0 - 0.2 * time_noon * skylight - 0.2 * skylight;
            // It needs the sky level, which is why the signature above gained one. At night skylight is 0 and
            // this is a no-op; under open sky at noon it takes a fifth to two fifths off.
            fieldFalloff *= 1.0 - 0.2 * time_noon * sloFieldSkylight - 0.2 * sloFieldSkylight;
            // Still not taken: the daylight reduction, which needs the skylight level this function is not
            // given. Everything else vanilla multiplies a block light by is here now.
            float field_faces = (0.9 + 0.1 * normal.x) * (0.8 + 0.2 * abs(flat_normal.y));
            return tint.rgb * fieldFalloff * field_faces * blocklight_scale;
        }

        // A slightly wrapped terminator, but a surface facing away from the
        // flash still reaches exactly zero.
        float wrapped = clamp((dot(normal, to_light) + 0.2) / 1.2, 0.0, 1.0);
        // A flood light has no terminator and no ambient occlusion -- vanilla block light has neither -- but
        // it is still stopped by walls, so it casts like every other light. Only these two terms are dropped.
        float diffuse = flood ? 1.0 : mix(1.0, wrapped, sloNormalInfluence);
        float occlusion = flood ? 1.0 : mix(1.0, ao, 0.75);

        float visibility = 1.0;
#if defined PROGRAM_DEFERRED4
        // Who casts is the mod's call, one bit per light: it is the only side that knows which lights are
        // lamps, which are cannon fire, and what this particular player asked to spend their frame time on.
        // It used to be "the first N", which forced the mod to reorder its light list to put a chosen light
        // at a chosen index -- a mask has no such coupling. It is also what lets a registered lamp cast at
        // all: tint.w means "do not light this lamp's own block", and it only decided shadows before
        // because there was no other channel to carry the decision.
        int coneCount = (style >> SLO_CONE_COUNT_SHIFT) & SLO_CONE_COUNT_MASK;
        // A lamp with cones only casts inside them; everywhere else the term passes through unshadowed.
        // The test is skipped entirely for a lamp without cones, so the ordinary case pays nothing.
        // The rule the design started from, enforced here as well as in the mod: a light on the vanilla light
// field does not cast. The mod already withholds its bit, because a field spread everywhere and knows
// what it reached, and spending a slot on one takes it from a ray light; this makes the shader say the
// same thing even if a bit is ever set by mistake.
bool casts = !flood && (sloShadowMask & (1 << slot)) != 0;
        if (casts && coneCount > 0) {
            int body = (style >> SLO_BODY_SHIFT) & SLO_BODY_MASK;
            casts = slo_in_any_cone(cones, coneCount, body, to_light);
        }
        if (casts) {
            // Read per light, from the sphere's otherwise-unused axis vector (see the uniforms above).
            // A beam reads none of it: axis.w > 0 means its axis is its direction, and it stays hard.
            float softness = axis.w > 0.0 ? 0.0 : max(axis.x, 0.0);
            int own = axis.w > 0.0 ? 0 : int(axis.y + 0.5);
            int ceiling = own > 0 ? own : sloShadowSteps;
            visibility = slo_visibility(scene_pos, light.xyz, light.w, softness, ceiling);
            if (visibility <= 0.0) return vec3(0.0);
        }
#endif

        // A flood light is meant to be indistinguishable from the block light it replaced, and the pack
        // scales that by blocklight_scale alone -- so it takes no part of the user's in-pack intensity,
        // which would make a level 15 lamp twice as bright as the vanilla light it stands in for. A ray
        // light is this mod's own shaping and keeps the multiplier.
        float scale = blocklight_scale * (flood ? 1.0 : sloIntensity);
        return tint.rgb * (light.w * falloff * diffuse * occlusion * visibility * scale);
}

vec3 get_slo_lighting(vec3 scene_pos, vec3 normal, vec3 flat_normal, float ao, float skylight,
                        vec3 albedo) {
    sloFieldSkylight = skylight;
    sloFieldAlbedo = albedo;
    // Nothing is lit this frame: sloLightCount is published every frame from the same record that
    // carries the slots, so a 0 here means the mod has no lights, not "not told". Bailing before anything
    // else runs is what keeps an empty scene free -- and unlike the arrays this block used to build first,
    // nothing below has a cost that is independent of how many lights there are. The branch is uniform,
    // so the derivatives taken below it are still well defined: every fragment of a draw takes the same
    // side.
    if (sloLightCount <= 0) return vec3(0.0);
    // How much world space one pixel covers at this fragment, taken once before any branch: derivatives are
    // only defined in uniform control flow. The lamp's own block is culled by a test on the reconstructed
    // scene position, and at a grazing angle one pixel spans a large slice of the world; a transition of fixed
    // width is then narrower than what the pixel can resolve, so the boundary lives inside a pixel and flips
    // it from frame to frame. Widening the transition to at least the pixel's own footprint means the cull can
    // never be sharper than the sampling that feeds it.
    float selfFootprint = max(length(dFdx(scene_pos)), length(dFdy(scene_pos)));
    // Positive by the guard at the head of this function.
    int live = min(sloLightCount, SLO_LIGHTS);
    vec3 total = vec3(0.0);
    if (live > 0) total += slo_slot(sloLight0, sloShape0, sloAxis0, sloTint0, sloDecay0.x, 0, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle0, sloCone0);
    if (live > 1) total += slo_slot(sloLight1, sloShape1, sloAxis1, sloTint1, sloDecay0.y, 1, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle1, sloCone1);
    if (live > 2) total += slo_slot(sloLight2, sloShape2, sloAxis2, sloTint2, sloDecay0.z, 2, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle2, sloCone2);
    if (live > 3) total += slo_slot(sloLight3, sloShape3, sloAxis3, sloTint3, sloDecay0.w, 3, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle3, sloCone3);
    if (live > 4) total += slo_slot(sloLight4, sloShape4, sloAxis4, sloTint4, sloDecay1.x, 4, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle4, sloCone4);
    if (live > 5) total += slo_slot(sloLight5, sloShape5, sloAxis5, sloTint5, sloDecay1.y, 5, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle5, sloCone5);
    if (live > 6) total += slo_slot(sloLight6, sloShape6, sloAxis6, sloTint6, sloDecay1.z, 6, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle6, sloCone6);
    if (live > 7) total += slo_slot(sloLight7, sloShape7, sloAxis7, sloTint7, sloDecay1.w, 7, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle7, sloCone7);
    if (live > 8) total += slo_slot(sloLight8, sloShape8, sloAxis8, sloTint8, sloDecay2.x, 8, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle8, sloCone8);
    if (live > 9) total += slo_slot(sloLight9, sloShape9, sloAxis9, sloTint9, sloDecay2.y, 9, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle9, sloCone9);
    if (live > 10) total += slo_slot(sloLight10, sloShape10, sloAxis10, sloTint10, sloDecay2.z, 10, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle10, sloCone10);
    if (live > 11) total += slo_slot(sloLight11, sloShape11, sloAxis11, sloTint11, sloDecay2.w, 11, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle11, sloCone11);
    if (live > 12) total += slo_slot(sloLight12, sloShape12, sloAxis12, sloTint12, sloDecay3.x, 12, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle12, sloCone12);
    if (live > 13) total += slo_slot(sloLight13, sloShape13, sloAxis13, sloTint13, sloDecay3.y, 13, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle13, sloCone13);
    if (live > 14) total += slo_slot(sloLight14, sloShape14, sloAxis14, sloTint14, sloDecay3.z, 14, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle14, sloCone14);
    if (live > 15) total += slo_slot(sloLight15, sloShape15, sloAxis15, sloTint15, sloDecay3.w, 15, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle15, sloCone15);
    if (live > 16) total += slo_slot(sloLight16, sloShape16, sloAxis16, sloTint16, sloDecay4.x, 16, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle16, sloCone16);
    if (live > 17) total += slo_slot(sloLight17, sloShape17, sloAxis17, sloTint17, sloDecay4.y, 17, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle17, sloCone17);
    if (live > 18) total += slo_slot(sloLight18, sloShape18, sloAxis18, sloTint18, sloDecay4.z, 18, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle18, sloCone18);
    if (live > 19) total += slo_slot(sloLight19, sloShape19, sloAxis19, sloTint19, sloDecay4.w, 19, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle19, sloCone19);
    if (live > 20) total += slo_slot(sloLight20, sloShape20, sloAxis20, sloTint20, sloDecay5.x, 20, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle20, sloCone20);
    if (live > 21) total += slo_slot(sloLight21, sloShape21, sloAxis21, sloTint21, sloDecay5.y, 21, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle21, sloCone21);
    if (live > 22) total += slo_slot(sloLight22, sloShape22, sloAxis22, sloTint22, sloDecay5.z, 22, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle22, sloCone22);
    if (live > 23) total += slo_slot(sloLight23, sloShape23, sloAxis23, sloTint23, sloDecay5.w, 23, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle23, sloCone23);
    if (live > 24) total += slo_slot(sloLight24, sloShape24, sloAxis24, sloTint24, sloDecay6.x, 24, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle24, sloCone24);
    if (live > 25) total += slo_slot(sloLight25, sloShape25, sloAxis25, sloTint25, sloDecay6.y, 25, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle25, sloCone25);
    if (live > 26) total += slo_slot(sloLight26, sloShape26, sloAxis26, sloTint26, sloDecay6.z, 26, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle26, sloCone26);
    if (live > 27) total += slo_slot(sloLight27, sloShape27, sloAxis27, sloTint27, sloDecay6.w, 27, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle27, sloCone27);
    if (live > 28) total += slo_slot(sloLight28, sloShape28, sloAxis28, sloTint28, sloDecay7.x, 28, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle28, sloCone28);
    if (live > 29) total += slo_slot(sloLight29, sloShape29, sloAxis29, sloTint29, sloDecay7.y, 29, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle29, sloCone29);
    if (live > 30) total += slo_slot(sloLight30, sloShape30, sloAxis30, sloTint30, sloDecay7.z, 30, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle30, sloCone30);
    if (live > 31) total += slo_slot(sloLight31, sloShape31, sloAxis31, sloTint31, sloDecay7.w, 31, scene_pos, normal, flat_normal, ao, selfFootprint, sloStyle31, sloCone31);

    // Each slot scaled itself (see the end of slo_slot): the two modes do not share a multiplier. Every
    // lamp carries its own colour, so no global tint is applied here either.
    return total;
}

#endif // INCLUDE_LIGHTING_SLO_LIGHT
