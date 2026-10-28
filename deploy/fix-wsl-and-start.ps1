#Requires -RunAsAdministrator
# Docker Desktop on Windows 10 Home needs WSL 2 / VirtualMachinePlatform
# Run this once as Administrator, then re-run "Docker Desktop" from Start Menu.

$ErrorActionPreference = 'Stop'
Write-Host '== Step 1: Enable required Windows features ==' -ForegroundColor Cyan
$feats = @(
  'Microsoft-Windows-Subsystem-Linux',
  'VirtualMachinePlatform'
)
foreach ($f in $feats) {
  $state = (Get-WindowsOptionalFeature -Online -FeatureName $f).State
  Write-Host "  $f : $state"
  if ($state -ne 'Enabled') {
    Write-Host "    enabling $f ..." -ForegroundColor Yellow
    Enable-WindowsOptionalFeature -Online -NoRestart -FeatureName $f | Out-Null
  }
}

Write-Host '== Step 2: WSL default version = 2 ==' -ForegroundColor Cyan
wsl --set-default-version 2 | Out-Null

Write-Host '== Step 3: Restart WslService ==' -ForegroundColor Cyan
Restart-Service WslService -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

Write-Host '== Step 4: Probe WSL ==' -ForegroundColor Cyan
wsl -e echo hello
if ($LASTEXITCODE -ne 0) {
  Write-Host 'WSL still failing. WSL kernel update may be needed:' -ForegroundColor Red
  Write-Host '  https://wslstorestorage.blob.core.windows.net/wslblob/wsl_update_x64.msi' -ForegroundColor Yellow
} else {
  Write-Host 'WSL ok.' -ForegroundColor Green
}

Write-Host '== Step 5: Launch Docker Desktop ==' -ForegroundColor Cyan
Start-Process -FilePath 'C:\Users\lenovo\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe'

Write-Host '== Step 6: Wait up to 90s for the engine ==' -ForegroundColor Cyan
$ok = $false
for ($i = 0; $i -lt 18; $i++) {
  Start-Sleep -Seconds 5
  docker info 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { $ok = $true; break }
  Write-Host "  waiting ... $((5*($i+1)))s"
}
if ($ok) {
  Write-Host 'Docker engine ready.' -ForegroundColor Green
} else {
  Write-Host 'Docker engine still not ready; check Docker Desktop tray icon.' -ForegroundColor Red
}