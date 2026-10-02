# One-shot Render env setup (paste in Dashboard → kalima-api → Environment if Blueprint sync did not apply).
# After saving, use Manual Deploy.

@(
  @{ Key = "SPRING_PROFILES_ACTIVE"; Value = "prod" }
  @{ Key = "CORS_ALLOWED_ORIGINS"; Value = "https://kalima-frontend.vercel.app,https://kalima-frontend-hadesalah693-codes-projects.vercel.app,https://kalima-frontend-git-main-hadesalah693-codes-projects.vercel.app" }
  @{ Key = "KALIMA_AUTH_REFRESH_COOKIE_SAME_SITE"; Value = "None" }
  @{ Key = "JAVA_OPTS"; Value = "-XX:+UseContainerSupport -Xms64m -Xmx180m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m" }
  @{ Key = "INIT_ADMIN_ENABLED"; Value = "true" }
  @{ Key = "INIT_ADMIN_EMAIL"; Value = "admin@kalima.com" }
  @{ Key = "INIT_ADMIN_PASSWORD"; Value = "KalimaAdmin2026!" }
  @{ Key = "INIT_ADMIN_RESET_PASSWORD"; Value = "true" }
  @{ Key = "INIT_ADMIN_NAME"; Value = "مدير النظام" }
) | Format-Table -AutoSize

Write-Host ""
Write-Host "Also ensure DATABASE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET exist (from Blueprint/DB)." -ForegroundColor Yellow
Write-Host "Then: kalima-api -> Manual Deploy -> Clear build cache -> Deploy" -ForegroundColor Cyan
Write-Host "Vercel NEXT_PUBLIC_API_BASE_URL should match Render service URL + /api"
Write-Host "  Example: https://kalima-api-nyo1.onrender.com/api"
Write-Host "Login:  admin@kalima.com / KalimaAdmin2026!" -ForegroundColor Green
Write-Host "After first successful login, set INIT_ADMIN_RESET_PASSWORD=false and redeploy." -ForegroundColor Yellow
