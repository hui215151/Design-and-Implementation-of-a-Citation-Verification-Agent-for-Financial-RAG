#Requires -RunAsAdministrator
# MySQL 8.0 root 密码重置脚本
# 新密码: finguard123

$ErrorActionPreference = "Stop"
$mysqld = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
$mysql  = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

Write-Host "=== 1. 停止 MySQL80 服务 ===" -ForegroundColor Cyan
Stop-Service MySQL80 -Force
Start-Sleep -Seconds 3

Write-Host "=== 2. 以 skip-grant-tables 模式后台启动 mysqld ===" -ForegroundColor Cyan
$proc = Start-Process -FilePath $mysqld `
    -ArgumentList "--skip-grant-tables","--skip-networking=OFF","--port=3306" `
    -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 5

Write-Host "=== 3. 无密码登录并重置 root 密码 ===" -ForegroundColor Cyan
& $mysql -u root -h 127.0.0.1 -P 3306 -e "FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED BY 'finguard123';"
if ($LASTEXITCODE -ne 0) {
    Write-Host "改密码失败，尝试 mysql_native_password..." -ForegroundColor Yellow
    & $mysql -u root -h 127.0.0.1 -P 3306 -e "FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'finguard123';"
}

Write-Host "=== 4. 关闭临时 mysqld ===" -ForegroundColor Cyan
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host "=== 5. 正常启动 MySQL80 服务 ===" -ForegroundColor Cyan
Start-Service MySQL80
Start-Sleep -Seconds 3

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " MySQL root 密码已重置为: finguard123" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
pause
