param([switch]$SkipTests)
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Users\aglobabear\.jdks\ms-21.0.11')) { $env:JAVA_HOME = 'C:\Users\aglobabear\.jdks\ms-21.0.11' }
$settings = Join-Path $PSScriptRoot 'maven-settings.xml'
$repository = Join-Path $root '.cache/maven'
Push-Location (Join-Path $root 'agentoa-backend')
try {
    & mvn -B -s $settings -gs $settings "-Dmaven.repo.local=$repository" -pl ruoyi-admin -am clean verify "-DskipTests=$($SkipTests.IsPresent.ToString().ToLower())"
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed' }
} finally { Pop-Location }
