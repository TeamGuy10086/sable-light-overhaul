[CmdletBinding()]
param(
    [ValidateSet('Plain','Euphoria')]
    [string]$Pack = 'Plain',
    [string]$SourceRoot = '',
    [string]$OutFile = '',
    [string]$DeployRoot = ''
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$LF = [string][char]10
$CRLF = [string][char]13 + [string][char]10

$repo = Split-Path -Parent $PSScriptRoot
$blockFile = Join-Path $repo 'patch\source\slo_complementary.glsl'

# ---------------------------------------------------------------------------
#  Which tree are we patching?
#  Plain    : the extracted scratch copy of the shipped Unbound zip.
#  Euphoria : discovered with a wildcard, because the version folder name is
#             non-ASCII and this script has to stay pure ASCII.
# ---------------------------------------------------------------------------
if (-not $SourceRoot) {
    if ($Pack -eq 'Plain') {
        $SourceRoot = (Join-Path $PSScriptRoot 'baselines\complementary\shaders')
    } else {
        $SourceRoot = Join-Path $PSScriptRoot 'baselines\complementary-euphoria\shaders'
    }
}
$src = Join-Path $SourceRoot 'lib\lighting\mainLighting.glsl'
if (-not (Test-Path -LiteralPath $src)) { throw "source not found: $src" }
if (-not (Test-Path -LiteralPath $blockFile)) { throw "block source not found: $blockFile" }

if (-not $OutFile) {
    $sub = 'complementary'
    if ($Pack -eq 'Euphoria') { $sub = 'complementary-euphoria' }
    $OutFile = Join-Path $repo ('patch\' + $sub + '\shaders\lib\lighting\mainLighting.glsl')
}

# ---------------------------------------------------------------------------
#  Read, and match the host's line ending so the inserted text does not mix EOL
# ---------------------------------------------------------------------------
$text = [System.IO.File]::ReadAllText($src, $utf8NoBom)
$eol = $LF
if ($text.Contains($CRLF)) { $eol = $CRLF }

$block = [System.IO.File]::ReadAllText($blockFile, $utf8NoBom)
if ($block.Contains($CRLF)) { $block = $block.Replace($CRLF, $LF) }
$block = $block.Trim() + $LF
if ($eol -ne $LF) { $block = $block.Replace($LF, $eol) }

# ---------------------------------------------------------------------------
#  The two anchors, found by text (never by line number: Plain and Euphoria
#  differ by ~243 lines but these two lines are character-identical in both)
# ---------------------------------------------------------------------------
$anchorBlock = 'vec3 highlightColor = normalize(pow(lightColor, vec3(0.37))) * (0.3 + 1.5 * sunVisibility2) * (1.0 - 0.85 * rainFactor);'
$anchorCall  = 'vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission);'
$callTail    = '+ minLighting) + pow2(emission);'
$callText    = '+ minLighting + pow2(get_slo_lighting_compat(playerPos, worldGeoNormal, vanillaAO))) + pow2(emission);'
$callNeedle  = 'get_slo_lighting_compat(playerPos, worldGeoNormal, vanillaAO)'

function Count-Exact([string]$hay, [string]$needle) {
    return ([regex]::Matches($hay, [regex]::Escape($needle))).Count
}
function Get-LineNumber([string]$hay, [string]$needle) {
    # the call anchor is the tail of an indented line, so compare trimmed text
    $lines = $hay -split "`r?`n"
    $want = $needle.Trim()
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i].Trim() -ceq $want) { return ($i + 1) } }
    return -1
}

$nBlock = Count-Exact $text $anchorBlock
$nCall  = Count-Exact $text $anchorCall
$nTail  = Count-Exact $anchorCall $callTail
Write-Output "pack          = $Pack"
Write-Output "source        = $src"
Write-Output "anchor(block) occurrences = $nBlock"
Write-Output "anchor(call)  occurrences = $nCall"
Write-Output "call tail     occurrences = $nTail"
if ($nBlock -ne 1 -or $nCall -ne 1 -or $nTail -ne 1) { throw 'anchors are not unique - refusing to patch' }

$lineBlockBefore = Get-LineNumber $text $anchorBlock
$lineCallBefore  = Get-LineNumber $text $anchorCall
Write-Output "anchor(block) = line $lineBlockBefore : $anchorBlock"
Write-Output "anchor(call)  = line $lineCallBefore : $anchorCall"

# ---------------------------------------------------------------------------
#  (a) inline the whole bridge at file scope, immediately before the anchor,
#      which is the first file-scope line after the last #include of the file
#      (the include itself sits inside its own #ifdef guard, so inserting
#      before this line is the only place that is genuinely unconditional).
#  (b) square the bridge into the finalDiffuse bracket group, one line above
#      the sqrt(max(...)) that follows it.
# ---------------------------------------------------------------------------
$patched = $text.Replace($anchorBlock, $block + $eol + $eol + $anchorBlock)
$patched = $patched.Replace($anchorCall, $anchorCall.Replace($callTail, $callText))

if (-not $patched.Contains($callNeedle)) { throw 'call insertion missing' }
if (-not $patched.Contains('uniform vec4 sloLight31;')) { throw 'inlined bridge missing' }
if (-not $patched.Contains('#ifndef INCLUDE_LIGHTING_SLO_COMPAT')) { throw 'inlined guard missing' }

$outDir = Split-Path -Parent $OutFile
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
[System.IO.File]::WriteAllText($OutFile, $patched, $utf8NoBom)

function Show-Bytes([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $last = [Math]::Min(7, $bytes.Length - 1)
    $first = ($bytes[0..$last] | ForEach-Object { $_.ToString('X2') }) -join ' '
    Write-Output ("  path   = {0}" -f $path)
    Write-Output ("  bytes  = {0}" -f $bytes.Length)
    Write-Output ("  hasBom = {0}" -f $hasBom)
    Write-Output ("  first8 = {0}" -f $first)
    if ($hasBom) { throw "written file has a BOM: $path" }
    # Plain starts with '//Lighting Includes//', Euphoria starts with an #include
    if ($bytes[0] -ne 0x2F -and $bytes[0] -ne 0x23) { throw "written file does not start with '/' or '#': $path" }
}

$lineBlockAfter = Get-LineNumber $patched $anchorBlock
$lineCallAfter  = Get-LineNumber $patched ($anchorCall.Replace($callTail, $callText))
if ($lineBlockAfter -le 0) { throw 'inlined block anchor lost after patching' }
if ($lineCallAfter -le 0) { throw 'rewritten finalDiffuse line not found after patching' }
$origLines = ([System.IO.File]::ReadAllLines($src, $utf8NoBom)).Count
$newLines  = ([System.IO.File]::ReadAllLines($OutFile, $utf8NoBom)).Count

Write-Output "patched -> $OutFile"
Write-Output ("lines = {0} -> {1} (added {2})" -f $origLines, $newLines, ($newLines - $origLines))
Write-Output ("anchor(block) = line {0} -> {1}" -f $lineBlockBefore, $lineBlockAfter)
Write-Output ("anchor(call)  = line {0} -> {1} (rewritten in place: our squared term is in the bracket group)" -f $lineCallBefore, $lineCallAfter)
Show-Bytes $OutFile

# ---------------------------------------------------------------------------
#  Optional: emit a complete patched pack tree (deliverable 4). Never touches
#  the source tree, never touches a zip, and refuses to write into an existing
#  folder so an instance pack can only ever be created, not overwritten.
# ---------------------------------------------------------------------------
if ($DeployRoot) {
    if (Test-Path -LiteralPath $DeployRoot) { throw "refusing to deploy: $DeployRoot already exists" }
    $packRoot = Split-Path -Parent $SourceRoot
    New-Item -ItemType Directory -Force -Path $DeployRoot | Out-Null
    Get-ChildItem -LiteralPath $packRoot -Force | Copy-Item -Destination $DeployRoot -Recurse -Force
    $deployFile = Join-Path $DeployRoot 'shaders\lib\lighting\mainLighting.glsl'
    if (-not (Test-Path -LiteralPath $deployFile)) { throw "deployed tree has no shaders\lib\lighting\mainLighting.glsl" }
    [System.IO.File]::WriteAllText($deployFile, $patched, $utf8NoBom)
    Write-Output "deployed -> $DeployRoot"
    Show-Bytes $deployFile
    $deployedCount = (Get-ChildItem -LiteralPath $DeployRoot -Recurse -File).Count
    $sourceCount = (Get-ChildItem -LiteralPath $packRoot -Recurse -File).Count
    Write-Output ("deployed files = {0} (source {1})" -f $deployedCount, $sourceCount)
    if ($deployedCount -ne $sourceCount) { throw 'deployed tree has a different file count than the source tree' }
}
