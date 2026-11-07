param(
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $Root 'backend\target\modbus-web.jar'

if (-not (Test-Path $Jar)) {
    Write-Host 'JAR not built yet; building now...' -ForegroundColor Yellow
    & (Join-Path $Root 'build.ps1') -SkipTests
}

$env:SERVER_PORT = "$Port"
Set-Location $Root
Write-Host "Starting Modbus Studio on http://127.0.0.1:$Port" -ForegroundColor Cyan
Write-Host 'Press Ctrl+C to stop.'
& java -jar $Jar
