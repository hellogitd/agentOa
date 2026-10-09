param([switch]$Install)
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
if(Test-Path 'C:\Program Files\nodejs\npm.cmd') { $env:Path='C:\Program Files\nodejs;' + $env:Path }
Push-Location (Join-Path $root 'agentoa-frontend')
try {
  if($Install) { & npm.cmd ci --ignore-scripts --no-audit --no-fund; if($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
  & npm.cmd run lint:eslint
  if($LASTEXITCODE -ne 0) { throw 'Frontend lint failed' }
  & npm.cmd run build:prod
  if($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
  # Vite generates the upstream auto-import declarations before type checking.
  & npm.cmd run typecheck
  if($LASTEXITCODE -ne 0) { throw 'Type checking failed' }
  & npm.cmd run test
  if($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
} finally { Pop-Location }
