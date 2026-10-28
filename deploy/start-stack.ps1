#Requires -RunAsAdministrator
# 在管理员 PowerShell 中运行：cd E:\java\program\LoveOfUs; .\deploy\start-stack.ps1
# 完成 .env 配置、构建镜像、启动 compose

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

if (-not (Test-Path '.env')) {
  Copy-Item '.env.example' '.env' -Force
  Write-Host '已生成 .env，请用文本编辑器填入 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / VIP_ADMIN_TOKEN 后重新运行本脚本。' -ForegroundColor Yellow
  exit 0
}

Write-Host '== 构建并启动（-d 后台） ==' -ForegroundColor Cyan
docker compose --env-file .env up -d --build
if ($LASTEXITCODE -ne 0) {
  Write-Host '启动失败，请检查上方日志' -ForegroundColor Red
  exit 1
}

Start-Sleep -Seconds 5
Write-Host '== 服务状态 ==' -ForegroundColor Cyan
docker compose --env-file .env ps
Write-Host ''
Write-Host '== 等待 backend 健康 ==' -ForegroundColor Cyan
for ($i=0; $i -lt 30; $i++) {
  $c = docker compose --env-file .env ps backend --format json 2>$null
  if ($LASTEXITCODE -eq 0 -and $c) {
    try { $s = $c | ConvertFrom-Json; if ($s.Health -eq 'healthy') { Write-Host 'backend healthy'; break } } catch {}
  }
  Start-Sleep -Seconds 2
}

Write-Host ''
Write-Host '访问入口（按 .env HTTP_PORT）:' -ForegroundColor Green
$port = (Select-String -Path '.env' -Pattern '^HTTP_PORT=').ToString().Split('=')[1].Trim()
if (-not $port) { $port = '80' }
Write-Host "  http://localhost:$port/"

Write-Host ''
Write-Host '== 后续常用命令 ==' -ForegroundColor Cyan
Write-Host '  查看日志:   docker compose --env-file .env logs -f'
Write-Host '  停止:       docker compose --env-file .env down'
Write-Host '  清理(含数据卷): docker compose --env-file .env down -v'