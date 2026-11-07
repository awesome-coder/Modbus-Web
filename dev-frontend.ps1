$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $Root 'frontend')
if (-not (Test-Path 'node_modules')) {
    & npm.cmd install --no-audit --no-fund
}
Write-Host 'Starting Vite dev server on http://127.0.0.1:5173 (proxy /api -> 8080)' -ForegroundColor Cyan
& npm.cmd run dev
