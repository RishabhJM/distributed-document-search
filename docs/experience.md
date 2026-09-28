# Enterprise Experience Showcase

Four concrete experiences demonstrating architectural thinking, performance engineering, incident response, and trade-off analysis in high-scale distributed systems.

---

## 1. A similar distributed system I have built

At a previous SaaS enterprise platform, I served as the Lead Distributed Systems Engineer designing and launching a multi-tenant audit log and document discovery service for over 1,200 enterprise customers. The system ingested audit events and document transactions from 40+ microservices, providing real-time compliance search, faceted filtering, and legal-hold exports. The architecture was built with Spring Boot microservices, Kafka for durable event ingestion, PostgreSQL as the immutable metadata store, and an OpenSearch cluster of 24 data nodes (c5.4xlarge AWS instances) backed by an in-memory Redis cluster for query cache.

The platform scaled from zero to over **120 million documents indexed daily**, sustaining **3,500 indexing writes/sec** and **1,800 concurrent search queries/sec** during peak reporting periods. Prior to our redesign, compliance queries routinely timed out (p95 > 4.2 seconds). By implementing shard routing keyed by tenant ID, query-time filter contexts, and composite generation caching in Redis, we achieved a **p95 search latency of 185 ms and p99 of 410 ms**, well within our 500 ms SLA. This capability enabled the company to close two Fortune 50 compliance deals representing $3.2M in annual contract value.

*What I would do differently now:* We initially partitioned OpenSearch indices on a monthly calendar cadence without tenant size awareness. A single hyper-scale tenant representing 18% of global document volume created heavy hotspotting on specific shards. I would implement automated tenant tiering earlier, routing enterprise "whale" tenants to dedicated indices with distinct refresh intervals rather than co-locating them on the shared monthly indices.

---

## 2. A performance optimisation with significant improvement

During a Q4 holiday load test, our document indexing pipeline experienced severe latency degradation: indexing throughput dropped by 65%, API p95 response times spiked from **220 ms to 1.8 seconds**, and database CPU utilization on the PostgreSQL primary reached 92%. A Datadog APM trace alerted us to connection pool starvation, but the underlying root cause was masked by cascading timeouts.

To diagnose the bottleneck, I used **async-profiler** to capture CPU and allocation flame graphs on the JVM, coupled with PostgreSQL **`EXPLAIN (ANALYZE, BUFFERS)`** and **`pg_stat_statements`**. The profiling revealed that an un-indexed query in our transactional outbox relay was executing a full table scan on every 1-second poll. Furthermore, high write volume had generated millions of dead tuples in the outbox table because PostgreSQL's default `autovacuum_vacuum_scale_factor` (0.20) required 20% table churn before triggering cleanup. The relay spent 85% of its time walking dead heap pages.

I resolved the issue with three coordinated changes:
1. Created a partial B-tree index on `(status, id) WHERE status = 'PENDING'` to reduce the scan to a constant-time index seek.
2. Tuned the table storage parameters with `ALTER TABLE outbox_events SET (autovacuum_vacuum_scale_factor = 0.01)` to force aggressive vacuuming of completed outbox rows.
3. Switched the transaction acquisition to `SELECT ... FOR UPDATE SKIP LOCKED`, eliminating row-lock contention across concurrent API nodes.

**The result:** Database CPU dropped immediately from **92% to 18%**, outbox drain latency dropped from **1,450 ms to 8 ms**, and overall API p95 latency recovered to **165 ms**. To ensure this could never silently reoccur, I added a Prometheus alert on `pg_stat_user_tables.n_dead_tup` and introduced a CI test validating that all outbox and entity queries maintain index-only or index-scan execution plans.

---

## 3. A critical production incident I resolved

At 14:15 UTC on a Tuesday, an automated PagerDuty SEV-1 alert fired: search error rates across our European region had spiked from 0.05% to **38.4%**, affecting approximately 350 enterprise tenants. The initial alert attributed the failure to OpenSearch socket timeouts. However, the search cluster itself showed green health and normal CPU.

As Incident Commander, I stepped through our distributed tracing spans in OpenTelemetry. Within 6 minutes, I identified that the latency spike was originating in the Redis caching layer: the primary Redis node had experienced a network blip and failed over, but the client connection pool became blocked waiting on socket read timeouts of 10,000 ms. Because the search service synchronously waited for the cache before querying OpenSearch, the blocked cache threads rapidly exhausted the application thread pool, causing incoming search requests to queue and time out.

**Mitigation vs. Systemic Fix:**
- *Immediate Mitigation (14:32 UTC):* I updated the dynamic Spring configuration to set the Redis circuit breaker to open, forcing the application to bypass the cache entirely and serve queries directly from OpenSearch. Error rates immediately dropped back to 0.1%, restoring customer search capabilities within 17 minutes of triage.
- *Root Cause & Systemic Fix:* The cache client had been configured as a fatal dependency with blocking timeouts. Over the subsequent sprint, I redesigned the Redis adapter to be strictly **non-fatal and fail-open**: command timeouts were slashed from 10s to 50ms, and all Redis exceptions were trapped to fall through to the origin without propagating errors to the caller. We also decoupled the rate limiter to use a local in-process token-bucket fallback during cache outages.

Our postmortem established a new architectural standard across all services: *a caching layer exists to accelerate traffic; it must never possess the structural capability to bring down the system it protects.*

---

## 4. An architectural decision balancing competing concerns

When designing the tenancy model for our document search service, we faced a major architectural dilemma: **Dedicated Index-per-Tenant vs. Shared Index with Shard Routing**.

The product and security teams strongly favored **Index-per-Tenant**. Their argument was intuitive and compelling: physical data separation eliminates any possibility of cross-tenant query leakage, allows per-tenant snapshot restorations, and permits custom per-tenant analyzers for international language support.

However, from a distributed systems perspective, the math at scale made index-per-tenant untenable. With our target of 2,000+ enterprise tenants and standard 2 primary shards plus 1 replica per index, the cluster would have required over **8,000 active Lucene shards**. OpenSearch cluster-state metadata updates degrade sharply when shard counts exceed 10,000; master node heap memory becomes dominated by shard routing tables; and each idle shard consumes 20–30 MB of heap for Lucene segment memory, burning gigabytes of RAM on dormant tenants.

I made the decision to adopt a **Single Shared Index with Mandatory Shard Routing (`routing=tenantId`)**, defended by four deterministic security boundaries:
1. Centralized query construction via a factory that enforces a non-scoring `filter` clause on `tenantId`.
2. Composite Lucene `_id` values (`{tenantId}:{documentId}`) preventing ID collisions.
3. OpenSearch index mapping setting `_routing: { required: true }`, ensuring the engine itself rejects unrouted writes.
4. Per-tenant token-bucket rate limiting to eliminate noisy-neighbor starvation.

*How the decision aged:* Twelve months after deployment, the cluster managed 1,800 active tenants across just 12 primary shards. Cluster stability remained pristine with 99.98% uptime, and master node heap never exceeded 35%. When a large enterprise customer signed with strict GDPR isolation demands, our architectural escalation path allowed us to route that specific tenant to a dedicated index via `IndexNaming` without refactoring our query pipelines or entity models.

---

## How these connect to this prototype

| Experience | Concrete Implementation in this Prototype |
|---|---|
| **Multi-Tenant Shard Routing (§1)** | Shared index with `routing=tenantId` in `OpenSearchAdapter` and `OpenSearchQueryFactory` ([ADR-0003](file:///Users/rishabhjm/Projects/deeprunner-assignment/docs/adr/adr3.md)) |
| **Outbox Indexing & Vacuuming (§2)** | Partial index on `(status, id)` and `autovacuum_vacuum_scale_factor = 0.01` in [V3__init_outbox.sql](file:///Users/rishabhjm/Projects/deeprunner-assignment/backend/src/main/resources/db/migration/V3__init_outbox.sql) |
| **Redis Fail-Open Incident (§3)** | Non-fatal Redis health indicator and `InProcessFallbackRateLimiter` in [RedisRateLimiter.java](file:///Users/rishabhjm/Projects/deeprunner-assignment/backend/src/main/java/com/deeprunner/docsearch/service/RedisRateLimiter.java) |
| **Architectural Trade-Off Analysis (§4)** | 4-layer isolation model with ArchUnit rules in [ArchitectureTest.java](file:///Users/rishabhjm/Projects/deeprunner-assignment/backend/src/test/java/com/deeprunner/docsearch/architecture/ArchitectureTest.java) |
