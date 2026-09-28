<#
.SYNOPSIS
    Deploy the mod jar + the Photon shader patch into a Minecraft instance, and verify both.

.DESCRIPTION
    Why this script exists: the shader patch has always been installed by *hand*, by copying a
    single file. As soon as the instance directory moves (e.g. the game was copied from G: to D:)
    or the patch was produced *after* that copy, it is very easy to end up with an instance that
    is missing a change. The symptom of that miss is something like "the flashlight has no cone",
    which you can only discover by launching the game.

    This script therefore turns "what should be installed in this instance" into an executable,
    reviewable fact.

    It manages exactly two things:
      1) mods\<archive name>-<version>.jar
         Byte-compared against the freshly built jar of the same name in build\libs. Any *other*
         version of our jar sitting in mods\ is deleted, because two jars with the same mod id
         stop the game from starting.
      2) shaderpacks\<pack>\shaders\include\lighting\diffuse_lighting.glsl
         The patch artifact is regenerated first (idempotent), then compared with the copy inside
         the instance and overwritten when it differs.

    config\*.toml inside the instance is NEVER rewritten: those are user-tuned values and the mod
    adds new keys by itself. The script only reports their presence and the lightingMode value.

.PARAMETER Instance
    The instance *version* directory - the one containing mods / config / shaderpacks. NOT the
    .minecraft directory. When omitted, the script searches the known instance roots and uses the
    only version folder that already has our mod configured.

.PARAMETER ShaderPack
    Name of the shaderpack *folder* (not the .zip). The folder form is what the README recommends.
    When omitted, the script picks the only pack folder that contains a diffuse_lighting.glsl.

.PARAMETER Check
    Report only; do not modify the instance. The patch artifact inside the repo is still
    regenerated (idempotent) so that a stale artifact cannot hide.

.EXAMPLE
    & 'D:\DSH\McMOD\work\sable-light-overhaul\patch\deploy-to-instance.ps1' -Check

.EXAMPLE
    & 'D:\DSH\McMOD\work\sable-light-overhaul\patch\deploy-to-instance.ps1' -Instance 'G:\HelloMC\.minecraft\versions\<version folder>'

.NOTES
    Keep this file pure ASCII. It runs under Windows PowerShell 5.1 with the ANSI code page set to
    936, which decodes a BOM-less file as GBK and turns any CJK literal into a parse error. Every
    other .ps1 in this repo is ASCII-only for exactly the same reason. That is also why the
    instance folder (whose name is CJK) is discovered instead of hard-coded.
#>
[CmdletBinding()]
param(
    [string]$Instance   = '',
    [string]$ShaderPack = '',
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$repo     = Split-Path -Parent $PSScriptRoot
$patchOut = Join-Path $repo 'patch\photon\shaders\include\lighting\diffuse_lighting.glsl'
$fragment = Join-Path $repo 'patch\source\cbc_muzzle_light.glsl'
$apply    = Join-Path $repo 'patch\apply-photon-patch.ps1'
$pristine = 'D:\DSH\McMOD\work\photon-inspection\photon-voxy-support\shaders\include\lighting\diffuse_lighting.glsl'

# Version roots that may hold a game instance. ASCII only, so never written as a literal path.
$roots = @(
    'D:\DSH\HelloMC\.minecraft\versions',
    'G:\HelloMC\.minecraft\versions'
)

$problems = New-Object System.Collections.ArrayList
$report   = New-Object System.Collections.ArrayList
function Add-Row([string]$item, [string]$state, [string]$detail) {
    [void]$report.Add((New-Object psobject -Property @{ Item = $item; State = $state; Detail = $detail }))
}
function Sha([string]$p) { (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash }
function Short([string]$h) { $h.Substring(0, 16) + '...' }

# ------------------------- 0. locate the instance -------------------------
if (-not $Instance) {
    $found = @()
    foreach ($r in $roots) {
        if (-not (Test-Path -LiteralPath $r)) { continue }
        foreach ($d in (Get-ChildItem -LiteralPath $r -Directory)) {
            if (Test-Path -LiteralPath (Join-Path $d.FullName 'mods')) { $found += $d.FullName }
        }
    }
    if ($found.Count -eq 1) { $Instance = $found[0] }
    elseif ($found.Count -eq 0) { throw "no instance found under: $($roots -join ' ; ') - pass -Instance" }
    else { throw "several instances found, pass -Instance explicitly:`n  " + ($found -join "`n  ") }
}

Write-Host ""
Write-Host "Instance   : $Instance"
Write-Host "Repo       : $repo"
Write-Host "Mode       : $(if ($Check) { 'CHECK (instance untouched)' } else { 'DEPLOY (instance will be modified)' })"

foreach ($p in @($Instance, (Join-Path $Instance 'mods'), (Join-Path $Instance 'shaderpacks'))) {
    if (-not (Test-Path -LiteralPath $p)) { throw "incomplete instance directory, missing: $p" }
}

# ------------------------- 0b. locate the shaderpack -------------------------
$packRoot = Join-Path $Instance 'shaderpacks'
if (-not $ShaderPack) {
    $cand = @(Get-ChildItem -LiteralPath $packRoot -Directory |
              Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'shaders\include\lighting\diffuse_lighting.glsl') })
    if ($cand.Count -eq 1) { $ShaderPack = $cand[0].Name }
    elseif ($cand.Count -eq 0) { throw "no shaderpack folder here contains shaders\include\lighting\diffuse_lighting.glsl" }
    else { throw "several candidate shaderpacks, pass -ShaderPack: " + (($cand | ForEach-Object { $_.Name }) -join ', ') }
}
Write-Host "ShaderPack : $ShaderPack"
Write-Host ""

# ------------------------- 1. the mod jar -------------------------
$ver = (Select-String -LiteralPath (Join-Path $repo 'build.gradle') `
        -Pattern "^version\s*=\s*'([^']+)'" | Select-Object -First 1).Matches[0].Groups[1].Value
if (-not $ver) { throw 'cannot read version from build.gradle' }
# The jar's own name comes out of build.gradle too, so that renaming the project cannot silently leave
# this script looking for a file that no longer exists. Both are single sources of truth there.
$archive = (Select-String -LiteralPath (Join-Path $repo 'build.gradle') `
        -Pattern "archivesName\s*=\s*'([^']+)'" | Select-Object -First 1).Matches[0].Groups[1].Value
if (-not $archive) { throw 'cannot read archivesName from build.gradle' }

$jarName   = "$archive-$ver.jar"
$built     = Join-Path $repo     "build\libs\$jarName"
$installed = Join-Path $Instance "mods\$jarName"

if (-not (Test-Path -LiteralPath $built)) {
    Add-Row "mods\$jarName" 'FAIL' 'not in build\libs - run .\gradlew.bat build first'
    [void]$problems.Add("build\libs\$jarName does not exist - run .\gradlew.bat build")
}
else {
    $shaBuilt = Sha $built
    if (-not (Test-Path -LiteralPath $installed)) {
        if ($Check) {
            Add-Row "mods\$jarName" 'MISSING' 'not installed'
            [void]$problems.Add("instance is missing $jarName")
        } else {
            Copy-Item -LiteralPath $built -Destination $installed -Force
            Add-Row "mods\$jarName" 'COPIED' "sha $(Short $shaBuilt)"
        }
    }
    elseif ((Sha $installed) -ne $shaBuilt) {
        if ($Check) {
            Add-Row "mods\$jarName" 'DIFFERS' "instance $(Short (Sha $installed)) != repo $(Short $shaBuilt)"
            [void]$problems.Add("$jarName in the instance differs from the repo build")
        } else {
            Copy-Item -LiteralPath $built -Destination $installed -Force
            Add-Row "mods\$jarName" 'UPDATED' "sha $(Short $shaBuilt)"
        }
    }
    else {
        Add-Row "mods\$jarName" 'OK' "sha $(Short $shaBuilt)"
    }
}

# Two jars with the same mod id stop the game from starting.
#
# Every archive name this mod has ever shipped under has to be swept, not just the current one: after a
# rename the previous jar is still sitting in mods\ under the old name, and `archivesName` alone would
# never match it. The mod id did not change, so the game sees two copies of the same mod and refuses to
# start. Add the new name here if it is ever renamed again.
$archiveNames = @($archive, 'cbc-muzzle-light')
$stale = @(Get-ChildItem -LiteralPath (Join-Path $Instance 'mods') -File -Filter '*.jar' |
           Where-Object {
               $name = $_.Name
               if ($name -eq $jarName) { return $false }
               foreach ($a in $archiveNames) { if ($name -like "$a-*.jar") { return $true } }
               return $false
           })
foreach ($s in $stale) {
    if ($Check) {
        Add-Row "mods\$($s.Name)" 'STALE' 'older version with the same mod id - must be removed'
        [void]$problems.Add("stale $($s.Name) in mods\ (two jars with one mod id break startup)")
    } else {
        Remove-Item -LiteralPath $s.FullName -Force
        Add-Row "mods\$($s.Name)" 'REMOVED' 'older version with the same mod id'
    }
}

# ------------------------- 2. the Photon shader patch -------------------------
$packDir = Join-Path $packRoot $ShaderPack

if (-not (Test-Path -LiteralPath $packDir)) {
    $avail = (Get-ChildItem -LiteralPath $packRoot -Directory | ForEach-Object { $_.Name }) -join ', '
    Add-Row "shaderpacks\$ShaderPack" 'FAIL' "not a folder; folders present: $avail"
    [void]$problems.Add("shaderpack folder $ShaderPack not found (present: $avail)")
}
else {
    # 2a. regenerate the artifact, so a stale one cannot hide
    if (-not (Test-Path -LiteralPath $pristine)) {
        Add-Row 'patch artifact' 'SKIP' "pristine Photon source missing, using committed artifact ($pristine)"
    }
    elseif (-not (Test-Path -LiteralPath $fragment)) {
        Add-Row 'patch artifact' 'FAIL' "missing source fragment $fragment"
        [void]$problems.Add("missing patch source fragment $fragment")
    }
    else {
        $before = Sha $patchOut
        & $apply | Out-Null
        $after  = Sha $patchOut
        if ($before -eq $after) {
            Add-Row 'patch artifact' 'OK' "up to date $(Short $after)"
        } else {
            Add-Row 'patch artifact' 'REGENERATED' "fragment and artifact had drifted apart; rebuilt $(Short $after)"
        }
    }

    # 2b. compare with the copy inside the instance
    $dst = Join-Path $packDir 'shaders\include\lighting\diffuse_lighting.glsl'
    if (-not (Test-Path -LiteralPath $dst)) {
        Add-Row 'diffuse_lighting.glsl' 'FAIL' "path does not exist: $dst"
        [void]$problems.Add("diffuse_lighting.glsl not found in the pack: $dst")
    }
    else {
        $shaNew = Sha $patchOut
        $shaCur = Sha $dst
        if ($shaCur -eq $shaNew) {
            Add-Row 'diffuse_lighting.glsl' 'OK' "sha $(Short $shaNew)"
        }
        elseif ($Check) {
            Add-Row 'diffuse_lighting.glsl' 'DIFFERS' "instance $(Short $shaCur) != patch $(Short $shaNew) - PATCH NOT INSTALLED"
            [void]$problems.Add('the shader patch is not installed (instance holds an older file)')
        }
        else {
            # keep one copy of whatever was there before the first deploy
            $bak = @(Get-ChildItem -LiteralPath (Split-Path -Parent $dst) -File -Filter 'diffuse_lighting.glsl.bak*' -ErrorAction SilentlyContinue)
            $bakName = 'diffuse_lighting.glsl.bak-original'
            if ($bak.Count -gt 0) { $bakName = $bak[0].Name }
            else { Copy-Item -LiteralPath $dst -Destination "$dst.bak-original" }
            Copy-Item -LiteralPath $patchOut -Destination $dst -Force

            $bytes = [System.IO.File]::ReadAllBytes($dst)
            $bom   = ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
            if ($bom) { [void]$problems.Add('the installed patch has a UTF-8 BOM - Iris preprocessing will fail') }
            if ($bytes[0] -ne 0x23) { [void]$problems.Add('the installed patch does not start with #') }

            Add-Row 'diffuse_lighting.glsl' 'UPDATED' ("sha $(Short (Sha $dst))  bytes=$($bytes.Length)  bom=$bom  backup=$bakName")
        }
    }
}

# ------------------------- 3. config (report only, never rewritten) -------------------------
foreach ($cfg in 'cbcmuzzlelight-client.toml', 'cbcmuzzlelight-server.toml') {
    $p = Join-Path $Instance "config\$cfg"
    if (Test-Path -LiteralPath $p) {
        Add-Row "config\$cfg" 'OK' "bytes=$((Get-Item -LiteralPath $p).Length)"
    } else {
        Add-Row "config\$cfg" 'ABSENT' 'the mod generates it on first launch (defaults)'
    }
}

$clientCfg = Join-Path $Instance 'config\cbcmuzzlelight-client.toml'
if (Test-Path -LiteralPath $clientCfg) {
    $m = Select-String -LiteralPath $clientCfg -Pattern '^\s*lightingMode\s*=\s*"([^"]+)"' | Select-Object -First 1
    if ($m) {
        $lm = $m.Matches[0].Groups[1].Value
        if ($lm -eq 'Advanced') { Add-Row 'lightingMode' 'OK' "$lm (Advanced is the tier this patch serves)" }
        else { Add-Row 'lightingMode' 'NOTE' "$lm - the patch does nothing outside Advanced (a perfectly legal combination)" }
    } else {
        Add-Row 'lightingMode' 'NOTE' 'key not present yet, the mod will add it'
    }
}

# ------------------------- 4. verdict -------------------------
$report | Format-Table -AutoSize Item, State, Detail | Out-String -Width 220 | Write-Host

if ($problems.Count -eq 0) {
    Write-Host "All consistent." -ForegroundColor Green
} else {
    Write-Host "$($problems.Count) item(s) need attention:" -ForegroundColor Yellow
    $problems | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "Reminder: after changing GLSL you MUST make Iris reload the shaderpack"
Write-Host "          (re-select the pack in the list, or restart the game)."
Write-Host "          F3+R reloads chunks/resources, not shaders."
Write-Host ""

if ($problems.Count -gt 0) { exit 1 }
exit 0
