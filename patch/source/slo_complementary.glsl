#ifndef INCLUDE_LIGHTING_SLO_COMPAT
#define INCLUDE_LIGHTING_SLO_COMPAT

// ===========================================================================
//  Sable Light Overhaul bridge -- Complementary Unbound, LIGHT ONLY, no shadows
//
//  Same uniform names as the Photon block (patch/source/slo_light.glsl),
//  so the mod's Iris bridge needs no change at all: the mod supplies
//  sloLight0..31, sloShape0..31, sloAxis0..31,
//  sloTint0..31, sloDecay0..7, sloLightCount, sloColor,
//  sloIntensity and sloNormalInfluence, and everything below is
//  driven by those alone. No new uniform name is invented here.
//
//  Every screen-space shadow feature of the Photon block is deliberately gone:
//  there is no depth texture, no visibility function, no tap loop, no shadow
//  mask, no softness/penumbra jitter and no long-range switch. Complementary
//  Unbound binds no depth texture in the gbuffers lighting pass, and this phase
//  ships coloured dynamic lights only.
//
//  Required on the mod side:  lightingMode = "Advanced"
//  (the mod then stops drawing its screen-space overlay, so the light comes
//  from the pack's own lighting pass instead of being added on top of it)
//
//  Coordinates: sloLight*.xyz are scene space (camera-relative, world
//  axes), the same space as this function's scene_pos argument, so N.L is a
//  plain dot product. The call site is Complementary's DoLighting, which has
//  exactly those two quantities as parameters:
//      playerPos       camera-relative world position (scene space)
//      worldGeoNormal  world-space geometric normal
//
//  Injection: this block is inlined at file scope in
//  lib/lighting/mainLighting.glsl and the call is added inside the
//  finalDiffuse bracket group, just before the sqrt() that follows it:
//      vec3 finalDiffuse = pow2(directionShade * vanillaAO) *
//          (blockLighting + pow2(sceneLighting) + minLighting
//           + pow2(get_slo_lighting_compat(playerPos, worldGeoNormal, vanillaAO)))
//          + pow2(emission);
//      finalDiffuse = sqrt(max(finalDiffuse, vec3(0.0)));
//  The term has to enter SQUARED: that whole bracket group is squared space --
//  the next line takes its sqrt, and the pack's own sky terms inside it are
//  pow2(sceneLighting) and pow2(emission).
// ===========================================================================

#define SLO_LIGHTS 32             // most lights ONE FRAME can carry -- not how many exist in the
                                         // world; 32 is the width of the per-light uniform block, one slot
                                         // per index, and an unused slot has light.w == 0

// 这个系数存在的原因：Complementary 的光照单位不是 Photon 的。Photon 用 blocklight_scale 把我们的光
// 换算进它的 HDR 区间，Complementary 没有这个常数；而且我们这一项是加进 finalDiffuse 的括号里的
// （那里是平方空间，紧接着就是 sqrt()），所以这里返回的线性光强最终会以 SLO_COMPAT_SCALE 的
// 倍数出现在画面上。它就是在游戏里直接调亮度的唯一旋钮：1.0 是保守的起点，偏暗就调大，过曝就调小。
#define SLO_COMPAT_SCALE 1.0

uniform vec3 sloColor;            // legacy global tint; per-light sloTintN replaced it
uniform float sloIntensity;       // overall scale
uniform float sloNormalInfluence; // 0 = flat, 1 = full N.L
// How many slots actually carry a light this frame. The loop below is bounded by this rather than by
// SLO_LIGHTS, so a frame with three lights pays for three instead of walking 32 dead slots.
// 0 means the mod has no lights this frame: it publishes this count every frame from the same record
// that carries the arrays, so 0 is a fact rather than a missing value. The function returns on it
// before touching anything -- see the guard at its head, the most important line in this block for a
// scene with nothing lit.
uniform int sloLightCount;

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
// NOTE: the Photon block also parked that light's shadow softness and tap ceiling in
// axis.xy. With shadows gone those two lanes are read nowhere; the vector is only ever
// the beam's direction and half-angle, so a beam and a sphere behave identically here
// whether or not the mod still fills axis.xy.
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
// w > 0.5 marks a light that must not light its own block (a registered lamp starts
// inside the block it replaced). With shadows gone this is the only thing tint.w does.
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

// Same three-segment spatial curve the mod uses on its own CPU and GPU paths.
float slo_falloff_compat(float q, float core, float middle, float tail) {
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
// Signal sphere opening in world space; zero/360 keeps legacy omnidirectional lighting.
float slo_signal_angle_gain(vec3 from_source, float angle) {
    float distance = length(from_source);
    if (angle <= 0.0 || angle >= 360.0 || distance < 0.0001) return 1.0;
    float half_angle = radians(angle * 0.5);
    return smoothstep(cos(half_angle), cos(half_angle * 0.9), -from_source.y / distance);
}

vec3 slo_slot_compat(vec4 light, vec4 shape, vec4 axis, vec4 tint, float decay, int slot,
                 vec3 scene_pos, vec3 normal, float ao, float selfFootprint) {
        if (light.w <= 0.0) return vec3(0.0);

        // A registered lamp is not lit by its own light: the light starts inside the lamp block, so
        // additive light there washes the block's own texture out to white. tint.w marks the lights
        // this applies to. The outward-facing test keeps the neighbouring block's face, which lies
        // on the same boundary plane, out of the cull.
        // The test sits on the lamp's own block boundary, so it is made a ramp whose width is never
        // narrower than one pixel's footprint in the world -- see selfFootprint.
        float selfFade = 1.0;
        if (tint.w > 0.5) {
            vec3 self = scene_pos - light.xyz;
            if (dot(normal, self) > 0.0) {
                float own = max(max(abs(self.x), abs(self.y)), abs(self.z));
                float width = max(0.085, selfFootprint);
                selfFade = smoothstep(0.5 - width, 0.5 + width, own);
            }
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
            if (along <= 0.0 || along > range) return vec3(0.0);
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
            // Two curves, two numbers. The cross-section is the angular profile in shape.yzw; a beam that
            // fades along its length multiplies in its own linear ramp, whose knee is decay. They used to
            // share one profile through max(across, down), so a single knob moved both how wide the cone read
            // and how far it reached -- which is the thing the page could not express and the reason the
            // flashlight's "decay" appeared to change its angle instead of its distance.
            falloff = slo_falloff_compat(across, shape.y, shape.z, shape.w);
            float knee = decay;
            if (knee < 0.0) {
                falloff *= slo_falloff_compat(along / range, 0.75, 1.0, 0.0);
            } else if (knee > 0.0) {
                falloff *= slo_falloff_compat(along / range, 0.0, knee, knee);
            }
        } else {
            vec3 offset = light.xyz - scene_pos;
            float distance = length(offset);
            falloff = slo_falloff_compat(
                distance / radius,
                shape.y,
                shape.z,
                shape.w
            );
            to_light = distance > 0.0001 ? offset / distance : vec3(0.0, 1.0, 0.0);
        }
        if (falloff <= 0.0) return vec3(0.0);
        falloff *= selfFade;

        // A slightly wrapped terminator, but a surface facing away from the
        // flash still reaches exactly zero.
        float wrapped = clamp((dot(normal, to_light) + 0.2) / 1.2, 0.0, 1.0);
        float diffuse = mix(1.0, wrapped, sloNormalInfluence);
        // Follow the pack's ambient occlusion so the flash sits in the scene.
        float occlusion = mix(1.0, ao, 0.75);

        return tint.rgb * (light.w * falloff * diffuse * occlusion);
}

vec3 get_slo_lighting_compat(vec3 scene_pos, vec3 normal, float ao) {
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
    if (live > 0) total += slo_slot_compat(sloLight0, sloShape0, sloAxis0, sloTint0, sloDecay0.x, 0, scene_pos, normal, ao, selfFootprint);
    if (live > 1) total += slo_slot_compat(sloLight1, sloShape1, sloAxis1, sloTint1, sloDecay0.y, 1, scene_pos, normal, ao, selfFootprint);
    if (live > 2) total += slo_slot_compat(sloLight2, sloShape2, sloAxis2, sloTint2, sloDecay0.z, 2, scene_pos, normal, ao, selfFootprint);
    if (live > 3) total += slo_slot_compat(sloLight3, sloShape3, sloAxis3, sloTint3, sloDecay0.w, 3, scene_pos, normal, ao, selfFootprint);
    if (live > 4) total += slo_slot_compat(sloLight4, sloShape4, sloAxis4, sloTint4, sloDecay1.x, 4, scene_pos, normal, ao, selfFootprint);
    if (live > 5) total += slo_slot_compat(sloLight5, sloShape5, sloAxis5, sloTint5, sloDecay1.y, 5, scene_pos, normal, ao, selfFootprint);
    if (live > 6) total += slo_slot_compat(sloLight6, sloShape6, sloAxis6, sloTint6, sloDecay1.z, 6, scene_pos, normal, ao, selfFootprint);
    if (live > 7) total += slo_slot_compat(sloLight7, sloShape7, sloAxis7, sloTint7, sloDecay1.w, 7, scene_pos, normal, ao, selfFootprint);
    if (live > 8) total += slo_slot_compat(sloLight8, sloShape8, sloAxis8, sloTint8, sloDecay2.x, 8, scene_pos, normal, ao, selfFootprint);
    if (live > 9) total += slo_slot_compat(sloLight9, sloShape9, sloAxis9, sloTint9, sloDecay2.y, 9, scene_pos, normal, ao, selfFootprint);
    if (live > 10) total += slo_slot_compat(sloLight10, sloShape10, sloAxis10, sloTint10, sloDecay2.z, 10, scene_pos, normal, ao, selfFootprint);
    if (live > 11) total += slo_slot_compat(sloLight11, sloShape11, sloAxis11, sloTint11, sloDecay2.w, 11, scene_pos, normal, ao, selfFootprint);
    if (live > 12) total += slo_slot_compat(sloLight12, sloShape12, sloAxis12, sloTint12, sloDecay3.x, 12, scene_pos, normal, ao, selfFootprint);
    if (live > 13) total += slo_slot_compat(sloLight13, sloShape13, sloAxis13, sloTint13, sloDecay3.y, 13, scene_pos, normal, ao, selfFootprint);
    if (live > 14) total += slo_slot_compat(sloLight14, sloShape14, sloAxis14, sloTint14, sloDecay3.z, 14, scene_pos, normal, ao, selfFootprint);
    if (live > 15) total += slo_slot_compat(sloLight15, sloShape15, sloAxis15, sloTint15, sloDecay3.w, 15, scene_pos, normal, ao, selfFootprint);
    if (live > 16) total += slo_slot_compat(sloLight16, sloShape16, sloAxis16, sloTint16, sloDecay4.x, 16, scene_pos, normal, ao, selfFootprint);
    if (live > 17) total += slo_slot_compat(sloLight17, sloShape17, sloAxis17, sloTint17, sloDecay4.y, 17, scene_pos, normal, ao, selfFootprint);
    if (live > 18) total += slo_slot_compat(sloLight18, sloShape18, sloAxis18, sloTint18, sloDecay4.z, 18, scene_pos, normal, ao, selfFootprint);
    if (live > 19) total += slo_slot_compat(sloLight19, sloShape19, sloAxis19, sloTint19, sloDecay4.w, 19, scene_pos, normal, ao, selfFootprint);
    if (live > 20) total += slo_slot_compat(sloLight20, sloShape20, sloAxis20, sloTint20, sloDecay5.x, 20, scene_pos, normal, ao, selfFootprint);
    if (live > 21) total += slo_slot_compat(sloLight21, sloShape21, sloAxis21, sloTint21, sloDecay5.y, 21, scene_pos, normal, ao, selfFootprint);
    if (live > 22) total += slo_slot_compat(sloLight22, sloShape22, sloAxis22, sloTint22, sloDecay5.z, 22, scene_pos, normal, ao, selfFootprint);
    if (live > 23) total += slo_slot_compat(sloLight23, sloShape23, sloAxis23, sloTint23, sloDecay5.w, 23, scene_pos, normal, ao, selfFootprint);
    if (live > 24) total += slo_slot_compat(sloLight24, sloShape24, sloAxis24, sloTint24, sloDecay6.x, 24, scene_pos, normal, ao, selfFootprint);
    if (live > 25) total += slo_slot_compat(sloLight25, sloShape25, sloAxis25, sloTint25, sloDecay6.y, 25, scene_pos, normal, ao, selfFootprint);
    if (live > 26) total += slo_slot_compat(sloLight26, sloShape26, sloAxis26, sloTint26, sloDecay6.z, 26, scene_pos, normal, ao, selfFootprint);
    if (live > 27) total += slo_slot_compat(sloLight27, sloShape27, sloAxis27, sloTint27, sloDecay6.w, 27, scene_pos, normal, ao, selfFootprint);
    if (live > 28) total += slo_slot_compat(sloLight28, sloShape28, sloAxis28, sloTint28, sloDecay7.x, 28, scene_pos, normal, ao, selfFootprint);
    if (live > 29) total += slo_slot_compat(sloLight29, sloShape29, sloAxis29, sloTint29, sloDecay7.y, 29, scene_pos, normal, ao, selfFootprint);
    if (live > 30) total += slo_slot_compat(sloLight30, sloShape30, sloAxis30, sloTint30, sloDecay7.z, 30, scene_pos, normal, ao, selfFootprint);
    if (live > 31) total += slo_slot_compat(sloLight31, sloShape31, sloAxis31, sloTint31, sloDecay7.w, 31, scene_pos, normal, ao, selfFootprint);

    // Scaled by SLO_COMPAT_SCALE instead of the Photon block's blocklight_scale: the
    // pack has no such constant, and its light units are its own. Each lamp already carries its
    // own colour, so no global tint is applied here. The caller squares this value (see the
    // header), which is why the scale sits on this linear result rather than outside the pow2.
    return SLO_COMPAT_SCALE * sloIntensity * total;
}

#endif // INCLUDE_LIGHTING_SLO_COMPAT
