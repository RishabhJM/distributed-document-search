# ADR-0006: Next.js route-handler proxy, not client-direct fetch

**Status:** Accepted

## Context

The front-end user interface must communicate with the Spring Boot search service while maintaining tenant isolation and network boundary safety.

Allowing browser clients to make direct fetch calls to the backend API introduces two significant risks:
1. The backend URL and internal networking topology are exposed directly to the public web browser.
2. In multi-tenant environments, relying on client-side JavaScript to supply authoritative headers like `X-Tenant-ID` creates spoofing vulnerabilities and complicates CORS policies.

## Decision

Implement a server-side **Next.js route-handler proxy (`/api/[...path]`)**.

Browser requests are issued to the same-origin Next.js server (`/api/...`). The server-side route handler:
1. Validates the request path against an explicit allow-list (`/documents`, `/search`, `/health`, `/actuator`).
2. Extracts the tenant identity from an `httpOnly` cookie or secure session and injects the authoritative `X-Tenant-ID` header.
3. Forwards the request over the internal network to the Spring Boot API (`http://api:8080`).
4. Relays the response, headers, and RFC 7807 problem details back to the client.

## Consequences

**Good.**
- **Same-Origin Security**: The browser communicates strictly with its origin; no CORS pre-flight requests are required.
- **Tenant Header Integrity**: Client-side JavaScript cannot forge arbitrary tenant headers; tenancy is bound on the server.
- **Network Isolation**: The backend Spring Boot service, PostgreSQL, OpenSearch, and Redis instances remain inside a private Docker bridge network or VPC, completely unexposed to the public internet.
- **Sanitized Proxy Routing**: Paths outside the approved prefix list are rejected with HTTP 403 before touching backend infrastructure.

**Bad.**
- An extra network hop through the Next.js Node.js server.
- Increased CPU and memory utilization on the front-end rendering tier.

## Alternatives considered

**Direct Client Fetch with CORS.**
- *Pros:* Simpler front-end codebase without route handler middleware.
- *Cons:* Exposes API gateway endpoints publicly, requires maintaining broad CORS access rules, and forces tenant credentials to reside in client-accessible browser storage (e.g. LocalStorage), violating enterprise security standards.
