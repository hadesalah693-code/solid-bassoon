# User trial smoke test — run while backend is up on :8080 (dev profile).
$Base = "http://localhost:8080/api"
$ErrorActionPreference = "Stop"

function Login([string]$Email, [string]$Password) {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -ContentType "application/json" -Body $body
    return $res.accessToken
}

function Step([string]$Label, [scriptblock]$Action) {
    Write-Host "`n== $Label ==" -ForegroundColor Cyan
    & $Action
    Write-Host "OK" -ForegroundColor Green
}

Step "Admin login" {
    $script:AdminToken = Login "admin@kalima.local" "admin123"
}

Step "List cases as admin" {
    $cases = Invoke-RestMethod -Uri "$Base/cases?page=0&size=20&search=DEMO-CLIENT" `
        -Headers @{ Authorization = "Bearer $AdminToken" }
    if ($cases.content.Count -lt 1) { throw "No cases found" }
    $demo = $cases.content | Select-Object -First 1
    $script:CaseId = $demo.id
    Write-Host "Demo case: $($demo.title) ($($demo.caseNumber))"
}

Step "List messages on demo case" {
    $page = Invoke-RestMethod -Uri "$Base/cases/$CaseId/messages" -Headers @{ Authorization = "Bearer $AdminToken" }
    Write-Host "Messages: $($page.items.Count)"
    if ($page.items.Count -lt 1) { throw "Expected seeded messages" }
}

Step "Lawyer login + chatbot" {
    $lawyerToken = Login "lawyer@kalima.local" "admin123"
    $ask = @{ question = "status" } | ConvertTo-Json
    $bot = Invoke-RestMethod -Uri "$Base/cases/$CaseId/chatbot/ask" -Method Post `
        -Headers @{ Authorization = "Bearer $lawyerToken" } -ContentType "application/json" -Body $ask
    Write-Host "Chatbot: $($bot.answer.Substring(0, [Math]::Min(80, $bot.answer.Length)))..."
}

Step "Client login + own cases" {
    $clientBody = @{ email = "client@kalima.local"; password = "client123" } | ConvertTo-Json
    $clientLogin = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -ContentType "application/json" -Body $clientBody
    $clientToken = $clientLogin.accessToken
    $clientId = $clientLogin.person.id
    $cases = Invoke-RestMethod -Uri "$Base/cases?clientId=$clientId&page=0&size=10" `
        -Headers @{ Authorization = "Bearer $clientToken" }
    Write-Host "Client cases: $($cases.content.Count)"
    if ($cases.content.Count -lt 1) { throw "Client should see demo case" }
}

Step "Actuator health" {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    Write-Host "Status: $($health.status)"
}

Write-Host "`nUser trial smoke test passed." -ForegroundColor Green
