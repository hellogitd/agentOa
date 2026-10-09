param([switch]$Install)
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
if(Test-Path 'C:\Program Files\nodejs\npm.cmd') { $env:Path='C:\Program Files\nodejs;' + $env:Path }
Push-Location (Join-Path $root 'agentoa-uniapp')
try {
  if($Install) { & npm.cmd ci --ignore-scripts --no-audit --no-fund; if($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
  & npm.cmd run lint:eslint
  if($LASTEXITCODE -ne 0) { throw 'H5 lint failed' }
  & npm.cmd run typecheck
  if($LASTEXITCODE -ne 0) { throw 'H5 typecheck failed' }
  & npm.cmd run build:h5
  if($LASTEXITCODE -ne 0) { throw 'H5 build failed' }
  & npm.cmd run build:mp-weixin
  if($LASTEXITCODE -ne 0) { throw 'mp-weixin build failed' }
} finally { Pop-Location }
