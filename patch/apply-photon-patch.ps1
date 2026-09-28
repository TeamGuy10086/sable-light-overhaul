$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$src = 'D:\DSH\McMOD\work\photon-inspection\photon-voxy-support\shaders\include\lighting\diffuse_lighting.glsl'
$fragment = 'D:\DSH\McMOD\work\cbc-muzzle-light\patch\source\cbc_muzzle_light.glsl'
$outDir = 'D:\DSH\McMOD\work\cbc-muzzle-light\patch\photon\shaders\include\lighting'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$text = [System.IO.File]::ReadAllText($src, $utf8NoBom)
$block = [System.IO.File]::ReadAllText($fragment, $utf8NoBom)

$block = [regex]::Replace($block, '^\s*#ifndef\s+INCLUDE_LIGHTING_CBC_MUZZLE_LIGHT\s*', '')
$block = [regex]::Replace($block, '#endif\s*//\s*INCLUDE_LIGHTING_CBC_MUZZLE_LIGHT\s*$', '')
$block = $block.Trim() + [string][char]10

$anchor1 = '#include "/include/lighting/colors/blocklight_color.glsl"'
$anchor2 = '    lighting += material.emission * emission_scale;'

$n1 = ([regex]::Matches($text, [regex]::Escape($anchor1))).Count
$n2 = ([regex]::Matches($text, [regex]::Escape($anchor2))).Count
Write-Output "anchor1 occurrences = $n1"
Write-Output "anchor2 occurrences = $n2"
if ($n1 -ne 1 -or $n2 -ne 1) { throw 'anchors are not unique - refusing to patch' }

$nl = [string][char]10
$insert2 = '    lighting += get_cbc_muzzle_lighting(scene_pos, normal, ao);'

$text = $text.Replace($anchor1, $anchor1 + $nl + $nl + $block)
$text = $text.Replace($anchor2, $insert2 + $nl + $nl + $anchor2)

if ($text -notmatch 'get_cbc_muzzle_lighting\(scene_pos') { throw 'call insertion missing' }
if ($text -notmatch 'uniform vec4 cbcMuzzleLight15;') { throw 'inlined bridge missing' }

$dest = Join-Path $outDir 'diffuse_lighting.glsl'
[System.IO.File]::WriteAllText($dest, $text, $utf8NoBom)

$bytes = [System.IO.File]::ReadAllBytes($dest)
$hasBom = ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
Write-Output "patched -> $dest"
Write-Output ("bytes={0}  hasBom={1}  first3={2}" -f $bytes.Length, $hasBom, (($bytes[0..2] | ForEach-Object { $_.ToString('X2') }) -join ' '))
if ($hasBom) { throw 'patched file still has a BOM' }
if ($bytes[0] -ne 0x23) { throw 'patched file does not start with #' }