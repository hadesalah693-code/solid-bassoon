# Deploy Kalima backend to Google Cloud Run (1 GiB RAM — fits Spring Boot free tier).
#
# Prerequisites:
#   1. Install Google Cloud SDK: https://cloud.google.com/sdk/docs/install
#   2. gcloud auth login
#   3. gcloud config set project YOUR_PROJECT_ID
#   4. Enable APIs (once per project):
#        gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
#
# Usage (from kalima-backend folder):
#   .\deploy-cloudrun.ps1 -ProjectId "your-gcp-project-id"
#
# Or set env vars first, then run without -ProjectId if gcloud project is already configured.

param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectId,

    [string]$ServiceName = "kalima-api",
    [string]$Region = "us-central1",
    [string]$FrontendOrigin = "https://kalima-frontend.vercel.app",

    [string]$DbUrl = "jdbc:postgresql://ep-restless-hill-aq7u14zo.c-8.us-east-1.aws.neon.tech/neondb?sslmode=require",
    [string]$DbUsername = "neondb_owner",
    [string]$DbPassword = "",
    [string]$JwtSecret = ""
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    Write-Error "gcloud not found. Install: https://cloud.google.com/sdk/docs/install"
}

if ($ProjectId) {
    gcloud config set project $ProjectId
}

if (-not $DbPassword) {
    $DbPassword = Read-Host "Neon DB_PASSWORD"
}
if (-not $JwtSecret) {
    $JwtSecret = Read-Host "JWT_SECRET (min 32 chars)"
}

$javaOpts = "-XX:+UseContainerSupport -Xms256m -Xmx768m -XX:+UseG1GC"
$envVars = @(
    "SPRING_PROFILES_ACTIVE=prod",
    "CORS_ALLOWED_ORIGINS=$FrontendOrigin",
    "KALIMA_AUTH_REFRESH_COOKIE_SAME_SITE=None",
    "DB_URL=$DbUrl",
    "DB_USERNAME=$DbUsername",
    "DB_PASSWORD=$DbPassword",
    "JWT_SECRET=$JwtSecret",
    "JAVA_OPTS=$javaOpts"
) -join ","

Write-Host "Deploying $ServiceName to Cloud Run ($Region, 1Gi)..." -ForegroundColor Cyan

gcloud run deploy $ServiceName `
    --source . `
    --region $Region `
    --platform managed `
    --allow-unauthenticated `
    --memory 1Gi `
    --cpu 1 `
    --timeout 300 `
    --min-instances 0 `
    --max-instances 5 `
    --port 8080 `
    --set-env-vars $envVars

$url = gcloud run services describe $ServiceName --region $Region --format "value(status.url)"
Write-Host ""
Write-Host "Deployed: $url" -ForegroundColor Green
Write-Host "Health:   $url/actuator/health"
Write-Host ""
Write-Host "Vercel env:" -ForegroundColor Yellow
Write-Host "NEXT_PUBLIC_API_BASE_URL=$url/api"
