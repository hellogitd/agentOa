# Quiesced local snapshot: application and storage are briefly stopped, then restarted.
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$destination=Join-Path $root "deploy/backups/$stamp"
New-Item -ItemType Directory -Path $destination -Force -ErrorAction Stop | Out-Null
Push-Location $root
try {
  $mysqlContainer=(& docker compose -f deploy/compose.yml ps -q mysql).Trim()
  $storageContainer=(& docker compose -f deploy/compose.yml ps -q storage).Trim()
  if(-not $mysqlContainer -or -not $storageContainer) { throw 'MySQL and storage must be running' }
  & docker compose -f deploy/compose.yml stop backend storage
  if($LASTEXITCODE -ne 0) { throw 'Could not stop writers' }
  try {
    & docker compose -f deploy/compose.yml exec -T mysql sh -ec 'MYSQL_PWD=$(cat /run/secrets/backup-password) mysqldump -uagentoa_backup --single-transaction --no-tablespaces --set-gtid-purged=OFF --hex-blob agentoa > /tmp/agentoa-backup.sql'
    if($LASTEXITCODE -ne 0) { throw 'Database backup failed' }
    & docker cp "${mysqlContainer}:/tmp/agentoa-backup.sql" (Join-Path $destination 'database.sql')
    if($LASTEXITCODE -ne 0) { throw 'Database copy failed' }
    & docker run --rm --volumes-from "${storageContainer}:ro" --mount "type=bind,source=$destination,target=/backup" --entrypoint sh public.ecr.aws/docker/library/nginx:1.28.0-alpine -ec 'tar czf /backup/storage.tar.gz -C /data .'
    if($LASTEXITCODE -ne 0) { throw 'Storage backup failed' }
    Get-FileHash (Join-Path $destination 'database.sql'),(Join-Path $destination 'storage.tar.gz') -Algorithm SHA256 |
      Select-Object @{n='File';e={Split-Path $_.Path -Leaf}},Hash | ConvertTo-Json | Set-Content (Join-Path $destination 'checksums.json') -Encoding UTF8
    Write-Output "Backup complete: $destination. Store secrets separately and copy backup off-host."
  } finally {
    & docker compose -f deploy/compose.yml start storage backend
  }
} finally { Pop-Location }
