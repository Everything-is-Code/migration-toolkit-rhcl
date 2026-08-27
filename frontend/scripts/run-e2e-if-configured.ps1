# Run Playwright E2E when lab credentials are available.
# Used by /verify when frontend/ changed.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (-not $env:THREESCALE_ADMIN_URL -or -not $env:THREESCALE_ACCESS_TOKEN) {
  Write-Host "SKIP: THREESCALE_ADMIN_URL / THREESCALE_ACCESS_TOKEN not set — UI E2E skipped"
  exit 2
}

if (-not $env:E2E_SKIP_WEBSERVER) {
  $env:E2E_SKIP_WEBSERVER = "true"
}

Write-Host "Running Playwright E2E (YAML verification)..."
npm run test:e2e
exit $LASTEXITCODE
