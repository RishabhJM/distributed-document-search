# Assumptions

The assignment asks for assumptions to be documented clearly. Each one below carries its impact if wrong, because an assumption without a consequence is just a disclaimer.

---

### 1. The demo runs a 50k-document corpus, not 10 million

**This is the most important entry in this file.** The 10M-document and 1000-searches/sec targets are argued analytically — shard math, routing behaviour, cache hit rates, the latency budget in PRODUCTION-READINESS.md §1 and §5 — and supported by a scaled-down local run. They are **not demonstrated at full scale**, because 10M documents will not fit on a laptop alongside the rest of the stack.

*If wrong:* the shard sizing and node counts are the part most likely to need revision under real data. The architecture (routing, shared index, source-of-truth split) does not change; the capacity numbers would.

---

### 2. `X-Tenant-ID` is client-supplied and therefore forgeable in this prototype

There is no authentication layer. Anyone can send any tenant header and the service will honour it after validating that the tenant exists and is active.

*Why it is acceptable here:* the assignment asks for "basic multi-tenant support (can be header-based or path-based)" and 3–4 hours of effort. Hand-rolling a fake JWT issuer would consume the budget without demonstrating anything the real design does not already state.

*How it is remediated:* the header is stripped at the gateway and re-injected from a signed JWT claim, or `TenantResolutionFilter` becomes a Spring Security `AuthenticationConverter`. Everything downstream reads only `TenantContext`, so the swap touches exactly one class. See PRODUCTION-READINESS.md §3.

*If wrong (i.e. if this shipped as-is):* complete cross-tenant compromise. This is the single largest gap between the prototype and a production system, which is why it is stated here, in the README, and in the architecture document rather than buried.

---

### 3. Documents are text, and at most ~1 MB each

No binary extraction, no OCR, no PDF or Office parsing. `content` is validated at 1 MB.

*If wrong:* an extraction pipeline goes in front of indexing — most likely as a separate service consuming the same outbox topic, which the Phase 2 broker design already accommodates.

---

### 4. Roughly 50–500 tenants, with a power-law size distribution

A long tail of small tenants and a handful of large ones. The seed data deliberately mirrors this (60 % / 30 % / 10 %).

*If wrong, in either direction:* at ~10 tenants, index-per-tenant becomes viable and would give better isolation. At ~50,000 tenants, the per-tenant metric tags and the tenant cache both need rework, and the routing partition strategy needs revisiting.

---

### 5. Read-heavy: roughly 20 : 1 reads to writes

This justifies caching search results aggressively, accepting that a write flushes a tenant's search cache, and putting replicas to work serving reads.

*If wrong:* a write-heavy tenant continuously invalidates its own search cache via the generation counter, so the cache stops paying for itself. The mitigation would be a shorter generation-bump debounce or per-query-shape invalidation.

---

### 6. Lexical relevance is what is wanted, not semantic similarity

BM25 with field boosting, stemming and highlighting. No embeddings, no vector search, no hybrid retrieval.

*If wrong:* OpenSearch supports k-NN, so this is an additive change — a vector field in the mapping and a hybrid query — rather than a re-platform. It is listed as deferred work.

---

### 7. A few seconds of search staleness is acceptable to the business

Document reads by id are strongly consistent; search is eventually consistent within ~1 s locally and ~5–6 s in production. `POST` returns `indexingState` so a caller can observe this rather than guess.

*If wrong:* `refresh-policy=WAIT_FOR` makes a write synchronously searchable at the cost of up to a full refresh interval of write latency. It is already implemented and is what the integration tests use.

---

### 8. Single region

No multi-region replication, no data-residency routing.

*If wrong:* this caps availability at the 99.95 % claimed. 99.99 % needs multi-region active-active, which is explicitly not claimed.

---

### 9. "10M+ documents" means per deployment, not per tenant

The shard math in §1 is computed on that basis.

*If wrong* and a single tenant holds 10M+ documents, that tenant crosses the dedicated-index threshold documented in ADR-0003.

---

### 10. Deletion is soft in the prototype

`deleted_at` is set and the document is removed from the search index. The row remains in Postgres.

*If wrong:* GDPR erasure requires a hard delete across Postgres, OpenSearch, caches and backups, with a documented purge window. The design is in PRODUCTION-READINESS.md §3; the implementation is deferred.

---

### 11. Local development runs a single OpenSearch node

Hence `number_of_replicas: 0` locally, and the health indicator treating `yellow` as UP. The production design is 3 shards with 1 replica.

*If wrong:* nothing in production. This is purely a local-environment accommodation, and it is why the same mapping resource is parameterised rather than duplicated.

---

### 12. The reviewer has Docker Desktop with at least 6 GB allocated

OpenSearch alone wants ~1.5 GB. Below that the container is OOM-killed and the stack appears broken for reasons unrelated to the code.

*If wrong:* `scripts/preflight.ps1` checks for this and says so before anything starts, rather than letting it fail obscurely.
