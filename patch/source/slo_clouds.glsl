#ifndef SLO_CLOUDS_INCLUDED
#define SLO_CLOUDS_INCLUDED 1

// ---------------------------------------------------------------------------
// Cloud lighting -- CLOUD-LIGHTING.zh-CN.md
//
// The flag below is the contract with the cloud layer files: they wrap their call in
// `#ifdef SLO_CLOUD_LIGHTING`, and only a program that has this block defines it. That is what keeps
// the sky-map pass (deferred.fsh, which renders clouds through the same layer files but does not
// include this block) compiling -- without it the first load failed with
// `deferred.fsh: error C1503: undefined variable "slo_cloud_light"`.
// ---------------------------------------------------------------------------
#define SLO_CLOUD_LIGHTING 1

// This block is spliced into BOTH cloud programs, so it has to stand on its own: Photon's fancy
// clouds live in `program/d1_clouds.fsh`, which does not include the surface lighting at all, while
// the blocky clouds live in `d4_deferred_shading.fsh`, which does. Everything the block needs is
// therefore declared here when -- and only when -- the surface block is absent.
//
// It is a volume term, not a surface one, and that is the whole reason it is separate code:
//   * no normal and no ambient occlusion -- light is scattered by the cloud, not reflected off it;
//   * no screen-space shadow marching, so a light does not get blocked by terrain or by a hull
//     standing between it and the cloud (accepted, see the design doc's decision 4);
//   * the third argument is the cloud sample's own scattering weight, so a thick part of the cloud
//     answers more than a thin one instead of every cloud glowing like the same sheet of paper.
//
// The caller adds the returned colour to the sample's scattering accumulation, which is what makes a
// shell cutting through the cloud deck leave a moving bright patch instead of a flat tint over the
// whole layer.
//
// Two entry points, one term, split by `sloCloudSplit`:
//   * `slo_cloud_light`        -- inside the march, integrated along the ray, weighted by the sample's
//                                 own scattering. Temporally smeared by the pack's cloud upsampling.
//   * `slo_cloud_light_screen` -- after that upsampling, once per pixel at the cloud's own distance.
//                                 Current-frame only, which is what stops a light that goes out from
//                                 glowing on the cloud for a second afterwards.
// The split is a client key (`clouds.integration`); 1 is the old all-integrated behaviour.
// ---------------------------------------------------------------------------
#ifndef INCLUDE_LIGHTING_SLO_LIGHT
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

uniform vec4 sloDecay0;
uniform vec4 sloDecay1;
uniform vec4 sloDecay2;
uniform vec4 sloDecay3;
uniform vec4 sloDecay4;
uniform vec4 sloDecay5;
uniform vec4 sloDecay6;
uniform vec4 sloDecay7;
uniform int sloLightCount;
uniform float sloIntensity;
uniform int sloCloudMask;
uniform vec4 sloCloudParams;
// How much of the cloud term is integrated inside the cloud march (1) and how much is evaluated once
// per pixel after the pack's temporal upsampling (0). See slo_cloud_light_screen below; also declared
// in slo_light.glsl, because in a program that has the surface block this guarded section is skipped.
uniform float sloCloudSplit;
// The multiplier on a light's own size that sets how wide its cloud patch is and how fast it fades
// (clouds.falloffScale). Also declared in slo_light.glsl, for the same reason as sloCloudSplit.
uniform float sloCloudFalloff;
#endif

// Same profile the surface lighting uses, under a different name so a program that already has
// slo_falloff (d4) does not end up with two definitions of one identifier.
float slo_cloud_falloff(float q, float core, float middle, float tail) {
    if (q <= core) return 1.0;
    if (q >= 1.0) return 0.0;
    if (q <= middle) return 1.0 - (1.0 - tail) * (q - core) / (middle - core);
    return tail * (1.0 - q) / (1.0 - middle);
}

// How a light's brightness fades across the cloud deck.
//
// It is measured in the light's OWN size (`shape.x`: a sphere's radius, a beam's range) times
// `sloCloudFalloff`, and it is measured sideways (a sphere: the horizontal offset) or along the beam,
// never on the 3D distance to the light -- because up there the 3D distance carries no information:
//
//   * the cloud deck sits ~125 blocks above a lamp, so a point 60 blocks to the side is
//     sqrt(125^2 + 60^2) = 138.7 blocks away: 11% further than straight up. On a three-segment curve
//     tuned for a surface five blocks away, 11% is entirely inside the flat core: the whole lit patch
//     comes out one colour and the far edge is still at the profile's tail. That was the report
//     "cloud brightness barely falls off, it is all about one colour".
//   * the reach that gates the light cannot be used as that length either: it is multiplied by the
//     player's `reachScale` (4..8) precisely so a 15-block muzzle flash can touch a deck 125 blocks
//     up, which would leave the ratio at ~0.3 across the entire visible patch.
//
// So the reach stays what it always was -- the gate that answers "can this light touch the clouds at
// all" -- and the gradient is measured on a length comparable to the patch itself. A lantern gets a
// small bright disc, an explosion a wide one, and `clouds.falloffScale` scales both.
float slo_cloud_distance(float q) {
    // Quadratic, not an S-curve. Both are "full at the light, zero at the falloff length", but this one
    // keeps the brightness near the middle: smoothstep is already at half brightness at half the radius,
    // which reads as a soft drift rather than as a light with a centre -- and once the bright part of the
    // patch runs past what the display can show, a gentle curve spends its whole range in the part that
    // saturates, so the lit area comes out flat and only its size changes when the radius is turned up.
    // (1 - q)^2 is at a quarter by then, and the report was exactly "a bigger uniformly tinted area".
    float t = clamp(1.0 - q, 0.0, 1.0);
    return t * t;
}

// The length over which this light's cloud patch fades, in blocks. Guarded against zero so a light
// whose size is unknown still gets a patch rather than an all-or-nothing disc of one block.
float slo_cloud_fade(vec4 shape) {
    return max(abs(shape.x) * max(sloCloudFalloff, 0.1), 1.0);
}

// One slot's contribution to a cloud sample, taking that slot's uniforms as parameters exactly like
// slo_slot does, so the slot index stays a compile-time constant and nothing builds an array.
//
// reach: for a sphere it is axis.z, which the mod writes with this light's cloud reach (0 = it does
//        not light clouds); for a beam it is the beam's own range in shape.x, so "the flashlight only
//        lights the cloud when it actually reaches it" falls out of the geometry.
vec3 slo_cloud_slot(vec4 light, vec4 shape, vec4 axis, vec4 tint, float decay,
                    vec3 scene_pos, vec3 view_dir, float density, vec4 params) {
    if (light.w <= 0.0) return vec3(0.0);
    vec3 to_light = light.xyz - scene_pos;
    float falloff;
    if (axis.w > 0.0) {
        // A beam: inside the cone and inside its range, with the same profile the cone uses on
        // surfaces. A beam has no free lane, so its cloud participation is the mask bit alone and
        // its reach is the range it already carries.
        //
        // The axis is normalised here rather than trusted: `along` is compared against a distance in
        // blocks, so a direction vector that is not unit length would make the range look longer than it
        // is by exactly that factor -- which is how a 48 block flashlight reached a cloud deck 130 blocks
        // up. The surface code does not care (its cone test is a ratio), so this only has to hold here.
        vec3 beam_dir = normalize(axis.xyz);
        float along = dot(scene_pos - light.xyz, beam_dir);
        float range = abs(shape.x);
        if (along <= 0.0 || along > range) return vec3(0.0);
        float across = length((scene_pos - light.xyz) - beam_dir * along) / max(along * axis.w, 1e-4);
        falloff = slo_cloud_falloff(across, shape.y, shape.z, shape.w);
        // The far end of a beam is dimmer than what is right in front of it. This used to happen only
        // when a light asked for it through the decay lane; on a cloud deck it is always what reads as
        // "the beam lands here".
        falloff *= slo_cloud_distance(length(to_light) / max(range, 1.0));
        if (decay > 0.0) falloff *= slo_cloud_falloff(along / range, 0.0, decay, decay);
    } else {
        // A negative reach is the mod's flag for "this light named its glow in blocks and wants the patch
        // to fill it" -- a tracer or a muzzle flash, whose own radius is only a few blocks while its glow
        // on a deck should be a wide soft patch. See AdvancedLights.cloudReachFor.
        float reach = abs(axis.z);
        if (reach <= 0.0) return vec3(0.0);
        // The gate: the reach the mod sends is what says "this light touches the clouds". Inside it the
        // patch is a disc whose radius is the light's own size times `clouds.falloffScale` -- but never
        // wider than the disc the gate actually allows at this height. That clamp is what a static lamp
        // needs: a falloff length that outlives the gate leaves a *uniformly lit plate with a hard edge
        // cut out of it*, because across the whole plate the curve has barely moved (it is all near full
        // brightness) and the boundary you see is the gate itself. That was the report "the static lamp
        // is still an equal-coloured circle": the edge was real, the fade was not. Clamping to the gate's
        // own span makes the brightness reach zero exactly where the gate cuts, so the disc fades out
        // instead of being cut out. A light that asked for a glow in blocks skips the radius rule: its
        // gate *is* the size it asked for, so the patch fills it.
        if (length(to_light) > reach) return vec3(0.0);
        float span = sqrt(max(reach * reach - to_light.y * to_light.y, 0.0));
        float fade = axis.z < 0.0 ? max(span, 1.0) : min(slo_cloud_fade(shape), max(span, 1.0));
        falloff = slo_cloud_distance(length(to_light.xz) / fade);
    }
    if (falloff <= 0.0) return vec3(0.0);
    // Isotropic by default. The phase lane is a manual quality switch: a Henyey-Greenstein lobe
    // around the light, normalised so that 0 leaves the isotropic answer untouched and 1 makes the
    // cloud brightest when the light is behind the sample as seen from the eye -- which is the
    // "shell passing overhead" look.
    if (params.y > 0.0) {
        float cos_theta = dot(-view_dir, normalize(to_light));
        float g = 0.6;
        float hg = (1.0 - g * g) / (12.5663706 * pow(1.0 + g * g - 2.0 * g * cos_theta, 1.5));
        falloff *= mix(1.0, hg, params.y);
    }
    // Multi-scatter lane: a second, softer copy that scales with the sample's own density, so a thick
    // cloud keeps more of the light than a thin one. Off by default.
    if (params.z > 0.0) falloff *= mix(1.0, 1.0 + density, params.z);
    return tint.rgb * (light.w * falloff);
}

// The whole frame's worth of cloud light at one sample. The head guard is uniform, so an ordinary
// frame -- nothing in the world reaches the clouds -- pays one comparison and leaves.
vec3 slo_cloud_light(vec3 scene_pos, vec3 view_dir, float density) {
    if (sloCloudParams.x <= 0.0 || sloCloudMask == 0 || sloLightCount <= 0) return vec3(0.0);
    int live = min(sloLightCount, 32);
    vec3 total = vec3(0.0);
    if (live > 0 && (sloCloudMask & 1) != 0) total += slo_cloud_slot(sloLight0, sloShape0, sloAxis0, sloTint0, sloDecay0.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 1 && (sloCloudMask & 2) != 0) total += slo_cloud_slot(sloLight1, sloShape1, sloAxis1, sloTint1, sloDecay0.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 2 && (sloCloudMask & 4) != 0) total += slo_cloud_slot(sloLight2, sloShape2, sloAxis2, sloTint2, sloDecay0.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 3 && (sloCloudMask & 8) != 0) total += slo_cloud_slot(sloLight3, sloShape3, sloAxis3, sloTint3, sloDecay0.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 4 && (sloCloudMask & 16) != 0) total += slo_cloud_slot(sloLight4, sloShape4, sloAxis4, sloTint4, sloDecay1.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 5 && (sloCloudMask & 32) != 0) total += slo_cloud_slot(sloLight5, sloShape5, sloAxis5, sloTint5, sloDecay1.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 6 && (sloCloudMask & 64) != 0) total += slo_cloud_slot(sloLight6, sloShape6, sloAxis6, sloTint6, sloDecay1.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 7 && (sloCloudMask & 128) != 0) total += slo_cloud_slot(sloLight7, sloShape7, sloAxis7, sloTint7, sloDecay1.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 8 && (sloCloudMask & 256) != 0) total += slo_cloud_slot(sloLight8, sloShape8, sloAxis8, sloTint8, sloDecay2.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 9 && (sloCloudMask & 512) != 0) total += slo_cloud_slot(sloLight9, sloShape9, sloAxis9, sloTint9, sloDecay2.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 10 && (sloCloudMask & 1024) != 0) total += slo_cloud_slot(sloLight10, sloShape10, sloAxis10, sloTint10, sloDecay2.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 11 && (sloCloudMask & 2048) != 0) total += slo_cloud_slot(sloLight11, sloShape11, sloAxis11, sloTint11, sloDecay2.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 12 && (sloCloudMask & 4096) != 0) total += slo_cloud_slot(sloLight12, sloShape12, sloAxis12, sloTint12, sloDecay3.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 13 && (sloCloudMask & 8192) != 0) total += slo_cloud_slot(sloLight13, sloShape13, sloAxis13, sloTint13, sloDecay3.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 14 && (sloCloudMask & 16384) != 0) total += slo_cloud_slot(sloLight14, sloShape14, sloAxis14, sloTint14, sloDecay3.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 15 && (sloCloudMask & 32768) != 0) total += slo_cloud_slot(sloLight15, sloShape15, sloAxis15, sloTint15, sloDecay3.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 16 && (sloCloudMask & 65536) != 0) total += slo_cloud_slot(sloLight16, sloShape16, sloAxis16, sloTint16, sloDecay4.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 17 && (sloCloudMask & 131072) != 0) total += slo_cloud_slot(sloLight17, sloShape17, sloAxis17, sloTint17, sloDecay4.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 18 && (sloCloudMask & 262144) != 0) total += slo_cloud_slot(sloLight18, sloShape18, sloAxis18, sloTint18, sloDecay4.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 19 && (sloCloudMask & 524288) != 0) total += slo_cloud_slot(sloLight19, sloShape19, sloAxis19, sloTint19, sloDecay4.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 20 && (sloCloudMask & 1048576) != 0) total += slo_cloud_slot(sloLight20, sloShape20, sloAxis20, sloTint20, sloDecay5.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 21 && (sloCloudMask & 2097152) != 0) total += slo_cloud_slot(sloLight21, sloShape21, sloAxis21, sloTint21, sloDecay5.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 22 && (sloCloudMask & 4194304) != 0) total += slo_cloud_slot(sloLight22, sloShape22, sloAxis22, sloTint22, sloDecay5.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 23 && (sloCloudMask & 8388608) != 0) total += slo_cloud_slot(sloLight23, sloShape23, sloAxis23, sloTint23, sloDecay5.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 24 && (sloCloudMask & 16777216) != 0) total += slo_cloud_slot(sloLight24, sloShape24, sloAxis24, sloTint24, sloDecay6.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 25 && (sloCloudMask & 33554432) != 0) total += slo_cloud_slot(sloLight25, sloShape25, sloAxis25, sloTint25, sloDecay6.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 26 && (sloCloudMask & 67108864) != 0) total += slo_cloud_slot(sloLight26, sloShape26, sloAxis26, sloTint26, sloDecay6.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 27 && (sloCloudMask & 134217728) != 0) total += slo_cloud_slot(sloLight27, sloShape27, sloAxis27, sloTint27, sloDecay6.w, scene_pos, view_dir, density, sloCloudParams);
    if (live > 28 && (sloCloudMask & 268435456) != 0) total += slo_cloud_slot(sloLight28, sloShape28, sloAxis28, sloTint28, sloDecay7.x, scene_pos, view_dir, density, sloCloudParams);
    if (live > 29 && (sloCloudMask & 536870912) != 0) total += slo_cloud_slot(sloLight29, sloShape29, sloAxis29, sloTint29, sloDecay7.y, scene_pos, view_dir, density, sloCloudParams);
    if (live > 30 && (sloCloudMask & 1073741824) != 0) total += slo_cloud_slot(sloLight30, sloShape30, sloAxis30, sloTint30, sloDecay7.z, scene_pos, view_dir, density, sloCloudParams);
    if (live > 31 && (sloCloudMask & 2147483648) != 0) total += slo_cloud_slot(sloLight31, sloShape31, sloAxis31, sloTint31, sloDecay7.w, scene_pos, view_dir, density, sloCloudParams);
    // `clouds.intensity` is the whole scale here, and that is deliberate: it is the one knob a player can
    // reason about, and the light's own brightness already arrives normalised -- the bridge packs
    // `brightness / 15`, so `light.w` is 0..1 and intensity 1.0 means "the patch is about half as bright
    // as a white cloud", 2.0 (the default) about two thirds. Neither the pack's blocklight scale (6.0) nor
    // the pack-lighting slider belongs here: those are about surfaces, and mixing them in is what made one
    // lamp arrive at ~12 and clip every pixel inside a wide contour of the falloff curve straight to white
    // -- the reason the patch looked uniform and only changed size.
    //
    // The soft compression keeps that from coming back at any slider position. It is monotone, so a
    // gradient always survives, and it preserves hue: the magnitude is compressed, the direction is not.
    // m -> m/(1+m) can never exceed 1, so no amount of intensity can turn the patch into flat white.
    vec3 added = sloCloudParams.w * total;
    float m = max(max(added.r, added.g), added.b);
    return added / (1.0 + m);
}

// The instant half of the same term, for the composite pass.
//
// The cloud the player sees is not this frame's raymarch: the pack renders clouds at a fraction of the
// resolution and mixes them with the previous frames (d1_clouds -> d2_clouds_upscaling), so a light that
// goes out fades off the cloud over about a second. That is right for the cloud itself -- the noise, the
// wind and the cloud shape all move together -- but wrong for a light: a muzzle flash stops existing the
// moment it stops firing.
//
// So the term is split. This half is added after the upsampling, in the pass that composites the cloud
// into the image, and therefore belongs to the current frame alone: light off, cloud dark, next frame.
// It is evaluated once per pixel at the cloud's own distance rather than along the ray, which is the
// trade the split exists to make explicit -- the player buys response with `clouds.integration` and the
// rest stays in the march, where it integrates and keeps the moving patch a shell leaves behind.
//
// Fully self-contained (no pack function is called) so the block can be spliced in anywhere, including
// before the file that defines the space conversions. The caller reconstructs the position:
//   scene_pos = direction_to_the_pixel * apparent_distance   (camera-relative, the light uniforms' space)
vec3 slo_cloud_light_screen(vec3 scene_pos, vec3 view_dir, float transmittance) {
    if (sloCloudParams.x <= 0.0 || sloCloudMask == 0 || sloLightCount <= 0) return vec3(0.0);
    if (sloCloudSplit >= 1.0) return vec3(0.0);
    // A cloud pixel is one the pack did not see through. The upsampled transmittance is the only density
    // the composite pass has, and using it here is what keeps a thin cloud from answering as loudly as a
    // thick one -- and what keeps a pixel with no cloud at all (transmittance 1) dark.
    float coverage = min(max(1.0 - transmittance, 0.0), 1.0);
    if (coverage <= 0.0) return vec3(0.0);
    return (1.0 - sloCloudSplit) * coverage *
        slo_cloud_light(scene_pos, view_dir, coverage);
}

#endif // SLO_CLOUDS_INCLUDED
