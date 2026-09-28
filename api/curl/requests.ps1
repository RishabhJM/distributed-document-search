param (
    [string]$Tenant = "acme"
)

$ApiUrl = if ($env:API_URL) { $env:API_URL } else { "http://localhost:8080" }

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " DeepRunner Document Search — Interactive curl Demonstration" -ForegroundColor Cyan
Write-Host " Active Tenant: $Tenant" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

Write-Host "`n1. Health Check:" -ForegroundColor Green
Invoke-RestMethod -Uri "$ApiUrl/health" -Method Get | ConvertTo-Json

Write-Host "`n2. Index new document:" -ForegroundColor Green
$body = @{
    externalId = "demo-doc-1"
    title = "Distributed systems consensus protocols"
    content = "A technical survey of Raft, Paxos, and Zab replication mechanisms in highly available distributed databases."
    author = "staff-architect"
    tags = @("distributed-systems", "consensus", "architecture")
} | ConvertTo-Json

$resp = Invoke-RestMethod -Uri "$ApiUrl/documents" -Method Post -Body $body -ContentType "application/json" -Headers @{"X-Tenant-ID"=$Tenant}
$resp | ConvertTo-Json
$docId = $resp.id

if ($docId) {
    Write-Host "`n3. Read own document as '$Tenant':" -ForegroundColor Green
    Invoke-RestMethod -Uri "$ApiUrl/documents/$docId" -Method Get -Headers @{"X-Tenant-ID"=$Tenant} | ConvertTo-Json

    Write-Host "`n4. Cross-tenant read as 'globex' (MUST RETURN 404):" -ForegroundColor Green
    try {
        Invoke-RestMethod -Uri "$ApiUrl/documents/$docId" -Method Get -Headers @{"X-Tenant-ID"="globex"}
    } catch {
        Write-Host "Caught expected status: $($_.Exception.Response.StatusCode)" -ForegroundColor Green
    }
}

Write-Host "`n5. Search documents:" -ForegroundColor Green
Invoke-RestMethod -Uri "$ApiUrl/search?q=consensus+protocols" -Method Get -Headers @{"X-Tenant-ID"=$Tenant} | ConvertTo-Json -Depth 4
