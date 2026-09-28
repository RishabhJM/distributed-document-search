# Architecture — Distributed Document Search Service

**Scope.** A multi-tenant full-text document search service targeting 10M+ documents, <500 ms p95, 1000+ searches/sec, with tenant isolation and horizontal scale. This document covers the design; scale numbers are in PRODUCTION-READINESS.md, caveats in ASSUMPTIONS.md, and per-decision rationale in adr/.

---

## 1. High-level architecture

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
   │              API gateway / load balancer  (production)               │
   │        TLS · WAF · global rate limit · JWT verification              │
   └───────┬──────────────────────┬───────────────────────┬───────────────┘
           ▼                      ▼                       ▼
     ┌───────────┐          ┌───────────┐           ┌───────────┐
     │   api-1   │          │   api-2   │           │   api-N   │   stateless,
     │ Spring    │          │           │           │           │   horizontally
     │ Boot 3    │          │           │           │           │   scalable
     └─────┬─────┘          └─────┬─────┘           └─────┬─────┘
           │  per-request filter chain (order is the security boundary)
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

         Observability: Micrometer → /actuator/prometheus; structured JSON
         logs carrying requestId + tenantId; OpenTelemetry spans per hop.
```

---

## 2. Data flow — indexing

```
POST /documents  {title, content, tags}   X-Tenant-ID: acme
  │
  ├─▶ filters 5 → 10 → 20     ✗ any gate → 400 / 403 / 429, stop here
  ├─▶ @Valid                  tenantId is NOT a field on the request body
  │
  └─▶ DocumentService.index()
       ╔════════════ one Postgres transaction ════════════╗
       ║  INSERT documents      (tenant from CONTEXT)      ║
       ║  INSERT outbox_events  status=PENDING             ║
       ╚══════════════════ COMMIT ════════════════════════╝
                   │   ← intent-to-index is now exactly as durable
        AFTER      │     as the document. No dual write.
        COMMIT     │
                   ├─▶ OpenSearch index
                   │     _id      = "acme:{uuid}"     ← composite id
                   │     routing  = "acme"            ← 1 shard, not N
                   │     ✓ → outbox row PROCESSED
                   │     ✗ → stays PENDING, metric++, client NOT failed
                   ├─▶ documentCache.evict(acme, id)
                   └─▶ INCR searchgen:v1:acme   ← O(1) tenant cache flush

  ◀── 201 Created · Location: /api/v1/documents/{id}
      { id, tenantId, version, indexingState: INDEXED | PENDING }
                                 └─ the API states its own staleness

  Relay (every 2s, any instance):
      SELECT … WHERE status='PENDING' ORDER BY id LIMIT 200
        FOR UPDATE SKIP LOCKED      ← disjoint batches, multi-instance safe
      → re-apply → PROCESSED, or attempts++ → DEAD after 5 (alerted)
```

Network I/O never happens inside the transaction: it would hold a database connection across a remote call, and a rollback after a successful index would leave a phantom document. Cache eviction is likewise post-commit, or a concurrent read would repopulate the cache with pre-commit data.

---

## 3. Data flow — search

```
GET /search?q=quarterly+revenue        X-Tenant-ID: acme
  │
  ├─▶ filters 5 → 10 → 20    (a conflicting ?tenant= is a 403)
  │
  └─▶ SearchService.search()
       ├─ 1. gen = GET searchgen:v1:acme
       ├─ 2. key = search:v1:acme:{gen}:{sha256(canonical query)}
       ├─ 3. Redis GET
       │      ╔═ HIT ═▶ return {cached:true}, ~2-5 ms ═════════════╗
       │      ╚═══════════════════════════════════════════════════╝
       │
       ├─ 4. MISS ─▶ POST /documents/_search?routing=acme
       │      {
       │        query: { bool: {
       │          filter: [ {term:{tenantId:"acme"}} ],   ← non-scoring,
       │          must:   [ {multi_match:{                   cacheable
       │                      fields:["title^3","tags^2",
       │                              "author.text^1.5","content^1"],
       │                      type:"best_fields",
       │                      minimum_should_match:"2<70%" }} ],
       │          should: [ {multi_match:{type:"phrase", slop:2,
       │                                  boost:2.0}} ]
       │        }},
       │        timeout: "400ms",          ← per-shard safety valve
       │        track_total_hits: 10000,   ← exact counts past this are waste
       │        _source: [… no content …], ← snippets come from highlight
       │        highlight: { content: {fragment_size:160} }
       │      }
       │      ✗ → SearchUnavailableException → 503 + Retry-After
       │
       ├─ 5. SETEX key, ttl = 60s × (1 ± 0.2)   ← jitter = anti-stampede
       └─ 6. record timer + cache counter
  ◀── 200 { hits[{id,title,snippet,score}], page{…,totalIsLowerBound},
            tookMs, cached, facets }
```

---

## 4. Storage strategy

| Need | Choice | Why | Rejected |
|---|---|---|---|
| Relevance-ranked full text over 10M+ docs, sub-100 ms | **OpenSearch 2.18** | Inverted index + BM25 + native highlighting + shard routing for tenant locality; Apache-2.0, no licence risk | Postgres `tsvector` — no cross-node scale-out, weaker relevance tuning, no comparable highlighting. Elasticsearch — SSPL |
| Durable transactional record; the thing the index is rebuilt from | **PostgreSQL 16** | ACID, referential integrity, transactional outbox in the same commit, cheap PK lookups | OpenSearch as source of truth — no transactions, no FK integrity, and a reindex needs an immutable source |
| Sub-5 ms repeat reads + distributed counters | **Redis 7.4** | Single-digit-ms GET, atomic Lua for the token bucket, TTL-native, `allkeys-lru` | In-process cache alone — per-instance, so N instances would each grant a tenant its full quota |

→ ADR-0001, ADR-0002, ADR-0004

---

## 5. API design

| Method | Path | Purpose | Success |
|---|---|---|---|
| POST | `/documents` | Index a document | `201` + `Location` |
| POST | `/documents/_bulk` | Index up to 1000 documents | `200` |
| GET | `/search?q=&from=&size=&tags=&highlight=&fuzzy=` | Search | `200` |
| GET | `/documents/{id}` | Fetch by id | `200` |
| DELETE | `/documents/{id}` | Remove | `204` |
| GET | `/actuator/health` | Dependency status | `200` |

Also mounted under `/api/v1/…`. Every request requires `X-Tenant-ID`. `X-Request-Id` is optional inbound, always returned. Every response carries `X-RateLimit-Limit`/`-Remaining`/`-Reset`.

```jsonc
// POST /documents          X-Tenant-ID: acme
{ "externalId": "runbook-17", "title": "Q3 payroll runbook",
  "content": "Steps for the quarterly payroll batch…",
  "author": "r.majithiya", "tags": ["payroll","runbook"],
  "contentType": "text/plain" }
// ← 201
{ "id": "6f1c…", "tenantId": "acme", "version": 0,
  "indexingState": "INDEXED", "createdAt": "2026-09-25T10:00:00Z" }
```

```jsonc
// GET /search?q=payroll+runbook    ← 200
{ "query": "payroll runbook", "tenantId": "acme",
  "hits": [ { "id": "6f1c…", "title": "Q3 payroll runbook",
              "snippet": "Steps for the quarterly <em>payroll</em> batch…",
              "score": 8.41, "tags": ["payroll","runbook"] } ],
  "page": { "from": 0, "size": 20, "totalHits": 1, "totalIsLowerBound": false },
  "tookMs": 12, "cached": false, "facets": {} }
```

Errors are RFC 7807 `application/problem+json` with a stable `code`, plus `requestId` and `timestamp`. No stack traces, SQL or document content ever crosses the wire.

```jsonc
{ "type": "https://docsearch.deeprunner.com/errors/rate-limit-exceeded",
  "title": "Rate limit exceeded", "status": 429,
  "detail": "Tenant 'acme' exceeded 50 requests/second.",
  "code": "RATE_LIMIT_EXCEEDED", "retryAfterSeconds": 1,
  "requestId": "0f2c…", "timestamp": "2026-09-25T10:00:00Z" }
```

---

## 6. Consistency model and trade-offs

| Operation | Guarantee |
|---|---|
| `POST` / `GET /{id}` / `DELETE` | **Strongly consistent, read-your-writes.** Served from Postgres. Readable the instant `POST` returns. |
| `GET /search` | **Eventually consistent**, bounded by `indexing latency + refresh_interval` ≈ **1 s** locally, ≈ 5–6 s in production (`refresh_interval: 5s`). |

We chose write throughput and availability over immediate search visibility. The escape hatch is `refresh-policy=WAIT_FOR`, which the integration tests use to make near-real-time search deterministic without sleeping.

**The dual-write problem is structurally avoided**, not merely mitigated: Postgres is the sole source of truth, so divergence is recoverable by definition — a reindex always converges. The outbox row commits atomically with the document, the index call happens after commit, and an index failure is not a client error. Deletes run index-first so a partial failure never leaves a deleted document searchable — fail-closed in the security-sensitive direction.

→ ADR-0005

---

## 7. Caching strategy

| Layer | Contents | TTL |
|---|---|---|
| L0 browser / Next | document detail | 60 s; search is `no-store` |
| **L2 Redis** | `doc:v1:{tenant}:{id}` | 10 min |
| **L2 Redis** | `search:v1:{tenant}:{gen}:{sha256(query)}` | 60 s ± 20 % jitter |
| L3 OpenSearch | shard request + filesystem cache | engine-managed — enabled *because* the tenant term is a `filter`, not a `must` |

**The tenant is the second segment of every key, unconditionally**, and keys are built only by `CacheKeys`. Cache-layer cross-tenant leakage is the vector most implementations miss; `TenantIsolationIT` asserts a warm cache for one tenant cannot serve another.

Search results cannot be selectively invalidated — you cannot know which cached queries a new document would have matched. So a write does `INCR searchgen:v1:{tenant}`: every subsequent key differs, flushing that tenant's search cache in one atomic O(1) operation. The alternative, `SCAN`-and-delete over a large keyspace, is a genuine production hazard. Orphans expire by TTL.

Query text is normalised (trim, collapse whitespace, lowercase) before hashing, so trivially different spellings share an entry. TTL jitter desynchronises expiry; a distributed single-flight lock is designed but deferred. All cache failures are non-fatal.

---

## 8. Asynchronous operations

Phase 1 uses a **transactional outbox drained by an in-process scheduled relay** using `FOR UPDATE SKIP LOCKED`: at-least-once delivery, multi-instance safe, zero extra infrastructure. Phase 2 publishes the same rows to Kafka `doc.index.v1`, keyed by `tenantId` (per-tenant ordering), 12 partitions, a consumer group of N indexers, DLQ `doc.index.dlq`. Because the producer sits behind `IndexingGateway`, the switch is one property — `docsearch.indexing.mode=OUTBOX` — with no change to the service, controllers, entities or schema.

Other workloads a broker would carry: reindex/backfill, tenant deletion, analytics events.

→ ADR-0005

---

## 9. Multi-tenancy and data isolation

**Model: one shared index, `tenantId` keyword field, mandatory filter, `routing=tenantId`.** Index-per-tenant is the intuitive answer and it fails at exactly this scale: 1,000 tenants × 1 shard × 1 replica is 2,000 shards, and cluster-state latency degrades past ~10,000. A shared index is 3 primaries regardless of tenant count. Routing means a query touches **1 shard instead of N** — the single largest lever for the p95 and throughput targets.

**Four enforcement layers, any one of which would prevent a leak:**

1. `TenantResolutionFilter` rejects a missing (400), malformed (400), unknown or suspended (403) tenant before any handler runs. Unknown and suspended return an *identical* 403 so neither is an enumeration oracle.
2. Every Postgres finder is tenant-scoped by signature; an ArchUnit rule fails the build if any call site uses the inherited `findById`.
3. Every OpenSearch query is built by `OpenSearchQueryFactory`, which throws rather than emit a query without a tenant filter.
4. Composite `_id` = `{tenant}:{uuid}` with `"_routing": {"required": true}` — the engine itself rejects an unrouted write.

**Noisy neighbours** are handled by per-tenant token buckets (from the `tenants` table), a 400 ms server-side query timeout, a 10,000 result-window cap, and body-size limits.

**Escalation path:** shared index → dedicated index above ~50M docs or 5 % of query volume → dedicated cluster for data-residency obligations. `IndexNaming` is the single class that decision touches.

**Known limitation, stated plainly:** in this prototype `X-Tenant-ID` is client-supplied and therefore forgeable. In production it is stripped at the gateway and re-injected from a signed JWT claim, or this filter becomes a Spring Security `AuthenticationConverter`. Everything downstream reads only `TenantContext`, so that swap touches one class. Postgres RLS is the documented defence-in-depth target.

→ ADR-0003, ASSUMPTIONS.md
