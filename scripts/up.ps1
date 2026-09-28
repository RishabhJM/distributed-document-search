param (
    [string]$Mode = "full",
    [switch]$Seed
)

Write-Host "Starting DeepRunner Document Search in mode: $Mode..." -ForegroundColor Cyan

if ($Mode -eq "full") {
    docker compose --profile full up -d --build
} else {
    docker compose up -d postgres opensearch redis
}

if ($Mode -eq "full") {
    Write-Host "Waiting for service readiness..." -ForegroundColor Cyan
    $retries = 0
    $maxRetries = 30
    while ($retries -lt $maxRetries) {
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8080/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
            if ($resp.status -eq "UP" -or $resp.status -eq "DEGRADED") {
                Write-Host "✓ DeepRunner Document Search is ready!" -ForegroundColor Green
                Write-Host "  Web UI: http://localhost:3000" -ForegroundColor Yellow
                Write-Host "  API:    http://localhost:8080" -ForegroundColor Yellow
                break
            }
        } catch {
            Start-Sleep -Seconds 3
            $retries++
        }
    }
}

if ($Seed) {
    & "$PSScriptRoot\seed.ps1"
}
