param([switch]$Download)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $root '.cache/upstream'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
$headers = @{ 'User-Agent' = 'AgentOA-foundation'; Accept = 'application/vnd.github+json' }
foreach ($repo in @('dromara/RuoYi-Vue-Plus', 'JavaLionLi/plus-ui')) {
    $name = ($repo -split '/')[1]
    $response = Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri "https://api.github.com/repos/$repo/tags?per_page=100" -TimeoutSec 60
    [IO.File]::WriteAllText((Join-Path $cache "$name-tags.json"), $response.Content)
    $tags = $response.Content | ConvertFrom-Json
    $tags | Select-Object -First 15 name, @{n='commit';e={$_.commit.sha}} | Format-Table -AutoSize
}
$commit = '8136a0191a2258c0e1b36a8146a1c5ebc070c139'
foreach ($path in @('README.md', 'pom.xml')) {
    $response = Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri "https://api.github.com/repos/dromara/RuoYi-Vue-Plus/contents/${path}?ref=$commit" -TimeoutSec 60
    $entry = $response.Content | ConvertFrom-Json
    $content = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($entry.content))
    [IO.File]::WriteAllText((Join-Path $cache "backend-$path"), $content)
}
if ($Download) {
    $archive = Join-Path $cache 'backend-5.6.2.zip'
    if (-not (Test-Path $archive)) {
        Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri "https://api.github.com/repos/dromara/RuoYi-Vue-Plus/zipball/$commit" -OutFile $archive -TimeoutSec 180
    }
    Get-FileHash $archive -Algorithm SHA256 | Select-Object Hash,Path
    $frontendArchive = Join-Path $cache 'frontend-2.6.2.zip'
    if (-not (Test-Path $frontendArchive)) {
        Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri 'https://api.github.com/repos/JavaLionLi/plus-ui/zipball/d0d451967676707021b9857df529c395b27e90a7' -OutFile $frontendArchive -TimeoutSec 180
    }
    Get-FileHash $frontendArchive -Algorithm SHA256 | Select-Object Hash,Path
}
