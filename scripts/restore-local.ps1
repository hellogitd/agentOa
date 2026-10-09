param(
    [Parameter(Mandatory=$true)][string]$BackupDir,
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "agentoa",
    [string]$DbUser = "root",
    [string]$TargetSchema = "agentoa_restore_test"
)
$ErrorActionPreference = 'Stop'

Write-Host "=== AgentOA 恢复演练 ===" -ForegroundColor Cyan
Write-Host "备份目录: $BackupDir"
Write-Host "目标库: ${DbHost}:${DbPort}/${TargetSchema}"

# 1. 验证备份文件完整性
$sqlFile = Join-Path $BackupDir "agentoa.sql"
if (-not (Test-Path $sqlFile)) { throw "备份 SQL 文件不存在: $sqlFile" }
$hash = (Get-FileHash $sqlFile -Algorithm SHA256).Hash
Write-Host "SHA256: $hash" -ForegroundColor Yellow

$hashFile = Join-Path $BackupDir "agentoa.sql.sha256"
if (Test-Path $hashFile) {
    $expected = (Get-Content $hashFile).Trim()
    if ($expected -ne $hash) { throw "哈希校验失败！期望 $expected，实际 $hash" }
    Write-Host "哈希校验通过" -ForegroundColor Green
} else {
    Write-Host "警告: 无哈希文件，跳过校验" -ForegroundColor Yellow
}

# 2. 创建目标库
Write-Host "`n--- 创建恢复目标库 ---"
& mysql -h $DbHost -P $DbPort -u $DbUser -p -e "CREATE DATABASE IF NOT EXISTS ``$TargetSchema`` DEFAULT CHARSET utf8mb4;"
if ($LASTEXITCODE -ne 0) { throw "创建数据库失败" }

# 3. 导入 SQL
Write-Host "`n--- 导入 SQL（计时开始）---"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& mysql -h $DbHost -P $DbPort -u $DbUser -p $TargetSchema < $sqlFile
if ($LASTEXITCODE -ne 0) { throw "SQL 导入失败" }
$sw.Stop()
Write-Host "导入耗时: $($sw.Elapsed.TotalSeconds) 秒" -ForegroundColor Green

# 4. 验证关键表
Write-Host "`n--- 验证关键表 ---"
$tables = @("sys_user", "oa_reimburse_request", "oa_invoice", "oa_payment_record", "oa_calendar_event", "oa_document")
foreach ($t in $tables) {
    $count = & mysql -h $DbHost -P $DbPort -u $DbUser -p -N -e "SELECT COUNT(*) FROM ``$TargetSchema``.``$t``;"
    Write-Host "  $t : $count 行"
}

Write-Host "`n=== 恢复演练完成 ===" -ForegroundColor Cyan
Write-Host "RTO 参考: $($sw.Elapsed.TotalSeconds) 秒（仅 SQL 导入，不含应用启动）"
