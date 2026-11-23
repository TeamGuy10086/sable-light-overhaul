# Spotlights in the composite: the aperture glow and the beam volume.
#
# Both are additive light written into colortex0 from c1_blend_layers, which runs before c2 (depth of field) and the
# bloom passes -- so they bloom, fog and depth test with the rest of the scene, and neither needs geometry, a render
# type or an extra pass.
#
# Correctness notes that cost a round each:
#   * the mouth is published in SCENE space, so it must be pushed through the model-view before projecting; using the
#     projection alone put the glow at the wrong place on screen.
#   * a cone equation describes a DOUBLE cone. Without the half-space test the mirror cone behind the lamp glows too,
#     which also looked like the beam shining through walls.
#   * the segment must be clipped to the beam's own reach as well as to the scene depth, or the beam is longer than
#     the lamp's range.
#   * a screen-space march cannot answer "can the LAMP see this point" (it answers "can the camera see it", which is
#     what the depth clip already does). The occlusion has to come from data in the lamp's own frame; the mod casts
#     it and hands it over as a texture, and this file only reads it. See docs/DEVELOPMENT.zh-CN.md 6.24.
param(
    [Parameter(Mandatory = $true)][string]$OutShaders,
    [switch]$Complementary,
    [switch]$Mellow
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$rel = if ($Mellow) { 'program/composite1.fsh' } elseif ($Complementary) { 'program/composite1.glsl' } else { 'program/c1_blend_layers.fsh' }
$path = Join-Path $OutShaders $rel
if (-not (Test-Path $path)) { throw "no blend pass at $path" }

$text = [System.IO.File]::ReadAllText($path, $utf8NoBom)
if ($text -match 'SLO_PER_LIGHT_VOLUME') {
    Write-Output "spotlights -> $rel already patched"
    return
}
if ($text -match 'sloBeamA0') {
    throw 'this blend pass carries an older spotlight patch; regenerate patch/photon/shaders from the baseline'
}

$nl = "`n"
$decl = "// SLO SPOTLIGHTS BEGIN // SLO_PER_LIGHT_VOLUME" + $nl
$decl += "uniform int sloFlareCount;" + $nl
$decl += "// Rays per side of one beam's shadow map. Must match SpotShadows.RAYS on the mod side." + $nl
$decl += "#define SLO_SPOT_SHADOW_RAYS 32" + $nl
$decl += "uniform sampler2D sloSpotShadow;" + $nl
$decl += "uniform float sloBeamVolume;${nl}uniform float sloBeamGlow;${nl}uniform float sloBeamDebug;${nl}uniform float sloBeamWidth;${nl}uniform float sloBeamSamples;${nl}"
for ($i = 0; $i -lt 8; $i++) {
    $decl += "uniform vec4 sloBeamA$i;${nl}uniform vec4 sloBeamB$i;${nl}uniform vec4 sloBeamC$i;${nl}uniform vec4 sloBeamD$i;${nl}uniform vec4 sloBeamE$i;${nl}uniform vec4 sloBeamF$i;${nl}uniform vec4 sloBeamG$i;${nl}"
}
for ($i = 0; $i -lt 128; $i++) {
    $decl += "uniform vec4 sloFlareA$i;${nl}uniform vec4 sloFlareC$i;${nl}"
}
for ($i = 0; $i -lt 32; $i++) { $decl += "uniform vec4 sloSourceGlow$i;${nl}" }
$decl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_glow_visibility.glsl'), $utf8NoBom) + $nl
$decl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_flare_glow.glsl'), $utf8NoBom) + $nl
$decl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_beam_interval.glsl'), $utf8NoBom) + $nl
$decl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_spot_shadow.glsl'), $utf8NoBom) + $nl

$aperture = $nl + "    {" + $nl
$aperture += "        ivec2 slo_asize = textureSize(colortex0, 0);" + $nl
$aperture += "        vec2 slo_ares = vec2(slo_asize);" + $nl
$aperture += "        vec3 sloMissileCores=vec3(0.0), sloMissileHalos=vec3(0.0);" + $nl
$aperture += "        float slo_aaspect = slo_ares.x / max(slo_ares.y, 1.0);" + $nl
$aperture += "        vec2 slo_auv = gl_FragCoord.xy / slo_ares;" + $nl
$aperture += "        float slo_beamGlow = sloBeamGlow;" + $nl
for ($i = 0; $i -lt 8; $i++) {
    $aperture += @"

        if (sloBeamA$i.w > 0.0) {
            vec3 slo_am$i = sloBeamA$i.xyz;
            float slo_adist$i = length(slo_am$i);
            vec3 slo_aav$i = sloBeamB$i.xyz;
            float slo_areach$i = max(length(slo_aav$i), 0.01);
            vec3 slo_aax$i = slo_aav$i / slo_areach$i;
            vec3 slo_atom$i = slo_am$i / max(slo_adist$i, 0.001);
            if (dot(slo_atom$i, -slo_aax$i) > sloBeamB$i.w || dot(-slo_atom$i, slo_aax$i) > 0.94) {
                vec4 slo_aclip$i = gbufferProjection * (vec4(mat3(gbufferModelView) * slo_am$i, 1.0));
                if (slo_aclip$i.w > 0.001) {
                    vec2 slo_ac$i = slo_aclip$i.xy / slo_aclip$i.w * 0.5 + 0.5;
                    float slo_visibility$i = slo_glow_visibility(slo_am$i, slo_ac$i, 0.9);
                    vec2 slo_ad$i = (slo_auv - slo_ac$i) * vec2(slo_aaspect, 1.0);
                    float slo_ar$i = length(slo_ad$i);
                    // The core is the lamp's own opening, so it shrinks with distance. Everything around it is a
                    // lens artifact and stays roughly one size on screen, which is why the flare below is written
                    // in screen units and only widened a little by the core's angular radius.
                    float slo_aap$i = max(sloBeamA$i.w / max(slo_adist$i, 0.5), 0.00002);
                    float slo_acore$i = exp(-slo_ar$i * slo_ar$i / (slo_aap$i * slo_aap$i * 0.60));
                    // Anamorphic streak: a thin hot line inside a wider soft band. One gaussian reads as a bar,
                    // two read as a lens.
                    float slo_asy$i = 0.0045 + slo_aap$i * 0.30;
                    // Capped, and driven mostly by the mouth's angular size rather than by a fixed screen size: the
                    // uncapped form reached 0.47 of the screen height at four blocks, so the streak ran edge to edge
                    // the moment the camera came close to the lamp.
                    float slo_asx$i = min(0.34, 0.055 + slo_aap$i * 3.5);
                    float slo_astk$i = exp(-slo_ad$i.y * slo_ad$i.y / (slo_asy$i * slo_asy$i))
                        * exp(-slo_ad$i.x * slo_ad$i.x / (slo_asx$i * slo_asx$i * 0.45));
                    float slo_aline$i = exp(-slo_ad$i.y * slo_ad$i.y / (slo_asy$i * slo_asy$i * 0.09))
                        * exp(-slo_ad$i.x * slo_ad$i.x / (slo_asx$i * slo_asx$i * 1.1));
                    // Two halos, both falling off monotonically with radius. There used to be a ring here, and a
                    // ring plus a small core plus nothing between them is the shape of an astronomical black hole:
                    // bright circle, dark gap, hot dot. It is a LAMP -- the brightness has to decrease outward the
                    // whole way, so there is no gap to read as an event horizon. A lens ring can come back as a
                    // faint addition on top of the halo if it is ever wanted; it must not be the dominant feature.
                    float slo_ahalo$i = exp(-slo_ar$i / (0.011 + slo_aap$i * 0.55));
                    float slo_awide$i = exp(-slo_ar$i / (0.050 + slo_aap$i * 1.9));
                    float slo_anear$i = clamp(1.0 - slo_adist$i / slo_areach$i, 0.0, 1.0);
                    fragment_color.rgb += sloBeamC$i.rgb
                        * (slo_acore$i + 0.60 * slo_aline$i + 0.30 * slo_astk$i
                           + 0.55 * slo_ahalo$i + 0.20 * slo_awide$i)
                        * sloBeamC$i.w * (0.35 + 0.65 * slo_anear$i) * slo_beamGlow * slo_visibility$i;
                }
            }
        }
"@
}
for ($i = 0; $i -lt 128; $i++) {
    $aperture += "        if (sloFlareCount > $i) { if ($i >= sloFlareCount-sloMissileGlowCount) slo_missile_glow(sloFlareA$i,sloFlareC$i,slo_auv,slo_ares,sloMissileCores,sloMissileHalos); else fragment_color.rgb += slo_flare_glow(sloFlareA$i, sloFlareC$i, slo_auv, slo_ares) * slo_beamGlow; }" + $nl
}
$aperture += "        fragment_color.rgb += (sloMissileCores+slo_missile_halo_resolve(sloMissileHalos))*slo_beamGlow;" + $nl
for ($i = 0; $i -lt 32; $i++) {
    $aperture += "        if (sloSourceGlow$i.w > 0.5) fragment_color.rgb += slo_point_glow(vec4(sloLight$i.xyz,0.14),vec4(sloTint$i.rgb,sloLight$i.w),sloSourceGlow$i,slo_auv,slo_ares)*slo_beamGlow;" + $nl
}
$aperture += "    }" + $nl

# Tier 0 draws nothing at all here: the aperture above still glows, which is the intended "lamp on, no shaft".
# Plasma uses the same composite and view ray as the spotlight, with a finite cylindrical cross section.
$decl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source/slo_plasma_volume.glsl'), $utf8NoBom) + $nl
if (!$Complementary -and !$Mellow) { $decl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source/slo_plasma_lens.glsl'), $utf8NoBom) + $nl }
$decl += "#ifndef SLO_PLASMA_SOURCES${nl}#define SLO_PLASMA_SOURCES${nl}uniform vec4 sloPlasmaExtinction;$nl"
for ($i = 0; $i -lt 4; $i++) {
    $decl += "uniform vec4 sloPlasmaStart$i, sloPlasmaAxis$i, sloPlasmaFlow$i;$nl"
}
$decl += "#endif$nl"
$activePlasma = (0..3 | ForEach-Object { "sloPlasmaStart$_.w > 0.0" }) -join ' || '
$plasmaWorldPixel = if ($Complementary) { 'z0 > 0.56' } elseif ($Mellow) { 'true' } else { '!front_is_hand' }
if (!$Complementary -and !$Mellow) { $aperture += "#ifdef SLO_PLASMA_COLUMN$nl" }
$aperture += "    if ($plasmaWorldPixel && ($activePlasma)) {$nl"
$plasmaRayExpr = if (!$Complementary -and !$Mellow) { 'direction_world' } else { 'normalize(front_position_world - cameraPosition)' }
$aperture += "        vec3 plasmaRay = $plasmaRayExpr;$nl"
$surfaceDistance = if ($Mellow) { 'slo_scene_distance(texcoord, false)' } elseif ($Complementary) { 'slo_scene_distance(texCoord, true)' } else { 'view_distance' }
$aperture += "        vec3 plasmaSurface = plasmaRay * $surfaceDistance;$nl"
$aperture += "        vec3 plasmaRadiance = vec3(0.0); float plasmaTransmission = 1.0;$nl"
for ($i = 0; $i -lt 4; $i++) {
    $aperture += "        if (sloPlasmaStart$i.w > 0.0) { vec4 plasma = slo_plasma_light(plasmaRay, plasmaSurface, sloPlasmaStart$i, sloPlasmaAxis$i, sloPlasmaFlow$i, sloBeamVolume); plasma *= (1.0 - sloPlasmaExtinction[$i]); plasmaRadiance += plasma.rgb; plasmaTransmission *= 1.0 - plasma.a; }$nl"
    if (!$Complementary -and !$Mellow) { $aperture += "        if (sloPlasmaStart$i.w > 0.0) plasmaRadiance += (1.0 - sloPlasmaExtinction[$i]) * slo_plasma_lens(sloPlasmaStart$i, sloPlasmaAxis$i, gl_FragCoord.xy / view_res, view_res.x / view_res.y, -front_position_view.z);$nl" }
}
$aperture += "        if (plasmaRadiance.r > 0.0) fragment_color.rgb = fragment_color.rgb * mix(1.0, plasmaTransmission, sloPlasmaVolumeMix) + plasmaRadiance;$nl"
$aperture += "    }$nl"
if (!$Complementary -and !$Mellow) { $aperture += "#endif$nl" }

$volume = $nl + "    // SLO VOLUME INTEGRATION" + $nl + "    {" + $nl
$volume += "    vec3 slo_vray = normalize(front_position_world - cameraPosition);" + $nl
$volume += "    float slo_vscene = length(front_position_world - cameraPosition);" + $nl
for ($i = 0; $i -lt 8; $i++) {
    $volume += @"

        if (sloBeamA$i.w > 0.0 && sloBeamD$i.y > 0.5 && (sloBeamD$i.y < 1.5 || sloBeamE$i.w > 0.5)) {
            vec3 slo_m$i = sloBeamA$i.xyz;
            vec3 slo_bv$i = sloBeamB$i.xyz;
            float slo_reach$i = max(length(slo_bv$i), 0.01);
            vec3 slo_ax$i = slo_bv$i / slo_reach$i;
            float slo_cosRaw$i = sloBeamB$i.w;
            float slo_bw$i = sloBeamWidth > 0.05 ? sloBeamWidth : 1.0;
            float slo_cosB$i = clamp(slo_cosRaw$i, 0.001, 0.999999);
            float slo_tan$i = sqrt(max(1.0 - slo_cosB$i * slo_cosB$i, 0.0)) / slo_cosB$i * slo_bw$i;
            float slo_cos2$i = 1.0 / (1.0 + slo_tan$i * slo_tan$i);
            float slo_vd$i = dot(slo_vray, slo_ax$i);
            vec3 slo_w$i = -slo_m$i;
            float slo_wd$i = dot(slo_w$i, slo_ax$i);
            float slo_vw$i = dot(slo_vray, slo_w$i);
            {
                vec2 slo_interval$i = slo_beam_interval(slo_vd$i, slo_wd$i, slo_vw$i,
                    dot(slo_w$i, slo_w$i), slo_cos2$i, slo_reach$i, slo_vscene, sloBeamD$i.z);
                float slo_lo$i = slo_interval$i.x;
                float slo_hi$i = slo_interval$i.y;
                if (slo_hi$i > slo_lo$i) {
                    // Everything from here is an integral over [lo, hi], sampled at twelve fixed midpoints. No single
                    // point of the segment is allowed to decide the pixel: that is what put a disc at the far
                    // end three times over -- the inside test, the lateral test and the tail all read one
                    // sample and threw the whole segment away when that one sample landed outside.
                    float slo_len$i = slo_hi$i - slo_lo$i;
                    float slo_phase$i = 0.30 + 0.70 * pow(max(dot(slo_vray, -slo_ax$i), 0.0), 2.0);
                    // The lamp's own cross-section profile, transcribed from BeamLight.profile: the shaft and
                    // the lit spot have to fade at the same radius. Mode 1, the default, is already down to
                    // 5% at half the cone. The +1 encoding lets zero mean "this jar sent nothing".
                    float slo_bmode$i = max(sloBeamD$i.x - 1.0, 0.0);
                    float slo_pcore$i = slo_bmode$i < 0.5 ? 0.999999
                        : (slo_bmode$i < 1.5 ? 0.0 : (slo_bmode$i < 2.5 ? 0.0 : (slo_bmode$i < 3.5 ? 0.25 : 0.1)));
                    float slo_pmid$i = slo_bmode$i < 0.5 ? 0.9999999
                        : (slo_bmode$i < 1.5 ? 0.4 : (slo_bmode$i < 2.5 ? 0.5 : (slo_bmode$i < 3.5 ? 0.7 : 0.65)));
                    float slo_ptail$i = slo_bmode$i < 0.5 ? 0.0
                        : (slo_bmode$i < 1.5 ? 0.05 : (slo_bmode$i < 2.5 ? 0.5 : (slo_bmode$i < 3.5 ? 0.35 : 0.2)));
                    float slo_pgain$i = slo_bmode$i < 3.5 ? 1.0 : 0.2;
                    // 1.0 = the whole geometric cone; lower makes the shaft narrower than the light, which only
                    // makes sense for the curved profiles. Falls back to 1.0 when the jar does not send it.
                    float slo_sum$i = 0.0;
                    float slo_lit$i = 0.0;
                    float slo_inside$i = 0.0;
                    int slo_steps$i = int(clamp(sloBeamSamples > 0.5 ? sloBeamSamples : 12.0, 3.0, 64.0));
                    for (int slo_k = 0; slo_k < slo_steps$i; slo_k++) {
                        float slo_t$i = slo_lo$i + slo_len$i * ((float(slo_k) + 0.5) / float(slo_steps$i));
                        vec3 slo_p$i = slo_vray * slo_t$i - slo_m$i;
                        float slo_al$i = dot(slo_p$i, slo_ax$i);
                        // Behind the mouth or past the reach: this tap is outside the beam and says nothing.
                        if (slo_al$i <= 0.0 || slo_al$i >= slo_reach$i) { continue; }
                        float slo_across$i = length(slo_p$i - slo_ax$i * slo_al$i)
                            / max(sloBeamD$i.z + slo_al$i * slo_tan$i, 1.0e-4);
                        // Outside the shaft is not an unshadowed sample, including in debug mode.
                        if (slo_across$i >= 1.0) { continue; }
                        slo_inside$i += 1.0;
                        float slo_cross$i = slo_across$i <= slo_pcore$i ? 1.0
                            : (slo_across$i <= slo_pmid$i
                                ? 1.0 - (1.0 - slo_ptail$i) * (slo_across$i - slo_pcore$i)
                                    / max(slo_pmid$i - slo_pcore$i, 1.0e-5)
                                : slo_ptail$i * (1.0 - slo_across$i) / max(1.0 - slo_pmid$i, 1.0e-5));
                        slo_cross$i = pow(max(slo_cross$i, 0.0), 0.45)
                            * (1.0 - smoothstep(0.92, 1.0, slo_across$i));
                        // Flat to three quarters of the reach, then straight down: the ship lamps' own rule.
                        float slo_tail$i = clamp((1.0
                            - clamp(length(slo_p$i) / slo_reach$i, 0.0, 1.0)) * 4.0, 0.0, 1.0);
                        float slo_litk$i = 1.0;
                        if (sloBeamD$i.y > 1.5) {
                            slo_litk$i = slo_spot_visibility(slo_p$i,sloBeamE$i,sloBeamF$i,sloBeamG$i,sloBeamD$i.w);
                        }
                        slo_sum$i += slo_cross$i * slo_tail$i * slo_litk$i;
                        slo_lit$i += slo_litk$i;
                    }
                    // A hidden lamp aperture does not hide air elsewhere in its cone. Viewer depth clips
                    // each ray above; the lamp-space map determines whether light can reach each sample.
                    float slo_scatter$i = (1.0 - exp(-slo_len$i * 0.05)) * slo_phase$i
                        * (slo_sum$i * (1.0 / float(slo_steps$i))) * slo_pgain$i;
                    if (sloBeamDebug > 0.5) {
                        // False colour drawn instead of the light, so one screenshot says what the taps saw:
                        // white = all inside taps lit, blue = partially shadowed, red = no inside tap.
                        vec3 slo_dbg$i = vec3(1.0);
                        if (slo_inside$i < 0.5) slo_dbg$i = vec3(1.0, 0.0, 0.0);
                        else if (slo_lit$i < slo_inside$i - 0.5)
                            slo_dbg$i = vec3(0.0, 0.2 + 0.78 * slo_lit$i / slo_inside$i, 1.0);
                        fragment_color.rgb = mix(fragment_color.rgb, slo_dbg$i * 0.22, 0.75);
                    } else {
                        fragment_color.rgb += sloBeamC$i.rgb * sloBeamC$i.w * slo_scatter$i
                            * sloBeamVolume;
                    }
                }
            }
        }
"@
}
$volume += "    }" + $nl + "// SLO SPOTLIGHTS END" + $nl
if ($Mellow) {
    $visibility = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source/slo_glow_visibility.glsl'), $utf8NoBom)
    $decl = $decl.Replace($visibility, '')
    $slots = (0..31 | ForEach-Object { "uniform vec4 sloLight$_, sloTint$_;$nl" }) -join ''
    $decl = '#include "/lib/slo/depth.glsl"' + $nl + $slots + $decl
    $decl += $nl + 'vec3 slo_world_ray(vec2 uv) { return normalize(mat3(gbufferModelViewInverse) * slo_pixel_ray(gbufferProjectionInverse, uv - 0.5 * taaJitter)); }' + $nl
    $decl = $decl.Replace('vec4 style, vec2 uv, vec2 resolution) {',
        'vec4 style, vec2 uv, vec2 resolution, vec2 gain) {')
    $decl = $decl.Replace('0.75/max(resolution.y,1.0)', '1.5/max(resolution.y,1.0)')
    $decl = $decl.Replace('core+0.55*halo+0.20*wide', 'gain.x*core+gain.y*(0.55*halo+0.20*wide)')
    $decl = $decl.Replace('0.60*line+0.30*streak', 'gain.y*(0.60*line+0.30*streak)')
    $decl = $decl.Replace('return slo_point_glow(source,tint,vec4(1.0),uv,resolution);',
        'return slo_point_glow(source,tint,vec4(1.0),uv,resolution,vec2(SLO_MELLOW_FLARE_CORE,SLO_MELLOW_FLARE_HALO));')
    $aperture = $aperture.Replace('slo_auv,slo_ares)*slo_beamGlow;',
        'slo_auv,slo_ares,vec2(SLO_MELLOW_EMISSION))*slo_beamGlow;')
    $aperture = $aperture.Replace('slo_ares) * slo_beamGlow;', 'slo_ares);')
    $aperture = $aperture.Replace('* slo_beamGlow * slo_visibility', '* SLO_MELLOW_EMISSION * slo_beamGlow * slo_visibility')
    $activeBeam = (0..7 | ForEach-Object { "(sloBeamA$_.w > 0.0 && sloBeamD$_.y > 0.5)" }) -join ' || '
    for ($i = 0; $i -lt 8; $i++) {
        $volume = $volume.Replace("(sloBeamD$i.y < 1.5 || sloBeamE$i.w > 0.5)", "(SLO_MELLOW_LIGHT_SHADOWS == 0 || sloBeamD$i.y < 1.5 || sloBeamE$i.w > 0.5)")
        $volume = $volume.Replace("if (sloBeamD$i.y > 1.5) {", "if (SLO_MELLOW_LIGHT_SHADOWS == 1 && sloBeamD$i.y > 1.5) {")
    }
    $volume = "    if (sloBeamVolume > 0.0 && ($activeBeam)) {$nl" + $volume + "    }$nl"
    $body = ($aperture + $volume).Replace('fragment_color.rgb', 'Color.rgb')
    $body = $body.Replace('normalize(front_position_world - cameraPosition)', 'slo_world_ray(texcoord)')
    $body = $body.Replace('length(front_position_world - cameraPosition)', 'min(slo_scene_distance(texcoord, false), slo_cloud_distance(texcoord))')
    $text = $text.Replace('void main() {', $decl + $nl + 'void main() {')
    $at = $text.LastIndexOf("`n}")
    [IO.File]::WriteAllText($path, $text.Insert($at, $body + $nl), $utf8NoBom)
    Write-Output "spotlights -> $rel"
    return
}

if ($Complementary) {
    # The shared light include supplies the point light and tint slots.
    $decl = $decl.Replace('#define SLO_SPOT_SHADOW_RAYS 32', "#define SLO_SPOT_SHADOW_RAYS 32${nl}#include `"/lib/slo/surface.glsl`"")
    $decl = $decl.Replace([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source/slo_glow_visibility.glsl'), $utf8NoBom), '#include "/lib/slo/glow_visibility.glsl"')
    $flareFile = Join-Path $OutShaders 'program/composite5.glsl'
    $flareText = [IO.File]::ReadAllText($flareFile, $utf8NoBom)
    $flareSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source/slo_flare_glow.glsl'), $utf8NoBom)
    $flareSource = $flareSource.Replace('vec4 style, vec2 uv, vec2 resolution) {', 'vec4 style, vec2 uv, vec2 resolution, vec2 gain) {')
    $flareSource = $flareSource.Replace('0.75/max(resolution.y,1.0)', '1.5/max(resolution.y,1.0)')
    $flareSource = $flareSource.Replace('core+0.55*halo+0.20*wide', 'gain.x*core+gain.y*(0.55*halo+0.20*wide)')
    $flareSource = $flareSource.Replace('0.60*line+0.30*streak', 'gain.y*(0.60*line+0.30*streak)')
    $flareSource = $flareSource.Replace('return slo_point_glow(source,tint,vec4(1.0),uv,resolution);',
        'return slo_point_glow(source,tint,vec4(1.0),uv,resolution,vec2(SLO_COMPLEMENTARY_SIGNAL_CORE, SLO_COMPLEMENTARY_SIGNAL_HALO));')
    $flareDecl = '#include "/lib/slo/depth.glsl"' + $nl + '#include "/lib/slo/glow_visibility.glsl"' + $nl
    $flareDecl += "uniform int sloFlareCount;" + $nl
    $flareBody = "    vec2 sloResolution = vec2(viewWidth, viewHeight);${nl}    vec3 sloMissileCores = vec3(0.0), sloMissileHalos = vec3(0.0);${nl}"
    for ($i = 0; $i -lt 128; $i++) {
        $flareDecl += "uniform vec4 sloFlareA$i; uniform vec4 sloFlareC$i;${nl}"
        $flareBody += "    if (sloFlareCount > $i) { if ($i >= sloFlareCount-sloMissileGlowCount) slo_missile_glow(sloFlareA$i,sloFlareC$i,texCoord,sloResolution,sloMissileCores,sloMissileHalos); else color += slo_flare_glow(sloFlareA$i, sloFlareC$i, texCoord, sloResolution); }${nl}"
        $aperture = $aperture.Replace("        if (sloFlareCount > $i) { if ($i >= sloFlareCount-sloMissileGlowCount) slo_missile_glow(sloFlareA$i,sloFlareC$i,slo_auv,slo_ares,sloMissileCores,sloMissileHalos); else fragment_color.rgb += slo_flare_glow(sloFlareA$i, sloFlareC$i, slo_auv, slo_ares) * slo_beamGlow; }" + $nl, '')
    }
    $flareBody += "    color += sloMissileCores+slo_missile_halo_resolve(sloMissileHalos);${nl}"
    $flareAnchor = '    #ifdef COLORGRADING'
    $flareMain = 'void main() {'
    if ($flareText.IndexOf($flareMain) -lt 0 -or $flareText.IndexOf($flareAnchor) -lt 0 -or
        $flareText.IndexOf($flareAnchor) -ne $flareText.LastIndexOf($flareAnchor)) {
        throw 'Expected fragment main and unique color grading anchor in composite5'
    }
    $flareText = $flareText.Insert($flareText.IndexOf($flareMain), $flareDecl + $flareSource + $nl)
    $flareText = $flareText.Insert($flareText.IndexOf($flareAnchor), $flareBody + $nl)
    [IO.File]::WriteAllText($flareFile, $flareText, $utf8NoBom)
    $main = 'void main() {'
    $at = $text.IndexOf($main, [StringComparison]::Ordinal)
    if ($at -lt 0) { throw "no fragment main in $rel" }
    $text = $text.Insert($at, $decl + $nl)
    $output = '    /* DRAWBUFFERS:0 */'
    $at = $text.IndexOf($output, [StringComparison]::Ordinal)
    if ($at -lt 0 -or $text.LastIndexOf($output, [StringComparison]::Ordinal) -ne $at) {
        throw "expected a single composite color output in $rel"
    }
    $activeBeam = (0..7 | ForEach-Object { "sloBeamA$_.w > 0.0 && sloBeamD$_.y > 0.5" }) -join ' || '
    $volume = "${nl}    if (sloBeamVolume > 0.0 && ($activeBeam)) {${nl}" + $volume + "${nl}    }${nl}"
    $aperture = $aperture.Replace('float slo_beamGlow = sloBeamGlow;', 'float slo_beamGlow = sloBeamGlow * SLO_COMPLEMENTARY_EMISSION;')
    $body = ($aperture + $volume).Replace('fragment_color.rgb', 'color')
    $body = $body.Replace('normalize(front_position_world - cameraPosition)', 'normalize(mat3(gbufferModelViewInverse) * viewPos.xyz)')
    $body = $body.Replace('length(front_position_world - cameraPosition)', 'min(slo_scene_distance(texCoord, true), slo_cloud_distance(texCoord))')
    $text = $text.Insert($at, $body + $nl)
    [IO.File]::WriteAllText($path, $text, $utf8NoBom)
    Write-Output "spotlights -> $rel"
    return
}

$include = '#include "/include/utility/space_conversion.glsl"'
$at = $text.IndexOf($include)
if ($at -lt 0) { throw 'the blend pass does not include space_conversion where expected' }
$text = $text.Insert($at + $include.Length, $nl + $decl)
$last = $text.LastIndexOf("`n}")
if ($last -lt 0) { throw 'the blend pass has no closing brace to insert the spotlights before' }
$text = $text.Insert($last, $aperture + $volume)
# A replacement written with single quotes keeps "\n" as two characters; GLSL then sees them as tokens. Turn any
# that survived into real line breaks before the file is written.
$text = $text.Replace([string][char]92 + 'n', $nl)
[System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
Write-Output ("spotlights -> {0} ({1} bytes)" -f $rel, ([System.IO.File]::ReadAllBytes($path)).Length)
