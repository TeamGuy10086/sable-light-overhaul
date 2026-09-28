$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

# Discover the instance rather than hardcoding its name: a non-ASCII literal inside a BOM-less
# .ps1 is decoded as ANSI by Windows PowerShell and gets mangled.
$versionsRoot = 'G:\HelloMC\.minecraft\versions'
$versionDir = Get-ChildItem -LiteralPath $versionsRoot -Directory -ErrorAction Stop |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'config\cbcmuzzlelight-client.toml') } |
    Select-Object -First 1
if (-not $versionDir) { throw 'could not locate a modpack config to migrate' }
$live = Join-Path $versionDir.FullName 'config\cbcmuzzlelight-client.toml'
Write-Output "live config = $live"
$deliver = 'D:\DSH\McMOD\work\cbc-muzzle-light\patch\cbcmuzzlelight-client.toml'

$lines = [System.IO.File]::ReadAllLines($live, $utf8NoBom)
Write-Output ("source lines = {0}" -f $lines.Count)

$out = New-Object System.Collections.Generic.List[string]
$modeChanged = 0
$removed = 0
foreach ($line in $lines) {
    $trim = $line.Trim()
    # The in-pack switch is gone: the Advanced tier decides this now.
    if ($trim -like 'inPackLighting*' -or ($trim.StartsWith('#') -and $trim.Contains('inPackLighting'))) {
        $removed++
        continue
    }
    if ($trim -like 'lightingMode*') {
        $out.Add('lightingMode = "Advanced"')
        $modeChanged++
        continue
    }
    $out.Add($line)
}
Write-Output ("lightingMode rewritten = {0}, inPackLighting lines removed = {1}" -f $modeChanged, $removed)
if ($modeChanged -ne 1) { throw 'expected exactly one lightingMode line' }

$text = ($out -join [string][char]10) + [string][char]10
[System.IO.File]::WriteAllText($deliver, $text, $utf8NoBom)
Write-Output "deliverable -> $deliver"

# Back up the live file and install the new one.
Copy-Item -LiteralPath $live -Destination ($live + '.bak-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) -Force
[System.IO.File]::WriteAllText($live, $text, $utf8NoBom)
Write-Output "live config replaced (backup kept next to it)"

# Show the result.
Select-String -LiteralPath $live -Pattern '^lightingMode|^\t(intensity|normalInfluence|packPathIntensity|shadowLightCount|colorRed|colorGreen|colorBlue) ' |
    ForEach-Object { "  {0}: {1}" -f $_.LineNumber, $_.Line }
Write-Output ("any inPackLighting left: {0}" -f ((Select-String -LiteralPath $live -Pattern 'inPackLighting').Count))
