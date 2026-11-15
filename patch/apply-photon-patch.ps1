$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$src = (Join-Path $PSScriptRoot 'baselines\photon\shaders\include\lighting\diffuse_lighting.glsl')
$fragment = (Join-Path $PSScriptRoot 'source\slo_light.glsl')
$cloudFragment = (Join-Path $PSScriptRoot 'source\slo_clouds.glsl')
$outDir = (Join-Path $PSScriptRoot 'photon\shaders\include\lighting')
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$text = [System.IO.File]::ReadAllText($src, $utf8NoBom)
$block = [System.IO.File]::ReadAllText($fragment, $utf8NoBom)
# The cloud block is self-contained and keeps its own `#ifndef INCLUDE_LIGHTING_SLO_LIGHT` guard on
# purpose: in a program that already has the surface block the guard skips its uniform declarations,
# and in one that does not (Photon's fancy clouds) it declares them. Do not strip it here.
$cloudBlock = [System.IO.File]::ReadAllText($cloudFragment, $utf8NoBom).Trim() + [string][char]10

$block = [regex]::Replace($block, '^\s*#ifndef\s+INCLUDE_LIGHTING_SLO_LIGHT\s*', '')
$block = [regex]::Replace($block, '#endif\s*//\s*INCLUDE_LIGHTING_SLO_LIGHT\s*$', '')
$block = $block.Trim() + [string][char]10

$anchor1 = '#include "/include/lighting/colors/blocklight_color.glsl"'
$anchor2 = '    lighting += material.emission * emission_scale;'

$n1 = ([regex]::Matches($text, [regex]::Escape($anchor1))).Count
$n2 = ([regex]::Matches($text, [regex]::Escape($anchor2))).Count
Write-Output "anchor1 occurrences = $n1"
Write-Output "anchor2 occurrences = $n2"
if ($n1 -ne 1 -or $n2 -ne 1) { throw 'anchors are not unique - refusing to patch' }

$nl = [string][char]10
# The call passes the FLAT normal as well as the shading one.
#
# Photon's deferred pass replaces `normal` with the normal-mapped one when NORMAL_MAPPING is on, and that
# perturbation is per pixel. The field has to step off the surface along the face it is on, so it needs the
# geometric direction; taking it from the perturbed normal made the chosen axis flicker between neighbours pixel
# by pixel, which showed up as a blocky patchwork and as large wrongly lit areas beside a wall.
$insert2 = '    lighting += get_slo_lighting(scene_pos, normal, flat_normal, ao, light_levels.y, material.albedo);'

$text = $text.Replace($anchor1, $anchor1 + $nl + $nl + $block + $nl + $cloudBlock)
$text = $text.Replace($anchor2, $insert2 + $nl + $nl + $anchor2)

if ($text -notmatch 'get_slo_lighting\(scene_pos') { throw 'call insertion missing' }
# Every function that reads the field has to receive the flat normal, and this is checked because it was not:
# abs(flat_normal) was used inside slo_slot, which takes `normal` but not `flat_normal`, and the pack failed to
# compile with `error C1503: undefined variable "flat_normal"`. Iris then drew nothing of ours at all, which reads
# in game as "the light is completely gone" rather than as a build error.
if ($text -notmatch 'vec3 slo_slot\([^)]*vec3 normal, vec3 flat_normal') {
    throw 'slo_slot does not take the flat normal, so abs(flat_normal) inside it cannot compile' }
if ($text -notmatch 'slo_slot\(sloLight0[^;]*normal, flat_normal, ao') {
    throw 'slo_slot call sites do not pass the flat normal' }
if ($text -notmatch 'get_slo_lighting\(vec3 scene_pos, vec3 normal, vec3 flat_normal, float ao, float skylight,') {
    throw 'get_slo_lighting signature does not take the flat normal' }
if ($text -notmatch 'uniform vec4 sloLight31;') { throw 'inlined bridge missing' }
if ($text -notmatch 'vec3 slo_cloud_light\(vec3 scene_pos, vec3 view_dir, float density\)') { throw 'cloud block missing' }
if ($text -notmatch 'vec3 slo_cloud_light_screen\(vec3 scene_pos') { throw 'screen-space cloud term missing' }

$dest = Join-Path $outDir 'diffuse_lighting.glsl'
[System.IO.File]::WriteAllText($dest, $text, $utf8NoBom)

$bytes = [System.IO.File]::ReadAllBytes($dest)
$hasBom = ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
Write-Output "patched -> $dest"
Write-Output ("bytes={0}  hasBom={1}  first3={2}" -f $bytes.Length, $hasBom, (($bytes[0..2] | ForEach-Object { $_.ToString('X2') }) -join ' '))
if ($hasBom) { throw 'patched file still has a BOM' }
if ($bytes[0] -ne 0x23) { throw 'patched file does not start with #' }

# ---------------------------------------------------------------------------
# The cloud program. Photon's fancy clouds are drawn in program/d1_clouds.fsh, which does NOT include
# the surface lighting, so the cloud block has to be spliced in there directly. It goes in at the very
# top, before anything this file includes. Two reasons, both learned the hard way: an `#ifdef` is
# decided where it appears in the text, so a flag defined after the cloud layers compiles their calls
# out silently (no error, no light); and putting the block here rather than in sky/clouds.glsl keeps a
# program from declaring the 160 light uniforms twice, which is a hard redeclaration error (the outer
# include guard only covers the function definitions).
#
# One copy is enough. This used to also be inserted at the `#include "/include/sky/clouds.glsl"` anchor,
# which put the whole block in the file twice: harmless (the outer guard skipped the second copy) but
# 15 KB of text nothing could ever read, and a reader had to work out which copy counted.
$cloudSrc = (Join-Path $PSScriptRoot 'baselines\photon\shaders\program\d1_clouds.fsh')
$cloudOutDir = (Join-Path $PSScriptRoot 'photon\shaders\program')
New-Item -ItemType Directory -Force -Path $cloudOutDir | Out-Null
$cloudText = [System.IO.File]::ReadAllText($cloudSrc, $utf8NoBom)
if ($cloudText -notmatch '#include "/include/sky/clouds\.glsl"') { throw 'd1_clouds no longer includes the cloud layers' }
$cloudText = $cloudBlock + $nl + $cloudText
$blockCopies = ([regex]::Matches($cloudText, 'SLO_CLOUDS_INCLUDED')).Count
Write-Output "cloud block copies in d1_clouds = $blockCopies"
# #ifndef + #define + #endif -- three mentions of the guard name mean exactly one copy of the block.
if ($blockCopies -ne 3) { throw 'the cloud block is not in d1_clouds exactly once (guard + define + endif)' }
if ($cloudText -notmatch 'vec3 slo_cloud_light\(vec3 scene_pos, vec3 view_dir, float density\)') { throw 'cloud block missing from d1_clouds' }
# ---------------------------------------------------------------------------
# The near fade: how close a volumetric cloud may come to the camera before its samples are dropped.
#
# Why it goes inside the marching loop and not at the end of the program: `draw_clouds` returns one
# accumulated result for the whole ray, so fading that fades the entire sky instead of the part of it that is
# near the camera. Inside the loop every sample knows its own distance, and Photon computes exactly that
# quantity already for its far fade -- a sample that close covers most of the screen, so it costs the most of
# anything in the frame and reads as noise sliding over the lens rather than as cloud.
#
# Units, which cost two wrong attempts: the pack's cloud space is blocks times CLOUDS_SCALE (ten), so
# `distance_to_sample` is in tenths of a block and the key is multiplied by CLOUDS_SCALE before it is used.
#
# The value is read from the live instance's client config, so the mod's own key is the knob, and a deploy is
# what makes a changed value take effect.
$sloCloudNear = 6.0
$nearCandidates = @()
foreach ($nearRoot in @('D:\DSH\HelloMC\.minecraft\versions')) {
    if (-not (Test-Path -LiteralPath $nearRoot)) { continue }
    foreach ($nearDir in (Get-ChildItem -LiteralPath $nearRoot -Directory)) {
        $nearFile = Join-Path $nearDir.FullName 'config\sablelightoverhaul-client.toml'
        if (Test-Path -LiteralPath $nearFile) { $nearCandidates += $nearFile }
    }
}
foreach ($nearFile in $nearCandidates) {
    $nearText = [System.IO.File]::ReadAllText($nearFile)
    $nearMatch = [regex]::Match($nearText, '(?m)^\s*nearCull\s*=\s*(-?[\d.]+)')
    if ($nearMatch.Success) {
        $sloCloudNear = [double]$nearMatch.Groups[1].Value
        Write-Output "nearCull read from $nearFile = $sloCloudNear"
        break
    }
}
$sloDefine = '#define SLO_CLOUD_NEAR ' +
    $sloCloudNear.ToString([System.Globalization.CultureInfo]::InvariantCulture)
$cloudText = $sloDefine + " // blocks; cloud space is this times CLOUDS_SCALE`n" + $cloudText

# How much of the ground fog a cloud takes. The fog is composited after the cloud in this program and its
# scattering replaces the cloud's own colour as the distance grows, so at a single scale the deck turns into
# fog colour near the horizon. A cloud is a solid object with its own lit colour, not a volume of air, so it
# should keep most of that colour and only lean into the haze -- hence a fraction, read from the client config
# like the near distance is, with 0.35 as the fallback when the key is absent.
$sloCloudFogScale = 0.35
foreach ($fogFile in $nearCandidates) {
    $fogText = [System.IO.File]::ReadAllText($fogFile)
    $fogMatch = [regex]::Match($fogText, '(?m)^\s*fogScale\s*=\s*(-?[\d.]+)')
    if ($fogMatch.Success) {
        $sloCloudFogScale = [double]$fogMatch.Groups[1].Value
        Write-Output "fogScale read from $fogFile = $sloCloudFogScale"
        break
    }
}
$cloudText = '#define SLO_CLOUD_FOG_SCALE ' +
    $sloCloudFogScale.ToString([System.Globalization.CultureInfo]::InvariantCulture) +
    " // fraction of the ground fog a cloud takes; 1.0 lets the fog own the colour`n" + $cloudText
Write-Output "SLO cloud near fade = $sloCloudNear block(s); zero or less means off"

$cloudDest = Join-Path $cloudOutDir 'd1_clouds.fsh'
[System.IO.File]::WriteAllText($cloudDest, $cloudText, $utf8NoBom)
$cloudBytes = [System.IO.File]::ReadAllBytes($cloudDest)
Write-Output ("cloud patched -> {0}" -f $cloudDest)
Write-Output ("bytes={0}  hasBom={1}" -f $cloudBytes.Length, ($cloudBytes[0] -eq 0xEF))

# ---------------------------------------------------------------------------
# The per-sample call, inside the cloud layer's own march.
#
# Three statements per layer, and they only make sense together:
#
#   1. a `vec3 slo_cloud_color` accumulator, because the layer's own `scattering` is a vec2 (how much
#      direct light, how much skylight) that is turned into a colour much later, in the layer's own
#      light colour. Folding our term into `scattering.x` was the first thing that compiled -- it was
#      how the module shipped -- and it is why every light in the clouds came out white: the term was
#      reduced to a luminance and then multiplied by the sun's colour, so a red flare lit the cloud
#      white. Accumulating colour separately and adding it at the end keeps the light's own tint.
#   2. the accumulation itself, in colour, weighted by `transmittance` so it integrates along the ray
#      exactly like the sun's scattering does, and by `sloCloudSplit` so the client key can move the
#      term (see slo_clouds.glsl for what moving it buys).
#   3. the composite, inserted *before* `clouds_aerial_perspective` on purpose: our light travels
#      through the same air as the sun's, so it gets the same haze. Added after that call, a flash
#      140 blocks up would arrive as if the cloud were next to the eye.
#
# The accumulator is declared unconditionally (no `#ifdef`) because the sky-map pass renders these same
# files without the block: an unused, zero-initialised vec3 costs nothing there and keeps the composite
# line free of preprocessor conditionals.
$nl2 = $nl
$fancyLayers = @(
    'include\sky\clouds\cumulus.glsl',
    'include\sky\clouds\altocumulus.glsl',
    'include\sky\clouds\cumulus_congestus.glsl'
)
$layerOutDir = (Join-Path $PSScriptRoot 'photon\shaders\include\sky\clouds')
New-Item -ItemType Directory -Force -Path $layerOutDir | Out-Null

foreach ($file in $fancyLayers) {
    $layerSrc = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $file
    $layerText = [System.IO.File]::ReadAllText($layerSrc, $utf8NoBom)

    # 1. the accumulator, next to the layer's own. The comment is part of the anchor: each of these
    #    files holds a second, comment-less `vec2 scattering = vec2(0.0);` in an earlier function.
    $accAnchor = '    vec2 scattering = vec2(0.0); // x: direct light, y: skylight'
    if (([regex]::Matches($layerText, [regex]::Escape($accAnchor))).Count -ne 1) { throw "accumulator anchor is not unique in $file" }
    $accInsert = $accAnchor + $nl2 +
        '    // Sable Light Overhaul: our lights'' colour, kept apart from the pack''s own two scalars so' + $nl2 +
        '    // that a coloured light lights the cloud in its own colour instead of in the sun''s.' + $nl2 +
        '    vec3 slo_cloud_color = vec3(0.0);'
    $layerText = $layerText.Replace($accAnchor, $accInsert)

    # 2. the accumulation, in the march.
    $loopAnchor = '        transmittance *= step_transmittance;'
    if (([regex]::Matches($layerText, [regex]::Escape($loopAnchor))).Count -ne 1) { throw "march anchor is not unique in $file" }
    $loopInsert = '        // Sable Light Overhaul: this sample''s share of the mod''s lights (slo_cloud_light).' + $nl2 +
        '        // The sample''s camera-relative position is the offset from the ray''s origin divided back by the' + $nl2 +
        '        // air-space scale; the view direction is taken from the eye, not from the ray''s cloud entry point,' + $nl2 +
        '        // because the phase lane is defined against the line of sight.' + $nl2 +
        '        // Guarded: the sky-map pass renders clouds through these same files without the block.' + $nl2 +
        '        #ifdef SLO_CLOUD_LIGHTING' + $nl2 +
        '        slo_cloud_color += slo_cloud_light((ray_pos - air_viewer_pos) / CLOUDS_SCALE,' + $nl2 +
        '            normalize(ray_pos - air_viewer_pos), density) * transmittance * sloCloudSplit;' + $nl2 +
        '        #endif' + $nl2 + $nl2
    $layerText = $layerText.Replace($loopAnchor, $loopInsert + $loopAnchor)

    # 3. the composite, before aerial perspective.
    $compAnchor = '    clouds_scattering = clouds_aerial_perspective('
    if (([regex]::Matches($layerText, [regex]::Escape($compAnchor))).Count -ne 1) { throw "composite anchor is not unique in $file" }
    $compInsert = '    // Sable Light Overhaul: our lights before the pack''s aerial perspective, so cloud light seen' + $nl2 +
        '    // through a hundred blocks of air is attenuated like the sun''s scattering and not like a lamp' + $nl2 +
        '    // held against the cloud.' + $nl2 +
        '    clouds_scattering += slo_cloud_color;' + $nl2
    $layerText = $layerText.Replace($compAnchor, $compInsert + $compAnchor)

    if ($layerText -notmatch 'slo_cloud_light\(\(ray_pos - air_viewer_pos\)') { throw "cloud call missing from $file" }
    if ($layerText -notmatch 'clouds_scattering \+= slo_cloud_color;') { throw "cloud composite missing from $file" }
    $dest = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $file
    $destDir = Split-Path $dest -Parent
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    [System.IO.File]::WriteAllText($dest, $layerText, $utf8NoBom)
    Write-Output ("layer patched -> {0}  ({1} bytes)" -f $file, ([System.IO.File]::ReadAllBytes($dest)).Length)
}

# ---------------------------------------------------------------------------
# The blocky cloud style, whose march already accumulates a colour (unlike the fancy layers), so it
# needs the call and nothing else -- no accumulator, no composite step.
#
# It is also the one path that does NOT read sloCloudSplit, on purpose: blocky clouds are ray marched in
# d4_deferred_shading, which is a per-frame pass with no history, so the term is already immediate there.
# Splitting it would only dim it. It also lives in a file included before the surface block defines
# anything, so the uniform would not even be declared at that point; a prototype supplies the function.
$blockyFile = 'include\sky\blocky_clouds.glsl'
$blockySrc = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $blockyFile
$blockyText = [System.IO.File]::ReadAllText($blockySrc, $utf8NoBom)
$blockyAnchor = '        transmittance *= step_transmittance;'
if (([regex]::Matches($blockyText, [regex]::Escape($blockyAnchor))).Count -ne 1) { throw 'blocky march anchor is not unique' }
if ($blockyText -notmatch 'vec3 scattering = vec3\(0\.0\);') { throw 'blocky march no longer accumulates a vec3' }
$blockyInsert = '        // Sable Light Overhaul: this sample''s share of the mod''s lights (slo_cloud_light).' + $nl2 +
    '        // Absolute world space here, so the sample is made camera-relative against the eye; the layer' + $nl2 +
    '        // already accumulates a vec3, so the colour arrives as colour. No sloCloudSplit: this pass is' + $nl2 +
    '        // per-frame and has no history to fight.' + $nl2 +
    '        #ifdef SLO_CLOUD_LIGHTING' + $nl2 +
    '        scattering += slo_cloud_light(world_pos - cameraPosition,' + $nl2 +
    '            normalize(world_pos - cameraPosition), density) * transmittance;' + $nl2 +
    '        #endif' + $nl2 + $nl2
$protoAnchor = 'float blocky_clouds_scattering_coeff = blocky_clouds_extinction_coeff;'
if (([regex]::Matches($blockyText, [regex]::Escape($protoAnchor))).Count -ne 1) { throw 'blocky prototype anchor is not unique' }
$proto = '#define SLO_CLOUD_LIGHTING 1' + $nl2 +
    '// Sable Light Overhaul: declared here because this file is included before the block that defines it.' + $nl2 +
    'vec3 slo_cloud_light(vec3 scene_pos, vec3 view_dir, float density);' + $nl2 + $nl2
$blockyText = $blockyText.Replace($protoAnchor, $proto + $protoAnchor)
$blockyText = $blockyText.Replace($blockyAnchor, $blockyInsert + $blockyAnchor)
if ($blockyText -notmatch 'scattering \+= slo_cloud_light\(world_pos - cameraPosition') { throw 'cloud call missing from blocky_clouds' }
$blockyDest = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $blockyFile
New-Item -ItemType Directory -Force -Path (Split-Path $blockyDest -Parent) | Out-Null
[System.IO.File]::WriteAllText($blockyDest, $blockyText, $utf8NoBom)
Write-Output ("layer patched -> {0}  ({1} bytes)" -f $blockyFile, ([System.IO.File]::ReadAllBytes($blockyDest)).Length)

# ---------------------------------------------------------------------------
# The instant half of the term (CLOUD-LIGHTING.zh-CN.md, "P2").
#
# `include/sky/clouds/sampling.glsl` is the one place the pack turns the temporally upsampled cloud
# buffer into the colour it composites, and it is included by exactly two programs: d4_deferred_shading
# (opaque and sky pixels) and c1_blend_layers (translucents in front of clouds). The pack itself already
# adds a per-frame term here -- the lightning flash -- which is the precedent this follows.
#
# Both callers receive the same `result`, so the term is added in the function rather than at the two
# call sites: same colour, same place in the pipeline, no chance of the two drifting apart.
#
# The position comes from the pack's own cloud distance (colortex10/12.x), the same number
# d2_clouds_upscaling reprojects clouds with, so the term is evaluated where the cloud actually is
# rather than at a guessed altitude. `apparent_distance` is 1e6 where there is no cloud, which is the
# early-out guard.
$samplingFile = 'include\sky\clouds\sampling.glsl'
$samplingSrc = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $samplingFile
$samplingText = [System.IO.File]::ReadAllText($samplingSrc, $utf8NoBom)
if ($samplingText -match 'SLO_CLOUD_LIGHTING') { throw 'sampling.glsl already carries the cloud term' }
$samplingAnchor = '    result.xyz *= clamp01(1.0 - blindness - darknessFactor);'
if (([regex]::Matches($samplingText, [regex]::Escape($samplingAnchor))).Count -ne 1) { throw 'sampling composite anchor is not unique' }
$samplingInsert = '    // Sable Light Overhaul: the instant half of the cloud light term (slo_cloud_light_screen).' + $nl2 +
    '    // Added before the pack multiplies by blindness/darkness, so a flash on the cloud is dimmed by' + $nl2 +
    '    // exactly what dims the cloud itself -- the same place the pack puts its own lightning flash.' + $nl2 +
    '    // The direction is reconstructed the way d2_clouds_upscaling reconstructs it for reprojection,' + $nl2 +
    '    // and the cloud sits at the distance the pack itself measured, in camera-relative blocks.' + $nl2 +
    '    #ifdef SLO_CLOUD_LIGHTING' + $nl2 +
    '    if (apparent_distance < 1.0e5) {' + $nl2 +
    '        vec3 slo_cloud_dir = normalize(' + $nl2 +
    '            view_to_scene_space(screen_to_view_space(vec3(uv, 1.0), false)) -' + $nl2 +
    '            gbufferModelViewInverse[3].xyz);' + $nl2 +
    '        result.xyz += slo_cloud_light_screen(' + $nl2 +
    '            slo_cloud_dir * apparent_distance, slo_cloud_dir, result.w);' + $nl2 +
    '    }' + $nl2 +
    '    #endif' + $nl2 + $nl2
$samplingText = $samplingText.Replace($samplingAnchor, $samplingInsert + $samplingAnchor)
if ($samplingText -notmatch 'slo_cloud_light_screen\(') { throw 'screen-space cloud term missing from sampling.glsl' }
if ($samplingText -notmatch 'apparent_distance < 1\.0e5') { throw 'screen-space cloud guard missing from sampling.glsl' }
$samplingDest = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $samplingFile
New-Item -ItemType Directory -Force -Path (Split-Path $samplingDest -Parent) | Out-Null
[System.IO.File]::WriteAllText($samplingDest, $samplingText, $utf8NoBom)
Write-Output ("patched -> {0}  ({1} bytes)" -f $samplingFile, ([System.IO.File]::ReadAllBytes($samplingDest)).Length)

# ---------------------------------------------------------------------------
# c1_blend_layers also reads the cloud buffer, so it needs the block that sampling.glsl now calls into.
# d4 gets it through diffuse_lighting.glsl (which it includes before sampling.glsl); c1 includes no
# lighting at all, so here the guarded uniform declarations are live and the block stands alone.
#
# Prepended at the top rather than next to the include: the block calls no pack function, only built-ins,
# which is exactly what makes that safe to do.
$c1File = 'program\c1_blend_layers.fsh'
$c1Src = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $c1File
$c1Text = [System.IO.File]::ReadAllText($c1Src, $utf8NoBom)
if ($c1Text -notmatch '#include "/include/sky/clouds/sampling\.glsl"') { throw 'c1_blend_layers no longer includes the cloud sampling' }
if ($c1Text -match 'diffuse_lighting\.glsl') { throw "c1_blend_layers now includes the surface block - the cloud uniforms would be declared twice" }
$c1Text = $cloudBlock + $nl + $c1Text
$c1Copies = ([regex]::Matches($c1Text, 'SLO_CLOUDS_INCLUDED')).Count
Write-Output "cloud block copies in c1_blend_layers = $c1Copies"
if ($c1Copies -ne 3) { throw 'the cloud block is not in c1_blend_layers exactly once (guard + define + endif)' }
if ($c1Text -notmatch 'vec3 slo_cloud_light_screen\(vec3 scene_pos') { throw 'cloud block missing from c1_blend_layers' }
$c1Dest = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $c1File
New-Item -ItemType Directory -Force -Path (Split-Path $c1Dest -Parent) | Out-Null
[System.IO.File]::WriteAllText($c1Dest, $c1Text, $utf8NoBom)
Write-Output ("patched -> {0}  ({1} bytes)" -f $c1File, ([System.IO.File]::ReadAllBytes($c1Dest)).Length)

# ---------------------------------------------------------------------------
# The near fade, injected into the layers themselves.
#
# This has to run last: an earlier section rewrites these same three files from the pristine pack on every
# run, so a version written before it is not patched, it is overwritten. (It was, and the verification step
# is what caught it -- the files came out with no mention of the define.)
#
# Each layer marches its own ray and computes its own `distance_to_sample` for the far fade, so that line is
# the anchor: it appears exactly once per layer, and the sample's own distance is in hand right there. The
# block below is the mirror of the far fade and replaces the earlier attempt, which faded the accumulated
# result of the whole ray -- and so faded the entire sky, at any distance, whenever it was switched on.
$nearLayers = @('cumulus', 'altocumulus', 'cumulus_congestus')
# The guard has to travel with the block. These layer files are compiled into more than one program -- the
# cloud program defines SLO_CLOUD_NEAR at the top, but a program that includes a layer some other way does
# not, and the result is a pack that refuses to load with `undefined variable "SLO_CLOUD_NEAR"`. The same
# value is used as the fallback, so whichever way a layer is reached it fades at the same distance.
$nearGuard = "#ifndef SLO_CLOUD_NEAR`n" +
    "#define SLO_CLOUD_NEAR " +
    $sloCloudNear.ToString([System.Globalization.CultureInfo]::InvariantCulture) + "`n" +
    "#endif"
$nearBody = @'
        // SLO near fade: the mirror of the far fade above. Below SLO_CLOUD_NEAR blocks -- times CLOUDS_SCALE,
        // because this is cloud space -- the sample is dropped outright, which is where the cost saving is:
        // a cloud sample that close covers most of the screen. The short ramp above it stops the boundary
        // from reading as a shell around the player. Zero or less means off.
        if (SLO_CLOUD_NEAR > 0.0) {
            if (distance_to_sample < SLO_CLOUD_NEAR * CLOUDS_SCALE) {
                continue;
            }
            density *= smoothstep(
                SLO_CLOUD_NEAR * CLOUDS_SCALE,
                SLO_CLOUD_NEAR * CLOUDS_SCALE * 1.6,
                distance_to_sample
            );
        }

        // NOTE (withdrawn): a horizon fade was tried here, multiplying the density by a smoothstep of the rays
        // dissolve the deck's edge where it grazes the horizon. It cannot work at this level: ray_dir.y belongs
        // to the whole ray, not to the sample, so every ray that is not looking up had its entire cloud layer
        // multiplied away and the deck vanished outright. Dissolving that edge needs a per-sample quantity --
        // how close the sample's own ray comes to the shell's tangent height -- and that is a different change.
'@
$nearInject = $nearGuard + $nl + $nearBody.TrimEnd()
foreach ($nearLayer in $nearLayers) {
    $nearRel = "include\sky\clouds\$nearLayer.glsl"
    $nearPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $nearRel
    if (-not (Test-Path -LiteralPath $nearPath)) { throw "$nearRel was not produced by the patch" }
    $nearText = [System.IO.File]::ReadAllText($nearPath, $utf8NoBom)
    $nearAnchor = 'float distance_to_sample = distance(ray_origin, ray_pos);'
    $nearCount = ([regex]::Matches($nearText, [regex]::Escape($nearAnchor))).Count
    if ($nearCount -ne 1) { throw "$nearLayer has $nearCount sample loops - refusing to patch" }
    if ($nearText -match 'SLO_CLOUD_NEAR') { throw "$nearLayer already carries a near fade - the patch is not idempotent" }
    $nearText = $nearText.Replace($nearAnchor, $nearAnchor + $nl + $nearInject.TrimEnd())
    [System.IO.File]::WriteAllText($nearPath, $nearText, $utf8NoBom)
    Write-Output ("near fade -> {0}  ({1} bytes)" -f $nearRel, ([System.IO.File]::ReadAllBytes($nearPath)).Length)
}

# ---------------------------------------------------------------------------
# No ground pad below the horizon.
#
# Photon clamps every downward ray to just under the horizon before sampling its atmosphere:
#
#     #ifndef SKY_GROUND
#         mu = max(mu, horizon_mu);
#     #endif
#
# That exists so a short render distance has something plausible underneath it, and the cost is that the whole
# lower half of the sky becomes one flat colour with a hard line at the horizon. With LoD terrain drawing the
# ground, the pad buys nothing and cuts the clouds off at the knees.
#
# Mirroring the direction removes the pad without inventing a surface: below the horizon the sky is the sky
# above it, and because |mu| agrees at mu = 0 from both sides the horizon joins seamlessly with no fade. Only
# the sky is affected -- fog reads atmosphere_transmittance, a different function with its own ground
# handling, and that is left alone.
$horizonRel = 'include\sky\atmosphere.glsl'
$horizonSrc = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $horizonRel
$horizonPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $horizonRel
if (-not (Test-Path -LiteralPath $horizonSrc)) { throw "$horizonRel is missing from the pristine pack" }
$horizonText = [System.IO.File]::ReadAllText($horizonSrc, $utf8NoBom)
$clampPattern = '(?s)#ifndef SKY_GROUND\r?\n\s*float horizon_mu = .*?\r?\n\s*mu = max\(mu, horizon_mu\);\r?\n#endif'
$clampCount = ([regex]::Matches($horizonText, $clampPattern)).Count
Write-Output "horizon clamp occurrences = $clampCount"
if ($clampCount -lt 1) { throw 'the horizon clamp is gone - this patch assumes the shipped pack' }
$mirror = '// SLO: the sky below the horizon is the sky above it. Photon clamps the direction to just under' + $nl +
    '        // the horizon instead, so that a short render distance has something plausible below it, and that' + $nl +
    '        // is what turns the whole lower half into one flat colour with a hard line at the horizon.' + $nl +
    '        mu = abs(mu);'
$horizonText = [regex]::Replace($horizonText, $clampPattern, $mirror)
if ($horizonText -match 'mu = max\(mu, horizon_mu\)') { throw 'a horizon clamp survived the patch' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $horizonPath) | Out-Null
[System.IO.File]::WriteAllText($horizonPath, $horizonText, $utf8NoBom)
Write-Output ("horizon mirror -> {0}  ({1} occurrence(s), {2} bytes)" -f $horizonRel, $clampCount, ([System.IO.File]::ReadAllBytes($horizonPath)).Length)

# ---------------------------------------------------------------------------
# The clouds get fogged by their own distance.
#
# The fog pass in this program runs after the clouds have been blended, and it is computed from the pixel's
# own geometry -- for a cloud pixel that is the *surface behind the cloud*, which is close, so the cloud kept
# a short-distance fog while the sky around it, at the far plane, took the full amount. The result is what a
# cloud layer against a fogged horizon looked like: a dark band in a white field, with neither the fog nor the
# cloud reading as itself. Fogging the cloud with the distance to the cloud puts both back: the terrain keeps
# its haze, and the cloud dissolves into the same haze at the distance it is actually at.
#
# Unit note: `clouds_apparent_distance` is in blocks. The cloud program stores
# `result.apparent_distance * rcp(CLOUDS_SCALE)`, and that value is in cloud space -- blocks times
# CLOUDS_SCALE -- so dividing by CLOUDS_SCALE hands back blocks, which is what air_fog_analytic wants.
$cloudFogRel = 'program\c1_blend_layers.fsh'
$cloudFogPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $cloudFogRel
if (-not (Test-Path -LiteralPath $cloudFogPath)) { throw "$cloudFogRel was not produced by the patch" }
$cloudFogText = [System.IO.File]::ReadAllText($cloudFogPath, $utf8NoBom)
$cloudFogAnchor = @'
        if (clouds_apparent_distance < view_distance) {
            fragment_color =
                fragment_color * clouds_and_aurora.w + clouds_and_aurora.xyz;
        }
'@
$cloudFogAnchor = $cloudFogAnchor.Replace("`r`n", "`n").Trim()
$cloudFogHit = ([regex]::Matches($cloudFogText, [regex]::Escape($cloudFogAnchor))).Count
Write-Output "cloud composite anchor occurrences = $cloudFogHit"
if ($cloudFogHit -ne 1) { throw 'the cloud composite is not where this patch expects it' }
$cloudFogNew = @'
        if (clouds_apparent_distance < view_distance) {
            // SLO: fog the cloud with the distance to the cloud, not with the distance to whatever surface
            // is behind it. See the note in the patch script: the fog below is per-pixel, and for a cloud
            // pixel that pixel is far away, which left the cloud unfogged against a fogged sky.
            vec3 slo_cloud_world =
                cameraPosition + direction_world * clouds_apparent_distance;
            mat2x3 slo_cloud_fog = air_fog_analytic(
                cameraPosition,
                slo_cloud_world,
                false,
                eye_skylight,
                1.0
            );
            vec4 slo_cloud = vec4(
                clouds_and_aurora.xyz *
                        mix(vec3(1.0), slo_cloud_fog[1], SLO_CLOUD_FOG_SCALE) +
                    slo_cloud_fog[0] * SLO_CLOUD_FOG_SCALE,
                clouds_and_aurora.w
            );
            fragment_color = fragment_color * slo_cloud.w + slo_cloud.xyz;
        }
'@
$cloudFogGuard = '#ifndef SLO_CLOUD_FOG_SCALE' + $nl +
    '#define SLO_CLOUD_FOG_SCALE ' +
    $sloCloudFogScale.ToString([System.Globalization.CultureInfo]::InvariantCulture) + $nl +
    '#endif'
$cloudFogNew = $cloudFogGuard + $nl + $cloudFogNew
$cloudFogNew = $cloudFogNew.Replace("`r`n", "`n").Trim()
$cloudFogText = $cloudFogText.Replace($cloudFogAnchor, $cloudFogNew)
if ($cloudFogText -notmatch 'slo_cloud_world') { throw 'the cloud fog patch did not apply' }
[System.IO.File]::WriteAllText($cloudFogPath, $cloudFogText, $utf8NoBom)
Write-Output ("cloud fog -> {0}  ({1} bytes)" -f $cloudFogRel, ([System.IO.File]::ReadAllBytes($cloudFogPath)).Length)

# ---------------------------------------------------------------------------
# The air fog is symmetric about the horizon for sky pixels.
#
# Photon integrates the air mass along the view ray, and the air is denser lower down, so a downward ray
# collects far more fog than an upward one. That is right for fog on terrain. For a *sky* pixel it is not: the
# ray length is forced to the fog's own end, so every pixel below the horizon takes the air mass of the
# densest part of the atmosphere and the lower half of the sky becomes a solid pale wall, while the half above
# the horizon has almost none. That difference is the line, and it is also why clouds in the lower half lose
# their contrast against it.
#
# Mirroring the direction for sky pixels only gives both halves the same air mass, so the line goes. Fog on
# terrain is untouched, and the haze can be made as thick as anyone likes with OVERWORLD_FOG_INTENSITY --
# which now deepens it symmetrically instead of tearing a second line into the sky.
$airFogRel = 'include\fog\overworld\analytic.glsl'
$airFogSrc = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $airFogRel
$airFogPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $airFogRel
if (-not (Test-Path -LiteralPath $airFogSrc)) { throw "$airFogRel is missing from the pristine pack" }
$airFogText = [System.IO.File]::ReadAllText($airFogSrc, $utf8NoBom)
$airFogAnchor = '    ray_length = sky ? fog_end : ray_length;'
$airFogHit = ([regex]::Matches($airFogText, [regex]::Escape($airFogAnchor))).Count
Write-Output "air fog anchor occurrences = $airFogHit"
if ($airFogHit -ne 1) { throw 'the air fog is not where this patch expects it' }
$airFogNew = @'
    // SLO: a sky pixel's ray is forced to the fog's end, so below the horizon it collects the air mass of
    // the densest air and the lower half of the sky turns into a solid wall, while above the horizon there
    // is almost none -- that difference is the horizon line. Mirroring the direction for sky pixels only
    // gives both halves the same air mass. Fog on terrain is left exactly as it was.
    if (sky) {
        ray_direction_world.y = abs(ray_direction_world.y);
    }
    ray_length = sky ? fog_end : ray_length;
'@
$airFogNew = $airFogNew.Replace("`r`n", "`n").Trim()
$airFogText = $airFogText.Replace($airFogAnchor, $airFogNew)
if ($airFogText -notmatch 'ray_direction_world\.y = abs') { throw 'the air fog patch did not apply' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $airFogPath) | Out-Null
[System.IO.File]::WriteAllText($airFogPath, $airFogText, $utf8NoBom)
Write-Output ("air fog -> {0}  ({1} bytes)" -f $airFogRel, ([System.IO.File]::ReadAllBytes($airFogPath)).Length)


# ---------------------------------------------------------------------------
# The deck's underside is a longer gradient than it ships with.
#
# The remaining faint boundary after the fog was dealt with is the deck's own base. A layer's density is
# multiplied by a ramp that brings it up from zero at the bottom, and that ramp is where the underside's
# sharpness lives: cumulus and altocumulus ramp over a fifth of the layer's thickness, cumulus congestus over
# a tenth. Seen from below and edge-on, a fifth still reads as a boundary.
#
# Widening the ramp is the per-sample fix -- `altitude_fraction` is the sample's own height inside its layer,
# which is exactly the quantity a horizon fade wanted and could not have (that one used the ray's direction,
# which belongs to the whole ray, and deleted every cloud that was not looked at from below).
#
# The top edge is left alone: it is high above the player and its sharpness is what makes a cumulus look like
# one.
$deckRamps = @(
    @{ rel = 'include\sky\clouds\cumulus.glsl'; old = 'density *= smoothstep(0.0, 0.2, altitude_fraction);'; new = 'density *= smoothstep(0.0, 0.45, altitude_fraction);' },
    @{ rel = 'include\sky\clouds\altocumulus.glsl'; old = 'density *= smoothstep(0.0, 0.2, altitude_fraction);'; new = 'density *= smoothstep(0.0, 0.45, altitude_fraction);' },
    @{ rel = 'include\sky\clouds\cumulus_congestus.glsl'; old = 'density *= smoothstep(0.0, 0.1, altitude_fraction);'; new = 'density *= smoothstep(0.0, 0.25, altitude_fraction);' }
)
foreach ($ramp in $deckRamps) {
    $rampPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $ramp.rel
    if (-not (Test-Path -LiteralPath $rampPath)) { throw "$($ramp.rel) was not produced by the patch" }
    $rampText = [System.IO.File]::ReadAllText($rampPath, $utf8NoBom)
    $rampCount = ([regex]::Matches($rampText, [regex]::Escape($ramp.old))).Count
    Write-Output "$($ramp.rel) base ramp anchor occurrences = $rampCount"
    if ($rampCount -ne 1) { throw "$($ramp.rel): the base ramp is not where this patch expects it" }
    $rampText = $rampText.Replace($ramp.old, $ramp.new)
    if ($rampText -notmatch [regex]::Escape($ramp.new)) { throw "$($ramp.rel): the base ramp was not widened" }
    [System.IO.File]::WriteAllText($rampPath, $rampText, $utf8NoBom)
    Write-Output ("deck base ramp -> {0}  ({1} bytes)" -f $ramp.rel, ([System.IO.File]::ReadAllBytes($rampPath)).Length)
}

# ---------------------------------------------------------------------------
# The volumetric fog's ceiling stops being a ceiling.
#
# `air_fog_volume_top` is the absolute constant 320.0, while the bottom of the same volume is written relative
# to sea level -- and the fog program branches on it three ways (below, inside, above), so crossing that height
# swaps which branch runs and the fog changes in a single step. That is the jump seen at y = 318 on foot: the
# eye sits about 1.6 above the feet, so the eye crosses 320 there.
#
# No ramp or colour can hide a branch, and there is no need to: the density already falls off with altitude
# through air_fog_falloff_start and its half life, which is the quantity that should decide how much fog there
# is up there. The constant only cuts that falloff short. Moving it out of reach -- and writing it the same way
# the bottom is written, relative to sea level -- leaves the falloff in charge and the branch unreachable.
$volRel = 'include\fog\overworld\constants.glsl'
$volSrc = Join-Path (Join-Path $PSScriptRoot 'baselines\photon\shaders') $volRel
$volPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $volRel
$volText = [System.IO.File]::ReadAllText($volSrc, $utf8NoBom)
$volOld = 'const float air_fog_volume_top = 320.0;'
$volCount = ([regex]::Matches($volText, [regex]::Escape($volOld))).Count
Write-Output "air fog volume top anchor occurrences = $volCount"
if ($volCount -ne 1) { throw 'the air fog ceiling is not where this patch expects it' }
$volText = $volText.Replace($volOld, 'const float air_fog_volume_top = SEA_LEVEL + 512.0; // SLO: was 320.0 absolute')
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $volPath) | Out-Null
[System.IO.File]::WriteAllText($volPath, $volText, $utf8NoBom)
Write-Output ("air fog ceiling -> {0}  ({1} bytes)" -f $volRel, ([System.IO.File]::ReadAllBytes($volPath)).Length)

# ---------------------------------------------------------------------------
# The cloud layer's far fade covers more of its range.
#
# Each layer fades its density out as the sample's distance approaches `max_ray_length`, which exists to hide
# where the march stops. The band it fades over is the last five percent of that range -- about a hundred
# blocks against two thousand -- and the rays that suffer most are the ones that graze the shell: they cross
# the layer lengthwise, run out of range first, and are cut while their neighbours a degree higher are not.
# That is the horizontal line seen across the sky, worst from the middle of the deck, where a grazing ray sits
# near the middle of the screen, and it grows with distance because distance is what the fade is about.
#
# Widening the band costs nothing: the same rays are marched, they just thin out gradually instead of being
# switched off, so the deck dissolves into the distance rather than ending at a line.
$farFades = @(
    @{ rel = 'include\sky\clouds\cumulus.glsl'; old = 'density *=' + $nl + '            smoothstep(1.0, 0.95, distance_to_sample * rcp(max_ray_length));'; new = 'density *=' + $nl + '            smoothstep(1.0, 0.45, distance_to_sample * rcp(max_ray_length));' },
    @{ rel = 'include\sky\clouds\altocumulus.glsl'; old = 'smoothstep(0.95, 1.0, distance_to_sample * rcp(max_ray_length));'; new = 'smoothstep(0.55, 1.0, distance_to_sample * rcp(max_ray_length));' }
    # cumulus_congestus is deliberately absent: its march has no max_ray_length (it has its own distance
    # constants and a distance parabola of its own), so the line this patch widens elsewhere does not exist
    # there and adding one only broke the program with an undefined variable.
)
foreach ($fade in $farFades) {
    $fadePath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $fade.rel
    $fadeText = [System.IO.File]::ReadAllText($fadePath, $utf8NoBom)
    $fadeCount = ([regex]::Matches($fadeText, [regex]::Escape($fade.old))).Count
    Write-Output "$($fade.rel) far fade anchor occurrences = $fadeCount"
    if ($fadeCount -ne 1) { throw "$($fade.rel): the far fade is not where this patch expects it" }
    $fadeText = $fadeText.Replace($fade.old, $fade.new)
    [System.IO.File]::WriteAllText($fadePath, $fadeText, $utf8NoBom)
    Write-Output ("far fade widened -> {0}  ({1} bytes)" -f $fade.rel, ([System.IO.File]::ReadAllBytes($fadePath)).Length)
}

# ---------------------------------------------------------------------------
# The ground fog fades out with distance.
#
# Without this the fog has no far limit at all: every pixel gets the fog of a ray that reaches the fog's own
# end, so at long range it accumulates into a wall of one colour -- which is the boundary that has been chased
# through this whole session. Turning the fog off removes the wall and everything else with it, which is why it
# looked like the answer; a far fade keeps the fog where it does its job and lets the far field be sky.
#
# The factor is a function of the pixel's own view distance, so it is per-pixel and smooth: nothing branches,
# nothing changes in a step, and the fog on nearby terrain is untouched. Values: full strength to 2000 blocks,
# nothing by 3000.
$fogFarFactor = @'
    // SLO: the ground fog fades out between 2000 and 3000 blocks. Past that the far field is sky, not a wall.
    float slo_fog_far = 1.0 - smoothstep(2000.0, 3000.0, view_distance);
'@
$fogFarFactor = $fogFarFactor.Replace("`r`n", "`n").Trim()
$c1FarRel = 'program\c1_blend_layers.fsh'
$c1FarPath = Join-Path (Join-Path $PSScriptRoot 'photon\shaders') $c1FarRel
$c1FarText = [System.IO.File]::ReadAllText($c1FarPath, $utf8NoBom)
# The volumetric composite: its own anchor, and it is the one that runs when volume fog is on.
$farVl = '    fragment_color = fragment_color * fog_transmittance + fog_scattering;'
$farVlCount = ([regex]::Matches($c1FarText, [regex]::Escape($farVl))).Count
Write-Output "c1 volumetric fog anchor occurrences = $farVlCount"
if ($farVlCount -ne 1) { throw 'the volumetric fog composite is not where this patch expects it' }
# SLO_SMOKE_FOG_FIX: the fog below is computed for the whole camera->background ray, but the composite it is
# applied to already contains the depthless translucent layer (the tank smoke, every other particle). That
# layer has no depth of its own, so `view_distance` here belongs to the terrain *behind* it -- hundreds of
# blocks of LoD ground at dusk. Multiplying the whole composite by that transmittance and adding the
# full-path scattering drowns the layer exactly where the background is fog-bright: the smoke vanishes
# along the terrain silhouette while surviving against the dark sky, which is the "transparent rim" that
# depth writes, blend modes and TAA never explained (SLO_SMOKE_ONLY proved the composite itself is right;
# this block runs right after it). The fix is to fog the background term only and leave the layer its own
# shading -- what a depth-writing translucent gets for free from front_position_world.
$sloSplitFog = @'
// Depthless RT13 is composited after fog, without subtracting HDR radiance.
'@
$sloSplitFog = $sloSplitFog.Replace("`r`n", "`n").TrimEnd()
$farVlNew = $fogFarFactor + $nl +
    '    vec3 slo_fog_trans = mix(vec3(1.0), fog_transmittance, slo_fog_far);' + $nl +
    '    vec3 slo_fog_scat = fog_scattering * slo_fog_far;' + $nl +
    ($sloSplitFog.Replace('SLO_FOG_TRANS', 'slo_fog_trans').Replace('SLO_FOG_SCAT', 'slo_fog_scat')) + $nl +
    '    fragment_color = fragment_color * slo_fog_trans + slo_fog_scat;'
$c1FarText = $c1FarText.Replace($farVl, $farVlNew)
# The analytic composites (water and overworld both use these two lines). slo_fog_far is defined again
# inside each replacement scope: when VL is off the block that owns the other definition is compiled out,
# and the two analytic sites are in disjoint scopes, so neither shadows anything.
$farAn = '        fragment_color *= analytic_fog[1];' + $nl + '        fragment_color += analytic_fog[0];'
$farAnCount = ([regex]::Matches($c1FarText, [regex]::Escape($farAn))).Count
Write-Output "c1 analytic fog anchor occurrences = $farAnCount"
if ($farAnCount -lt 1) { throw 'the analytic fog composite is not where this patch expects it' }
$farAnNew = '        // SLO: the ground fog fades out between 2000 and 3000 blocks. Past that the far field is sky, not a wall.' + $nl +
    '        float slo_fog_far = 1.0 - smoothstep(2000.0, 3000.0, view_distance);' + $nl +
    '        vec3 slo_fog_trans = mix(vec3(1.0), analytic_fog[1], slo_fog_far);' + $nl +
    '        vec3 slo_fog_scat = analytic_fog[0] * slo_fog_far;' + $nl +
    ($sloSplitFog.Replace('SLO_FOG_TRANS', 'slo_fog_trans').Replace('SLO_FOG_SCAT', 'slo_fog_scat') -replace '(?m)^    ', '        ') + $nl +
    '        fragment_color = fragment_color * slo_fog_trans + slo_fog_scat;'
$c1FarText = $c1FarText.Replace($farAn, $farAnNew)
# ------------------------------------------------------------------------------------------------
# RT13 is premultiplied OVER, including shields. Its alpha must be positive coverage.
# A negative shield marker cancels smoke coverage at b=a/(1+a), while RGB survives;
# abs(alpha) cannot recover it. It also toggled fog/refraction/TAA at the zero crossing.
# No replacement marker or extra render target: retain real scene depths and the
# shared depthless-layer fog path. Shields configured to write depth are unsupported.

$markerFetch = '    vec4 translucent_color = texelFetch(colortex13, texel, 0);'
$nMarkerFetch = ([regex]::Matches($c1FarText, [regex]::Escape($markerFetch))).Count
Write-Output "c1 translucent layer fetch occurrences = $nMarkerFetch"
if ($nMarkerFetch -ne 1) { throw 'the translucent layer fetch is not where this patch expects it' }

$voxyZero = 'if (front_depth_lod < 1.0 && z_vanilla < z_lod && front_depth == back_depth) {'
$lodWater = 'if (front_depth_lod != back_depth_lod) {'

$c1FarText = $c1FarText.Replace($markerFetch, $markerFetch + $nl +
    '    // SLO: all RT13 writers use positive premultiplied coverage; no signed marker.' + $nl +
    '    float slo_smoke_cover = clamp(translucent_color.a, 0.0, 1.0);' + $nl +
    '    #ifdef SLO_RT13_ALPHA' + $nl +
    '    // SLO debug: show the translucent layer coverage in exposure-independent bands. Red channel' + $nl +
    '    // means alpha >= 0.5, red+green >= 0.8, red+green+blue >= 0.95 (white). Black is below 0.5.' + $nl +
    '    // The thresholds are applied before any tonemap, so auto-exposure cannot turn a faint puff' + $nl +
    '    // white. Read it against the final frame: white where the final frame shows LoD in front means' + $nl +
    '    // the composite is losing a fully covered smoke; black means the smoke is genuinely thin there.' + $nl +
    '    fragment_color = vec3(step(0.50, translucent_color.a),' + $nl +
    '                          step(0.80, translucent_color.a),' + $nl +
    '                          step(0.95, translucent_color.a));' + $nl +
    '    return;' + $nl +
    '    #endif')

$nVoxyZero = ([regex]::Matches($c1FarText, [regex]::Escape($voxyZero))).Count
Write-Output "c1 lod cleanup occurrences = $nVoxyZero"
if ($nVoxyZero -gt 1) { throw 'the LoD cleanup is not unique' }
if ($nVoxyZero -eq 1) {
$c1FarText = $c1FarText.Replace($voxyZero, 'if (false) { // SLO: disabled, see note below -- clearing this layer is what made a hull see-through')
} else { Write-Output 'this baseline has no LoD cleanup to guard (older photon)' }

$nLodWater = ([regex]::Matches($c1FarText, [regex]::Escape($lodWater))).Count
Write-Output "c1 lod water occurrences = $nLodWater"
if ($nLodWater -gt 1) { throw 'the LoD water block is not unique' }
if ($nVoxyZero -eq 1 -and $c1FarText -notmatch 'if \(false\) \{ // SLO: disabled') { throw 'the voxy translucent fix was not disabled' }
Write-Output 'translucent composition: positive coverage, no sign-dependent depth overrides'
# A depth debug for the far-hull question, dead code unless the pack defines SLO_DEPTH_DEBUG.
#
#   green   the vanilla surface is nearer than the LoD surface, so the hull does occlude the LoD
#   blue    the LoD surface is nearer, which is the report
#   yellow  only a vanilla surface is here
#   magenta only LoD depth is here, so nothing in the vanilla pass covered this pixel
$depthDebugBlock = @"

#if defined SLO_DEPTH_DEBUG && defined LOD_MOD_ACTIVE
    {
        float slo_d_vanilla = texelFetch(depthtex1, texel, 0).x;
        float slo_d_lod = texelFetch(lod_depth_tex_solid, texel, 0).x;
        if (slo_d_vanilla < 1.0 && slo_d_lod < 1.0) {
            fragment_color = slo_d_vanilla < slo_d_lod
                ? mix(fragment_color, vec3(0.0, 1.0, 0.0), 0.55)
                : mix(fragment_color, vec3(0.0, 0.35, 1.0), 0.55);
        } else if (slo_d_vanilla < 1.0) {
            fragment_color = mix(fragment_color, vec3(1.0, 1.0, 0.0), 0.40);
        } else if (slo_d_lod < 1.0) {
            fragment_color = mix(fragment_color, vec3(1.0, 0.0, 1.0), 0.40);
        }
    }
#endif
"@
# Inserted before the last closing brace, which is the end of main(): everything above -- fog, clouds -- has
# already had its say, so the paint is what the player actually sees.
# The detonation wave, drawn by the composite rather than by a mesh of our own: it is fogged, bloomed and blended with
# the scene because it is part of the same pass, it gets the LoD depth test for free, and nothing has to be rotated or
# rebuilt per frame. The mod publishes up to eight waves as numbered vec4 pairs -- never an array, which has never
# delivered a value in this pack.
$waveDecl = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_wave_translucency.glsl'), $utf8NoBom) + $nl
for ($i = 0; $i -lt 16; $i++) {
    $waveDecl += "uniform vec4 sloWaveA$i;`nuniform vec4 sloWaveB$i;`n"
    if ($i -eq 0) { $waveDecl += "uniform float sloWaveRefract;`nuniform float sloWaveDispersion;`n" }
}
$waveDecl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_wave_geometry.glsl'), $utf8NoBom) + $nl
$waveDecl += [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_wave_sampling.glsl'), $utf8NoBom) + $nl
# Compute each wave once. Bend the background before translucent/fog composition;
# retain the established foreground-coverage protection for condensation/scattering.
$wavePrepare = @"
    vec3 slo_wave_ray = normalize(front_position_scene - gbufferModelViewInverse[3].xyz);
    float slo_wave_scene_t = (front_depth == 1.0 && front_depth_lod == 1.0) ? 1e20 : length(front_position_view);
    vec2 slo_wave_offset = vec2(0.0);
    float slo_wave_nearest = 1e20;
    float slo_wave_vapour = 0.0, slo_wave_stem = 0.0;
    float slo_wave_pixel_angle = 2.0 / max(abs(gbufferProjection[1][1]) * view_res.y, 1.0);
"@
for ($i = 0; $i -lt 16; $i++) {
    $wavePrepare += @"

    if (sloWaveA$i.w > 0.0 && sloWaveB$i.y > 0.0) {
        float slo_hit;
        vec4 slo_profile = slo_wave_profile(slo_wave_ray, slo_wave_scene_t,
            sloWaveA$i, sloWaveB$i, mat3(gbufferModelView), slo_wave_pixel_angle, slo_hit);
        slo_wave_offset += slo_profile.xy;
        slo_wave_vapour += slo_profile.z;
        slo_wave_stem += slo_profile.w;
        if (dot(slo_profile.xy, slo_profile.xy) > 1e-8) slo_wave_nearest = min(slo_wave_nearest, slo_hit);
    }
"@
}
$wavePrepare += @"

    // At most one background distortion, even with overlapping waves. Brightness
    // already fades in the profile: do not multiply it into the colour a second time.
    slo_wave_offset *= max(0.0, sloWaveRefract);
    slo_wave_offset *= min(1.0, 24.0 / max(length(slo_wave_offset), 0.001));
    if (dot(slo_wave_offset, slo_wave_offset) > 1e-8 && !front_is_hand) {
        fragment_color = slo_wave_refract_scene(refracted_uv, slo_wave_offset * view_pixel_size, slo_wave_nearest);
    } else {
        fragment_color = texture(colortex0, refracted_uv * taau_render_scale).rgb;
    }
"@
$waveSample = '    fragment_color = texture(colortex0, refracted_uv * taau_render_scale).rgb;'
if (-not $c1FarText.Contains($waveSample)) { throw 'wave background sampling anchor absent' }
$c1FarText = $c1FarText.Replace($waveSample, $wavePrepare)
$waveBody = @"

    if ((slo_wave_vapour > 0.0 || slo_wave_stem > 0.0) && !front_is_hand) {
        vec3 slo_before_wave = fragment_color.rgb;
        float slo_gain = 0.05 + 3.8 * clamp(dot(fragment_color.rgb, vec3(0.2126,0.7152,0.0722)), 0.0, 1.0);
        // Daylight only: +20% condensation coverage, no change to the stem/refraction profile.
        float slo_day_vapour = slo_wave_vapour * (1.0 + 0.20 * clamp(time_noon, 0.0, 1.0));
        fragment_color.rgb = mix(fragment_color.rgb, vec3(0.90,0.91,0.94), clamp(slo_day_vapour * 1.55, 0.0, 0.88));
        fragment_color.rgb += vec3(0.95,0.90,0.82) * slo_gain * (slo_wave_vapour * 0.35 + slo_wave_stem);
        fragment_color.rgb = slo_wave_over_translucency(slo_before_wave, fragment_color.rgb, slo_smoke_cover);
    }
"@

$sloLastBrace2 = $c1FarText.LastIndexOf("`n}")
if ($sloLastBrace2 -lt 0) { throw 'the blend pass has no closing brace to insert the wave into' }
$c1FarText = $c1FarText.Insert($sloLastBrace2, $waveBody)
# Declarations go at file scope, just after the last include.
$include = '#include "/include/utility/space_conversion.glsl"'
$at = $c1FarText.IndexOf($include)
if ($at -lt 0) { throw 'the blend pass does not include space_conversion where expected' }
$c1FarText = $c1FarText.Insert($at + $include.Length, "`n" + $waveDecl)
Write-Output ("detonation wave -> {0}" -f $c1FarRel)




$sloLastBrace = $c1FarText.LastIndexOf("`n}")
if ($sloLastBrace -lt 0) { throw the blend pass has no closing brace to insert the depth debug before }
$c1FarText = $c1FarText.Insert($sloLastBrace, $depthDebugBlock)
# Coverage tint: paint this frame's translucent coverage bands ON TOP of the finished frame, so one
# screenshot shows both the pixels the smoke owns and what the frame actually looks like there. Blue
# >= 0.90, green >= 0.50, red >= 0.05. If the background line the owner objects to survives inside a
# blue area, something after this program re-adds it; if it only survives in green/red, the smoke is
# genuinely thin there and the fix is coverage, not another pipeline stage.
$coverageTint = @'

#ifdef SLO_COVERAGE_TINT
    {
        float slo_ct = clamp(translucent_color.a, 0.0, 1.0);
        vec3 slo_ct_color = slo_ct >= 0.90 ? vec3(0.0, 0.15, 1.0)
                         : slo_ct >= 0.50 ? vec3(0.0, 1.0, 0.15)
                         : slo_ct >= 0.05 ? vec3(1.0, 0.15, 0.0)
                         : vec3(0.0);
        fragment_color = mix(fragment_color, slo_ct_color, 0.65 * step(0.05, slo_ct));
    }
#endif
'@
$c1FarText = $c1FarText.Insert($sloLastBrace, $coverageTint)


# The border fade belongs to the background, not to depthless RT13 contributions.
$borderHelper = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_translucent_border.glsl'), $utf8NoBom)
$borderHelper += $nl + [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_depthless_composite.glsl'), $utf8NoBom)
$blendAnchor = '    fragment_color = blend_layers_with_fog('
if ([regex]::Matches($c1FarText,[regex]::Escape($blendAnchor)).Count -ne 1) { throw 'translucent blend anchor mismatch' }
$c1FarText = $c1FarText.Replace($blendAnchor, '    vec3 slo_before_translucent = fragment_color;' + $nl + $blendAnchor)
$borderPattern = '    fragment_color =\s+mix\(original_color,\s+fragment_color,\s+border_fog\(front_position_scene, direction_world\)\);'
if ([regex]::Matches($c1FarText,$borderPattern).Count -ne 1) { throw 'border fog anchor mismatch' }
$borderNew = @'
    float slo_border = border_fog(front_position_scene, direction_world);
    float slo_layer_border = slo_translucent_border_weight(slo_border, front_depth, back_depth,
        front_depth_lod, back_depth_lod, translucent_color.a);
    fragment_color = slo_translucent_border_blend(original_color,slo_before_translucent,fragment_color,
        slo_border,slo_layer_border,translucent_color.a);
#if defined WORLD_OVERWORLD
    #ifdef SLO_SMOKE_BG_FOG
    // SLO: the smoke writes no depth, so blend_layers_with_fog never fogged the background behind it
    // (is_translucent is false) and even a dense puff still shows the raw, HDR-bright LoD terrain at
    // (1-a). 10% of a sunset horizon is brighter than the smoke itself, which is what reads as 'the
    // LoD is on top'. Fog the background over the camera->background distance in proportion to the
    // coverage -- the treatment the depth-writing build got for free -- so at full coverage the
    // background is haze, not terrain, while the smoke colour is still composited on top.
    {
        float slo_c = clamp(translucent_color.a, 0.0, 1.0);
        if (!is_translucent && slo_c > 0.001) {
            mat2x3 slo_f = air_fog_analytic(cameraPosition, back_position_world, is_sky, eye_skylight, 1.0);
            vec3 slo_bg_fog = slo_before_translucent * slo_f[1] + slo_f[0];
            vec3 slo_composite_fog = slo_bg_fog * (1.0 - slo_c) + translucent_color.rgb;
            fragment_color = mix(fragment_color, slo_composite_fog, slo_c);
        }
    }
    #endif
#endif
#ifdef SLO_SMOKE_ONLY
    // SLO debug: stop right after the translucent composite. If the smoke looks right here and wrong
    // in the final frame, a later block (clouds, fog, spotlights, the shockwave) put the background
    // back on top of it; if it is already wrong here, the composite itself is losing the smoke.
    return;
#endif
'@
$c1FarText = [regex]::Replace($c1FarText,$borderPattern,$borderNew)
$c1FarText = $c1FarText.Replace('void main() {', $borderHelper + $nl + 'void main() {')
# Keep the layer intact, fog/cloud the background, then OVER exactly once.
# No extra framebuffer, texture fetch or subtractive reconstruction of HDR radiance.
$deferStart = @'
    vec4 slo_deferred_layer = vec4(0.0);
    bool slo_defer_layer = false;
#ifdef SLO_SMOKE_FOG_FIX
#ifndef SLO_SMOKE_ONLY
    slo_defer_layer = !is_translucent && !back_is_hand && translucent_color.a > 0.0;
    if(slo_defer_layer) {
        slo_deferred_layer = translucent_color;
        translucent_color = vec4(0.0);
    }
#endif
#endif
'@
$c1FarText = $c1FarText.Replace('    vec3 slo_before_translucent = fragment_color;',
    $deferStart.Replace("`r`n","`n") + $nl + '    vec3 slo_before_translucent = fragment_color;')
$deferEnd = @'
    if(slo_defer_layer) {
        translucent_color = slo_deferred_layer;
        fragment_color = slo_depthless_over(fragment_color,translucent_color);
    }
'@
$deferAnchor = '    if ((slo_wave_vapour > 0.0 || slo_wave_stem > 0.0) && !front_is_hand) {'
if([regex]::Matches($c1FarText,[regex]::Escape($deferAnchor)).Count -ne 1) { throw 'deferred layer end anchor mismatch' }
$c1FarText = $c1FarText.Replace($deferAnchor,$deferEnd.Replace("`r`n","`n")+$nl+$deferAnchor)
[System.IO.File]::WriteAllText($c1FarPath, $c1FarText, $utf8NoBom)

Write-Output ("fog far fade -> {0}  ({1} bytes)" -f $c1FarRel, ([System.IO.File]::ReadAllBytes($c1FarPath)).Length)

# ------------------------------------------------------------------------------------------------
# gbuffers_basic, with vertex alpha.
#
# Iris routes a mod's own core-shader draws here, so a translucent overlay drawn with position_color
# arrives in this program and -- until now -- was written into the opaque G-buffer and then lit. That
# is why such geometry looked solid and shaded, and why changing its alpha changed nothing: the
# program already wrote the vertex alpha into colortex0's A channel, but nothing in the pack ever
# reads that channel (the deferred pass is `out vec3` and overwrites RT0's rgb).
#
# So the fragment goes where this pack already composites translucents: colortex13, which
# c1_blend_layers blends as `background * (1.0 - a) + rgb`. That is a premultiplied form, so the
# colour written here is multiplied by its own alpha -- write the plain colour and a translucent
# overlay comes out too bright. RT0/RT1/RT2 are written with a zero alpha, which contributes nothing
# under the draw's own SRC_ALPHA blend; that is what keeps the deferred pass and the G-buffer layout
# untouched.
#
# The option is listed without a `!`, so it is on by default and can be switched off in the pack's
# settings, which is the escape hatch the shield project asked for.
# ------------------------------------------------------------------------------------------------
$basicRel = 'program\gbuffers_basic.fsh'
$basicVshRel = 'program\gbuffers_basic.vsh'
$basicPropsRel = 'shaders.properties'
$baselineShaders = Join-Path $PSScriptRoot 'baselines\photon\shaders'
$outShaders = Join-Path $PSScriptRoot 'photon\shaders'

$basicText = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $basicRel), $utf8NoBom)
$basicVshText = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $basicVshRel), $utf8NoBom)
$basicProps = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $basicPropsRel), $utf8NoBom)

# A vet that is not also carried by the fragment shader would fail to link, so the two are changed
# together and both are checked afterwards.
$vetAnchor = 'flat out vec4 tint;'
$fetAnchor = 'flat in vec4 tint;'
$nVet = ([regex]::Matches($basicVshText, [regex]::Escape($vetAnchor))).Count
$nFet = ([regex]::Matches($basicText, [regex]::Escape($fetAnchor))).Count
Write-Output "gbuffers_basic tint declarations: vet $nVet, fet $nFet"
if ($nVet -ne 1 -or $nFet -ne 1) { throw 'the tint declarations are not where this patch expects them' }
$basicVshText = $basicVshText.Replace($vetAnchor, 'out vec4 tint;')
$basicText = $basicText.Replace($fetAnchor, 'in vec4 tint;')

$outAnchor = ') out vec4 gbuffer_data_1; // detailed normal, specular map (optional)'
$rtAnchor = '/* RENDERTARGETS: 0,1,2 */'
$nOut = ([regex]::Matches($basicText, [regex]::Escape($outAnchor))).Count
$nRt = ([regex]::Matches($basicText, [regex]::Escape($rtAnchor))).Count
Write-Output "gbuffers_basic output anchor $nOut, rendertargets anchor $nRt"
if ($nOut -ne 1 -or $nRt -ne 1) { throw 'the gbuffers_basic outputs are not where this patch expects them' }
$basicText = $basicText.Replace($outAnchor, $outAnchor + $nl +
    'layout(location = 3) out vec4 slo_translucent; // SLO: RT13, the rendered translucent layer')
$sloLayer = -not $env:SLO_NO_RT13
# (no switch here: the RT13 experiment is produced by editing a copy, see docs)
$basicText = $basicText.Replace($rtAnchor, '/* RENDERTARGETS: 0,1,2,13 */')

$branchAnchor = '    vec4 base_color = tint;' + $nl + '    vec2 adjusted_light_levels = light_levels;'
$nBranch = ([regex]::Matches($basicText, [regex]::Escape($branchAnchor))).Count
Write-Output "gbuffers_basic branch anchor $nBranch"
if ($nBranch -ne 1) { throw 'the gbuffers_basic body is not where this patch expects it' }
$branch = @'
    vec4 base_color = tint;
    vec2 adjusted_light_levels = light_levels;

#ifdef TRANSLUCENT_BASIC
    // Zero for the opaque path below, and the whole contribution for the translucent one. Writing it
    // unconditionally is what makes the layer correct whichever branch runs.
    slo_translucent = vec4(0.0);

    // A tint below full opacity is an overlay that wants real alpha blending -- the shield project's
    // sphere -- not a lit G-buffer entry. It goes into the translucent layer, and the three opaque
    // targets get a zero alpha so the draw's own SRC_ALPHA blend leaves them alone.
    if (tint.a < 0.999) {
        vec3 slo_overlay_color = srgb_eotf_inv(base_color.rgb) * rec709_to_working_color;
        // Premultiplied, which is this pack's convention for the translucent layer: every other translucent
        // program blends colortex13 as ONE, ONE_MINUS_SRC_ALPHA (see shaders.properties), and c1_blend_layers
        // composites it as bg * (1 - a) + rgb, a form that takes an already-multiplied colour.
        // Alpha is coverage, never a signed material marker: later smoke must attenuate this
        // layer without cancelling its opacity or switching composite fog/TAA branches.
        slo_translucent = vec4(slo_overlay_color * tint.a, tint.a);
        scene_color = vec4(0.0);
        gbuffer_data_0 = vec4(0.0);
        gbuffer_data_1 = vec4(0.0);
        return;
    }
#endif
'@
$basicText = $basicText.Replace($branchAnchor, $branch.TrimEnd())
$overlayAlpha = 'tint.a'

# ------------------------------------------------------------------------------------------------
# The discard Photon puts in front of everything here.
#
# Its own comment says it plainly -- "I have yet to see anything render something transparent but we assume it
# can happen" -- and it throws away every fragment whose vertex alpha is below a tenth. For a hand drawn line
# that is a harmless economy; for a mod's translucent shape it is a trap, because a shield at 0.05 alpha simply
# is not drawn at all, marker and all, and the symptom is identical to a patch that never applied. The guard is
# kept only for exactly-invisible fragments, where blending would contribute nothing anyway.
$discardAnchor = 'if (tint.a < 0.1) {'
$nDiscard = ([regex]::Matches($basicText, [regex]::Escape($discardAnchor))).Count
Write-Output "gbuffers_basic alpha discard anchor $nDiscard"
if ($nDiscard -ne 1) { throw 'the alpha discard is not where this patch expects it' }
$basicText = $basicText.Replace($discardAnchor,
    '// SLO: was 0.1, which silently deleted any translucent shape drawn below a tenth alpha.' + $nl +
    '    if (tint.a <= 0.0) {')

if ($sloLayer -and $basicText -notmatch 'RENDERTARGETS: 0,1,2,13') { throw 'the translucent layer was not added to the targets' }
if ($basicText -notmatch ('slo_translucent = vec4\(slo_overlay_color \* tint\.a, ' + [regex]::Escape($overlayAlpha) + '\)')) { throw 'the overlay write is missing' }
if ($basicText -notmatch 'in vec4 tint;') { throw 'the smoothed tint is missing from the fragment shader' }
if ($basicText -match 'if \(tint\.a < 0\.1\)') { throw 'the alpha discard is back, and it deletes translucent shapes below a tenth alpha' }
if ($basicVshText -notmatch 'out vec4 tint;') { throw 'the smoothed tint is missing from the vertex shader' }

$miscAnchor = ' DITHERED_TRANSLUCENCY_FALLBACK'
$nMisc = ([regex]::Matches($basicProps, [regex]::Escape($miscAnchor))).Count
Write-Output "shaders.properties misc anchor $nMisc"
if ($nMisc -ne 1) { throw 'the misc option row is not where this patch expects it' }
# The blend line is the whole reason a translucent overlay never worked in this pack. Iris' blend.<program>
# overrides whatever blending the render type asked for, and this pack turns blending off for gbuffers_basic,
# so a mod's translucent shape was written into the opaque buffer and lit no matter what it requested. Turning
# it on cannot change any draw that is already opaque -- blending with an alpha of one is the identity -- so
# the only draws it affects are the ones that meant to be translucent.
$blendAnchor = 'blend.gbuffers_basic                 = off'
$nBlend = ([regex]::Matches($basicProps, [regex]::Escape($blendAnchor))).Count
Write-Output "shaders.properties blend anchor $nBlend"
if ($nBlend -ne 1) { throw 'the gbuffers_basic blend line is not where this patch expects it' }
$basicProps = $basicProps.Replace($blendAnchor, '# SLO: was off, which made every translucent overlay opaque. Iris has no bare "on": a blend value is' + $nl +
    '# either off or a four-term equation, the same one this pack gives its other translucent programs.' + $nl +
    'blend.gbuffers_basic                 = ONE ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA' + $nl +
    '# The layer this program writes into is blended premultiplied by every other translucent program in the' + $nl +
    '# pack (water, weather, handheld water, textured), and c1_blend_layers composites it as' + $nl +
    '# bg * (1 - a) + rgb -- so the colour written is pre-multiplied, and this line says the same thing.' + $nl +
    'blend.gbuffers_basic.colortex13      = ONE ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA' + $nl +
    '# SLO: the fallback program for every particle and translucent entity while the separate translucent' + $nl +
    '# programs are disabled (this pack ships USE_SEPARATE_ENTITY_DRAWS off). It had no blend entry at all,' + $nl +
    '# so Iris used its own SRC_ALPHA default on a colour the particle shader had already premultiplied:' + $nl +
    '# the tank smoke was multiplied by alpha twice and read as a faint veil with the LoD terrain unchanged' + $nl +
    '# behind it. The Java render-type state cannot fix this, because the pack owns the blend of its own' + $nl +
    '# programs. Same equation as the separate programs below, and the one c1_blend_layers expects.' + $nl +
    'blend.gbuffers_all_translucent.colortex13 = ONE ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA')

$basicProps = $basicProps.Replace($miscAnchor, $miscAnchor + ' TRANSLUCENT_BASIC SLO_NO_LOD_DISCARD SLO_RT13_ALPHA SLO_SMOKE_ONLY SLO_TAA_SMOKE SLO_COVERAGE_TINT SLO_SMOKE_BG_FOG SLO_SMOKE_FOG_FIX SLO_SMOKE_EDGE_FIX')
$basicProps = $basicProps.TrimEnd() + $nl + $nl +
    '# SLO-CAPABILITY: gbuffers_basic_vertex_alpha = 1 (slo_translucent, since SLO Patch D 0.1.311)' + $nl +
    '# 0.1.311 is the first version that works: before it this pack had blending off for gbuffers_basic, so a' + $nl +
    '# translucent overlay came out opaque whatever it asked for. Consumers should require 0.1.311 or newer.' + $nl +
    '# TRANSLUCENT_BASIC: gbuffers_basic composites through the translucent layer instead of being lit.' + $nl

# ------------------------------------------------------------------------------------------------
# Optional temporal coverage diagnostic.
#
# Keep the optional coverage diagnostic, but do not fetch RT13 in normal TAA.
# Signed markers were invalid under OVER and could switch temporal rejection at
# the smoke/shield cancellation contour. Ordinary temporal clipping remains active.
# ------------------------------------------------------------------------------------------------
$taaRel = 'program\c4_taa_exposure.fsh'
$taaText = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $taaRel), $utf8NoBom)
$taaUniform = 'uniform sampler2D colortex5; // Scene history'
$taaMix = 'current_color = mix(history_color, current_color, alpha);'
$nTaaUniform = ([regex]::Matches($taaText, [regex]::Escape($taaUniform))).Count
$nTaaMix = ([regex]::Matches($taaText, [regex]::Escape($taaMix))).Count
Write-Output "c4 history sampler anchor $nTaaUniform, blend anchor $nTaaMix"
if ($nTaaUniform -ne 1 -or $nTaaMix -ne 1) { throw 'the temporal filter is not where this patch expects it' }
$taaText = $taaText.Replace($taaUniform, $taaUniform + $nl +
    '#ifdef SLO_TAA_SMOKE' + $nl +
    'uniform sampler2D sloTranslucentLayer; // SLO: positive coverage diagnostic only' + $nl +
    '#endif' + $nl +
    '// SLO-CAPABILITY: taa_translucent_guard = 1 (optional coverage diagnostic)')
$taaText = $taaText.Replace($taaMix,
    '#ifdef SLO_TAA_SMOKE' + $nl +
    '    // SLO: a smoke pixel has no depth of its own, so the reprojection above follows the terrain behind' + $nl +
    '    // it and the history holds that terrain. Mixing it in uses a ratio set by the TAA weight, not by' + $nl +
    '    // the smoke''s coverage -- which is what puts the LoD back through a dense puff. Trust the current' + $nl +
    '    // frame in proportion to the coverage instead; the smoke is soft and does not need the history.' + $nl +
    '    alpha = mix(alpha, 1.0, clamp(texelFetch(sloTranslucentLayer, texel, 0).a, 0.0, 1.0));' + $nl +
    '#endif' + $nl + $nl +
    '    ' + $taaMix)
if ($taaText -match 'sloTranslucentLayer, texel, 0\)\.a < 0\.0') { throw 'stale signed temporal marker' }
$taaDest = Join-Path $outShaders $taaRel
[System.IO.File]::WriteAllText($taaDest, $taaText, $utf8NoBom)
Write-Output ("optional temporal coverage diagnostic -> {0} ({1} bytes)" -f $taaRel, ([System.IO.File]::ReadAllBytes($taaDest)).Length)
$basicDest = Join-Path $outShaders $basicRel
$basicVshDest = Join-Path $outShaders $basicVshRel
New-Item -ItemType Directory -Force -Path (Split-Path $basicDest -Parent) | Out-Null
[System.IO.File]::WriteAllText($basicDest, $basicText, $utf8NoBom)
[System.IO.File]::WriteAllText($basicVshDest, $basicVshText, $utf8NoBom)
[System.IO.File]::WriteAllText((Join-Path $outShaders $basicPropsRel), $basicProps, $utf8NoBom)
Write-Output ("gbuffers_basic vertex alpha -> {0} ({1} bytes), {2}, {3}" -f $basicRel,
    ([System.IO.File]::ReadAllBytes($basicDest)).Length, $basicVshRel, $basicPropsRel)

# ------------------------------------------------------------------------------------------------
# The option's label.
#
# Listing TRANSLUCENT_BASIC on the screen.misc row is what makes Iris offer it, but an option with no lang
# entry renders as a row with no name: the owner reported exactly that -- the misc page open, the row
# invisible. The keys are the pack's own convention (option.<NAME> and option.<NAME>.tooltip) and both
# languages the pack ships are written, so the setting can be found and understood.
#
# The last key is not shown to anyone: it is the string the deploy script looks for to tell a file this
# patch wrote from one a different pack shipped, and a lang file has nowhere to put a comment.
# ------------------------------------------------------------------------------------------------
$langLines = @{
    'lang\en_US.lang' = @(
        'option.TRANSLUCENT_BASIC=Translucent overlays (vertex alpha)'
        'comment.TRANSLUCENT_BASIC=Draw geometry whose vertex colour is not fully opaque -- a mod''s own translucent shapes, such as a shield sphere -- through the translucent layer, so it is blended rather than written into the opaque buffer and lit. Turning this off restores the previous behaviour.'
        'slo_capability=gbuffers_basic honours vertex alpha'
    )
    'lang\zh_CN.lang' = @(
        'option.TRANSLUCENT_BASIC=半透明叠加几何体（顶点 alpha）'
        'comment.TRANSLUCENT_BASIC=把顶点色不透明的几何体（例如护盾球这类模组自绘的半透明形体）送入半透明层做真 alpha 合成，而不是写进不透明缓冲再受光照。关掉它即回到旧行为。'
        'slo_capability=gbuffers_basic 尊重顶点 alpha'
    )
}
foreach ($langRel in $langLines.Keys) {
    $langText = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $langRel), $utf8NoBom)
    if ($langText -notmatch 'option\.TRANSLUCENT_BASIC=') {
        $langText = $langText.TrimEnd() + $nl + ($langLines[$langRel] -join $nl) + $nl
    }
    $langDest = Join-Path $outShaders $langRel
    New-Item -ItemType Directory -Force -Path (Split-Path $langDest -Parent) | Out-Null
    [System.IO.File]::WriteAllText($langDest, $langText, $utf8NoBom)
    Write-Output ("option label -> {0} ({1} bytes)" -f $langRel, ([System.IO.File]::ReadAllBytes($langDest)).Length)
}

# ------------------------------------------------------------------------------------------------
# The option itself, in settings.glsl.
#
# The pack lists its options there -- one #define per option, // before it when the default is off -- and the
# screen.* rows in shaders.properties only say which column an option is shown in and in what order. So an
# option that appears on a screen row but has no #define is not offered at all: the owner opened the misc
# page and found nothing, which is exactly what happened before this step existed. Leaving the define
# uncommented makes the option default to on, which is what the shield project asked for.
# ------------------------------------------------------------------------------------------------

# ------------------------------------------------------------------------------------------------
# gbuffers_all_translucent: hide what the LoD terrain covers.
#
# The LoD keeps its depth in its own buffer (vxDepthTexOpaque), so the depth test never sees it: a particle, or a patch
# of water, floating in front of far terrain is drawn straight through it. This compares the fragment against the LoD's
# own depth and discards it when the terrain is nearer. It costs one compare and no extra fetch -- the LoD depth is
# already sampled a few lines above for the fog distance -- and it does on the GPU what the mod used to ask the CPU for,
# per particle, per pass.
#
# No test against depthtex1 is made on purpose: at exactly these pixels the vanilla depth is empty, because a particle
# writes no depth and the terrain behind it is LoD only. Requiring "vanilla is elsewhere" skipped the one case this is for.
# ------------------------------------------------------------------------------------------------
$talRel = 'program\gbuffers_all_translucent.fsh'
$talText = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $talRel), $utf8NoBom)
$talAnchor = "    if (is_lod_terrain(depth1, depth1_lod)) {" + $nl + "        view_back_pos =" + $nl + "            screen_to_view_space(vec3(coord, depth1_lod), true, true);" + $nl + "    }" + $nl + "#endif"
$nTal = ([regex]::Matches($talText, [regex]::Escape($talAnchor))).Count
Write-Output "gbuffers_all_translucent lod block occurrences = $nTal"
if ($nTal -ne 1) { throw 'the LoD depth block of the translucent pass is not where this patch expects it' }
$talInsert = $talAnchor + $nl + $nl +
    '    // SLO: hide a translucent fragment that the LoD terrain covers. The LoD depth lives in its own buffer, so the' + $nl +
    '    // depth test never sees it, and a particle -- or a patch of water -- floating in front of far terrain was drawn' + $nl +
    '    // through it. No test on depth1 here: at exactly these pixels the vanilla depth is empty, because a particle' + $nl +
    '    // writes no depth and the terrain behind it is LoD only.' + $nl +
    '    #if defined LOD_MOD_ACTIVE && !defined SLO_NO_LOD_DISCARD' + $nl +
    '    if (depth1_lod < 1.0) {' + $nl +
    '        float slo_z_fragment = screen_to_view_space_depth(gbufferProjectionInverse, depth0);' + $nl +
    '        float slo_z_lod = screen_to_view_space_depth(lod_projection_matrix_inverse, depth1_lod);' + $nl +
    '        if (slo_z_lod < slo_z_fragment) discard;' + $nl +
    '    }' + $nl +
    '    #endif'
$talText = $talText.Replace($talAnchor, $talInsert)
if ($talText -notmatch 'slo_z_lod < slo_z_fragment') { throw 'the LoD depth test was not added' }
$talDest = Join-Path $outShaders $talRel
[System.IO.File]::WriteAllText($talDest, $talText, $utf8NoBom)
Write-Output ("lod depth test -> {0} ({1} bytes)" -f $talRel, ([System.IO.File]::ReadAllBytes($talDest)).Length)

$settingsRel = 'settings.glsl'
$settingsText = [System.IO.File]::ReadAllText((Join-Path $baselineShaders $settingsRel), $utf8NoBom)
# A baseline may ship that option already enabled; the anchor is written commented, so normalise first.
$settingsText = $settingsText -replace '(?m)^\s*#define DITHERED_TRANSLUCENCY_FALLBACK', '//#define DITHERED_TRANSLUCENCY_FALLBACK'
$miscAnchor = '//#define DITHERED_TRANSLUCENCY_FALLBACK'
$nMiscSetting = ([regex]::Matches($settingsText, [regex]::Escape($miscAnchor))).Count
Write-Output "settings.glsl misc anchor $nMiscSetting"
if ($nMiscSetting -ne 1) { throw 'the misc option block is not where this patch expects it' }
if ($settingsText -notmatch '#define TRANSLUCENT_BASIC') {
    $settingsText = $settingsText.Replace($miscAnchor, $miscAnchor + $nl + $nl +
        '// SLO: gbuffers_basic draws a translucent overlay through the translucent layer instead of writing it' + $nl +
        '// into the opaque buffer and lighting it. On by default; uncommenting the line below is what turns it' + $nl +
        '// off, which is this pack''s own convention for a boolean option.' + $nl +
        '#define TRANSLUCENT_BASIC' + $nl +
        '// SLO-CAPABILITY: settings_patch = 1 (TRANSLUCENT_BASIC + the two LoD/smoke debug options)' + $nl + $nl +
        '// SLO debug: skip the per-fragment LoD discard in gbuffers_all_translucent. Off is the shipped' + $nl +
        '// behaviour; turning it on answers whether that discard -- or the composite -- is what puts LoD' + $nl +
        '// terrain in front of the smoke. If the smoke covers the LoD with this on, the discard test is wrong.' + $nl +
        '//#define SLO_NO_LOD_DISCARD' + $nl + $nl +
        '// SLO debug: paint the translucent layer''s coverage in exposure-independent bands (red 0.5,' + $nl +
        '// yellow 0.8, white 0.95), so a screenshot can say whether the smoke reaches RT13 where the' + $nl +
        '// final frame shows LoD on top of it.' + $nl +
        '//#define SLO_RT13_ALPHA' + $nl + $nl +
        '// SLO debug: stop the composite right after the translucent layer is blended in. If the smoke' + $nl +
        '// is right here and wrong in the final frame, a later block (clouds, fog, spotlights, the' + $nl +
        '// shockwave) is putting the background back on top of it.' + $nl +
        '//#define SLO_SMOKE_ONLY' + $nl + $nl +
        '// SLO wave/foreground coverage protection is part of the normal composite.' + $nl + $nl +
        '// SLO fix candidate: the smoke writes no depth, so TAA reprojects it as the terrain behind it and' + $nl +
        '// blends that terrain out of the history at the TAA weight -- a ratio unrelated to the smoke''s' + $nl +
        '// coverage. This trusts the current frame in proportion to the coverage instead.' + $nl +
        '//#define SLO_TAA_SMOKE' + $nl + $nl +
        '// SLO debug: tint the finished frame by this frame''s translucent coverage. Blue >= 0.90, green' + $nl +
        '// >= 0.50, red >= 0.05. One screenshot then shows both what the smoke owns and what the frame' + $nl +
        '// looks like there -- the measurement that says whether the background is re-added after the' + $nl +
        '// composite or the smoke is simply thin at that pixel.' + $nl +
        '//#define SLO_COVERAGE_TINT' + $nl + $nl +
        '// SLO fix candidate: without a depth write the composite never fogs the background behind the' + $nl +
        '// smoke, so a dense puff still shows the raw LoD terrain at (1-a) -- and 10% of a bright horizon' + $nl +
        '// is brighter than the smoke. Fog the background over the camera->background distance in' + $nl +
        '// proportion to the coverage: the look the depth-writing build got, without writing depth.' + $nl +
        '// NOTE (2026-11-02): tested, still wrong -- the background is already fog-coloured there, so' + $nl +
        '// fogging it again changes nothing. Kept for reference; see SLO_SMOKE_FOG_FIX instead.' + $nl +
        '//#define SLO_SMOKE_BG_FOG' + $nl + $nl +
        '// SLO fix candidate: c1 fogs the *composited* pixel with the fog of the terrain *behind* the' + $nl +
        '// smoke, because the smoke writes no depth and the fog distance is read from the depth buffer.' + $nl +
        '// At dusk the LoD ground''s transmittance is tiny and its scattering huge, so the whole smoke' + $nl +
        '// layer is drowned exactly where the background is fog-bright -- the smoke ends at the terrain' + $nl +
        '// silhouette instead of at its own mask, which is the "transparent rim". SLO_SMOKE_ONLY proved' + $nl +
        '// the composite itself is correct; this fog stage is what erases it. With this option the fog' + $nl +
        '// is applied to the background term only and the translucent layer keeps its own shading.' + $nl +
        '//#define SLO_SMOKE_FOG_FIX' + $nl + $nl +
        '// SLO soft-smoke material: fog/shift straight radiance before premultiplying coverage.' + $nl +
        '//#define SLO_SMOKE_EDGE_FIX')
}
if ($settingsText -notmatch '#define TRANSLUCENT_BASIC') { throw 'the option define was not added' }
# A depth debug for the LoD question: it paints the answer per pixel instead of describing it, so a screenshot
# can say whether the surface at that pixel is in the vanilla depth buffer, in the LoD one, or in both and which
# is nearer. Off unless SLO_DEPTH_DEBUG is set in the environment when the patch runs -- a debug build is a separate
# pack, not a mode of the shipped one.
if ($env:SLO_DEPTH_DEBUG) {
 $settingsText = $settingsText.Replace('#define TRANSLUCENT_BASIC', '#define TRANSLUCENT_BASIC' + $nl + '#define SLO_DEPTH_DEBUG')
 Write-Output 'depth debug ON: the pack will paint depth sources'
}
$settingsDest = Join-Path $outShaders $settingsRel
[System.IO.File]::WriteAllText($settingsDest, $settingsText, $utf8NoBom)
Write-Output ("option define -> {0} ({1} bytes)" -f $settingsRel, ([System.IO.File]::ReadAllBytes($settingsDest)).Length)

# ------------------------------------------------------------------------------------------------
# Self-check. Every load failure this patch has ever had was one of these three, and every one of
# them is visible in the patched text without a compiler: a file that no longer opens with a
# directive (Iris preprocesses it, so it must), a macro that did not travel with the program that
# reads it, and mix() called with a float where a vec3 was needed. The compiler only speaks after a
# pack reload and names a line rather than a cause, so the check is done here, on the tree that is
# about to be shipped, and it throws before anything else happens.
# ------------------------------------------------------------------------------------------------
$checkRoot = Join-Path $PSScriptRoot 'photon\shaders'
$checked = 0
$problems = New-Object System.Collections.Generic.List[string]
$notices = New-Object System.Collections.Generic.List[string]
foreach ($file in (Get-ChildItem -Path $checkRoot -Recurse -File -Include '*.glsl', '*.fsh', '*.vsh')) {
    $rel = $file.FullName.Substring($checkRoot.Length + 1)
    $text = [System.IO.File]::ReadAllText($file.FullName, $utf8NoBom)
    $checked++
    # Only the files Iris preprocesses itself have to open with a directive: the world*/ wrappers, which start
    # with #version and then include a program/. Everything an include reaches may open with a comment block --
    # Photon's gbuffers_basic does, and so does settings.glsl -- and demanding a # of those was this check
    # reporting files that are perfectly fine.
    if ($rel -match '^world[-0-9]*[\\/]' -and $text.Length -gt 0 -and -not $text.StartsWith('#')) {
        $problems.Add("$rel : does not start with a directive")
    }
    foreach ($macro in @('SLO_CLOUD_NEAR', 'SLO_CLOUD_FOG_SCALE')) {
        if ($text -match "\b$macro\b" -and $text -notmatch "#define\s+$macro") {
            # A file may reach the macro through an include instead of defining it. That is legitimate and
            # is reported, not failed: failing it would train the reader to ignore the check.
            if ($text -match '#include') { $notices.Add("$rel : uses $macro and relies on an include for it") }
            else { $problems.Add("$rel : uses $macro without defining it") }
        }
    }
    if ($text -match 'mix\(\s*[01]\.0\s*,') {
        # Not fatal, and the reason is worth writing down: mix(0.0, a, b) is perfectly legal when a and b are
        # floats, and Photon uses it. What actually broke a pack once was mix(1.0, aVec3, aFloat), and no
        # reading of one line can tell those apart with confidence -- the types live in the other files. So this
        # is reported for a human to glance at, while the two checks that did catch real breakage stay fatal.
        $notices.Add("$rel : mix() with a numeric literal first -- check the argument types")
    }
    # A bool compared to a number is a compile error, and Iris's answer to any compile error is to stop using
    # the pack -- silently, from the player's side: the world renders unshaded and no line says why. This one
    # shipped once (`bool field_body = ...;` and later `field_body != 0`) and cost a launch. The declaration is
    # matched in the same file, which is enough because a bool declared in one file is almost never compared in
    # another.
    foreach ($boolName in [regex]::Matches($text, '(?m)^\s*bool\s+(\w+)\s*=') | ForEach-Object { $_.Groups[1].Value }) {
        if ($text -match "\b$boolName\s*[!=]=\s*0\b") {
            $problems.Add("$rel : '$boolName' is a bool but is compared with a number -- a GLSL compile error, and Iris then drops the whole pack")
        }
    }
    # RT13 uses ONE/ONE_MINUS_SRC_ALPHA; all overlay outputs must already be premultiplied
    # and carry positive coverage, including the otherwise unlit basic program.
    if ($text -match '\bslo_translucent\s*=' -and $text -notmatch ('slo_translucent = vec4\(slo_overlay_color \* tint\.a, ' + [regex]::Escape($overlayAlpha) + '\)')) {
        $problems.Add("$rel : must write positive premultiplied translucent coverage")
    }
}
# Blending has to be on for that program, or the write above is ignored by the driver and the overlay lands in
# the layer at full strength -- and, worse, the zero-alpha writes to the opaque targets stop being no-ops. This
# was the setting that made every translucent overlay opaque in the first place, so it is checked, not trusted.
# Reject any surviving signed-marker read after converting the writer to positive coverage.
$c1Path = Join-Path $checkRoot 'program\c1_blend_layers.fsh'
if ((Test-Path $c1Path) -and ([System.IO.File]::ReadAllText($c1Path, $utf8NoBom) -match 'bool slo_overlay|abs\(translucent_color.a\)')) {
    $problems.Add("program/c1_blend_layers.fsh: stale signed coverage handling")
}
# The old sign-triggered TAA rejection is gone; only the optional diagnostic remains.
$taaPath = Join-Path $checkRoot 'program\c4_taa_exposure.fsh'
if ((Test-Path $taaPath) -and ([System.IO.File]::ReadAllText($taaPath, $utf8NoBom) -notmatch 'sloTranslucentLayer')) {
    $problems.Add("program/c4_taa_exposure.fsh: missing optional coverage diagnostic")
}# The vanilla light field is a contract between two halves of this mod: the client floods a 3D texture and
# the shader samples it. A pack that lost the sampling would silently keep the flat look forever, so the
# source is checked for it here as well as for the uniforms it needs.
$sloPath = Join-Path $PSScriptRoot 'source\slo_light.glsl'
if (Test-Path $sloPath) {
    $sloText = [System.IO.File]::ReadAllText($sloPath, $utf8NoBom)
    if ($sloText -notmatch 'uniform sampler2D sloField;') { $problems.Add("slo_light.glsl: declares no sloField sampler, so the vanilla light field cannot reach the shader") }
# The sampler is deliberately absent from shaders.properties: Iris turns customTexture entries into dynamic
# samplers in IrisSamplers.addCustomTextures, and this mod hooks that same method to add its own entry. A
# shaders.properties line would instead send Iris looking for a resource-pack file that does not exist.
    if ($sloText -notmatch 'if \(flood && decay > 0\.0\)') { $problems.Add("slo_light.glsl: never tests for a field, so a lamp on the vanilla model would keep the ray path") }
    if ($sloText -notmatch 'texture\(sloField') { $problems.Add("slo_light.glsl: never samples the field") }
}
$propsPath = Join-Path $checkRoot 'shaders.properties'
if (Test-Path $propsPath) {
    $propsText = [System.IO.File]::ReadAllText($propsPath, $utf8NoBom)
    if ($propsText -notmatch 'blend\.gbuffers_basic\s*=\s*ONE ONE_MINUS_SRC_ALPHA') {
        $problems.Add("shaders.properties: blend.gbuffers_basic is not the premultiplied equation, so a translucent overlay cannot blend")
    }
    if ($propsText -notmatch 'blend\.gbuffers_basic\.colortex13\s*=\s*ONE ONE_MINUS_SRC_ALPHA') {
        $problems.Add("shaders.properties: the translucent layer of gbuffers_basic is not blended premultiplied")
    }
    if ($propsText -notmatch 'blend\.gbuffers_all_translucent\.colortex13\s*=\s*ONE ONE_MINUS_SRC_ALPHA') {
        $problems.Add("shaders.properties: the all-translucent fallback blends straight alpha, so its premultiplied smoke is multiplied twice")
    }
    # Every blend line, whatever it was meant to say. Iris has no bare on/off keyword pair for these: a value is
    # either the word off or exactly four terms, and it stops loading the whole pack when it is neither. That is
    # what a trailing "// SLO: ..." comment on one of these lines did, and the pack failing to load is a much
    # worse outcome than the option not appearing at all.
    foreach ($line in ($propsText -split "`n")) {
        if ($line -notmatch '^\s*blend\.') { continue }
        $value = ($line -split '=', 2)
        if ($value.Count -lt 2) { continue }
        $terms = @($value[1].Trim() -split '\s+' | Where-Object { $_ -ne '' })
        if ($terms.Count -eq 1 -and $terms[0] -eq 'off') { continue }
        if ($terms.Count -ne 4) {
            $problems.Add("shaders.properties: blend line '$($line.Trim())' has $($terms.Count) terms, not 4 (or off)")
        }
    }
}
# A vet and a fet that disagree about interpolation fail to link, and the compiler names the varying rather
# than the cause. Both files are read here whether or not they were otherwise in scope.
$vetPath = Join-Path $checkRoot 'program\gbuffers_basic.vsh'
$fetPath = Join-Path $checkRoot 'program\gbuffers_basic.fsh'
if ((Test-Path $vetPath) -and (Test-Path $fetPath)) {
    $vetText = [System.IO.File]::ReadAllText($vetPath, $utf8NoBom)
    $fetText = [System.IO.File]::ReadAllText($fetPath, $utf8NoBom)
    $vetFlat = $vetText -match 'flat\s+out\s+vec4\s+tint;'
    $fetFlat = $fetText -match 'flat\s+in\s+vec4\s+tint;'
    if ($vetFlat -ne $fetFlat) {
        $problems.Add("program/gbuffers_basic: tint is flat in one stage and smooth in the other (link error)")
    }
}
Write-Output "self-check: $checked shader file(s), $($problems.Count) problem(s), $($notices.Count) notice(s)"
foreach ($notice in $notices) { Write-Output "  notice  $notice" }
foreach ($problem in $problems) { Write-Output "  PROBLEM $problem" }
if ($problems.Count -gt 0) {
    throw "the patched shader tree failed its own self-check ($($problems.Count) problem(s)); nothing else was changed"
}
Write-Output 'self-check passed'

Write-Output ''


# Spotlights: aperture glow and beam volume, in their own script because the block is long.
& (Join-Path $PSScriptRoot 'apply-spotlights.ps1') -OutShaders $outShaders
& (Join-Path $PSScriptRoot 'apply-muzzle-particles.ps1') -OutShaders $outShaders

Write-Output 'photon patch applied'
