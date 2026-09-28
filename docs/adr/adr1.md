# ADR-0001: OpenSearch as the search engine

**Status:** Accepted

## Context

The service must handle 10M+ documents across multiple tenants, support full-text search with BM25 relevance ranking, match term highlighting, and deliver results in under 500ms for 95th percentile queries under 1,000+ concurrent searches per second.

The search layer must support horizontal scaling, shard routing, and index lifecycle management without vendor lock-in or restrictive licensing.

## Decision

Adopt **OpenSearch 2.18** as the derived full-text search engine.

Search queries execute against a shared index (`documents-live` alias) using shard routing (`routing=tenantId`), composite `_id` values (`{tenantId}:{documentId}`), and strict query-level tenant filters.

## Consequences

**Good.**
- **Sub-100ms relevance queries**: Native inverted index with BM25 scoring, field boosting (`title^3`, `tags^2`, `author^1.5`, `content^1`), and phrase matching.
- **Tenant shard routing**: Setting `routing=tenantId` directs queries to exactly 1 shard instead of fanning out across all cluster shards, drastically reducing coordinating node overhead.
- **Native snippet highlighting**: Highlighting with custom HTML tags (`<em>...</em>`) without retrieving raw document contents in `_source`.
- **Open-source license**: Apache-2.0 license guarantees freedom from restrictive commercial licensing, cloud vendor lock-in, and unpredictable subscription pricing.

**Bad.**
- An additional distributed stateful service to operate and monitor.
- Search queries are eventually consistent rather than immediately visible (bounded by `refresh_interval`).
- Shard capacity and heap memory must be monitored carefully to prevent cluster-state bottlenecks.

## Alternatives considered

**PostgreSQL Full-Text Search (`tsvector` + GIN indexes).**
- *Pros:* Eliminates an entire external dependency; gives ACID guarantees on search data.
- *Cons:* PostgreSQL FTS does not scale horizontally across multiple worker nodes for high search throughput. At 1,000 searches/sec and 10M documents, GIN index maintenance and vacuuming cause severe I/O degradation and table bloat. Highlighting and BM25 relevance tuning are significantly weaker than dedicated Lucene-based search engines.

**Elasticsearch (Elastic NV).**
- *Pros:* Feature parity with OpenSearch.
- *Cons:* Server Side Public License (SSPL) / Elastic License introduces legal risk for multi-tenant and managed cloud offerings. OpenSearch's Apache 2.0 license provides an unencumbered enterprise foundation.
