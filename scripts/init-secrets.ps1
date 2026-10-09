$ErrorActionPreference='Stop'
$directory=Join-Path (Split-Path $PSScriptRoot -Parent) 'deploy/secrets'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$generator=[Security.Cryptography.RandomNumberGenerator]::Create()
try {
  foreach($name in @('mysql-root-password','db-password','migration-password','backup-password','redis-password','s3-access-key','s3-secret-key','bootstrap-password')) {
    $path=Join-Path $directory $name
    if(Test-Path $path) { continue }
    $bytes=New-Object byte[] 24
    $generator.GetBytes($bytes)
    $value=([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
    if($name -eq 's3-access-key') { $value=$value.Substring(0,20) }
    [IO.File]::WriteAllText($path,$value,[Text.UTF8Encoding]::new($false))
  }
} finally { $generator.Dispose() }
$storageConfig=@{identities=@(@{name='agentoa';credentials=@(@{accessKey=[IO.File]::ReadAllText((Join-Path $directory 's3-access-key'));secretKey=[IO.File]::ReadAllText((Join-Path $directory 's3-secret-key'))});actions=@('Read:agentoa','Write:agentoa','List:agentoa')})}
[IO.File]::WriteAllText((Join-Path $directory 's3-config'),($storageConfig | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
Write-Output 'Secrets ready in deploy/secrets (existing values preserved). Read bootstrap-password locally for the first admin login.'
