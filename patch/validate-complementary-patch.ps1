[CmdletBinding()]
param(
    [ValidateSet('Plain','Euphoria')]
    [string]$Pack = 'Plain',
    [string]$SourceRoot = '',
    [string]$Patched = '',
    [string]$GlslangPath = ''
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$LF = [string][char]10
$CRLF = [string][char]13 + [string][char]10
$repo = Split-Path -Parent $PSScriptRoot
$g = (Join-Path (Split-Path -Parent $repo) 'glslang\bin\glslang.exe')
if ($GlslangPath) { $g = $GlslangPath }
$blockFile = Join-Path $repo 'patch\source\slo_complementary.glsl'

if (-not (Test-Path -LiteralPath $g)) { throw "glslang not found: $g" }

if (-not $SourceRoot) {
    if ($Pack -eq 'Plain') {
        $SourceRoot = (Join-Path $PSScriptRoot 'baselines\complementary\shaders')
    } else {
        $SourceRoot = Join-Path $PSScriptRoot 'baselines\complementary-euphoria\shaders'
    }
}
$original = Join-Path $SourceRoot 'lib\lighting\mainLighting.glsl'
if (-not (Test-Path -LiteralPath $original)) { throw "unmodified pack file not found: $original" }
if (-not $Patched) {
    $sub = 'complementary'
    if ($Pack -eq 'Euphoria') { $sub = 'complementary-euphoria' }
    $Patched = Join-Path $repo ('patch\' + $sub + '\shaders\lib\lighting\mainLighting.glsl')
}
if (-not (Test-Path -LiteralPath $Patched)) { throw "patched file not found: $Patched" }

Write-Output "pack     = $Pack"
Write-Output "original = $original"
Write-Output "patched  = $Patched"

# ---- 1) the patch must be purely additive, with ONE documented exception ----
# The inlined block is pure insertion. The call is not: it has to enter the
# finalDiffuse bracket group, so exactly one original line is rewritten. That
# single line is allowed through by name and printed; anything else removed is
# a hard failure. Below that, the whole file is rebuilt from the original and
# compared, which proves nothing else was touched.
$anchorBlock = 'vec3 highlightColor = normalize(pow(lightColor, vec3(0.37))) * (0.3 + 1.5 * sunVisibility2) * (1.0 - 0.85 * rainFactor);'
$anchorCall  = 'vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission);'
$callTail    = '+ minLighting) + pow2(emission);'
$callText    = '+ minLighting + pow2(get_slo_lighting_compat(playerPos, worldGeoNormal, vanillaAO))) + pow2(emission);'

$origText = [System.IO.File]::ReadAllText($original, $utf8NoBom)
$patchedText = [System.IO.File]::ReadAllText($Patched, $utf8NoBom)
$eol = $LF
if ($origText.Contains($CRLF)) { $eol = $CRLF }
$block = [System.IO.File]::ReadAllText($blockFile, $utf8NoBom)
if ($block.Contains($CRLF)) { $block = $block.Replace($CRLF, $LF) }
$block = $block.Trim() + $LF
if ($eol -ne $LF) { $block = $block.Replace($LF, $eol) }

$origLines = [System.IO.File]::ReadAllLines($original, $utf8NoBom)
$newLines  = [System.IO.File]::ReadAllLines($Patched, $utf8NoBom)

$removed = @(); $cursor = 0
foreach ($line in $origLines) {
    $found = -1
    for ($i = $cursor; $i -lt $newLines.Count; $i++) { if ($newLines[$i] -ceq $line) { $found = $i; break } }
    if ($found -lt 0) { $removed += $line } else { $cursor = $found + 1 }
}
$unexpected = @($removed | Where-Object { $_.Trim() -cne $anchorCall })
Write-Output ("original lines = {0}, patched lines = {1}, added = {2}, removed = {3}" -f $origLines.Count, $newLines.Count, ($newLines.Count - $origLines.Count), $removed.Count)
if ($removed.Count -gt 0) { $removed | ForEach-Object { "  REWRITTEN: $($_.Trim())" } }
if ($unexpected.Count -gt 0) { throw 'patch removed original content other than the documented finalDiffuse rewrite' }

$rebuilt = $origText.Replace($anchorBlock, $block + $eol + $eol + $anchorBlock)
$rebuilt = $rebuilt.Replace($anchorCall, $anchorCall.Replace($callTail, $callText))
if ($rebuilt -cne $patchedText) { throw 'patched file is not exactly original + the two documented edits' }
Write-Output 'reconstruction check: patched == original + inlined block (+2 blank lines) + one rewritten finalDiffuse line; nothing else differs'

# ---- 1b) the block we inlined must be exactly the source block --------------
$srcBlockLines = [System.IO.File]::ReadAllLines($blockFile, $utf8NoBom) | ForEach-Object { $_.TrimEnd() }
$start = -1; $end = -1
for ($i = 0; $i -lt $newLines.Count; $i++) {
    if ($start -lt 0 -and $newLines[$i] -match '^#ifndef\s+INCLUDE_LIGHTING_SLO_COMPAT\s*$') { $start = $i; continue }
    if ($start -ge 0 -and $newLines[$i] -match '^#endif\s*//\s*INCLUDE_LIGHTING_SLO_COMPAT\s*$') { $end = $i; break }
}
if ($start -lt 0 -or $end -lt 0) { throw 'inlined block guard (#ifndef INCLUDE_LIGHTING_SLO_COMPAT) not found' }
$inlineLines = $newLines[$start..$end] | ForEach-Object { $_.TrimEnd() }
Write-Output ("inlined block = lines {0}..{1} ({2} lines)" -f ($start + 1), ($end + 1), ($end - $start + 1))
if ($inlineLines.Count -ne $srcBlockLines.Count) { throw "inlined block is $($inlineLines.Count) lines, source is $($srcBlockLines.Count)" }
for ($i = 0; $i -lt $srcBlockLines.Count; $i++) {
    if ($inlineLines[$i] -cne $srcBlockLines[$i]) { throw ("inlined block differs from source at line {0}: '{1}' vs '{2}'" -f ($i + 1), $inlineLines[$i], $srcBlockLines[$i]) }
}
Write-Output "inlined block is byte-identical (per line) to patch\source\slo_complementary.glsl"

# ---- 1c) the shadow machinery must be gone ----------------------------------
$shadowForbidden = @('depthtex1', 'sloShadowMask', 'sloShadowSteps', 'sloShadowLongRange',
                     'slo_visibility', 'slo_view_to_screen', 'slo_screen_to_view_depth',
                     'slo_random', 'PROGRAM_DEFERRED4', 'noisetex', 'frameCounter')
$blockText = ($inlineLines -join $LF)
foreach ($needle in $shadowForbidden) {
    if ($blockText.Contains($needle)) { throw "shadow machinery survived in the inlined block: $needle" }
}
Write-Output "shadow check: none of depthtex1 / shadow mask / steps / long range / visibility / tap-loop helpers are present"

# ---- 1d) the call site is inside the finalDiffuse bracket group -------------
$finalIdx = -1
for ($i = 0; $i -lt $newLines.Count; $i++) {
    # full one-line structure, so the example in the block's own header comment cannot match
    if ($newLines[$i] -match '^[ \t]*vec3 finalDiffuse = pow2\(directionShade \* vanillaAO\) \* \(blockLighting \+ pow2\(sceneLighting\) \+ minLighting') { $finalIdx = $i; break }
}
if ($finalIdx -lt 0) { throw 'finalDiffuse line not found in patched file' }
$finalLine = $newLines[$finalIdx]
$sqrtLine = $newLines[$finalIdx + 1]
Write-Output ("finalDiffuse  = line {0}" -f ($finalIdx + 1))
Write-Output ("next line     = line {0}" -f ($finalIdx + 2))
if ($finalLine -notmatch 'get_slo_lighting_compat\(playerPos, worldGeoNormal, vanillaAO\)') { throw 'call is not on the finalDiffuse line' }
if ($finalLine -notmatch 'pow2\(get_slo_lighting_compat') { throw 'call is not squared (pow2) inside the bracket group' }
if ($finalLine -notmatch '\+ minLighting \+ pow2\(get_slo_lighting_compat') { throw 'call is not inside the bracket group' }
if ($sqrtLine -notmatch 'sqrt\(max\(finalDiffuse') { throw 'the line after finalDiffuse is not the sqrt(max(...)) line' }
Write-Output "call site check: our term sits in the (blockLighting + pow2(sceneLighting) + minLighting + ...) group, one line above sqrt(max(...))"

# ---- 2) what #version does this pack's own lighting code use? ---------------
$srcText = [System.IO.File]::ReadAllText($original, $utf8NoBom)
$ver = ''
$verFrom = ''
$m = [regex]::Match($srcText, '(?m)^[ \t]*#version[^\r\n]*')
if ($m.Success) {
    $ver = $m.Value.Trim()
    $verFrom = 'lib/lighting/mainLighting.glsl itself'
} else {
    $wrapper = Join-Path $SourceRoot 'world0\gbuffers_terrain.fsh'
    if (-not (Test-Path -LiteralPath $wrapper)) { throw "neither mainLighting.glsl nor $wrapper declares #version" }
    $wrapperText = [System.IO.File]::ReadAllText($wrapper, $utf8NoBom)
    $mw = [regex]::Match($wrapperText, '(?m)^[ \t]*#version[^\r\n]*')
    if (-not $mw.Success) { throw 'no #version in world0/gbuffers_terrain.fsh either' }
    $ver = $mw.Value.Trim()
    $verFrom = 'world0/gbuffers_terrain.fsh, the fragment entry point that includes mainLighting.glsl (mainLighting.glsl is an include and carries no #version of its own)'
}
Write-Output ("#version = '{0}'  (from {1})" -f $ver, $verFrom)
if ($ver -notmatch '#version\s+130') { Write-Output "NOTE: version is not 130 - the validator follows whatever the pack declares" }

# The pack's own fragment entry point for the lighting path, defines included:
$wrapper2 = Join-Path $SourceRoot 'world0\gbuffers_terrain.fsh'
$wrapperText2 = [System.IO.File]::ReadAllText($wrapper2, $utf8NoBom)
$defines = @([regex]::Matches($wrapperText2, '(?m)^[ \t]*#define[^\r\n]*') | ForEach-Object { $_.Value.Trim() })
Write-Output ("required defines from that entry point: {0}" -f ($defines -join ' | '))
$defineText = ($defines -join $LF)

# ---- 3) compile the inlined block with glslang ------------------------------
$header = $ver + $LF + $defineText + $LF + 'out vec4 fragColor;' + $LF

$blockOnly = $LF + 'void main() {' + $LF +
    '    vec3 p = vec3(0.5, 0.0, -3.0);' + $LF +
    '    vec3 n = normalize(vec3(0.0, 1.0, 0.0));' + $LF +
    '    fragColor = vec4(get_slo_lighting_compat(p, n, 0.7), 1.0);' + $LF + '}' + $LF

$callSite = $LF + 'void main() {' + $LF +
    '    vec3 directionShade = vec3(1.0);' + $LF +
    '    vec3 blockLighting = vec3(0.1);' + $LF +
    '    vec3 sceneLighting = vec3(0.2);' + $LF +
    '    vec3 minLighting = vec3(0.01);' + $LF +
    '    float emission = 0.0;' + $LF +
    '    vec3 playerPos = vec3(0.5, 0.0, -3.0);' + $LF +
    '    vec3 worldGeoNormal = normalize(vec3(0.0, 1.0, 0.0));' + $LF +
    '    float vanillaAO = 0.7;' + $LF +
    ($finalLine.TrimEnd()) + $LF +
    ($sqrtLine.TrimEnd()) + $LF +
    '    fragColor = vec4(finalDiffuse, 1.0);' + $LF + '}' + $LF

# pow2() is a real function in Complementary (lib/util/commonFunctions.glsl), so
# the call-site configuration only has to bring its own copy for glslang.
$callHeader = $header.Replace('out vec4 fragColor;', '#define pow2(x) ((x) * (x))' + $LF + 'out vec4 fragColor;')

$script:glslangExit = @{}
function Compile-Block([string]$name, [string]$header, [string]$code, [string]$main) {
    $tmp = Join-Path $env:TEMP ("slo_compat_" + $name + ".frag")
    [System.IO.File]::WriteAllText($tmp, $header + $code + $main, $utf8NoBom)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & $g -S frag $tmp 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $old
    Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    $script:glslangExit[$name] = $exit
    Write-Output ("[{0}] glslang exit={1}" -f $name, $exit)
    if ($out) { $out | ForEach-Object { "    $_" } } else { Write-Output '    (no diagnostics)' }
    if ($exit -ne 0) { throw "the inlined block failed GLSL validation in the '$name' configuration" }
}

Compile-Block 'block+call' $header $blockText $blockOnly
Compile-Block 'callsite-finalDiffuse' $callHeader $blockText $callSite

Write-Output ("glslang summary: block+call exit={0}, callsite-finalDiffuse exit={1}" -f $script:glslangExit['block+call'], $script:glslangExit['callsite-finalDiffuse'])
Write-Output 'VALIDATION OK'
