param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host '==> Building React frontend' -ForegroundColor Cyan
Push-Location (Join-Path $Root 'frontend')
try {
    if (-not (Test-Path 'node_modules')) {
        & npm.cmd install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
}
finally {
    Pop-Location
}

Write-Host '==> Packaging Spring Boot application (frontend copied into JAR)' -ForegroundColor Cyan
Push-Location (Join-Path $Root 'backend')
try {
    $arguments = @('clean', 'package')
    if ($SkipTests) { $arguments += '-DskipTests' }
    & .\mvnw.cmd @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
}
finally {
    Pop-Location
}

$Jar = Join-Path $Root 'backend\target\modbus-web.jar'
if (-not (Test-Path $Jar)) { throw "JAR not found: $Jar" }
Write-Host ''
Write-Host "Build complete: $Jar" -ForegroundColor Green
Write-Host "Run with: .\run.cmd" -ForegroundColor Green
