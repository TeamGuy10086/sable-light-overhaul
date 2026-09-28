$ErrorActionPreference = 'Stop'
$g = 'D:\DSH\McMOD\work\glslang\bin\glslang.exe'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$original = 'D:\DSH\McMOD\work\photon-inspection\photon-voxy-support\shaders\include\lighting\diffuse_lighting.glsl'
$patched = 'D:\DSH\McMOD\work\cbc-muzzle-light\patch\photon\shaders\include\lighting\diffuse_lighting.glsl'

$origLines = [System.IO.File]::ReadAllLines($original, $utf8NoBom)
$newLines = [System.IO.File]::ReadAllLines($patched, $utf8NoBom)

# ---- 1) the patch must be purely additive ------------------------------------
$removed = @(); $cursor = 0
foreach ($line in $origLines) {
    $found = -1
    for ($i = $cursor; $i -lt $newLines.Count; $i++) { if ($newLines[$i] -ceq $line) { $found = $i; break } }
    if ($found -lt 0) { $removed += $line } else { $cursor = $found + 1 }
}
Write-Output ("original lines = {0}, patched lines = {1}, added = {2}, removed = {3}" -f $origLines.Count, $newLines.Count, ($newLines.Count - $origLines.Count), $removed.Count)
if ($removed.Count -gt 0) { $removed | ForEach-Object { "  REMOVED: $_" }; throw 'patch removed original content' }

# ---- 2) locate the inlined block (inserted two lines after the anchor) -------
$anchor = -1
for ($i = 0; $i -lt $newLines.Count; $i++) {
    if ($newLines[$i] -match '^#include "/include/lighting/colors/blocklight_color\.glsl"') { $anchor = $i; break }
}
if ($anchor -lt 0) { throw 'anchor include not found' }
$start = $anchor + 2
$end = -1
for ($i = $start; $i -lt $newLines.Count; $i++) {
    if ($newLines[$i] -match '^#include "/include/misc/end_lighting_fix') { $end = $i - 1; break }
}
if ($end -lt 0) { throw 'end of inlined block not found' }
$block = ($newLines[$start..$end] -join [string][char]10)
Write-Output ("inline block = lines {0}..{1} ({2} lines)" -f ($start + 1), ($end + 1), ($end - $start + 1))

$nl = [string][char]10
$main = $nl + 'void main() {' + $nl +
    '    vec3 p = vec3(0.5, 0.0, -3.0);' + $nl +
    '    vec3 n = normalize(vec3(0.0, 1.0, 0.0));' + $nl +
    '    fragColor = vec4(get_cbc_muzzle_lighting(p, n, 0.7), 1.0);' + $nl + '}' + $nl

function Compile-Block([string]$name, [string]$header) {
    $tmp = Join-Path $env:TEMP ("cbc_muzzle_" + $name + ".frag")
    [System.IO.File]::WriteAllText($tmp, $header + $block + $main, $utf8NoBom)
    $out = & $g -S frag $tmp 2>&1
    $exit = $LASTEXITCODE
    Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    Write-Output ("[{0}] glslang exit={1}" -f $name, $exit)
    if ($out) { $out | ForEach-Object { "    $_" } }
    if ($exit -ne 0) { throw "block failed GLSL validation in the '$name' configuration" }
}

# Deferred pass: shadow branch compiled in (mirrors d4's declarations, which all
# appear before the include of diffuse_lighting.glsl).
$deferredHeader = '#version 400 compatibility' + $nl + '#define PROGRAM_DEFERRED4' + $nl +
    'const float blocklight_scale = 6.0;' + $nl +
    'uniform sampler2D depthtex1;' + $nl +
    'uniform sampler2D noisetex;' + $nl +
    'uniform int frameCounter;' + $nl +
    'uniform mat4 gbufferModelView;' + $nl +
    'uniform mat4 gbufferProjection;' + $nl +
    'uniform mat4 gbufferProjectionInverse;' + $nl +
    'out vec4 fragColor;' + $nl
Compile-Block 'deferred' $deferredHeader

# Forward / other programs: shadow branch compiled out.
$plainHeader = '#version 400 compatibility' + $nl +
    'const float blocklight_scale = 6.0;' + $nl +
    'out vec4 fragColor;' + $nl
Compile-Block 'nondeferred' $plainHeader
