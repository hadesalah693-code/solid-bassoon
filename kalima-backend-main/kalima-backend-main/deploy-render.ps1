# Kalima backend — Render deployment helper
#
# Fastest path (recommended):
#   1. https://dashboard.render.com → New → Blueprint
#   2. Connect repo: hadesalah693-code/kalima-backend
#   3. Render creates kalima-api + kalima-db automatically from render.yaml
#   4. Skip GEMINI/GROQ if prompted (optional)
#   5. Wait for deploy → open https://kalima-api.onrender.com/actuator/health
#
# If you already have a web service (no Blueprint):
#   Settings → Dockerfile Path = Dockerfile.render → Manual Deploy

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== Kalima Render deploy ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Blueprint creates:" -ForegroundColor Green
Write-Host "  - Web service:  kalima-api  (Dockerfile.render)"
Write-Host "  - Database:     kalima-db   (Postgres free)"
Write-Host "  - Auto env:     DATABASE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET"
Write-Host ""
Write-Host "Open Blueprint setup:" -ForegroundColor Yellow
Write-Host "  https://dashboard.render.com/blueprints"
Write-Host ""
Write-Host "Health check after deploy:" -ForegroundColor Yellow
Write-Host "  https://kalima-api.onrender.com/actuator/health"
Write-Host ""

$branch = git rev-parse --abbrev-ref HEAD 2>$null
if ($LASTEXITCODE -eq 0) {
    $ahead = git rev-list --count "origin/$branch..HEAD" 2>$null
    if ($ahead -gt 0) {
        Write-Host "Local commits not pushed yet ($ahead). Push first:" -ForegroundColor Red
        Write-Host "  git push origin $branch"
    } else {
        Write-Host "Git is up to date with origin/$branch" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "Optional: trigger deploy from CLI (needs RENDER_API_KEY + service id):" -ForegroundColor DarkGray
Write-Host "  render deploys create <SERVICE_ID> --confirm"
