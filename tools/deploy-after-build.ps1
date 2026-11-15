param(
    [string]$Instance = $env:SLO_INSTANCE
)
$ErrorActionPreference = 'Stop'

if (-not $Instance) {
    $localConfig = Join-Path $PSScriptRoot '..\local-deploy.json'
    if (Test-Path -LiteralPath $localConfig) {
        $Instance = (Get-Content -LiteralPath $localConfig -Raw | ConvertFrom-Json).instance
    }
}
if (-not $Instance) {
    Write-Host 'Auto-deploy skipped. Set SLO_INSTANCE or local-deploy.json to opt in.'
    exit 0
}

if (-not (Test-Path -LiteralPath (Join-Path $Instance 'mods'))) {
    Write-Host 'Auto-deploy skipped. The configured local instance is unavailable.'
    exit 0
}

$clients = @(Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" |
    Where-Object {
        $_.CommandLine -and (
            $_.CommandLine.Contains('net.minecraft.client.main.Main') -or
            $_.CommandLine.Contains('forgeclient') -or
            $_.CommandLine.Contains('--gameDir') -or
            $_.CommandLine.ToLowerInvariant().Contains($Instance.ToLowerInvariant()))
    })
if ($clients.Count -gt 0) {
    Write-Host 'Auto-deploy skipped. Minecraft is running. The build is ready for later deployment.'
    exit 0
}

& (Join-Path $PSScriptRoot '..\patch\deploy-to-instance.ps1') -Instance $Instance -NoBuild
