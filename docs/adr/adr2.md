# ADR-0002: PostgreSQL as the source of truth

**Status:** Accepted

## Context

Document metadata, permissions, content, and tenant state require durable, long-term persistence. While search engines like OpenSearch provide fast indexing and full-text retrieval, using a search cluster as a primary database introduces significant operational and consistency risks.

We need a system of record that provides strong transactional guarantees, referential integrity, point-in-time recovery, and an atomic transactional outbox to prevent data divergence.

## Decision

Use **PostgreSQL 16** as the authoritative source of truth for all tenant definitions, documents, and indexing outbox events.

The OpenSearch cluster is treated as a **derived, fully rebuildable read-model**. Document reads by primary key (`GET /documents/{id}`) and soft-deletes are served directly from PostgreSQL; search queries (`GET /search`) are served from OpenSearch.

## Consequences

**Good.**
- **ACID Transactions**: Document insertion and outbox event creation commit in a single database transaction, structurally eliminating the dual-write divergence risk.
- **Strong Consistency for Document CRUD**: Callers get immediate read-your-writes guarantees on `GET /documents/{id}` without waiting for search engine segment refreshes.
- **Full Reconstructibility**: The entire search index can be completely recreated from PostgreSQL via background backfill pipelines if OpenSearch indices become corrupted or need mapping changes.
- **Enterprise Tooling**: Mature ecosystem for WAL archiving, point-in-time recovery (PITR), schema migrations (Flyway), and row-level security (RLS).

**Bad.**
- Dual-storage footprint: Data exists in both PostgreSQL tables and OpenSearch indices.
- Eventual consistency between PostgreSQL commits and OpenSearch searchability (tolerated at ~1s local, ~5s production).

## Alternatives considered

**OpenSearch as the sole datastore.**
- *Pros:* Simpler architecture with one fewer storage engine.
- *Cons:* Lucene-based search engines lack multi-entity ACID transactions, foreign keys, and rollbacks. In-place updates cause high segment tombstones and merge I/O overhead. Furthermore, re-indexing or changing mappings becomes extremely hazardous without a raw, canonical store to re-read from.

**DynamoDB / NoSQL Document Stores.**
- *Pros:* High write scalability.
- *Cons:* Complex multi-table transactions, vendor lock-in, and significantly more overhead to implement transactional outboxes with `SKIP LOCKED` compared to PostgreSQL.
