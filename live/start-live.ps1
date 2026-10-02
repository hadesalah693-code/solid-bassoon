# Start the live Kalima stack and print the public URL.
# Requires: backend jar already built (target\kalima-backend-0.0.1-SNAPSHOT.jar)
#           and frontend production build (.next) already present.
$ErrorActionPreference = "SilentlyContinue"

$live       = "C:\Users\Dell\Desktop\kalma\live"
$backendDir = "C:\Users\Dell\Desktop\kalma\kalima-backend-main\kalima-backend-main"
$frontDir   = "C:\Users\Dell\Desktop\kalma\kalima-frontend-main (1)\kalima-frontend-main"
$env:JWT_SECRET = "kalima-prod-session-$env:COMPUTERNAME-2026-fixed-secret-0123456789"

function PortUp($port) { return [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) }

if (-not (PortUp 8080)) {
  Start-Process -FilePath "java" -ArgumentList @("-Xms512m","-Xmx1536m","-XX:+UseParallelGC","-jar","target\kalima-backend-0.0.1-SNAPSHOT.jar","--spring.profiles.active=dev") -WorkingDirectory $backendDir -RedirectStandardOutput "$live\backend.log" -RedirectStandardError "$live\backend.err" -WindowStyle Hidden
  Write-Host "backend: started (8080)" -ForegroundColor Green
  Start-Sleep -Seconds 25
} else { Write-Host "backend: already up (8080)" -ForegroundColor Yellow }

if (-not (PortUp 3000)) {
  Start-Process -FilePath "npm.cmd" -ArgumentList @("start") -WorkingDirectory $frontDir -RedirectStandardOutput "$live\frontend.log" -RedirectStandardError "$live\frontend.err" -WindowStyle Hidden
  Write-Host "frontend: started (3000)" -ForegroundColor Green
  Start-Sleep -Seconds 10
} else { Write-Host "frontend: already up (3000)" -ForegroundColor Yellow }

if (-not (PortUp 8081)) {
  Start-Process -FilePath "$live\caddy.exe" -ArgumentList @("run","--config","$live\Caddyfile","--adapter","caddyfile") -WorkingDirectory $live -RedirectStandardOutput "$live\caddy.log" -RedirectStandardError "$live\caddy.err" -WindowStyle Hidden
  Write-Host "reverse proxy: started (8081)" -ForegroundColor Green
} else { Write-Host "reverse proxy: already up (8081)" -ForegroundColor Yellow }

# always start a fresh tunnel (old quick-tunnel hostnames die) — kill any stuck process
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item "$live\tunnel.log", "$live\tunnel.err" -ErrorAction SilentlyContinue
Start-Process -FilePath "$live\cloudflared.exe" -ArgumentList @("tunnel","--url","http://localhost:8081","--no-autoupdate") -WorkingDirectory $live -RedirectStandardOutput "$live\tunnel.log" -RedirectStandardError "$live\tunnel.err" -WindowStyle Hidden
Write-Host "tunnel: starting..." -ForegroundColor Green

# wait for the public URL and save it
$urlFile = "$live\live-url.txt"
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 1
  $line = Get-Content "$live\tunnel.err" -ErrorAction SilentlyContinue | Where-Object { $_ -match "trycloudflare\.com" } | Select-Object -First 1
  if ($line -match "(https://[a-zA-Z0-9\-]+\.trycloudflare\.com)") {
    $url = $Matches[1]
    Set-Content -Path $urlFile -Value $url -Encoding UTF8
    Write-Host ""
    Write-Host "PUBLIC URL: $url" -ForegroundColor Cyan
    Write-Host "Saved to: $urlFile"
    exit 0
  }
}
Write-Host "Could not grab tunnel URL yet. Check: Get-Content '$live\tunnel.err'" -ForegroundColor Red