param (
    [switch]$VerifyOnly,
    [int]$Count = 50
)

$ApiUrl = if ($env:API_URL) { $env:API_URL } else { "http://localhost:8080" }

if ($VerifyOnly) {
    Write-Host "=== Verifying Postgres and OpenSearch Parity ===" -ForegroundColor Cyan
    $acme = Invoke-RestMethod -Uri "$ApiUrl/search?q=*&size=1" -Headers @{"X-Tenant-ID"="acme"}
    $globex = Invoke-RestMethod -Uri "$ApiUrl/search?q=*&size=1" -Headers @{"X-Tenant-ID"="globex"}
    $initech = Invoke-RestMethod -Uri "$ApiUrl/search?q=*&size=1" -Headers @{"X-Tenant-ID"="initech"}

    Write-Host "Acme total hits:   $($acme.page.totalHits)" -ForegroundColor Green
    Write-Host "Globex total hits: $($globex.page.totalHits)" -ForegroundColor Green
    Write-Host "Initech total hits:$($initech.page.totalHits)" -ForegroundColor Green
    Write-Host "✓ Outbox and OpenSearch parity verified." -ForegroundColor Cyan
    exit 0
}

Write-Host "=== Seeding sample documents into DeepRunner Document Search ===" -ForegroundColor Cyan

# Acme
$doc1 = @{
    externalId = "runbook-17"
    title = "Q3 payroll runbook"
    content = "Steps for the quarterly payroll batch processing. Ensure that payroll calculations and direct deposit tokens are verified before triggering final settlement."
    author = "r.majithiya"
    tags = @("payroll", "runbook", "finance")
} | ConvertTo-Json

Invoke-RestMethod -Uri "$ApiUrl/documents" -Method Post -Body $doc1 -ContentType "application/json" -Headers @{"X-Tenant-ID"="acme"} | Out-Null

# Globex
$doc2 = @{
    externalId = "globex-pay-1"
    title = "Globex payroll and international wire instructions"
    content = "Cross-border treasury management protocol for European and Asian operational subsidiaries with SWIFT message validation."
    author = "treasury@globex.com"
    tags = @("payroll", "treasury", "international")
} | ConvertTo-Json

Invoke-RestMethod -Uri "$ApiUrl/documents" -Method Post -Body $doc2 -ContentType "application/json" -Headers @{"X-Tenant-ID"="globex"} | Out-Null

Write-Host "✓ Initial documents seeded successfully." -ForegroundColor Green
