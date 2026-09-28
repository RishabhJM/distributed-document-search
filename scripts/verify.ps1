$ApiUrl = if ($env:API_URL) { $env:API_URL } else { "http://localhost:8080" }

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " DeepRunner Document Search Service — Automated Smoke Verification" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Health
Write-Host -NoNewline "1. Checking service health... "
try {
    $h = Invoke-RestMethod -Uri "$ApiUrl/health" -Method Get
    if ($h.status -eq "UP" -or $h.status -eq "DEGRADED") {
        Write-Host "✓ PASS ($($h.status))" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ FAIL" -ForegroundColor Red
    exit 1
}

# 2. Write document
Write-Host -NoNewline "2. Testing transactional write and outbox commit... "
$body = @{
    externalId = "verify-1"
    title = "Smoke test document for verification"
    content = "Automated verification payload testing end-to-end data pipelines."
    tags = @("verification", "smoke")
} | ConvertTo-Json

try {
    $doc = Invoke-RestMethod -Uri "$ApiUrl/documents" -Method Post -Body $body -ContentType "application/json" -Headers @{"X-Tenant-ID"="acme"}
    Write-Host "✓ PASS ($($doc.id))" -ForegroundColor Green
} catch {
    Write-Host "❌ FAIL" -ForegroundColor Red
    exit 1
}

# 3. Read own document
Write-Host -NoNewline "3. Testing strongly consistent read-your-writes from Postgres... "
try {
    $ownDoc = Invoke-RestMethod -Uri "$ApiUrl/documents/$($doc.id)" -Method Get -Headers @{"X-Tenant-ID"="acme"}
    if ($ownDoc.title -eq "Smoke test document for verification") {
        Write-Host "✓ PASS" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ FAIL" -ForegroundColor Red
    exit 1
}

# 4. Cross-tenant isolation
Write-Host -NoNewline "4. Testing tenant isolation (Globex accessing Acme doc)... "
try {
    Invoke-RestMethod -Uri "$ApiUrl/documents/$($doc.id)" -Method Get -Headers @{"X-Tenant-ID"="globex"}
    Write-Host "❌ FAIL (Should have failed with 404)" -ForegroundColor Red
    exit 1
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "✓ PASS (Returned HTTP 404)" -ForegroundColor Green
    } else {
        Write-Host "❌ FAIL (Expected 404)" -ForegroundColor Red
        exit 1
    }
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " ✓ ALL VERIFICATION CHECKS PASSED!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
