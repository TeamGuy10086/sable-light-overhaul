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

    It manages exactly three things:
      1) mods\<archive name>-<version>.jar
         Byte-compared against the freshly built jar of the same name in build\libs. Any *other*
         version of our jar sitting in mods\ is deleted, because two jars with the same mod id
         stop the game from starting.
      2) shaderpacks\<pack>\shaders\**  -- every file under patch\photon\shaders, mirrored
         The patch artifacts are regenerated first (idempotent, from patch\source), then each file is
         compared with the copy inside the instance and overwritten when it differs. The list of files
         comes from the repository, so a new patched file is deployed without this script being told
         about it -- which is the whole point: the failure this repairs is a change that was built and
         then never installed, and the same failure one level up is a script that knows about some of
         the files.
      3) shaderpacks\<pack>\SLO-PATCH.txt -- the pack's own marker, written from those same hashes.
         A hand-written marker went stale (it advertised a hash that was not the file beside it), so
         the pack folder's own description of itself is now generated and checked like anything else.

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
    [switch]$Check,
    [switch]$NoBuild,
    # Shader files only, with the running-game check skipped.
    #
    # A jar cannot be replaced under a running game -- the classes already loaded come from the old one and the ones it
    # loads later from the new one -- so that check stays for anything that writes into mods\. GLSL has no such rule:
    # the pack is read from disk when it is selected, so replacing a shader file while the game runs is exactly how a
    # shader iteration is done, and requiring a shutdown for it only slows the loop. This switch mirrors the shader
    # files and the pack marker and touches nothing else.
    [switch]$ShadersOnly
)

$ErrorActionPreference = 'Stop'

$repo     = Split-Path -Parent $PSScriptRoot
$fragment = Join-Path $repo 'patch\source\slo_light.glsl'
$apply    = Join-Path $repo 'patch\apply-photon-patch.ps1'
$pristine = (Join-Path $PSScriptRoot 'baselines\photon\shaders\include\lighting\diffuse_lighting.glsl')
# The whole pristine tree, so any file in the patch can be told apart from "a pack that is not this one".
$pristineRoot = (Join-Path $PSScriptRoot 'baselines\photon\shaders')

# Version roots that may hold a game instance. ASCII only, so never written as a literal path.
$roots = @(
    'D:\DSH\HelloMC\.minecraft\versions',
    'G:\HelloMC\.minecraft\versions'
)

$problems = New-Object System.Collections.ArrayList
$skips    = New-Object System.Collections.ArrayList
$report   = New-Object System.Collections.ArrayList
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
# What marks a file in an instance as one this patch wrote: any identifier our fragments inject. Kept as
# one regex so the FOREIGN message can name exactly what it looked for.
$oursMarker = 'sloLight|slo_cloud|sloStyle|SLO_CLOUDS_INCLUDED|slo_translucent|sloTranslucentLayer|SLO-CAPABILITY|slo_capability|slo_z_lod|sloMuzzleKind|slo_muzzle|TRANSLUCENT_BASIC|slo\$'
function Add-Row([string]$item, [string]$state, [string]$detail) {
    [void]$report.Add((New-Object psobject -Property @{ Item = $item; State = $state; Detail = $detail }))
}
function Sha([string]$p) { (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash }
function Short([string]$h) { $h.Substring(0, 16) + '...' }
<#
    Replace a file so that a reader can only ever see the whole old one or the whole new one.

    Copy-Item writes into the destination, so between its first byte and its last the file on disk is a
    half-arrived copy. Anything reading it in that window gets a truncated zip or a partial GLSL file, and a
    launcher that has already opened a mod jar reports it as "the specified mixin ... was not found" -- a
    message that blames a class which is present and correct in the finished file.

    Copying beside the destination and renaming fixes the ordering, because a rename within one volume is
    atomic: the name switches from the old file to the new one without an intermediate state. The temporary
    is written in the same directory so the rename never crosses a volume, and it is named after this process
    so a failed run cannot leave a file a later run would mistake for its own.
#>
function Copy-FileAtomic([string]$source, [string]$destination) {
    $dir = Split-Path -Parent $destination
    $tmp = Join-Path $dir ("." + (Split-Path -Leaf $destination) + "." + $PID + ".deploying")
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force }
    Copy-Item -LiteralPath $source -Destination $tmp -Force
    try {
        # -Force replaces an existing destination. If the game holds it open this throws, which is the wanted
        # outcome: refusing beats leaving a truncated jar where a running game will read it.
        Move-Item -LiteralPath $tmp -Destination $destination -Force
    } catch {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
        throw ("cannot replace $destination - is the game still running? ($($_.Exception.Message))")
    }
}

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

# ------------------------- 0a. is the game running? -------------------------
# A deploy while the game is running is the worst case of all, not just the starting case below: the classes
# already loaded come from the jar that was there when the game began, and every class it loads later comes from
# the replacement. The game then runs as a mixture of two builds, and any symptom it shows is unattributable --
# a lamp that lights in one build and not the other can do either, depending on the order classes happened to be
# resolved. A deploy while the game is *starting* is the narrower version of the same fault: the copy lands in
# place, the class loader reads a zip whose central directory is still arriving, and every mixin in the jar
# becomes "ClassNotFoundException ... was not found", which names a perfectly good class and says nothing about
# the cause. Every write below goes through Copy-FileAtomic, which a reader can only ever observe before or
# after; this check exists to say the one thing that fix cannot -- "start the game after the deploy, not before".
#
# The match deliberately does NOT look for the instance path. It did once, and it missed: with c2me and sodium
# installed the launcher's command line is rewritten to the point of naming neither the game directory nor this
# instance, so a running game went undetected and a jar was replaced under it. What every Minecraft client has
# in common is the main class and the launch target, so those are what is matched.
if (-not $Check -and -not $ShadersOnly) {
    $running = $null
    try {
        $running = @(Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" -ErrorAction Stop |
            Where-Object {
                $_.CommandLine -and (
                    $_.CommandLine.Contains('net.minecraft.client.main.Main') -or
                    $_.CommandLine.Contains('forgeclient') -or
                    $_.CommandLine.Contains('--gameDir') -or
                    $_.CommandLine.ToLowerInvariant().Contains($Instance.ToLowerInvariant()))
            })
    } catch {
        # A sandbox that forbids process enumeration must not break the deploy: fall through to the write-time
        # lock, which still refuses rather than truncates.
        $running = $null
    }
    if ($running.Count -gt 0) {
        throw ("a Minecraft client is running (pid {0}). Close it, deploy, then start it: a jar replaced under a " +
               "running game leaves it a mixture of two builds, and every symptom from it is unattributable.") `
              -f (($running | ForEach-Object { $_.ProcessId }) -join ', ')
    }
}

# ------------------------- 0b. locate the shaderpack(s) -------------------------
# More than one patched pack folder is a normal state, not an error: this repository's own instance keeps a
# working copy and a spare, and Iris loads exactly one of them (the iris.properties row names which). Every
# folder that already holds a patched pack gets the same files, so which one is loaded can never decide
# whether a change is installed. Pass -ShaderPack to narrow it to one.
$packRoot = Join-Path $Instance 'shaderpacks'
if ($ShaderPack) {
    if (-not (Test-Path -LiteralPath (Join-Path $packRoot $ShaderPack))) { throw "shaderpack folder not found: $ShaderPack" }
    $targets = @($ShaderPack)
} else {
    $cand = @(Get-ChildItem -LiteralPath $packRoot -Directory |
              Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'shaders\include\lighting\diffuse_lighting.glsl') })
    if ($cand.Count -eq 0) { throw "no shaderpack folder here contains shaders\include\lighting\diffuse_lighting.glsl" }
    $targets = @($cand | ForEach-Object { $_.Name })
}
Write-Host "ShaderPack : $($targets -join ', ')"
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

# Build from clean before comparing anything. A stale build directory can produce a jar that refers to a
# class its own sources no longer contain, and the only symptom is a NoClassDefFoundError mid-game, hours
# later, from a jar nobody can reproduce. The clean build costs a handful of seconds and removes the class
# of failure entirely; -NoBuild skips it when the jar was just built by hand.
if (-not $Check -and -not $NoBuild -and -not $ShadersOnly) {
    Write-Host 'Building from clean ...' -ForegroundColor Cyan
    Push-Location $repo
    try {
        & (Join-Path $repo 'gradlew.bat') clean build --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradle clean build failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

$jarName   = "$archive-$ver.jar"
$built     = Join-Path $repo     "build\libs\$jarName"
$installed = Join-Path $Instance "mods\$jarName"

if (-not (Test-Path -LiteralPath $built)) {
    Add-Row "mods\$jarName" 'FAIL' 'not in build\libs - run .\gradlew.bat build first'
    [void]$problems.Add("build\libs\$jarName does not exist - run .\gradlew.bat build")
}
else {
    # Shader-only deploys never look at the jar: it is not what is being installed, and the copy would need the game
 # closed for no reason.
 if (-not $ShadersOnly) {
 $shaBuilt = Sha $built
    if (-not (Test-Path -LiteralPath $installed)) {
        if ($Check) {
            Add-Row "mods\$jarName" 'MISSING' 'not installed'
            [void]$problems.Add("instance is missing $jarName")
        } else {
            Copy-FileAtomic   -Source $built -Destination $installed
            Add-Row "mods\$jarName" 'COPIED' "sha $(Short $shaBuilt)"
        }
    }
    elseif ((Sha $installed) -ne $shaBuilt) {
        if ($Check) {
            Add-Row "mods\$jarName" 'DIFFERS' "instance $(Short (Sha $installed)) != repo $(Short $shaBuilt)"
            [void]$problems.Add("$jarName in the instance differs from the repo build")
        } else {
            Copy-FileAtomic   -Source $built -Destination $installed
            Add-Row "mods\$jarName" 'UPDATED' "sha $(Short $shaBuilt)"
        }
    }
    else {
        Add-Row "mods\$jarName" 'OK' "sha $(Short $shaBuilt)"
    }
}
}

# Two jars with the same mod id stop the game from starting.
#
# Every archive name this mod has ever shipped under has to be swept, not just the current one: after a
# rename the previous jar is still sitting in mods\ under the old name, and `archivesName` alone would
# never match it. The mod id did not change, so the game sees two copies of the same mod and refuses to
# start. Add the new name here if it is ever renamed again.
#
# This sweep only runs when the freshly built jar is actually there. It ran unconditionally once, and a
# build that had failed left the instance with no copy of the mod at all: the old jar was removed and
# nothing replaced it, which is worse than a stale jar in every way and is silent until the game starts.
$archiveNames = @($archive, 'sable-light-overhaul')
$stale = @()
if ((Test-Path -LiteralPath $built) -and -not $ShadersOnly) {
    $stale = @(Get-ChildItem -LiteralPath (Join-Path $Instance 'mods') -File -Filter '*.jar' |
               Where-Object {
                   $name = $_.Name
                   if ($name -eq $jarName) { return $false }
                   foreach ($a in $archiveNames) { if ($name -like "$a-*.jar") { return $true } }
                   return $false
               })
}
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
# Every file under patch\photon\shaders is a patched copy of a file in the pack at the same relative path,
# and the list is read from the repository rather than written down here. That is deliberate: this script
# exists because a change can be built and then never installed, and the same failure one level up is a
# script that knows about one of the eight files the patch writes.
$patchRoot = Join-Path $repo 'patch\photon\shaders'

# 2a. regenerate the artifacts, so a stale one cannot hide
if (-not (Test-Path -LiteralPath $pristine)) {
    Add-Row 'patch artifacts' 'SKIP' "pristine Photon source missing, using committed artifacts"
}
elseif (-not (Test-Path -LiteralPath $fragment)) {
    Add-Row 'patch artifacts' 'FAIL' "missing source fragment $fragment"
    [void]$problems.Add("missing patch source fragment $fragment")
}
else {
    & $apply | Out-Null
    Add-Row 'patch artifacts' 'REGENERATED' 'rebuilt from the fragments (idempotent)'
}

$files = @(Get-ChildItem -LiteralPath $patchRoot -Recurse -File)
if ($files.Count -eq 0) { throw "no patched files under $patchRoot" }
Write-Host "Patched files in the repository: $($files.Count)"
Write-Host ""

# 2b. mirror them into every target pack folder
foreach ($pack in $targets) {
    $packDir = Join-Path $packRoot $pack
    if (-not (Test-Path -LiteralPath $packDir)) {
        Add-Row "shaderpacks\$pack" 'FAIL' 'not a folder'
        [void]$problems.Add("shaderpack folder $pack not found")
        continue
    }
    $current = 0; $written = 0; $absent = 0; $foreign = @()
    foreach ($file in $files) {
        $rel = $file.FullName.Substring($patchRoot.Length).TrimStart('\')
        $dst = Join-Path $packDir ('shaders\' + $rel)
        $shaNew = Sha $file.FullName
        if (-not (Test-Path -LiteralPath $dst)) {
            if ($Check) {
                Add-Row "$pack : $rel" 'MISSING' 'not installed'
                [void]$problems.Add("$pack is missing $rel")
            } else {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
                Copy-FileAtomic   -Source $file.FullName -Destination $dst
                Add-Row "$pack : $rel" 'COPIED' "sha $(Short $shaNew)"
                $written++
            }
            $absent++
        }
        elseif ((Sha $dst) -eq $shaNew) {
            $current++
        }
        else {
            # A file that is neither the pristine Photon source nor already carrying this patch is not
            # ours to overwrite. That is how a *different* pack -- a fork, or an older Photon with its own
            # LoD code -- is kept intact: writing Photon 1.3b's version of one of its programs over its
            # own would silently replace its features with ours, for exactly the files this patch touches.
            # It is reported, not repaired: the answer is either to port the patch to that pack or to
            # leave it alone, and both are decisions a human makes.
            $pristineCopy = Join-Path $pristineRoot $rel
            $isPristine = (Test-Path -LiteralPath $pristineCopy) -and ((Sha $dst) -eq (Sha $pristineCopy))
            # "Ours" means an identifier this patch injects is present. It cannot be a comment string like
            # "Sable Light Overhaul": only some of the injected pieces carry that word, and the largest
            # artifact of all -- the lighting block in diffuse_lighting.glsl -- carries none of it, so a
            # marker chosen that way classified our own previous patch as somebody else's file and
            # silently stopped updating it. Every artifact injects at least one slo identifier.
            $isOurs = (Select-String -LiteralPath $dst -Pattern $oursMarker -Quiet) -eq $true
            if (-not $isPristine -and -not $isOurs) {
                Add-Row "$pack : $rel" 'FOREIGN' 'not this patch''s file - left alone'
                [void]$skips.Add("$pack\$rel is not the Photon 1.3b file this patch was cut from and carries none of $oursMarker - left alone")
                $foreign += $rel
                continue
            }
            if ($Check) {
                Add-Row "$pack : $rel" 'DIFFERS' 'the instance holds an older patch - PATCH NOT INSTALLED'
                [void]$problems.Add("$pack\$rel differs from the patch")
                continue
            }
            # keep one copy of whatever was there before the first deploy of this file
            $backup = "$dst.bak-original"
            if (-not (Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $dst -Destination $backup }
            Copy-FileAtomic   -Source $file.FullName -Destination $dst

            $bytes = [System.IO.File]::ReadAllBytes($dst)
            $bom = ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
            if ($bom) { [void]$problems.Add("$rel has a UTF-8 BOM - Iris preprocessing will fail") }
            # Only what Iris preprocesses has to open with a directive: the world*/ wrappers. A program/ file
            # may open with a comment block (Photon's gbuffers_basic does), and a .properties file is not
            # shader source at all -- this check used to report both, after the file had already been copied.
            $isWrapper = $rel -match '^world[-0-9]*[\\/]'
            if ($isWrapper -and $bytes.Length -gt 0 -and $bytes[0] -ne 0x23) {
                [void]$problems.Add("$rel does not start with #")
            }
            Add-Row "$pack : $rel" 'UPDATED' "sha $(Short (Sha $dst))  bytes=$($bytes.Length)  bom=$bom"
            $written++
        }
    }
    # 2c. the pack's own marker file: which patch this folder carries, file by file.
    #
    # It used to be written by hand, and the hash inside it went stale the first time the patch changed:
    # the pack advertised a sha256 that was not the file sitting next to it. A marker that lies about the
    # thing it exists to prove is worse than no marker, so the deploy writes it from the same list of
    # hashes it just installed, and reports the row like any other file.
    $markerLines = @(
        'Sable Light Overhaul patch'
        "pack    : $pack"
        "mod     : $archive $ver"
        ("files   : {0} of {1} applied" -f ($files.Count - $foreign.Count), $files.Count)
    )
    foreach ($file in $files) {
        $rel = $file.FullName.Substring($patchRoot.Length).TrimStart('\')
        $state = if ($foreign -contains $rel) { 'NOT APPLIED HERE' } else { 'patched' }
        $markerLines += ('  {0}  {1}  {2}' -f (Short (Sha $file.FullName)), $state, $rel)
    }
    $markerLines += @(
        'This pack carries the mod''s shader patch. Select it in Iris and use lighting.mode = Advanced;'
        'an unpatched pack of the same name lights nothing in that tier.'
        'After any patch change, reload the pack in Iris (re-select it, or restart): F3+R does not reload shaders.'
    )
    $markerText = ($markerLines -join "`r`n") + "`r`n"
    $markerPath = Join-Path $packDir 'SLO-PATCH.txt'
    $existingMarker = ''
    if (Test-Path -LiteralPath $markerPath) { $existingMarker = [System.IO.File]::ReadAllText($markerPath) }
    if ($existingMarker -ceq $markerText) {
        Add-Row "$pack : SLO-PATCH.txt" 'OK' "lists $($files.Count) file hash(es)"
    }
    elseif ($Check) {
        Add-Row "$pack : SLO-PATCH.txt" 'DIFFERS' 'the marker does not match the deployed patch'
        [void]$problems.Add("$pack\SLO-PATCH.txt does not match the deployed patch")
    }
    else {
        [System.IO.File]::WriteAllText($markerPath, $markerText, $utf8NoBom)
        Add-Row "$pack : SLO-PATCH.txt" 'UPDATED' "lists $($files.Count) file hash(es)"
    }

    Add-Row "shaderpacks\$pack" $(if ($Check) { 'CHECKED' } else { 'SYNCED' }) `
        "$current current, $written written, $absent missing"
}

# ------------------------- 3. config (report only, never rewritten) -------------------------
foreach ($cfg in 'sablelightoverhaul-client.toml', 'sablelightoverhaul-server.toml') {
    $p = Join-Path $Instance "config\$cfg"
    if (Test-Path -LiteralPath $p) {
        Add-Row "config\$cfg" 'OK' "bytes=$((Get-Item -LiteralPath $p).Length)"
    } else {
        Add-Row "config\$cfg" 'ABSENT' 'the mod generates it on first launch (defaults)'
    }
}

$clientCfg = Join-Path $Instance 'config\sablelightoverhaul-client.toml'
if (Test-Path -LiteralPath $clientCfg) {
    # The tier moved from the root key `lightingMode` to `[lighting] mode` when the config was
    # reorganised, so both spellings are accepted: an instance whose file predates the reorganisation
    # still reports its real tier instead of a note saying the key is missing.
    $tier = $null
    $section = ''
    foreach ($line in [System.IO.File]::ReadAllLines($clientCfg, [System.Text.UTF8Encoding]::new($false))) {
        $s = $line.Trim()
        if ($s -match '^\[(.+)\]$') { $section = $Matches[1]; continue }
        if ($section -eq 'lighting' -and $s -match '^mode\s*=\s*"([^"]+)"') { $tier = $Matches[1]; break }
        if ($s -match '^lightingMode\s*=\s*"([^"]+)"') { $tier = $Matches[1]; break }
    }
    if ($tier) {
        if ($tier -eq 'Advanced') { Add-Row 'lighting.mode' 'OK' "$tier (Advanced is the tier this patch serves)" }
        else { Add-Row 'lighting.mode' 'NOTE' "$tier - the patch does nothing outside Advanced (a perfectly legal combination)" }
    } else {
        Add-Row 'lighting.mode' 'NOTE' 'key not present yet, the mod will add it'
    }
}

# ------------------------- 3b. iris.properties (report only, never rewritten) -------------------------
# The pack folder can be perfect and the game still load a different pack, because Iris remembers its
# choice in config\iris.properties. That is the failure this row exists to catch: the pack name here
# must match the folder name exactly, or the shaders in this instance are not the patched ones.
# Values are compared with the standard Java Properties rules (Iris writes this file with
# Properties.store), so escaped sequences such as \uXXXX are decoded before comparing.
$irisCfg = Join-Path $Instance 'config\iris.properties'
if (-not (Test-Path -LiteralPath $irisCfg)) {
    Add-Row 'config\iris.properties' 'ABSENT' 'Iris will fall back to its own choice on first launch'
}
else {
    $irisPack = $null
    foreach ($line in [System.IO.File]::ReadAllLines($irisCfg, [System.Text.UTF8Encoding]::new($false))) {
        $s = $line.Trim()
        if ($s -match '^shaderPack\s*=\s*(.*)$') {
            $irisPack = $Matches[1].Trim()
            $irisPack = [regex]::Replace($irisPack, '\\u([0-9A-Fa-f]{4})', {
                param($m) [string][char][Convert]::ToInt32($m.Groups[1].Value, 16)
            })
            break
        }
    }
    if ($null -eq $irisPack) {
        Add-Row 'config\iris.properties' 'NOTE' 'no shaderPack line; Iris picks the pack itself'
    }
    elseif ($targets -contains $irisPack) {
        Add-Row 'config\iris.properties' 'OK' "shaderPack=$irisPack (a patched folder)"
    }
    else {
        Add-Row 'config\iris.properties' 'DIFFERS' "shaderPack='$irisPack' but the patched folders are: $($targets -join ', ')"
        [void]$problems.Add("config\iris.properties shaderPack is '$irisPack', expected one of: $($targets -join ', ') - the game would load an unpatched pack")
    }
}

# ------------------------- 4. verdict -------------------------
$report | Format-Table -AutoSize Item, State, Detail | Out-String -Width 220 | Write-Host

if ($skips.Count -gt 0) {
    Write-Host "$($skips.Count) file(s) left alone (not this patch's source):" -ForegroundColor Cyan
    $skips | ForEach-Object { Write-Host "  - $_" -ForegroundColor Cyan }
    Write-Host ""
}

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
