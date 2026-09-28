# How to Run and Test the Distributed Document Search Service

This guide provides step-by-step instructions to run, seed, test, and evaluate the Distributed Multi-Tenant Document Search Service. For an exhaustive architectural walkthrough with sequence diagrams for all user and system flows, refer to [**`docs/ARCHITECTURE_REVIEW.md`**](docs/ARCHITECTURE_REVIEW.md).

---

## 1. Prerequisites & Health Check

Ensure Docker Desktop is installed and running on your machine:
- **macOS / Linux:** Start Docker Desktop from Applications.
- **Windows:** Start Docker Desktop from the Start Menu.

Run the preflight check script to verify port availability (`5432`, `9200`, `6379`, `8080`, `3000`) and Docker environment:

```bash
# macOS / Linux:
./scripts/preflight.sh

# Windows PowerShell:
.\scripts\preflight.ps1
```

---

## 2. Running the System

You can run the system using either **Option A (Full Docker - Recommended for evaluation)** or **Option B (Hybrid Local Development)**.

### Option A: Complete Docker Compose (Recommended)

One command starts all 5 containers (PostgreSQL 16, OpenSearch 2.18, Redis 7.4, Spring Boot API, Next.js Web UI), waits for health checks, and seeds initial sample documents:

```bash
# macOS / Linux:
./scripts/up.sh -Mode full -Seed

# Windows PowerShell:
.\scripts\up.ps1 -Mode full -Seed
```

*Alternatively, using standard Docker Compose directly:*
```bash
cp .env.example .env
docker compose --profile full up -d --build
# Once containers are healthy (~45-60s), seed sample data:
./scripts/seed.sh
```

#### Access Points
- **Web UI:** [http://localhost:3000](http://localhost:3000)
- **API Health & Dependency Status:** [http://localhost:8080/health](http://localhost:8080/health)
- **Actuator Prometheus Metrics:** [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

---

### Option B: Hybrid Local Development (For Live Code Debugging)

Run the stateful data infrastructure in Docker, while running the application backend and frontend directly on your local host:

#### Step 1: Start Datastores
```bash
docker compose up -d postgres opensearch redis
```

#### Step 2: Start Spring Boot Backend
```bash
cd backend
mvn spring-boot:run
```
*(Backend runs on `http://localhost:8080`)*

#### Step 3: Start Next.js Frontend (in a new terminal)
```bash
cd frontend
npm install
npm run dev
```
*(Frontend runs on `http://localhost:3000`)*

#### Step 4: Seed Sample Data
```bash
./scripts/seed.sh
```

---

## 3. How to Run Automated Tests

### A. Backend Unit, Architecture & Integration Tests
Runs all 17 automated tests, including ArchUnit architecture enforcement rules and multi-tenant data leakage tests:

```bash
cd backend
mvn test
```

#### Key Tests Included:
1. `ArchitectureTest`: Verifies ArchUnit rules (enforces no callers invoke un-tenanted `findById`, and validates clean layer boundaries).
2. `TenantIsolationIT`: Verifies 5 cross-tenant data leakage prevention scenarios (proving tenant cross-reads return HTTP 404 and cache keys never collide).
3. `TenantResolutionFilterTest`: Verifies fail-closed security gates (HTTP 400 on missing/malformed tenant, HTTP 403 on tenant mismatch or unknown tenant).
4. `OpenSearchQueryFactoryTest`: Verifies that queries without a `tenantId` throw an exception, and asserts BM25 boosting and highlight configuration.
5. `RateLimiterTest`: Verifies token bucket replenishment, bursting, and tenant quota isolation.

### B. Automated End-to-End Smoke Verification
Runs an automated suite of live HTTP requests against the running system to verify end-to-end functionality:

```bash
# macOS / Linux:
./scripts/verify.sh

# Windows PowerShell:
.\scripts\verify.ps1
```

*Verifies:*
1. Service health status (`UP` / `DEGRADED`).
2. Transactional write and outbox event creation.
3. Strongly consistent read-your-writes from PostgreSQL by document ID.
4. Tenant isolation (cross-tenant read returns HTTP 404).
5. Mismatched query parameter protection (returns HTTP 403).
6. Missing tenant header rejection (fails closed with HTTP 400).

---

## 4. Five-Minute Interactive Demo Walkthrough

Follow these 6 steps to demonstrate the core architecture claims:

### Step 1: Search & Cache Speed
1. Open [http://localhost:3000](http://localhost:3000).
2. With active tenant set to **`acme`**, search **`payroll runbook`**.
3. **Observe:**
   - Results show BM25 relevance score and highlighted terms in `<em>...</em>`.
   - Latency badge shows low tens of milliseconds.
4. Click Search or re-enter the query.
5. **Observe:**
   - The green **"Served from cache (Redis L2)"** badge appears.
   - Latency drops to single-digit milliseconds.

### Step 2: Strict Tenant Isolation
1. With the search results displayed, switch the tenant selector in the top-right from **`acme`** to **`globex`**.
2. **Observe:**
   - The result set changes completely to Globex-owned documents.
3. Copy any document ID from the Acme result list, switch tenant to Globex, and attempt to fetch it:
   ```bash
   curl -i http://localhost:8080/documents/<ACME_DOC_ID> -H "X-Tenant-ID: globex"
   ```
4. **Observe:**
   - Returns **HTTP 404 Not Found** (not a 403). Returning 404 prevents disclosing the existence of another tenant's document.

### Step 3: Read-Your-Writes & Eventual Consistency
1. In the Web UI, click the **Index Document** tab.
2. Enter a title (e.g. `Q4 Strategic Initiatives`), content, author, and tags, then click **Index Document**.
3. **Observe:**
   - Instant response with `indexingState: INDEXED` (or `PENDING`).
   - The document is immediately readable by its UUID from PostgreSQL (strongly consistent).
   - The document becomes searchable in OpenSearch within ~1 second (bounded by refresh interval).

### Step 4: Graceful Degradation (Stop Redis)
Demonstrate that Redis is a non-fatal dependency:

```bash
docker compose stop redis
```

1. Return to the Web UI and refresh the page.
2. **Observe:**
   - In the header, the Redis status dot turns amber.
   - Click the **Topology & Diagnostics** tab: system status is **DEGRADED (HTTP 200)**.
   - Execute a search: search **still works** by falling back to OpenSearch directly, and all responses report `cached: false`.
3. Restart Redis:
   ```bash
   docker compose start redis
   ```

### Step 5: Per-Tenant Token-Bucket Rate Limiting
Run the interactive curl demo script to burst requests and trigger the rate limiter:

```bash
# macOS / Linux:
./api/curl/requests.sh acme

# Windows PowerShell:
.\api\curl\requests.ps1 -Tenant acme
```

**Observe:**
- Requests 1 to 50 succeed with standard rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`).
- Subsequent burst requests return **HTTP 429 Too Many Requests** with RFC 7807 problem details and a `Retry-After: 1` header.
- A concurrent query for tenant `globex` is unaffected, proving noisy-neighbor isolation.

### Step 6: Self-Healing Search Index (Outbox Relay)
Simulate an OpenSearch cluster outage while continuing to write documents:

```bash
# 1. Stop OpenSearch
docker compose stop opensearch

# 2. Index a new document while OpenSearch is down:
curl -X POST http://localhost:8080/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: acme" \
  -d '{"title":"Emergency Failover Plan","content":"Written while search cluster was offline","tags":["failover"]}'
```

**Observe:**
- The write **succeeds**! The client receives `201 Created` with `indexingState: PENDING`. The document and outbox event are durably stored in PostgreSQL.
- Now restart OpenSearch:
  ```bash
  docker compose start opensearch
  ```
- Wait 3-5 seconds for the scheduled relay (`SELECT ... FOR UPDATE SKIP LOCKED`), then verify parity:
  ```bash
  ./scripts/seed.sh -VerifyOnly
  ```
- **Observe:** The outbox relay automatically detected the pending event and indexed it into OpenSearch.

---

## 5. Teardown & Reset

To stop all services and preserve data volumes:
```bash
docker compose --profile full down
```

To stop all services and **reset all database volumes** for a clean run:
```bash
docker compose --profile full down -v
```
