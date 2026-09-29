# ADR-0005: Transactional outbox now, message broker in Phase 2

**Status:** Accepted

## Context

The deliverables ask for message-queue usage for asynchronous operations, and a
broker is genuinely the right answer at scale. But adding Kafka to a 3-4 hour
prototype spends a large share of the budget on infrastructure rather than on the
thing being assessed, and an under-configured broker demonstrates less than a
well-reasoned absence of one.

The real problem to solve is not "use a queue". It is the **dual-write problem**:
writing to Postgres and then to OpenSearch has no transaction spanning both, so a
failure between them diverges the stores permanently and silently, with no
mechanism to detect or heal it.

## Decision

Ship the **transactional outbox pattern in Phase 1**, drained by an in-process
`@Scheduled` relay using `SELECT ... FOR UPDATE SKIP LOCKED`. Defer the broker to
Phase 2, behind an `IndexingGateway` interface, with the `outbox_events` table
present from day one.

## Consequences

**Good.** The dual-write problem is solved *now* rather than deferred along with
the broker: the outbox row commits atomically with the document, so the intent to
index is exactly as durable as the document itself. `SKIP LOCKED` makes the relay
safe to run on every instance simultaneously, since each claims a disjoint batch.
The pipeline is genuinely self-healing: stop OpenSearch, write documents, restart
it, and the relay reconciles the index without manual intervention. Zero extra
infrastructure, so the whole stack still starts with one command.

Crucially, **Phase 2 is additive rather than a rewrite**. Setting
`docsearch.indexing.mode=OUTBOX` swaps the gateway implementation; the service,
controllers, entities and schema are untouched. The `outbox_events` table ships
with no consumer in Phase 1 and looks like dead code for a day. That is the
entire point.

**Bad.** A polling relay saturates around 2k documents/sec and adds up to one
poll interval of latency. It gives at-least-once delivery, so the index write
must be idempotent, which it is: indexing by a deterministic `_id` is naturally
so. The outbox table accumulates dead tuples, which is why
`autovacuum_vacuum_scale_factor = 0.01` is set on that table specifically.

**The honest gap.** Rows that exhaust five attempts are marked `DEAD` and need
operator intervention. That state is alerted on rather than silently swallowed.

## Alternatives considered

**Kafka in Phase 1.** The right end state, and the documented Phase 2 design:
topic `doc.index.v1`, keyed by `tenantId` for per-tenant ordering, 12 partitions,
a consumer group of N indexers, DLQ `doc.index.dlq`. Rejected *for now* purely on
time budget and on the reviewer's ability to run the stack, not on merit.

**Synchronous indexing with no outbox.** The simplest possible thing. Rejected
because it is precisely the dual-write anti-pattern, with no way to detect or
heal divergence once it happens.

**Debezium or CDC from the Postgres WAL.** Elegant, and it removes the outbox
table entirely. Rejected as considerably more infrastructure than a broker for a
prototype, but it is the natural evolution once a broker already exists.
