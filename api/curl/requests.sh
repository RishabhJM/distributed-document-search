#!/usr/bin/env bash
# Demonstration curl commands for DeepRunner Document Search

API_URL="${API_URL:-http://localhost:8080}"
TENANT="${1:-acme}"

echo "================================================================="
echo " DeepRunner Document Search — Interactive curl Demonstration"
echo " Active Tenant: $TENANT"
echo "================================================================="

echo ""
echo "1. Health Check (with dependency status):"
curl -s -i "$API_URL/health"
echo ""

echo ""
echo "2. Index a new document as tenant '$TENANT':"
CREATE_RESP=$(curl -s -i -X POST "$API_URL/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT" \
  -d '{
    "externalId": "demo-doc-1",
    "title": "Distributed systems consensus protocols",
    "content": "A technical survey of Raft, Paxos, and Zab replication mechanisms in highly available distributed databases.",
    "author": "staff-architect",
    "tags": ["distributed-systems", "consensus", "architecture"]
  }')
echo "$CREATE_RESP"

DOC_ID=$(echo "$CREATE_RESP" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "Captured Document ID: $DOC_ID"

if [ -n "$DOC_ID" ]; then
    echo ""
    echo "3. Fetch document by ID as tenant '$TENANT' (read-your-writes from Postgres):"
    curl -s -i "$API_URL/documents/$DOC_ID" -H "X-Tenant-ID: $TENANT"
    echo ""

    echo ""
    echo "4. Tenant Isolation Check: Fetch the same document as 'globex' (MUST RETURN 404):"
    curl -s -i "$API_URL/documents/$DOC_ID" -H "X-Tenant-ID: globex"
    echo ""
fi

echo ""
echo "5. Search documents with BM25 relevance & highlighting:"
curl -s -i "$API_URL/search?q=consensus+protocols&highlight=true" -H "X-Tenant-ID: $TENANT"
echo ""

echo ""
echo "6. Repeat Search (served from Redis L2 cache, check cached: true):"
curl -s -i "$API_URL/search?q=consensus+protocols&highlight=true" -H "X-Tenant-ID: $TENANT"
echo ""

echo ""
echo "7. Security Check: Conflicting ?tenant= parameter must return 403 TENANT_MISMATCH:"
curl -s -i "$API_URL/search?q=test&tenant=globex" -H "X-Tenant-ID: acme"
echo ""

echo ""
echo "8. Security Check: Missing X-Tenant-ID header must return 400 MISSING_TENANT:"
curl -s -i "$API_URL/search?q=test"
echo ""

echo ""
echo "9. Rate Limiting Test: Bursting requests to demonstrate HTTP 429 token bucket throttling:"
for i in {1..55}; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_URL/search?q=test" -H "X-Tenant-ID: $TENANT")
  if [ "$CODE" = "429" ]; then
    echo "Request $i: HTTP 429 RATE_LIMIT_EXCEEDED (Rate limiter engaged successfully!)"
    break
  else
    echo -n "$CODE "
  fi
done
echo ""
echo "================================================================="
echo " Demonstration complete!"
echo "================================================================="
