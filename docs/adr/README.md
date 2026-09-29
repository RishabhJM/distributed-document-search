# Architecture Decision Records (ADRs)

Architectural decisions documented in MADR format: Status / Context / Decision / Consequences / Alternatives considered.

| ADR | Decision Summary | Status | Document |
|---|---|---|---|
| **ADR-0001** | OpenSearch as the Search Engine (vs Postgres FTS, Elasticsearch) | Accepted | [adr-0001-opensearch-search-engine.md](adr-0001-opensearch-search-engine.md) |
| **ADR-0002** | PostgreSQL as the Source of Truth & Outbox (vs OpenSearch-only) | Accepted | [adr-0002-postgresql-source-of-truth.md](adr-0002-postgresql-source-of-truth.md) |
| **ADR-0003** | Shared Index with Tenant Shard Routing (vs Index-per-Tenant) | Accepted | [adr-0003-shared-index-tenant-routing.md](adr-0003-shared-index-tenant-routing.md) |
| **ADR-0004** | Redis for Both Caching and Lua Rate Limiting | Accepted | [adr-0004-redis-caching-and-rate-limiting.md](adr-0004-redis-caching-and-rate-limiting.md) |
| **ADR-0005** | Transactional Outbox Relay Now, Broker in Phase 2 | Accepted | [adr-0005-transactional-outbox-relay.md](adr-0005-transactional-outbox-relay.md) |
| **ADR-0006** | Next.js Route-Handler Proxy (vs Direct Client Fetch) | Accepted | [adr-0006-nextjs-route-handler-proxy.md](adr-0006-nextjs-route-handler-proxy.md) |
