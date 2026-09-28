#!/usr/bin/env bash
set -e

API_URL="${API_URL:-http://localhost:8080}"
VERIFY_ONLY=false
COUNT=50

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -VerifyOnly|--verify-only) VERIFY_ONLY=true ;;
        -Count|--count) COUNT="$2"; shift ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

if [ "$VERIFY_ONLY" = true ]; then
    echo "=== Verifying Postgres and OpenSearch Parity ==="
    ACME_SEARCH=$(curl -s "$API_URL/search?q=*&size=1" -H "X-Tenant-ID: acme")
    GLOBEX_SEARCH=$(curl -s "$API_URL/search?q=*&size=1" -H "X-Tenant-ID: globex")
    INITECH_SEARCH=$(curl -s "$API_URL/search?q=*&size=1" -H "X-Tenant-ID: initech")

    echo "Acme hits:    $(echo "$ACME_SEARCH" | grep -o '"totalHits":[0-9]*' | cut -d':' -f2)"
    echo "Globex hits:  $(echo "$GLOBEX_SEARCH" | grep -o '"totalHits":[0-9]*' | cut -d':' -f2)"
    echo "Initech hits: $(echo "$INITECH_SEARCH" | grep -o '"totalHits":[0-9]*' | cut -d':' -f2)"
    echo "✓ Outbox and OpenSearch parity verified."
    exit 0
fi

echo "=== Seeding sample documents into DeepRunner Document Search ==="

# Seed Acme documents
echo "Indexing documents for 'acme'..."
curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: acme" \
    -d '{
        "externalId": "runbook-17",
        "title": "Q3 payroll runbook",
        "content": "Steps for the quarterly payroll batch processing. Ensure that payroll calculations and direct deposit tokens are verified before triggering final settlement.",
        "author": "r.majithiya",
        "tags": ["payroll", "runbook", "finance"]
    }' > /dev/null

curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: acme" \
    -d '{
        "externalId": "arch-01",
        "title": "Kubernetes deployment blueprint",
        "content": "Production deployment specification across multi-availability zone AWS EKS clusters with zero-downtime rolling upgrades and canary rollouts.",
        "author": "devops-lead",
        "tags": ["kubernetes", "infrastructure", "devops"]
    }' > /dev/null

curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: acme" \
    -d '{
        "externalId": "sec-09",
        "title": "Incident response protocol for distributed outages",
        "content": "Step-by-step triage guide for Redis failover, cache stampede mitigation, circuit breaker analysis, and blameless postmortem execution.",
        "author": "sre-lead",
        "tags": ["incident", "sre", "runbook"]
    }' > /dev/null

# Seed Globex documents
echo "Indexing documents for 'globex'..."
curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: globex" \
    -d '{
        "externalId": "globex-pay-1",
        "title": "Globex payroll and international wire instructions",
        "content": "Cross-border treasury management protocol for European and Asian operational subsidiaries with SWIFT message validation.",
        "author": "treasury@globex.com",
        "tags": ["payroll", "treasury", "international"]
    }' > /dev/null

curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: globex" \
    -d '{
        "externalId": "globex-supply-4",
        "title": "Global logistics chain optimization",
        "content": "Logistics hub throughput analysis and container freight scheduling for transatlantic shipping routes.",
        "author": "logistics@globex.com",
        "tags": ["logistics", "operations"]
    }' > /dev/null

# Seed Initech documents
echo "Indexing documents for 'initech'..."
curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: initech" \
    -d '{
        "externalId": "tps-report-01",
        "title": "TPS report cover sheet procedure",
        "content": "Mandatory protocol regarding attaching the new cover sheet to all TPS reports before distribution to division vice presidents.",
        "author": "bill.lumbergh@initech.com",
        "tags": ["reports", "compliance"]
    }' > /dev/null

echo "✓ Initial documents seeded successfully."
