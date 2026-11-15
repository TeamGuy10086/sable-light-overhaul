# Pull printable strings out of specific class entries inside a jar, so we can read the
# command literals / log messages a mod actually uses without a decompiler.
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [Parameter(Mandatory = $true)][string[]]$Entries,
    [int]$MinLength = 4
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
foreach ($pattern in $Entries) {
    $hit = $zip.Entries | Where-Object { $_.FullName -like $pattern }
    if (-not $hit) { Write-Output "--- (no entry matching $pattern)"; continue }
    foreach ($entry in $hit) {
        Write-Output ("--- {0}" -f $entry.FullName)
        $stream = $entry.Open()
        $memory = New-Object System.IO.MemoryStream
        $stream.CopyTo($memory)
        $stream.Dispose()
        $buffer = $memory.ToArray()
        $memory.Dispose()
        # Windows PowerShell 5.1 has no Encoding.Latin1 (that arrived with .NET 5); 28591 is the same thing.
        $text = [System.Text.Encoding]::GetEncoding(28591).GetString($buffer)
        $seen = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($m in [regex]::Matches($text, "[ -~]{$MinLength,}")) {
            $s = $m.Value
            # constant-pool strings are preceded by a length byte and a tag byte; keep plausible ones
            if ($s -match '^[\x20-\x7e]+$' -and $seen.Add($s)) { Write-Output ("    " + $s) }
        }
    }
}
$zip.Dispose()
