<#
.SYNOPSIS
    Migrate the [flashlight] section of a server config to the 0.10.0 units.

.DESCRIPTION
    NeoForge never overwrites a config key that already exists, so the new defaults in LightingCommon
    do NOT reach an instance whose file was written by an older build. That matters more than usual
    here, because two of the keys changed MEANING rather than value:

      width    was a 1..15 "signal" (10..52 degrees); it is now a full angle in degrees (5..90).
               A file still saying 5 would now mean a five-degree beam, not a 22-degree one.
      falloff  was one of five named profiles; it is gone, replaced by "decay" (a percentage).
      range    kept its meaning, but its default moved from step 8 (240 blocks) to step 3 (80 blocks).
      intensity and pointRadius kept their meaning; only their defaults moved.

    Deleting the [flashlight] section IS the migration: the mod rebuilds it from its own spec on the
    next load, which is the only way to get both the new keys and the new defaults in one go. That is
    only safe while every decisive value is still the OLD default, so this script refuses if any of
    them was edited and prints what it found instead.

    The client config needs nothing: its new keys are pure additions, and a missing key is created
    with its default.

.PARAMETER Instance
    The instance version directory. Discovered the same way deploy-to-instance.ps1 discovers it.

.PARAMETER Check
    Report only; change nothing.

.NOTES
    Keep this file pure ASCII: Windows PowerShell 5.1 decodes a BOM-less .ps1 as ANSI (code page 936
    on this machine) and any CJK literal becomes a parse error.
#>
[CmdletBinding()]
param(
    [string]$Instance = '',
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$roots = @(
    'D:\DSH\HelloMC\.minecraft\versions',
    'G:\HelloMC\.minecraft\versions'
)

if (-not $Instance) {
    $found = @()
    foreach ($r in $roots) {
        if (-not (Test-Path -LiteralPath $r)) { continue }
        foreach ($d in (Get-ChildItem -LiteralPath $r -Directory)) {
            if (Test-Path -LiteralPath (Join-Path $d.FullName 'config\cbcmuzzlelight-server.toml')) { $found += $d.FullName }
        }
    }
    if ($found.Count -eq 1) { $Instance = $found[0] }
    elseif ($found.Count -eq 0) { throw "no instance with a server config found under: $($roots -join ' ; ')" }
    else { throw "several instances found, pass -Instance explicitly:`n  " + ($found -join "`n  ") }
}

$config = Join-Path $Instance 'config\cbcmuzzlelight-server.toml'
if (-not (Test-Path -LiteralPath $config)) { throw "missing server config: $config" }

Write-Host ""
Write-Host "Instance : $Instance"
Write-Host "Config   : $config"
Write-Host "Mode     : $(if ($Check) { 'CHECK (nothing written)' } else { 'MIGRATE' })"
Write-Host ""

$text = [System.IO.File]::ReadAllText($config, $utf8NoBom)

# The section runs from its own header to the next header, or to the end of the file.
$pattern = "(?ms)^\[flashlight\][ \t]*\r?\n(?<body>.*?)(?=^\[|\z)"
$match = [regex]::Match($text, $pattern)
if (-not $match.Success) {
    Write-Host "No [flashlight] section: nothing to migrate." -ForegroundColor Green
    exit 0
}
$body = $match.Groups['body'].Value

# What the old build wrote. Anything else means a human edited it and the units are ambiguous.
$keys = @('color','range','width','falloff','intensity','pointRadius','pointIntensity')
$expected = @{ color = '0'; range = '8'; width = '5'; falloff = '2'; intensity = '12'; pointRadius = '6'; pointIntensity = '10' }
$found = @{}
foreach ($key in $keys) {
    $m = [regex]::Match($body, "(?m)^[ \t]*$key[ \t]*=[ \t]*(?<v>[^\r\n#]+)")
    if ($m.Success) { $found[$key] = $m.Groups['v'].Value.Trim() } else { $found[$key] = '<absent>' }
}

Write-Host "Found in [flashlight]:"
foreach ($key in $keys) {
    $mark = ' '
    if ($found[$key] -ne $expected[$key]) { $mark = '!' }
    Write-Host ("  {0} {1,-15} {2,-9} (old default {3})" -f $mark, $key, $found[$key], $expected[$key])
}
Write-Host ""

# The four that decide whether the units are unambiguous. pointIntensity and color never changed, and
# falloff is the key being retired, so none of those three is a reason to stop.
$decisive = @('range','width','intensity','pointRadius')
$edited = @($decisive | Where-Object { $found[$_] -ne $expected[$_] })
if ($edited.Count -gt 0) {
    Write-Host "REFUSING: these were changed from the old defaults, so the units are ambiguous:" -ForegroundColor Yellow
    $edited | ForEach-Object { Write-Host "  - $_ = $($found[$_])" -ForegroundColor Yellow }
    Write-Host ""
    Write-Host "Decide what each should be under the new units, set them by hand, then run this again:"
    Write-Host "  range       1..32   a step; step 3 is 80 blocks on the shipped 16/32/512 table"
    Write-Host "  width       5..90   full angle in DEGREES (it was a 1..15 signal meaning 10..52 degrees)"
    Write-Host "  decay      20..300  percent; 100 is the reference shape (this replaced 'falloff')"
    Write-Host "  intensity   0..15"
    Write-Host "  pointRadius 0..24"
    exit 1
}

if ($Check) {
    Write-Host "Safe to migrate: every decisive value is still the old default." -ForegroundColor Green
    Write-Host "Run again without -Check to drop the section and let the mod rebuild it." -ForegroundColor Green
    exit 0
}

# Drop the section, and the blank line that separated it from the one before it.
$start = $match.Index
$end = $match.Index + $match.Length
$before = $text.Substring(0, $start)
$after = $text.Substring($end)
$before = [regex]::Replace($before, "(\r?\n)[ \t]*\r?\n\z", '$1')
$new = $before + $after
if (-not $new.EndsWith([string][char]10)) { $new += [string][char]10 }

$backup = $config + '.bak-flashlight-units-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
Copy-Item -LiteralPath $config -Destination $backup -Force
[System.IO.File]::WriteAllText($config, $new, $utf8NoBom)

Write-Host "Removed the [flashlight] section; the mod rebuilds it with the new keys on the next load." -ForegroundColor Green
Write-Host "Backup kept at: $backup"
Write-Host ""
Write-Host "Keys that will come back, with the new defaults:"
Write-Host "  color = 0     range = 3 (80 blocks)   width = 22 (degrees)   decay = 100"
Write-Host "  intensity = 10   pointRadius = 3   pointIntensity = 10   syncRange = 48"
Write-Host "  ('falloff' is gone: it was replaced by 'decay'.)"
exit 0
