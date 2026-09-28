# ADR-0003: Shared index with tenant routing, not index-per-tenant

**Status:** Accepted

## Context

Multiple tenants share one search cluster. Their documents must be strictly
isolated, and no tenant may degrade another's latency. The natural instinct is to
give each tenant its own index.

## Decision

**One shared index**, with `tenantId` as a `keyword` field, a mandatory filter
clause on every query, and `routing = tenantId` on every index, delete and search
operation.

## Consequences

**Good.** Cluster cost is flat in tenant count: 3 primaries plus 3 replicas
whether there are 3 tenants or 3,000. Small tenants cost almost nothing, because
documents share segments rather than each tenant burning a whole shard. A mapping
change or reindex is one operation, not N. And routing means a tenant's query
touches **1 shard instead of all of them** — roughly 3× less CPU per query today,
with no coordinating-node merge, and the advantage *grows* with cluster size
(30 shards still means 1 shard per query). This is the single largest lever
behind the sub-500 ms p95 and 1000 rps targets.

**Bad.** Isolation is logical rather than physical, so it depends on the query
builder being correct. Per-tenant custom analyzers and per-tenant retention
policies become hard — a genuine cost of this choice, not a hand-wave. A single
enormous tenant can unbalance a shard.

**Mitigations.** Four independent enforcement layers, any one of which would
prevent a leak: the tenant term is injected centrally by `OpenSearchQueryFactory`,
which *throws* rather than emit an untenanted query; `_routing` is set on every
operation; the document `_id` is composite (`{tenant}:{uuid}`); and the mapping
declares `"_routing": {"required": true}`, so the engine itself rejects an
unrouted write. `TenantIsolationIT` is the executable proof. For whale tenants,
`routing_partition_size` is exposed as configuration, and the documented
escalation is to promote the tenant to a dedicated index behind the read alias —
a change confined to `IndexNaming`.

## Alternatives considered

**Index per tenant.** The intuitive answer, and it gives the strongest possible
isolation: a query simply cannot reach another tenant's index. Rejected because
it fails at exactly the scale this assignment targets. 1,000 tenants × 1 shard ×
1 replica is 2,000 shards; cluster-state update latency degrades sharply past
~10,000 shards, and each shard carries fixed heap and file-handle overhead
regardless of whether it holds 50 documents or 50 million. Shard explosion
killing the cluster master is a well-documented production failure, and the long
tail of small tenants makes it worse, not better.

**Cluster per tenant.** Complete isolation, and the right answer for a regulated
or data-residency-bound tenant. Rejected as a default on cost and operational
burden — it is reserved as the top tier of the escalation path.
