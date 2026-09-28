#!/usr/bin/env bash
set -e

API_URL="${API_URL:-http://localhost:8080}"

echo "================================================================="
echo " DeepRunner Document Search Service — Automated Smoke Verification"
echo "================================================================="

# 1. Health check
echo -n "1. Checking service health... "
HEALTH_STATUS=$(curl -s "$API_URL/health" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
if [ "$HEALTH_STATUS" = "UP" ] || [ "$HEALTH_STATUS" = "DEGRADED" ]; then
    echo "✓ PASS (Status: $HEALTH_STATUS)"
else
    echo "❌ FAIL (Expected UP or DEGRADED, got $HEALTH_STATUS)"
    exit 1
fi

# 2. Write document
echo -n "2. Testing transactional write and outbox commit... "
WRITE_RESP=$(curl -s -X POST "$API_URL/documents" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: acme" \
    -d '{
        "externalId": "verify-1",
        "title": "Smoke test document for verification",
        "content": "Automated verification payload testing end-to-end data pipelines.",
        "tags": ["verification", "smoke"]
    }')
DOC_ID=$(echo "$WRITE_RESP" | grep -o '"id":"[^"]*' | cut -d'"' -f4)

if [ -n "$DOC_ID" ]; then
    echo "✓ PASS (Created doc: $DOC_ID)"
else
    echo "❌ FAIL (Could not extract created doc ID)"
    exit 1
fi

# 3. Read own document
echo -n "3. Testing strongly consistent read-your-writes from Postgres... "
READ_TITLE=$(curl -s "$API_URL/documents/$DOC_ID" -H "X-Tenant-ID: acme" | grep -o '"title":"[^"]*' | cut -d'"' -f4)
if [ "$READ_TITLE" = "Smoke test document for verification" ]; then
    echo "✓ PASS"
else
    echo "❌ FAIL (Expected title match)"
    exit 1
fi

# 4. Cross-tenant isolation
echo -n "4. Testing tenant isolation (Globex accessing Acme doc)... "
STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/documents/$DOC_ID" -H "X-Tenant-ID: globex")
if [ "$STATUS_CODE" = "404" ]; then
    echo "✓ PASS (Returned HTTP 404 - No cross-tenant disclosure)"
else
    echo "❌ FAIL (Expected 404, got $STATUS_CODE)"
    exit 1
fi

# 5. Security - tenant mismatch
echo -n "5. Testing tenant parameter conflict protection... "
MISMATCH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/search?q=test&tenant=globex" -H "X-Tenant-ID: acme")
if [ "$MISMATCH_CODE" = "403" ]; then
    echo "✓ PASS (Returned HTTP 403 TENANT_MISMATCH)"
else
    echo "❌ FAIL (Expected 403, got $MISMATCH_CODE)"
    exit 1
fi

# 6. Security - missing tenant
echo -n "6. Testing missing tenant rejection (fails closed)... "
MISSING_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/search?q=test")
if [ "$MISSING_CODE" = "400" ]; then
    echo "✓ PASS (Returned HTTP 400 MISSING_TENANT)"
else
    echo "❌ FAIL (Expected 400, got $MISSING_CODE)"
    exit 1
fi

echo "================================================================="
echo " ✓ ALL 6 VERIFICATION CHECKS PASSED!"
echo "================================================================="
