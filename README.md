# Distributed Document Search Service

A multi-tenant full-text document search platform engineered for **10M+ documents**, **sub-500ms p95 latency**, **1,000+ searches/second**, and strict **tenant data isolation**.

> 🚀 **Looking to run and test immediately?** See [**`RUN.md`**](RUN.md) for complete copy-paste commands, preflight checks, test execution, and a 5-minute verification walkthrough.

---

## 1. System Architecture

```
                         ┌──────────────────────────┐
                         │   Browser (Next.js 15)   │
                         └────────────┬─────────────┘
                          same-origin │
                                      ▼
                         ┌──────────────────────────┐
                         │  Next.js route handler   │
                         │  /api/[...path] proxy    │
                         │  injects X-Tenant-ID     │
                         │  from an httpOnly cookie │
                         └────────────┬─────────────┘
                                      │  X-Tenant-ID: acme
   ┌──────────────────────────────────▼───────────────────────────────────┐
   │              API Gateway / Load Balancer (Production)                │
   │        TLS · WAF · global rate limit · JWT verification              │
   └───────┬──────────────────────┬───────────────────────┬───────────────┘
           ▼                      ▼                       ▼
     ┌───────────┐          ┌───────────┐           ┌───────────┐
     │   api-1   │          │   api-2   │           │   api-N   │   Stateless,
     │ Spring    │          │           │           │           │   horizontally
     │ Boot 3    │          │           │           │           │   scalable
     └─────┬─────┘          └─────┬─────┘           └─────┬─────┘
           │  Per-request filter chain (Order is the security boundary)
           │    5  AppRequestContextFilter requestId → MDC
           │   10  TenantResolutionFilter validate tenant — FAILS CLOSED
           │   20  RateLimitFilter        per-tenant token bucket
           └──────────┬───────────────┬──────────────────┬──────────────┐
                      ▼               ▼                  ▼              ▼
              ┌──────────────┐ ┌──────────────┐  ┌──────────────┐ ┌──────────────┐
              │ PostgreSQL 16│ │ OpenSearch   │  │   Redis 7.4  │ │ Outbox relay │
              │              │ │    2.18      │  │              │ │ @Scheduled,  │
              │ SOURCE OF    │ │ derived,     │  │ search cache │ │ SKIP LOCKED  │
              │ TRUTH        │ │ rebuildable  │  │ doc cache    │ │              │
              │ documents    │ │ BM25 + high- │  │ token buckets│ │ ─► Phase 2:  │
              │ tenants      │ │ light        │  │ LRU, 256MB   │ │ Kafka topic  │
              │ outbox_events│ │ routing=     │  │              │ │ doc.index.v1 │
              │              │ │ tenantId     │  │ non-durable  │ │ keyed by     │
              │              │ │              │  │ by design    │ │ tenantId     │
              └──────────────┘ └──────────────┘  └──────────────┘ └──────────────┘
                   FATAL            FATAL          NON-FATAL
                                                (degrades, stays in the LB)
```

### Core Architecture Highlights

1. **PostgreSQL as Source of Truth & Outbox ([ADR-0002](docs/adr/adr2.md))**:
   Documents and `outbox_events` commit atomically in one ACID transaction. The dual-write divergence risk is structurally eliminated. Reads by ID (`GET /documents/{id}`) are strongly consistent read-your-writes.
2. **OpenSearch with Mandatory Shard Routing ([ADR-0001](docs/adr/adr1.md), [ADR-0003](docs/adr/adr3.md))**:
   One shared index (`documents-live` alias) with composite IDs (`{tenant}:{uuid}`) and `routing=tenantId`. Queries hit **1 shard instead of N**, enabling sub-100ms relevance retrieval and linear horizontal scale.
3. **Redis for Caching & Token-Bucket Rate Limiting ([ADR-0004](docs/adr/adr4.md))**:
   Atomic Lua token buckets enforce per-tenant quotas. Search queries are cached using an atomic generation counter (`searchgen:v1:{tenant}`), invalidating tenant search caches in $O(1)$ time upon document writes.
4. **Architectural Asymmetry (Fail-Closed vs Fail-Open)**:
   - **Tenant isolation FAILS CLOSED**: Missing, malformed, or unauthorized tenants receive immediate 400/403 responses.
   - **Rate limiter & Cache FAIL OPEN**: If Redis goes down, the service gracefully degrades using an in-process token bucket (`InProcessFallbackRateLimiter`) and continues serving origin queries.

---

## 2. Quick Start

### Prerequisites
- Docker & Docker Compose (or local Java 21, Maven 3.9+, Node 20+)
- Ports available: `5432` (PostgreSQL), `9200` (OpenSearch), `6379` (Redis), `8080` (API), `3000` (Web UI)

### Option A: Docker Compose (Full Stack)

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Start full multi-service topology (Postgres, OpenSearch, Redis, Spring Boot API, Next.js UI)
./scripts/up.sh -Mode full -Seed
# On Windows PowerShell: .\scripts\up.ps1 -Mode full -Seed
```
- **Web UI:** [http://localhost:3000](http://localhost:3000)
- **API Swagger/Health:** [http://localhost:8080/health](http://localhost:8080/health)

### Option B: Local Development

```bash
# 1. Start core data stores in Docker
docker compose up -d postgres opensearch redis

# 2. Run Spring Boot Backend
cd backend
mvn spring-boot:run

# 3. Run Next.js Frontend (in a new terminal)
cd frontend
npm install
npm run dev
```

---

## 3. Five-Minute Interactive Demo

Follow the 6 steps documented in [docs/demo.md](docs/demo.md) to verify all key architectural claims:

### 1. Fast Full-Text Search with BM25 & Highlights
- Open [http://localhost:3000](http://localhost:3000), select tenant **`acme`**, and search **`payroll runbook`**.
- *Observe:* High-relevance hits with matched terms highlighted in `<em>...</em>` and latency in low tens of milliseconds. Repeat the search to see the green **"Served from cache (Redis L2)"** badge appear.

### 2. Strict Multi-Tenant Data Isolation
- With the query active, switch the tenant selector from **`acme`** to **`globex`**.
- *Observe:* The results change completely.
- Try accessing an Acme document as Globex (`GET /documents/{acmeDocId}` with `X-Tenant-ID: globex`).
- *Observe:* Returns **HTTP 404 (Not Found)** — not a 403. A 403 would confirm the document exists, leaking information across tenants.

### 3. Read-Your-Writes & Eventual Consistency
- Navigate to **Index Document**, fill in the fields, and submit.
- *Observe:* Instant 201 Created with `indexingState: INDEXED` (or `PENDING` if OpenSearch is under load). Reads by ID are immediate via PostgreSQL; searchability converges within ~1s.

### 4. Graceful Degradation (Stop Redis)
```bash
docker compose stop redis
```
- Refresh the UI. The Redis indicator turns amber; `/health` returns **HTTP 200 (Status: DEGRADED)**; search queries continue serving directly from OpenSearch.
```bash
docker compose start redis
```

### 5. Per-Tenant Token-Bucket Rate Limiting
- Run the rate limit burst check:
```bash
./api/curl/requests.sh acme
```
- *Observe:* Requests exceeding Acme's 50 rps quota return **HTTP 429** with RFC 7807 problem details and `Retry-After: 1`. Requests for `globex` continue unthrottled in the same second.

### 6. Self-Healing Search Index (Outbox Relay)
```bash
docker compose stop opensearch
# Submit a document through the UI or curl (commits to Postgres outbox with status PENDING)
docker compose start opensearch
# Wait a few seconds for the scheduled relay, then verify parity:
./scripts/seed.sh -VerifyOnly
```
- *Observe:* The outbox relay automatically reconciles the missing document into OpenSearch via `SELECT ... FOR UPDATE SKIP LOCKED`.

---

## 4. REST API Contracts

All endpoints accept and return JSON. Every request requires `X-Tenant-ID`. Optional inbound `X-Request-Id` is echoed in responses for distributed tracing.

| Method | Path | Description | Success Code |
|---|---|---|---|
| `POST` | `/documents` | Index a document | `201 Created` (`Location` header set) |
| `POST` | `/documents/_bulk` | Batch index up to 1,000 documents | `200 OK` |
| `GET` | `/search?q=&from=&size=&tags=&highlight=&fuzzy=` | Full-text relevance search | `200 OK` |
| `GET` | `/documents/{id}` | Retrieve document details by UUID | `200 OK` |
| `DELETE` | `/documents/{id}` | Soft delete document | `204 No Content` |
| `GET` | `/health` | Live dependency health status | `200 OK` / `503 Service Unavailable` |

### Sample Search Response (`GET /search?q=payroll+runbook`)
```json
{
  "query": "payroll runbook",
  "tenantId": "acme",
  "hits": [
    {
      "id": "7b8849b2-3837-4d92-bf36-547348981442",
      "externalId": "runbook-17",
      "title": "Q3 payroll runbook",
      "snippet": "Steps for the quarterly <em>payroll</em> batch processing...",
      "score": 4.12,
      "author": "r.majithiya",
      "tags": ["payroll", "runbook", "finance"],
      "createdAt": "2026-09-28T09:48:00Z"
    }
  ],
  "page": {
    "from": 0,
    "size": 25,
    "totalHits": 1,
    "totalIsLowerBound": false
  },
  "tookMs": 14,
  "cached": false,
  "facets": {
    "tags": {
      "payroll": 1,
      "runbook": 1,
      "finance": 1
    }
  }
}
```

### RFC 7807 Problem Details (`HTTP 429`)
```json
{
  "type": "https://docsearch.deeprunner.com/errors/rate-limit-exceeded",
  "title": "Rate limit exceeded",
  "status": 429,
  "detail": "Tenant 'acme' exceeded 50 requests/second.",
  "code": "RATE_LIMIT_EXCEEDED",
  "retryAfterSeconds": 1,
  "requestId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "timestamp": "2026-09-28T09:48:00Z"
}
```

---

## 5. Verification & Testing

### Automated Test Suite
```bash
# Run unit, architecture, and integration tests
cd backend
mvn test
```

### Architecture Rule Enforcement (ArchUnit)
The build verifies strict architectural invariants in [ArchitectureTest.java](backend/src/test/java/com/deeprunner/docsearch/architecture/ArchitectureTest.java):
- **Rule 1:** Prohibits any call sites from invoking un-tenanted `findById`. All callers must use `findByTenantIdAndIdAndDeletedAtIsNull`.
- **Rule 2:** Enforces that controllers never touch repositories directly, guaranteeing all interactions traverse domain service boundaries.
- **Rule 3:** Enforces strict layer access rules preventing entity exposure directly to presentation layers.

### Automated Smoke Verification
```bash
./scripts/verify.sh
```

---

## 6. Project Documentation Directory (`docs/`)

- [docs/architecture.md](docs/architecture.md): Complete high-level architecture document, data flow diagrams, and isolation layers.
- [docs/production-readiness.md](docs/production-readiness.md): Production readiness analysis, 100x scale calculations (1B docs / 100k qps), Resilience4j configurations, and 99.95% SLA arithmetic.
- [docs/experience.md](docs/experience.md): Enterprise experience showcase covering high-scale distributed systems, performance profiling, production incidents, and architectural trade-offs.
- [docs/assumptions.md](docs/assumptions.md): Explicit assumptions, scope boundaries, and operational impact analysis.
- [docs/demo.md](docs/demo.md): Step-by-step 5-minute evaluation walkthrough.
- [docs/ai-tool-usage.md](docs/ai-tool-usage.md): Honest disclosure of AI tooling, prompts, corrections, and human oversight.
- [docs/adr/](docs/adr/): Architectural Decision Records (OpenSearch, PostgreSQL, Shard Routing, Redis, Outbox Relay, Next.js Route Proxy).
