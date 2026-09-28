param([Parameter(Mandatory=$true)][string]$CbcJar, [string]$CbcatJar, [Parameter(Mandatory=$true)][string]$SableJar, [Parameter(Mandatory=$true)][string]$LambJar, [Parameter(Mandatory=$true)][string]$CreateJar, [Parameter(Mandatory=$true)][string]$YuushyaJar, [Parameter(Mandatory=$true)][string]$CopycatsJar, [Parameter(Mandatory=$true)][string]$CopyfoxiesJar)
$ErrorActionPreference = 'Stop'
$expectedCbc = 'EA017AB94637032D039899D88404838D173FA0E6286657CAE6A6C3AF459E87CA'
if ((Get-FileHash -LiteralPath $CbcJar -Algorithm SHA256).Hash -ne $expectedCbc) { throw 'CBC binary differs from the inspected attachment.' }
$destination = Join-Path $PSScriptRoot 'libs'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
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
if ((Get-FileHash -LiteralPath $LambJar -Algorithm SHA256).Hash -ne '913B0358B1031828F1E060EA0EE361232DC2A1C82464F488A0A920739CE7C763') { throw 'Expected inspected LambDynamicLights 4.8.11+1.21.1.' }
function Expand-NestedJars([string]$JarPath, [string]$OutputDirectory) {
    $zip = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        foreach ($item in $zip.Entries) {
            if (!$item.FullName.EndsWith('.jar')) { continue }
            $target = Join-Path $OutputDirectory ([IO.Path]::GetFileName($item.FullName))
            [IO.Compression.ZipFileExtensions]::ExtractToFile($item, $target, $true)
            Expand-NestedJars $target $OutputDirectory
        }
    } finally { $zip.Dispose() }
}
$nested = Join-Path $destination 'lamb-deps'
New-Item -ItemType Directory -Force -Path $nested | Out-Null
Expand-NestedJars $LambJar $nested
Copy-Item -LiteralPath (Join-Path $nested 'lambdynamiclights-4.8.11+1.21.1-neoforge.jar') -Destination (Join-Path $destination 'lamb-runtime.jar')
Copy-Item -LiteralPath (Join-Path $nested 'lambdynamiclights-api-4.8.11+1.21.1-mojmap.jar') -Destination (Join-Path $destination 'lamb-api.jar')
$archive = [IO.Compression.ZipFile]::OpenRead($CreateJar)
try {
    $entry = $archive.GetEntry('META-INF/jarjar/flywheel-neoforge-1.21.1-1.0.6.jar')
    if (!$entry) { throw 'Expected Create 6.0.10 containing Flywheel 1.0.6.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $destination 'flywheel.jar'), $true)
} finally { $archive.Dispose() }
if ((Get-FileHash (Join-Path $destination 'flywheel.jar') -Algorithm SHA256).Hash -ne '31DDA15C205EB596D3B3449EF03F6AF7363A6CD35B3DA4BFE916B304F9E5337E') { throw 'Unexpected Flywheel binary.' }
Write-Output 'Dependencies prepared for compilation/tests only; none are bundled. Java 21: gradlew.bat build.'

if ((Get-FileHash -LiteralPath $YuushyaJar -Algorithm SHA256).Hash -ne 'EEDE4BD24A1B20AEB007C03761903AED8061B9B826DC68349A8CA146C9944AE5') { throw 'Unexpected yuushya binary.' }
Copy-Item -LiteralPath $YuushyaJar -Destination (Join-Path $destination 'yuushya.jar')

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