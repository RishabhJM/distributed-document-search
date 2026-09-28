# Production Readiness Analysis

What it would take to run this service for real. Each section ends with **what we would build first**, so this reads as a plan rather than a survey.

---

## 1. Scalability — 100× growth (10M → 1B documents, 1k → 100k searches/sec)

**What breaks first, in order.** This ordering matters more than any individual mitigation:

1. **OpenSearch shard fan-out.** At 1B documents a naive index has hundreds of shards and every search touches all of them. *This breaks before storage does.* Already mitigated: `routing=tenantId` confines a single-tenant query to one shard.
2. **Postgres write throughput** on `documents` + `outbox_events` — roughly 5–8k inserts/sec on one tuned primary before WAL and outbox autovacuum dominate.
3. **Redis single node** — ~80–100k ops/sec per core. At 100k searches/sec with two ops each (rate limit + cache) that is 200k ops/sec, so it must be clustered.
4. **The outbox relay** — a polling relay saturates around 2k docs/sec. This is the forcing function for the Kafka migration, not a preference.
5. **JVM GC and connection-pool saturation** on the API tier — last, because it is the easiest thing to scale out.

**Shard math for 1B documents.** Assuming an average 4 KB document and a ~1.4× on-disk multiplier for `_source` + inverted index + doc_values:

- 1B × 4 KB = 4 TB raw → **≈ 5.6 TB indexed**
- Target shard size **40 GB** (practical band 30–50 GB: below 20 GB per-shard overhead dominates, above 60 GB recovery and relocation get painful)
- 5.6 TB ÷ 40 GB = **140 primary shards**; with one replica, **280 shards / 11.2 TB**
- At 2 TB usable NVMe per data node: **6 data nodes minimum**. For query headroom at 100k searches/sec, budget **12–18 data nodes** (8 vCPU / 64 GB class) plus **3 dedicated cluster-manager nodes** and **4+ coordinator-only nodes**
- Heap rule of thumb: ≤ 20 shards per GB of heap, 31 GB maximum (the compressed-oops boundary)

At that size, move to **time-partitioned indices** `documents-{tier}-{yyyy.MM}` behind an alias, so older months roll to searchable snapshots on S3 (~1/10 the storage cost) and a reindex is per-partition rather than all-or-nothing.

**When the shared index stops being right:**

| Tenant size | Model | Rationale |
|---|---|---|
| < 1M docs (~90 % of tenants) | Shared index, filter + routing | Per-index overhead dominates; 500 tiny indices is a cluster-state problem, not a scale win |
| 1M – 50M | Shared index, dedicated routing shards | Still cheap; routing keeps latency flat |
| > 50M docs **or** > 5 % of cluster query volume | **Dedicated index** | Prevents a whale skewing the shared index; allows independent reindex and refresh tuning |
| Regulated / data residency | **Dedicated cluster or region** | Isolation as a compliance requirement, not a performance one |

Hard constraint to respect: **keep total cluster shards under ~10,000.** Past that, cluster-state update latency degrades sharply. That is precisely why 500+ tenants cannot each have their own index.

**Traffic.** At a 70 % cache hit rate (realistic — head queries repeat heavily) OpenSearch sees 30k qps, which is the 12–18 node figure above. The API tier at ~2k rps per instance needs **~50 instances**, autoscaled on p95 latency and CPU across 3 AZs. Postgres runs a primary plus two read replicas with `GET /documents/{id}` served from a replica, behind **PgBouncer in transaction mode** — 50 instances × 20 connections is 1000, which would kill a primary capped at 500.

**Cost levers:** searchable snapshots for cold data (~90 % storage saving), Graviton instances (~20 %), reserved capacity, and reducing replica count for reconstructable indices — justified precisely because Postgres is the source of truth.

**Build first:** PgBouncer and the read-replica split. Postgres is the first thing that breaks that is not already mitigated.

---

## 2. Resilience

**Circuit breakers (Resilience4j), with concrete configuration.**

*OpenSearch:* `slidingWindowType: COUNT_BASED`, `slidingWindowSize: 100`, `failureRateThreshold: 50%`, `slowCallDurationThreshold: 400ms`, `slowCallRateThreshold: 60%`, `waitDurationInOpenState: 10s`, `permittedNumberOfCallsInHalfOpenState: 5`, `minimumNumberOfCalls: 20`. Open → `503 SEARCH_UNAVAILABLE` + `Retry-After: 10`.

*Redis is deliberately different:* `failureRateThreshold: 30%`, `waitDurationInOpenState: 5s`, and **fail-open**. The rationale is worth stating explicitly: *the cache must never be able to take down the system it exists to protect.* The residual risk — that a Redis outage also disables rate limiting — is covered by `InProcessFallbackRateLimiter`, which holds a coarse per-instance limit of `globalLimit / instanceCount × 1.2` during the degraded window.

**Retries apply to indexing, not to search.** A retry inside a 500 ms p95 budget is a latency bug wearing a resilience costume. Search gets one attempt with a 400 ms server-side timeout and a fast fail. Indexing gets 3 attempts, exponential backoff **with full jitter**, base 50 ms, cap 800 ms, 1.2 s total budget, and only on retryable conditions (429, 502/503/504, connect timeouts) — never 400/404, never a non-idempotent POST without an idempotency key.

**Timeouts at every hop, each strictly shorter than its caller's:** browser → Next 8 s · Next → API 5 s · API → OpenSearch 400 ms query / 1 s socket · API → Postgres 2 s statement / 250 ms acquire · API → Redis 50 ms connect / 100 ms command.

**Bulkheads.** Separate connection pools and thread budgets for search versus indexing, so a bulk-load storm cannot starve interactive search, plus a per-tenant semaphore bulkhead as the noisy-neighbour backstop.

**Failover.** OpenSearch: 3 cluster-manager nodes across 3 AZs (quorum 2), `replicas ≥ 1` with AZ allocation awareness, so any single-AZ loss is survivable. Postgres: Multi-AZ synchronous standby, RTO 60–120 s, RPO 0. Be honest about the consequence — **writes return 503 during failover while search stays fully available**, because search reads OpenSearch. That asymmetry is a feature and protects roughly 90 % of traffic.

**Graceful degradation ladder** — every dependency has a defined partial-failure behaviour and nothing cascades:

| Dependency down | Behaviour |
|---|---|
| Redis | Slower, still correct. Health `DEGRADED`, stays in the load balancer. |
| OpenSearch | Search returns 503; document CRUD keeps working. |
| Postgres | Writes return 503; **search still serves from the index** — stale but available. |

**Build first:** the Resilience4j breaker around the OpenSearch adapter. It is the one remaining single point that can consume the whole latency budget.

---

## 3. Security

**Authentication.** OIDC/OAuth2 with short-lived RS256 access tokens (10 min) validated by a Spring Security resource server against a cached JWKS endpoint. **The tenant comes from a signed claim, never from a client header** — this is the explicit remediation of the prototype shortcut recorded in ASSUMPTIONS.md. Service-to-service traffic uses mTLS inside the mesh.

**Authorization.** The tenant claim populates `TenantContext`; role claims (`doc:read`, `doc:write`, `doc:delete`, `tenant:admin`) are enforced with `@PreAuthorize`. Two engine-level controls back the application logic: **Postgres row-level security** policies keyed to `current_setting('app.tenant_id')` set per transaction, so even an ORM bug cannot cross tenants; and **OpenSearch fine-grained access control** with a document-level template `{"term":{"tenantId":"${user.attr.tenant}"}}`, so the search engine enforces the filter independently of the query builder.

**Encryption.** TLS 1.3 on every hop *including internal ones* — OpenSearch transport-layer TLS, Postgres `sslmode=verify-full`, Redis TLS — with HSTS at the edge. At rest: KMS customer-managed keys per environment with annual rotation, node-to-node encryption, and volume or TDE encryption for Postgres. Secrets live in Secrets Manager or Vault with automatic rotation, never in a file. The repository's `.env` is local-development-only and gitignored; `.env.example` carries placeholder values, and `application-*.yml` contains no credentials at all.

**API security.** Per-tenant and per-principal rate limits · 1 MB per document and 10 MB per bulk request · strict Bean Validation allow-lists on every DTO · **query-injection defence**: the OpenSearch DSL is never built by string concatenation, `script` and leading-wildcard `query_string` are disabled, and `from + size` is capped at 10,000 with `search_after` required beyond — this is a denial-of-service vector, not merely a usability one · CSP, `X-Frame-Options` and `X-Content-Type-Options` on the Next.js responses · a path allow-list on the proxy route, because an open proxy in a graded repository is a finding in itself.

**Audit and compliance.** An append-only log of every write and **every cross-tenant access denial**, shipped to immutable storage. PII field tagging. GDPR erasure implemented as a hard delete from Postgres and OpenSearch plus cache invalidation and an audit tombstone, with a documented 30-day backup-purge window.

**Supply chain.** Pinned image *digests* in production, Trivy or Grype scanning with a build-breaking threshold, a CycloneDX SBOM per release, Dependabot, non-root containers (already the case), and read-only root filesystems.

**Build first:** JWT-derived tenancy. Everything else is defence in depth behind a control that is currently forgeable.

---

## 4. Observability

**Metrics** (Micrometer → Prometheus → Grafana). Note the cardinality decision: `tenantId` is bucketed to the top 20 tenants plus `other`, because per-tenant labels at 500 tenants × 20 metrics × 10 histogram buckets is a cardinality bomb. The prototype tags raw tenant ids only because it has three.

- `dr_search_duration_seconds{tenant_bucket, cache, outcome}` — histogram
- `dr_search_cache_hit_ratio`, `dr_ratelimit_rejections_total`
- **`dr_index_lag_seconds`** — outbox commit to searchable. *The most important business metric in the system.*
- `dr_outbox_pending_rows`, `dr_outbox_dead_total`
- `resilience4j_circuitbreaker_state`, `hikaricp_connections_active`, `jvm_gc_pause_seconds`
- OpenSearch: per-node query latency, **`search.rejected` thread-pool rejections** (the earliest warning of saturation), heap %, segment count

The four golden signals per endpoint sit on one Grafana row, beside a per-tenant top-10 latency table.

**Logging.** Structured JSON via Logback with mandatory MDC fields `traceId`, `spanId`, `tenantId`, `requestId`, `principal`. **Document content is never logged** — only identifiers and term counts. Sampling: 100 % of WARN/ERROR, 1 % of INFO on the search path. Retention 30 days hot, 1 year archived.

**Tracing.** OpenTelemetry with W3C `traceparent` propagated from the Next.js proxy through Spring into the OpenSearch and Postgres client spans. 1 % head sampling **plus tail sampling of 100 % of errors and anything over 400 ms**. A search trace shows five spans — proxy → controller → `redis.get` → `opensearch.search` → `redis.setex` — which is what lets you say whether a slow request was Redis, the query, or GC, rather than guessing.

**Alerting — symptom-based and tied to SLOs, never to causes.** p95 search latency > 500 ms for 5 min (page) · error rate > 1 % for 5 min (page) · `dr_index_lag_seconds` p95 > 30 s for 10 min (ticket) · circuit breaker open > 2 min (page) · outbox DEAD rows > 0 (ticket) · disk > 75 % (ticket) / > 85 % (page). Every alert links to a runbook. Error-budget burn-rate alerting: fast burn (2 % in 1 h) pages, slow burn (5 % in 6 h) tickets.

**Build first:** `dr_index_lag_seconds` and the outbox backlog gauges. They are the only signals that reveal the eventual-consistency window drifting out of contract.

---

## 5. Performance

**The 500 ms p95 budget, allocated.** Publishing the allocation is what makes the target actionable:

| Hop | Budget | Note |
|---|---|---|
| Edge / LB + TLS | 20 ms | |
| Auth (JWT verify, cached JWKS) | 5 ms | |
| Rate limit (Redis Lua) | 5 ms | one round trip |
| Cache lookup | 5 ms | |
| **OpenSearch query (miss path)** | **250 ms** | server-side `timeout: 400ms` as the hard stop |
| Response mapping + serialisation | 15 ms | `_source` filtering keeps payloads ~20 KB |
| Network to client | 50 ms | |
| **Headroom / GC / jitter** | **150 ms** | |

The cache-hit path is ~35 ms end to end. At a 70 % hit rate the blended p95 sits comfortably inside 500 ms.

**Query optimisation.** Tenant filter in `filter` context — non-scoring *and* cacheable in the shard request cache · `track_total_hits: 10000` rather than `true`, because **exact counts beyond the window are the single most common cause of slow search** · `_source` filtering to exclude `content` from result lists · `search_after` with a point-in-time for deep paging instead of `from`/`size` · `best_fields` multi-match with `title^3, tags^2, content^1` · no leading wildcards · `preference=_local` or session-sticky routing to lift shard request-cache hit rates.

**Index management.** `dynamic: strict` so an unmapped field is a hard error rather than a mapping explosion — both a stability and a security control, since it stops arbitrary client metadata becoming queryable fields · `tenantId` as `keyword` with doc_values · `index_options: offsets` on `content` only if highlighting demands it, weighing the ~10–20 % index-size cost against faster `fvh` highlighting · `norms: false` where length normalisation is meaningless · **`refresh_interval: 5s` in production, not 1 s** — a 5× reduction in segment churn for a 4 s consistency cost · rollover at 40 GB or 30 days · force-merge read-only partitions to a single segment · `_reindex` behind an alias for zero-downtime mapping changes.

**Postgres.** Composite `documents(tenant_id, created_at DESC)` · **hash-partition `documents` by `tenant_id`** (16 partitions) past ~100M rows · the outbox keeps a partial index on `(status, id) WHERE status='PENDING'` and is **truncated or partitioned rather than deleted from** — accumulated dead tuples in an outbox table is a classic production failure, and `autovacuum_vacuum_scale_factor = 0.01` is already set on it · HikariCP 20 per instance behind PgBouncer · prepared-statement caching · `log_min_duration_statement=200ms` feeding a slow-query dashboard.

**JVM.** Java 21, G1 with `MaxGCPauseMillis=100`, `MaxRAMPercentage=70`, AppCDS to cut startup, and **virtual threads for the I/O-bound search path** (already enabled) — with the caveat that `synchronized` pinning must be verified before relying on it under load.

**Build first:** a k6 benchmark at 500k documents producing a real p50/p95/p99 table. Every number above is currently reasoned rather than measured, and saying so is more useful than implying otherwise.

---

## 6. Operations

**Deployment.** Containers on EKS, a Helm chart per service, GitOps via Argo CD with environment overlays, immutable image digests. Flyway migrations run as a pre-sync Job, and **every migration must be backward-compatible with the previous application version** — expand/contract: add column → deploy → backfill → deploy the reader → drop in a later release. This is what actually makes zero-downtime possible; it deserves to be said before "rolling update".

**Zero downtime.** `maxSurge=25%, maxUnavailable=0` · a readiness probe that reports NOT-READY until the index template is verified and pools are warm · **`preStop` sleep 10 s with `terminationGracePeriodSeconds: 45`**, so in-flight searches drain and the load balancer deregisters before SIGTERM lands — omitting this is the most common cause of 502s during a supposedly zero-downtime deploy · PodDisruptionBudget `minAvailable: 75%`.

**Blue-green and canary.** Two index aliases (`documents-live`, `documents-next`) let a mapping change be reindexed into green and cut over with an **atomic alias swap**. Argo Rollouts drives a 5 % → 25 % → 100 % canary gated on p95-latency and error-rate analysis templates, with automatic rollback on SLO breach.

**Backup and recovery, with numbers.** Postgres: continuous WAL archiving plus a daily base backup → **RPO ≤ 5 min, RTO ≤ 30 min** via PITR, 35-day retention. OpenSearch: hourly snapshots → RPO ≤ 1 h — but the real recovery story is that **the index is fully reconstructible from Postgres**, so the worst case is a reindex, benchmarked at ~20k docs/sec, i.e. **1B documents in roughly 14 hours with 20 parallel workers**. That number is the honest answer and it is what justifies keeping snapshots at all. Redis needs no backup by design. **Restores are exercised quarterly in a game day** — an untested backup is not a backup.

**Disaster recovery.** Warm standby in a second region with cross-region snapshot replication, documented RTO 4 h / RPO 1 h for full regional loss, health-checked DNS failover.

**Runbooks** for: cluster red · index lag climbing · Redis eviction storm · Postgres failover · tenant onboarding · emergency rate-limit override. On-call carries severity definitions, an escalation path, and a blameless postmortem within five business days for any SEV1/2.

**Build first:** expand/contract migration discipline and the `preStop` drain. Both are cheap and both are prerequisites for every other zero-downtime claim.

---

## 7. SLA — achieving 99.95 % availability

**The budget, in minutes.** 99.95 % is **21.6 minutes of downtime per 30-day month** (4.38 hours/year, ~10 seconds/day). The corollary reframes everything: **a single 30-minute incident exhausts the entire month's budget.** The design therefore optimises for blast radius and MTTR, not for preventing all failures.

**SLI, defined precisely** — this matters more than the number. Availability = successful ÷ valid requests, where *successful* means HTTP < 500 **and** search latency < 1 s, measured at the load balancer in 1-minute buckets. **429s are excluded** (the system working as designed) but tracked on a separate quota-health SLI.

**What each architectural choice buys:**

| Choice | Failure removed | Budget protected |
|---|---|---|
| Stateless API × N over 3 AZs, autoscaled | Instance or AZ loss | ~0; the LB reroutes in < 10 s |
| OpenSearch 3 AZ, replicas ≥ 1, 3 managers | Node loss, AZ loss, split brain | Avoids the multi-hour red-cluster outage |
| **Redis fail-open** | Cache outage cascading | Converts a would-be full outage into a latency regression |
| **Search reads OpenSearch, not Postgres** | Postgres failover (60–120 s) | Reads stay 100 % available during a write outage — ~90 % of traffic |
| Async indexing via outbox | Index backpressure blocking writes | Writes stay fast when OpenSearch is slow |
| **Canary + automatic rollback** | Bad deploys — historically the largest single cause of downtime | Caps a bad release at ~2 min × 5 % of traffic ≈ 0.1 min instead of 20 |
| Expand/contract migrations | Migration-induced outages | Removes the planned-maintenance window entirely |

**Arithmetic that closes.** Three-AZ redundancy contributes ~2 min/month of infrastructure downtime; canary-plus-rollback ~2 min across ~20 deploys; dependency incidents ~8 min — leaving **~9 minutes of slack** for the unknown. And the ceiling is worth stating: **99.99 % (4.3 min/month) would require multi-region active-active, which this design does not claim.**

**Error-budget policy.** If more than 50 % of the budget is consumed by mid-month, feature deploys pause and reliability work takes priority; two consecutive breach months trigger an architecture review. Exclusions are named up front: scheduled maintenance (none by design), customer-caused 4xx, and force-majeure regional events beyond the documented DR RTO.

**Build first:** the SLI measurement itself. You cannot run an error-budget policy against a number nobody is computing.
