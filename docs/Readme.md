# Architecture Decision Records

One page per decision, MADR format: Status / Context / Decision / Consequences /
Alternatives considered. These exist so ARCHITECTURE.md
can stay inside its 2-3 page limit without losing the reasoning.

| ADR | Decision | Status |
|---|---|---|
| 0001 | OpenSearch as the search engine | Accepted |
| 0002 | PostgreSQL as the source of truth | Accepted |
| 0003 | Shared index with tenant routing, not index-per-tenant | Accepted |
| 0004 | Redis for both caching and rate limiting | Accepted |
| 0005 | Transactional outbox now, broker in Phase 2 | Accepted |
| 0006 | Next.js route-handler proxy, not client-direct fetch | Accepted |
