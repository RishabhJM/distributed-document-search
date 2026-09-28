# AI Tool Usage

The assignment explicitly encourages AI tooling and asks for a note on how it was used. This is that note.

## Tools

**Antigravity CLI (Gemini 3.8 Flash High)**, run in the terminal against this repository, for requirements extraction from the assignment PDF, architecture verification, implementation, and documentation.

## What it was used for, honestly

**Design exploration.** The strongest use was pressure-testing decisions rather than generating code. The index-per-tenant versus shared-index-with-routing comparison, the shard arithmetic for 1B documents, and the cache-invalidation options (TTL versus tag-based versus generation counter) were all worked through as trade-offs with explicit rejected alternatives. That analysis is in adr/ and ARCHITECTURE.md, and it is the part of this submission AI contributed most usefully to.

**Boilerplate at speed.** `docker-compose.yml` with healthchecks and profiles, the Flyway migrations, DTO records, the Postman collection, and the repetitive parts of the Spring configuration. Roughly 70% of the line count, and close to 0% of the decisions.

**Documentation structure.** The section skeletons, the diagram layouts, and holding ARCHITECTURE.md to its 2–3 page limit by pushing depth into satellite documents.

## What it got wrong, and I had to correct

Naming these matters more than the successes — they are where reviewing the output was load-bearing rather than optional.

1. **Embedded NUL bytes in a Java source file.** Writing `"\u0000"` as a content separator in `DocumentService` produced actual 0x00 bytes rather than the Java escape sequence, which would have failed compilation with an obscure error. Caught by scanning the sources for non-ASCII bytes; fixed by writing the literal escape.

2. **A fragile ArchUnit predicate.** The first version of the "no untenanted `findById`" rule composed `HasOwner.Predicates.With.owner(...)` with a described predicate — API gymnastics that would not have compiled. Replaced with the plain `callMethod(DocumentRepository.class, "findById", Object.class)` form, which is both correct and readable.

3. **ArchUnit layered architecture violations.** The initial web filter implementation directly referenced entity methods (`TenantEntity.isActive()`), which violated layered isolation. This prompted the introduction of `TenantService` and `TenantDto` as clean domain boundaries.

4. **Hardcoded credentials in `application-local.yml`.** The generated local profile inlined the Postgres username and password. The repository's GitGuardian pre-commit hook rejected the commit, correctly. Rather than bypassing it, credentials were removed from all `application-*.yml` files entirely and moved to environment binding via `.env` plus `scripts/run-local.ps1`. **This is the correction I would highlight**: the tool produced working code that was a security anti-pattern, and the fix improved the design rather than merely silencing the warning.

5. **Package and build alignment.** Resolved Jackson JSR-310 datetime module registration for raw test object mappers and added ByteBuddy experimental flags for forward compatibility with newer JDK runtimes.

## What was deliberately not delegated

- **EXPERIENCE.md.** Authored from first-hand enterprise engineering experience. AI produced the section prompts and scaffolding. Inventing professional history would be fabrication, and it is the one part of this submission where that distinction is absolute.
- **The tenant-isolation enforcement model.** The four-layer design, the decision to fail closed on tenancy while failing open on rate limiting, and the choice to return 404 rather than 403 on a cross-tenant read are judgement calls about threat model, made deliberately and defended in the documents.
- **The shard math and capacity figures**, which were reasoned from stated assumptions rather than accepted as generated numbers.

## Verification

- Every source file scanned for NUL and non-ASCII bytes after generation.
- `docker compose config` run to validate the compose topology and port bindings.
- `mvn test` executed and verified green across 12 unit, architecture, and integration tests (`ArchitectureTest`, `TenantIsolationIT`, `RateLimiterTest`, `TenantResolutionFilterTest`, `OpenSearchQueryFactoryTest`).
- `npm run build` executed and verified green for the Next.js 15 front-end standalone production package.

## Assessment

AI made this a 4-hour exercise instead of a 12-hour one, almost entirely by removing typing rather than by removing thinking. The decisions that carry the submission — the isolation model, the consistency contract, the fail-open/fail-closed asymmetry, what to defer — came from directing the tool and rejecting roughly a third of what it first produced. The most valuable habit was treating generated code as a competent draft from someone who has not read the requirements carefully, which is approximately what it is.
