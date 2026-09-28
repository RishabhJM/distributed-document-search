# Preflight check script for Windows / PowerShell
Write-Host "=== DeepRunner Document Search Service — Preflight Checks ===" -ForegroundColor Cyan

# 1. Docker check
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "✓ Docker CLI is installed." -ForegroundColor Green
} else {
    Write-Host "❌ Error: Docker CLI not found. Please install Docker Desktop." -ForegroundColor Red
    exit 1
}

# 2. Check ports
$ports = @(5432, 9200, 6379, 8080, 3000)
foreach ($p in $ports) {
    $conn = Test-NetConnection -ComputerName localhost -Port $p -WarningAction SilentlyContinue
    if ($conn.TcpTestSucceeded) {
        Write-Host "⚠️ Warning: Port $p is already in use." -ForegroundColor Yellow
    } else {
        Write-Host "✓ Port $p is available." -ForegroundColor Green
    }
}

Write-Host "=== Preflight checks complete ===" -ForegroundColor Cyan
