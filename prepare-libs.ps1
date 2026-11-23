param([Parameter(Mandatory=$true)][string]$CbcJar, [string]$CbcatJar, [Parameter(Mandatory=$true)][string]$RplJar, [Parameter(Mandatory=$true)][string]$SableJar, [Parameter(Mandatory=$true)][string]$CreateJar, [Parameter(Mandatory=$true)][string]$CopycatsJar, [Parameter(Mandatory=$true)][string]$CopyfoxiesJar, [string]$WolfMiscJar, [string]$WorldEditJar)
$ErrorActionPreference = 'Stop'
$expectedCbc = 'EA017AB94637032D039899D88404838D173FA0E6286657CAE6A6C3AF459E87CA'
if ((Get-FileHash -LiteralPath $CbcJar -Algorithm SHA256).Hash -ne $expectedCbc) { throw 'CBC binary differs from the inspected attachment.' }
$destination = Join-Path $PSScriptRoot 'libs'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
# WorldEdit 7.3.8 is compile-only for legacy schematic range migration.
if ($WorldEditJar) { Copy-Item -LiteralPath $WorldEditJar -Destination (Join-Path $destination 'worldedit.jar') }
if ($WolfMiscJar) { Copy-Item -LiteralPath $WolfMiscJar -Destination (Join-Path $destination 'wolfmisc.jar') }
Copy-Item -LiteralPath $CbcJar -Destination (Join-Path $destination 'cbc.jar')
if ($CbcatJar) {
    $expectedCbcat = 'C7A08EEA5EF2D69EB8052B9DA2A5628F10A78CB786896352891D203F36A890E7'
    if ((Get-FileHash -LiteralPath $CbcatJar -Algorithm SHA256).Hash -ne $expectedCbcat) { throw 'CBCAT binary differs from the inspected attachment.' }
    Copy-Item -LiteralPath $CbcatJar -Destination (Join-Path $destination 'cbcat.jar')
}


if ((Get-FileHash -LiteralPath $SableJar -Algorithm SHA256).Hash -ne 'DA6C3B66238586603D1DCAA2AFB012D36815FBCE0A2D5938FBB2936701D42279') { throw 'Expected inspected Sable 2.0.3 binary.' }
Copy-Item -LiteralPath $SableJar -Destination (Join-Path $destination 'sable.jar')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($SableJar)
try {
    $entry = $archive.GetEntry('META-INF/jarjar/sable-companion-common-1.21.1-1.6.0.jar')
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $destination 'sable-companion.jar'), $true)
} finally { $archive.Dispose() }
$archive = [IO.Compression.ZipFile]::OpenRead($CreateJar)
try {
    $entry = $archive.GetEntry('META-INF/jarjar/flywheel-neoforge-1.21.1-1.0.6.jar')
    if (!$entry) { throw 'Expected Create 6.0.10 containing Flywheel 1.0.6.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $destination 'flywheel.jar'), $true)
    $cbcDeps = Join-Path $destination 'cbc-deps'
    New-Item -ItemType Directory -Force -Path $cbcDeps | Out-Null
    foreach ($name in @('Registrate-MC1.21-1.3.0+67.jar', 'ponder-neoforge-1.0.82+mc1.21.1.jar')) {
        $dependency = $archive.GetEntry('META-INF/jarjar/' + $name)
        if (!$dependency) { throw "Missing CBC compile dependency $name in Create." }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($dependency, (Join-Path $cbcDeps $name), $true)
    }
    Copy-Item -LiteralPath $RplJar -Destination (Join-Path $cbcDeps 'rpl.jar')
} finally { $archive.Dispose() }
if ((Get-FileHash (Join-Path $destination 'flywheel.jar') -Algorithm SHA256).Hash -ne '31DDA15C205EB596D3B3449EF03F6AF7363A6CD35B3DA4BFE916B304F9E5337E') { throw 'Unexpected Flywheel binary.' }
  if ((Get-FileHash -LiteralPath $CreateJar -Algorithm SHA256).Hash -ne 'EF87FE5709F1BA1F5B8BB20A2925B5AFB4669E178FD6D8BF10C167759EEFE37A') { throw 'Unexpected Create binary.' }
  # Create's own classes are compiled against now: the contraption renderer is where a cannon's barrel is
  # drawn, so the jar is kept as well as unpacked for Flywheel.
  Copy-Item -LiteralPath $CreateJar -Destination (Join-Path $destination 'create.jar')
Write-Output 'Dependencies prepared for compilation/tests only; none are bundled. Java 21: gradlew.bat build.'


if ((Get-FileHash -LiteralPath $CopycatsJar -Algorithm SHA256).Hash -ne '8480E2A62EAA625F75776831C1D8A9EF3D77D2D816FA2E67566C6EC25BA745D1') { throw 'Unexpected copycats binary.' }
Copy-Item -LiteralPath $CopycatsJar -Destination (Join-Path $destination 'copycats.jar')

if ((Get-FileHash -LiteralPath $CopyfoxiesJar -Algorithm SHA256).Hash -ne '289E06145A7E2455E1CCBC6A85C2A85C43636AC7F5911DEC1275C27E1660BA07') { throw 'Unexpected copyfoxies binary.' }
Copy-Item -LiteralPath $CopyfoxiesJar -Destination (Join-Path $destination 'copyfoxies.jar')

# LDLib2 is the page toolkit the Synaxis-style configuration pages are drawn with. It stays an
# optional runtime dependency, but compiling against it needs three jars: the mod itself plus the
# two layout libraries it ships as jar-in-jar.
if ($LdlibJar) {
    if ((Get-FileHash -LiteralPath $LdlibJar -Algorithm SHA256).Hash -ne '858B9FDAA792AABF9BF7CB622634FDFBA6A3EA80019443ABBD127D5E2BFE85EB') { throw 'Unexpected LDLib2 binary.' }
    Copy-Item -LiteralPath $LdlibJar -Destination (Join-Path $destination 'ldlib2.jar')
    $ldlib = [IO.Compression.ZipFile]::OpenRead($LdlibJar)
    try {
        [IO.Compression.ZipFileExtensions]::ExtractToFile($ldlib.GetEntry('META-INF/jarjar/taffy-1.1.4.jar'), (Join-Path $destination 'taffy.jar'), $true)
        [IO.Compression.ZipFileExtensions]::ExtractToFile($ldlib.GetEntry('META-INF/jarjar/yoga-1.0.0.jar'), (Join-Path $destination 'yoga.jar'), $true)
    } finally { $ldlib.Dispose() }
    if ((Get-FileHash (Join-Path $destination 'taffy.jar') -Algorithm SHA256).Hash -ne '3AE3B857CBB4392CCB3245778C9B7192522238CF66BD5AC413EAD4613FA80425') { throw 'Unexpected taffy binary.' }
    if ((Get-FileHash (Join-Path $destination 'yoga.jar') -Algorithm SHA256).Hash -ne 'BACD086489858CA9FCD438721C75373CE8DEAB5E9FEAD23425A42A16FE1DC9A7') { throw 'Unexpected yoga binary.' }
}