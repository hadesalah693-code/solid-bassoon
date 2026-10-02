$ErrorActionPreference = "SilentlyContinue"
Get-Process cloudflared, caddy, java, node -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host "All live processes stopped." -ForegroundColor Yellow