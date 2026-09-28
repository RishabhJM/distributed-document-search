# Five-minute demo

Six steps. Each one proves a specific claim from the architecture document, and each has a line to say while it runs.

```powershell
Copy-Item .env.example .env
.\scripts\up.ps1 -Mode full -Seed
```

Wait for the readiness output. Cold start is 60–90 seconds; seeding 50k documents takes about 90 more.

> **TODO(candidate):** run this end to end once and correct anything below that does not match what you actually see. A demo script that has never been executed is a liability.

---

## 1. Search works, and is fast

Open http://localhost:3000 and search **`payroll runbook`**.

**Expect:** ranked results, matched terms highlighted, and a `tookMs` in the low tens of milliseconds. Search again and a **"served from cache"** pill appears with a lower `tookMs`.

> "Relevance is BM25 with field boosting — a title match is worth three times a body match. The second query is served from Redis, keyed by tenant and by a generation counter, so a write invalidates the whole tenant's search cache in one atomic operation rather than by scanning keys."

---

## 2. Tenant isolation — the headline

With the query still active, switch the tenant selector from **acme** to **globex**.

**Expect:** the result set changes completely. The header line reads *"N results for payroll runbook in **globex**"*.

Then try to read an acme document as globex. Copy a document id from the acme result list, switch to globex, and open `/documents/{id}`.

**Expect:** a 404 — not a 403.

> "Four independent layers enforce this: the tenant filter is injected centrally by a query factory that throws rather than emit an untenanted query; routing confines the query to one shard; the document `_id` is composite; and the mapping requires routing, so OpenSearch itself rejects an unrouted write.
>
> The 404 is deliberate. A 403 would confirm to the caller that the document exists, which is itself a disclosure."

Worth showing too — the header is authoritative:

```powershell
# 403 TENANT_MISMATCH: the ?tenant= parameter cannot override the header
curl.exe -s "http://localhost:8080/search?q=test&tenant=acme" -H "X-Tenant-ID: globex"

# 400 MISSING_TENANT: there is no default tenant, ever
curl.exe -s "http://localhost:8080/search?q=test"
```

> "Query parameters leak into access logs, browser history and Referer headers, so they are the wrong place for an identity claim. The parameter is accepted for spec compliance but must match the header."

---

## 3. Index a document and watch it appear

Go to **Index**, add a document with a distinctive title, and submit.

**Expect:** a success message noting it may take a moment to appear in search. Search for the distinctive term.

> "Reads by id are strongly consistent — the document is readable the instant the POST returns, because Postgres is the source of truth. Search is eventually consistent, bounded by the OpenSearch refresh interval, about a second locally.
>
> The write and the outbox row commit in the same transaction, so the intent to index is exactly as durable as the document. The index call happens after commit — inside the transaction it would hold a database connection across a network call, and a rollback after a successful index would leave a phantom document."

---

## 4. Graceful degradation — kill the cache

```powershell
docker compose stop redis
```

Watch the header dots. Refresh the page and search again.

**Expect:** the Redis dot turns amber, `/actuator/health` still returns **HTTP 200** with Redis reporting `DEGRADED`, and **search still works** — slower, and every response now reports `cached: false`.

> "Redis is a non-fatal dependency. It is excluded from the readiness group, so a cache outage never removes a working instance from the load balancer. The rate limiter fails open for the same reason: it exists to protect availability and must never be able to take down the service it protects.
>
> That asymmetry is deliberate. Tenant resolution fails *closed* — an isolation control may not degrade."

```powershell
docker compose start redis
```

---

## 5. Per-tenant rate limiting

```powershell
.\api\curl\requests.ps1 -Tenant acme
```

**Expect:** step 7 reports a number of throttled requests, and then shows globex being served normally in the same window.

> "The limiter is a Redis token bucket driven by an atomic Lua script — one round trip, no read-modify-write race across instances. The filter runs after tenant resolution, which is itself a security control: limiting an unvalidated tenant id would let an attacker mint unbounded Redis keys and exhaust the limiter's own memory.
>
> Note that the other tenant is unaffected. The noisy-neighbour claim is tested, not asserted."

---

## 6. Horizontal scale, and a self-healing index

```powershell
docker compose --profile full up -d --scale api=3
```

**Expect:** searches keep working. The application tier holds no state — the tenant context is per-request, the cache and the rate-limit buckets are in Redis.

Then show the index healing itself:

```powershell
docker compose stop opensearch
# Index a document through the UI. It succeeds: the POST does not fail.
docker compose start opensearch
# Wait a few seconds for the relay, then:
.\scripts\seed.ps1 -VerifyOnly
```

**Expect:** the write succeeded even with the search cluster down, and the parity check now reports Postgres and the index in agreement.

> "This is the outbox paying for itself. The document was durable; the index write failed; the outbox row stayed PENDING; and the relay reconciled it using `SELECT … FOR UPDATE SKIP LOCKED`, which is what makes it safe to run on every instance at once.
>
> Phase 2 replaces the relay with a Kafka producer. That is a one-property change — the service, controllers, entities and schema are untouched. Shipping the outbox table with no consumer on day one is exactly why."

---

## If you have another minute

```powershell
cd backend; .\mvnw verify
```

`TenantIsolationIT` is the test worth showing: eight cases, each closing a different leak vector, including the one most implementations miss — proving that a warm cache for one tenant cannot serve another.

`ArchitectureTest` is the other one. Five ArchUnit rules that turn the architecture document's claims into build failures, including a rule that fails the build if any call site uses the un-tenanted `findById`.
