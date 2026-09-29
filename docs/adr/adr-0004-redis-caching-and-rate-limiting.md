# ADR-0004: Redis for both caching and rate limiting

**Status:** Accepted

## Context

Two of the required features need shared state across a horizontally scaled,
stateless application tier: a cache to hit the latency target, and per-tenant
rate limiting to stop a noisy neighbour.

## Decision

Use **Redis 7.4** for both. Caching goes through Spring's `CacheManager` for
documents and a direct `StringRedisTemplate` for search results; rate limiting
uses an atomic Lua token-bucket script.

## Consequences

**Good.** One dependency serves both requirements. The token bucket is atomic in
a single round trip, so there is no read-modify-write race across instances,
which a naive `INCR`-based limiter would have. TTLs are native, so cache expiry
and idle-bucket cleanup are free. `allkeys-lru` with a memory cap means Redis
degrades by evicting rather than by failing.

**Bad.** A third datastore. More importantly, it creates a coupling worth naming:
if Redis is the rate limiter *and* the cache, one outage removes both.

**The fail-open / fail-closed asymmetry.** This is the load-bearing decision
here, and it is deliberate:

| Control | On Redis failure | Why |
|---|---|---|
| Cache | Fall through to origin | Latency cost only; correctness is unaffected |
| Rate limiter | **Fail open** | The limiter exists to protect availability and must never be able to take down the service it protects |
| Tenant resolution | **Fails closed** (does not depend on Redis for the decision) | An isolation control may not degrade, ever |

The residual risk, that failing open removes abuse protection entirely, is
covered by `InProcessFallbackRateLimiter`: a coarse per-instance bucket sized at
`globalLimit / instanceCount * 1.2` that engages only during the degraded window.

Redis is therefore a **non-fatal** dependency. Its health indicator reports
`DEGRADED` mapped to HTTP 200, and it is excluded from the readiness group, so a
cache outage never removes a working instance from the load balancer.

## Alternatives considered

**In-process cache only (Caffeine).** Faster, no network hop, no extra service.
Rejected as the sole mechanism because rate limiting cannot work per-instance: N
instances would each grant a tenant its full quota, so the effective limit would
be N times the intended one. Caffeine remains the right L1 in front of Redis and
is listed as deferred work.

**A separate store for each concern.** Cleaner separation of failure domains, and
arguably correct at large scale. Rejected here as unjustified operational cost
for a prototype, with the coupling documented rather than hidden.
