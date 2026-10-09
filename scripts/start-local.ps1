param([switch]$Build)
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
  & "$PSScriptRoot/init-secrets.ps1"
  if($Build) {
    & "$PSScriptRoot/build-backend.ps1"
    if(-not $?) { throw 'Backend build failed' }
    & "$PSScriptRoot/build-frontend.ps1" -Install
    if(-not $?) { throw 'Frontend build failed' }
    & "$PSScriptRoot/build-h5.ps1" -Install
    if(-not $?) { throw 'H5 build failed' }
  }
  & docker compose -f deploy/compose.yml config --quiet
  if($LASTEXITCODE -ne 0) { throw 'Invalid Compose configuration' }
  & docker compose -f deploy/compose.yml up --build -d --wait --wait-timeout 240
  if($LASTEXITCODE -ne 0) { throw 'Stack did not become healthy; inspect docker compose logs' }
  Write-Output 'AgentOA: http://localhost:18080 (admin; read deploy/secrets/bootstrap-password on first use)'
  Write-Output 'AgentOA H5: http://localhost:18082'
} finally { Pop-Location }
