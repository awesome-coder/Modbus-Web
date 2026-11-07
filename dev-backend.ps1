$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $Root 'backend')
Write-Host 'Starting Spring Boot backend on http://127.0.0.1:8080' -ForegroundColor Cyan
& .\mvnw.cmd spring-boot:run
