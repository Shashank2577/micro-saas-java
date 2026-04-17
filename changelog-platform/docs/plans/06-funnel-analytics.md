# 06 — Funnel Analytics (Admin Define + Compute + Read)

## Overview

This plan adds a first-class **funnel analytics** surface to the platform: admins define named multi-step funnels (ordered event matchers, each optionally constrained by a `properties` JSON predicate), the server computes step counts, step-to-step conversion rates, overall conversion, dropoff points, and the bottleneck step over a configurable date range, and the results are exposed for read along with their historical time-series.

The existing code conflates "funnel definition" and "funnel result" into a single row of `funnel_analytics` — every recompute duplicates the steps JSON, there is no stable identity for a funnel, and there is no admin CRUD. The current HTTP surface (`AnalyticsController.java:63` → `POST /api/v1/analytics/funnels/{funnelName}/calculate` with hard-coded `StandardFunnels`) is a tenant-side helper, not an admin definition API, and it only matches on `event_name` (no properties predicate), and it counts events instead of distinct users per step (see `FunnelAnalyticsService.java:103`).

We fix this by introducing a new `funnels` definition table (V5 migration), refactoring `FunnelAnalyticsService` to compute per-user step traces against `analytics_events`, and adding a dedicated admin controller under `/api/v1/admin/funnels/**`. We keep `funnel_analytics` as the result/history table but add a `funnel_id` foreign key so historical rows remain addressable.

## Goals & Non-goals

**Goals**
- Admin CRUD for funnel definitions (`POST/GET/GET-one/PUT/DELETE /api/v1/admin/funnels`).
- Deterministic compute pipeline: definition + `[from, to]` → per-user step trace → `step_counts` → `conversion_rates` → `overall_conversion` → `bottleneck_step` → `dropoff_points`.
- Scheduled daily recompute for every active funnel for the prior rolling window, and an on-demand recompute endpoint.
- Read API returning the latest result plus a historical time-series, filterable by date range.
- Performance: the per-tenant, per-event-name, time-ranged query on `analytics_events` must hit an index; document and add a composite index.
- Explicit, documented matching semantics (ordering, window, attribution).
- `SchemaValidationIT` stays green; `ddl-auto=validate` accepts every new column.

**Non-goals**
- Frontend UI for funnels (separate work).
- Real-time/streaming funnel evaluation (batch-only here).
- Cross-tenant / global funnels.
- Materialised views — we will plan them but only implement if indexed scan is insufficient (captured in risks).
- Backwards migration of existing `funnel_analytics` rows that have no `funnel_id` (marked nullable, backfilled best-effort by `funnel_name`).
- Retiring the legacy `POST /api/v1/analytics/funnels/{funnelName}/calculate` tenant endpoint — it stays for now and delegates to the new service; deprecation is a follow-up.

## Acceptance criteria

1. `POST /api/v1/admin/funnels` with a valid JSON body persists a row in the new `funnels` table and returns `201 Created` with the funnel including its UUID.
2. `GET /api/v1/admin/funnels` lists all funnels for the authenticated admin's tenant, ordered by `created_at DESC`.
3. `GET /api/v1/admin/funnels/{id}` returns `200` for a funnel in-tenant and `404` for an unknown id or an id belonging to another tenant (no tenant leak).
4. `PUT /api/v1/admin/funnels/{id}` updates name/description/steps/window config; attempting to change `tenant_id` is ignored. Editing `steps` bumps a `definition_version` integer so results can be tagged.
5. `DELETE /api/v1/admin/funnels/{id}` soft-deletes (`deleted_at`), does not cascade-delete `funnel_analytics` history, and subsequent `GET` returns `404`.
6. `POST /api/v1/admin/funnels/{id}/recompute?from=...&to=...` synchronously runs the pipeline and persists one new `funnel_analytics` row referencing the funnel.
7. `GET /api/v1/admin/funnels/{id}/results?from=...&to=...` returns `{ latest: FunnelResult, series: FunnelResult[] }` ordered by `period_start ASC` for the series.
8. The scheduled job `@Scheduled(cron = "0 30 2 * * *")` runs nightly, iterates over all non-deleted funnels across all tenants, and recomputes for `[today-30d, today-1d]`.
9. For a fixture set of `analytics_events` with 100 users hitting step 1, 40 hitting step 2, and 10 hitting step 3, the computed output asserts exactly `step_counts = {s1:100, s2:40, s3:10}`, `conversion_rates = {s1_to_s2:40.0, s2_to_s3:25.0}`, `overall_conversion=10.0`, `bottleneck_step="s2"` (the step with the lowest outgoing rate, per our documented tie-break).
10. `SchemaValidationIT` passes after V5 is applied.
11. All admin endpoints reject non-admin JWTs with `403`.
12. The funnel computation for a 30-day window across 100k `analytics_events` runs in under 2 seconds on a cold cache (verified via manual `EXPLAIN ANALYZE` check documented in the test plan).

## User-facing surface

All endpoints require a Keycloak JWT with `roles` claim containing `admin`. `tenant_id` is always resolved from the JWT via `TenantResolver` (`src/main/java/com/changelog/config/TenantResolver.java:7`) — never accepted from the client body or path.

### 1. Create funnel definition
`POST /api/v1/admin/funnels`

Request:
```json
{
  "name": "Free to Paid",
  "description": "Tracks conversion from pricing page view to subscription",
  "steps": [
    { "name": "Visited Pricing", "eventName": "pricing_page_viewed" },
    { "name": "Started Trial",   "eventName": "trial_started",
      "propertiesMatch": { "plan": "pro" } },
    { "name": "Added Payment",   "eventName": "payment_method_added" },
    { "name": "Subscribed",      "eventName": "subscription_created",
      "propertiesMatch": { "plan": "pro", "interval": "monthly" } }
  ],
  "window": {
    "attribution": "user",              // "user" | "session"
    "ordering":    "strict",            // "strict" | "any"
    "maxStepGapSeconds": 604800,        // 7 days between consecutive steps; null = no limit
    "maxTotalSpanSeconds": 2592000      // 30 days total; null = no limit
  },
  "active": true
}
```

Response `201 Created`:
```json
{
  "id": "7a3b2c1d-0000-4000-8000-00000000aaaa",
  "tenantId": "11111111-2222-3333-4444-555555555555",
  "name": "Free to Paid",
  "description": "Tracks conversion from pricing page view to subscription",
  "steps": [
    { "step": 1, "name": "Visited Pricing", "eventName": "pricing_page_viewed", "propertiesMatch": null },
    { "step": 2, "name": "Started Trial",   "eventName": "trial_started",       "propertiesMatch": { "plan": "pro" } },
    { "step": 3, "name": "Added Payment",   "eventName": "payment_method_added", "propertiesMatch": null },
    { "step": 4, "name": "Subscribed",      "eventName": "subscription_created", "propertiesMatch": { "plan": "pro", "interval": "monthly" } }
  ],
  "window": {
    "attribution": "user",
    "ordering": "strict",
    "maxStepGapSeconds": 604800,
    "maxTotalSpanSeconds": 2592000
  },
  "definitionVersion": 1,
  "active": true,
  "createdAt": "2026-04-16T12:00:00Z",
  "updatedAt": "2026-04-16T12:00:00Z"
}
```

Validation (all 422 on failure, RFC-7807 ProblemDetail body):
- `name` non-blank, ≤ 255 chars, unique per tenant amongst non-deleted funnels.
- `steps` non-empty and ≤ 20.
- Each `steps[i].eventName` non-blank and ≤ 255 chars.
- `steps[i].propertiesMatch` (if present) is an object with ≤ 10 keys; values restricted to string/number/boolean/null.
- `window.attribution` ∈ {"user","session"}; `window.ordering` ∈ {"strict","any"}.
- `window.maxStepGapSeconds`, `window.maxTotalSpanSeconds` positive integers or null.

### 2. List funnels
`GET /api/v1/admin/funnels?active=true`

Response `200`:
```json
{
  "items": [
    { "id": "7a3b...aaaa", "name": "Free to Paid", "active": true,
      "stepCount": 4, "definitionVersion": 1,
      "lastComputedAt": "2026-04-15T02:30:11Z", "createdAt": "2026-04-10T09:00:00Z" }
  ],
  "total": 1
}
```

### 3. Get single funnel
`GET /api/v1/admin/funnels/{id}` — same body as Create response.

### 4. Update funnel
`PUT /api/v1/admin/funnels/{id}` — same body as Create; any edit to `steps` or `window` increments `definitionVersion`. Name-only or description-only edits do not bump the version. Response `200` with the updated funnel.

### 5. Delete funnel
`DELETE /api/v1/admin/funnels/{id}` — soft-delete; `204 No Content`. Historical `funnel_analytics` rows are preserved and remain retrievable by `id` on that funnel-row (not via the list endpoint).

### 6. Recompute now
`POST /api/v1/admin/funnels/{id}/recompute?from=2026-03-01&to=2026-03-31`

- `from`/`to` are `LocalDate` (ISO-8601); inclusive of both ends, interpreted in **UTC** (see Risks).
- If omitted: defaults to `[today-30d, today-1d]`.

Response `200`:
```json
{
  "resultId": "beef1234-...",
  "funnelId": "7a3b...aaaa",
  "periodStart": "2026-03-01",
  "periodEnd": "2026-03-31",
  "definitionVersion": 1,
  "stepCounts": { "Visited Pricing": 10000, "Started Trial": 2000, "Added Payment": 1200, "Subscribed": 400 },
  "conversionRates": {
    "Visited Pricing_to_Started Trial": 20.0,
    "Started Trial_to_Added Payment":   60.0,
    "Added Payment_to_Subscribed":      33.33
  },
  "overallConversion": 4.0,
  "bottleneckStep": "Visited Pricing",
  "dropoffPoints": ["Visited Pricing_to_Started Trial", "Added Payment_to_Subscribed"],
  "calculatedAt": "2026-04-16T12:05:22Z"
}
```

### 7. Read results (latest + series)
`GET /api/v1/admin/funnels/{id}/results?from=2026-01-01&to=2026-04-16&limit=90`

Response `200`:
```json
{
  "latest": { /* same shape as recompute response */ },
  "series": [
    { "periodStart": "2026-02-01", "periodEnd": "2026-02-28",
      "overallConversion": 3.7, "bottleneckStep": "Visited Pricing",
      "stepCounts": { "Visited Pricing": 9500, "Started Trial": 1800, "Added Payment": 1100, "Subscribed": 352 },
      "definitionVersion": 1, "calculatedAt": "2026-03-01T02:30:11Z" }
  ],
  "definitionVersionsSeen": [1]
}
```

`definitionVersionsSeen` warns the caller that the series spans different step definitions (trend is not apples-to-apples — see Risks).

### 8. Error shape
All failures return `application/problem+json`:
```json
{ "type": "about:blank", "title": "Validation failed",
  "status": 422, "detail": "steps must not be empty",
  "instance": "/api/v1/admin/funnels" }
```

## Architecture & data flow

```
┌──────────────────────────────────┐
│ POST /api/v1/admin/funnels       │──► FunnelDefinitionService.create ──► funnels table
│ PUT  /api/v1/admin/funnels/{id}  │──► FunnelDefinitionService.update ──► bump definition_version
│ DELETE /api/v1/admin/funnels/{id}│──► soft-delete (deleted_at)
└──────────────────────────────────┘

┌──────────────────────────────────────┐
│ POST /api/v1/admin/funnels/{id}/     │
│      recompute?from=&to=             │
└──────────────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────────────────────┐
│ FunnelComputeService.compute(funnelId, from, to)               │
│                                                                │
│  1. Load definition (funnels row)                              │
│  2. Resolve attribution key (user_id vs session_id)            │
│  3. For each step i, run SQL:                                  │
│      SELECT attr_key, MIN(occurred_at) AS hit_at               │
│      FROM analytics_events                                     │
│      WHERE tenant_id = ? AND event_name = ?                    │
│        AND occurred_at >= ? AND occurred_at < ?                │
│        AND properties @> ?::jsonb   -- if predicate present    │
│      GROUP BY attr_key                                         │
│                                                                │
│  4. Join in application: keep an attr_key iff it passes        │
│     step[0], then step[1], ..., and                            │
│       - strict ordering:  hit_at[i+1] > hit_at[i]              │
│       - maxStepGapSeconds: hit_at[i+1] - hit_at[i] ≤ gap       │
│       - maxTotalSpanSeconds: hit_at[last] - hit_at[0] ≤ span   │
│     (For ordering="any", we require presence only.)            │
│                                                                │
│  5. stepCounts[i]       = |eligible set after step i|          │
│     conversionRates[i→j]= stepCounts[j] / stepCounts[i] * 100  │
│     overallConversion   = stepCounts[last] / stepCounts[0]*100 │
│     bottleneckStep      = argmin over conversionRates          │
│     dropoffPoints       = {edges with rate < 50%}              │
│                                                                │
│  6. Persist funnel_analytics row (funnel_id, definition_ver,   │
│     period_start/end, counts, rates, overall, bottleneck)      │
└────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────┐
│ Nightly @Scheduled("0 30 2 * * *")   │──► for every active funnel across tenants,
│ FunnelScheduler.nightlyRecompute()   │    call FunnelComputeService.compute(id, today-30d, today-1d)
└──────────────────────────────────────┘
```

**SQL strategy.** We use **one SELECT per step**, not a giant CTE/window-function query. Reasons:
- Each step is an independent scan that benefits from the index `(tenant_id, event_name, occurred_at)`.
- The cross-step join is small (bounded by `step_counts[0]`, typically tens of thousands) and easily done in Java — no need to ship large result sets through Postgres window functions.
- A CTE with `LATERAL` joins would require postgres-specific JSONB `@>` usage and makes it harder to handle the step-gap / span constraints without adding per-row subqueries.
- An optional future optimisation (materialised view per-day per-event-name aggregates) is noted in Risks, not implemented here.

**Attribution key.** `window.attribution="user"` uses `user_id` (nullable — events without a user are excluded from that funnel). `"session"` uses `session_id` (non-null on every event inserted by `AnalyticsService.trackEvent`, see `AnalyticsService.java:58`). We never mix the two.

**Matching semantics (locked in).**
- **Ordering="strict" (default):** step `i+1` must occur **strictly after** step `i` for the same attribution key. Ties (equal `occurred_at`) break towards earlier step — i.e., the later-in-definition step wins only if `>` strict.
- **Ordering="any":** mere presence of each step event within the window counts; no time-ordering constraint.
- **maxStepGapSeconds:** if set, the gap between step `i` and step `i+1` must be ≤ value. Only applied for `ordering="strict"`.
- **maxTotalSpanSeconds:** if set, the span from step 1 to last step must be ≤ value. Only applied for `ordering="strict"`.
- **User-vs-session:** explicit in `window.attribution`. Default `user`.
- **`propertiesMatch`:** Postgres JSONB `@>` operator (containment). Missing predicate ⇒ all events with that name match. All predicate keys must match.
- **Bottleneck tie-break:** lowest rate wins; ties broken by smaller step index (earlier bottleneck).

## Database changes

A new Flyway migration `V5__funnel_definitions.sql` introduces a dedicated `funnels` definition table and links `funnel_analytics` to it. We keep all existing `funnel_analytics` columns (including the just-added `bottleneck_step VARCHAR(255)` — see `FunnelAnalytics.java:60`) so existing rows remain valid; the new `funnel_id` column is **nullable** to permit rows created by the legacy tenant-side endpoint.

```sql
-- V5__funnel_definitions.sql

CREATE TABLE funnels (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES cc.tenants(id),
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    steps               JSONB NOT NULL,                     -- [{step,name,eventName,propertiesMatch}]
    window_config       JSONB NOT NULL DEFAULT '{}'::jsonb, -- {attribution,ordering,maxStepGapSeconds,maxTotalSpanSeconds}
    definition_version  INTEGER NOT NULL DEFAULT 1,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ                          -- soft-delete
);

CREATE UNIQUE INDEX idx_funnels_tenant_name_active
    ON funnels(tenant_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_funnels_tenant_active
    ON funnels(tenant_id, active)
    WHERE deleted_at IS NULL;

-- Link results back to their definition
ALTER TABLE funnel_analytics
    ADD COLUMN funnel_id          UUID REFERENCES funnels(id),
    ADD COLUMN definition_version INTEGER;

CREATE INDEX idx_funnel_analytics_funnel ON funnel_analytics(funnel_id, period_start DESC);

-- Critical performance index for the compute path.
-- tenant_id + event_name + occurred_at covers the per-step SELECT.
-- The existing idx_analytics_events_name is global (no tenant), and
-- idx_analytics_events_tenant_type_date (line 682 of V3) includes event_type,
-- not event_name. Neither is a good fit for our predicate shape.
CREATE INDEX idx_analytics_events_tenant_name_time
    ON analytics_events(tenant_id, event_name, occurred_at DESC);

-- GIN index to support future properties @> predicate pushdowns.
-- Cheap to add now; avoids a separate migration later.
CREATE INDEX idx_analytics_events_properties_gin
    ON analytics_events USING GIN (properties jsonb_path_ops);
```

**SchemaValidationIT impact.** The IT boots the app with `ddl-auto=validate`, so every `@Entity` must still match the DB. The new `funnels` table is a new entity (no drift). The two new columns on `funnel_analytics` (`funnel_id`, `definition_version`) must be added to `FunnelAnalytics.java` as nullable fields in the same change. After V5 applies and the entity updates land, the IT will pass.

**No RLS for `funnels`.** The existing `funnel_analytics` table does not enable RLS either (see `V3__business_modules.sql:694` — only `support_tickets`, `analytics_events`, `customer_health_scores` get RLS). We follow the same pattern and enforce tenancy at the service layer via `TenantResolver`. If RLS is adopted project-wide later, it will be a separate migration.

## Files to create or modify

**Create**
- `src/main/resources/db/migration/V5__funnel_definitions.sql` — migration above.
- `src/main/java/com/changelog/business/intelligence/model/Funnel.java` — `@Entity @Table(name="funnels")`, JSONB fields for `steps` and `windowConfig` matching the pattern in `FunnelAnalytics.java:34`.
- `src/main/java/com/changelog/business/intelligence/model/FunnelStepDefinition.java` — POJO mapped into the `steps` JSONB (`step`, `name`, `eventName`, `propertiesMatch`).
- `src/main/java/com/changelog/business/intelligence/model/FunnelWindowConfig.java` — POJO for `windowConfig`.
- `src/main/java/com/changelog/business/intelligence/repository/FunnelRepository.java` — Spring Data JPA: `findAllByTenantIdAndDeletedAtIsNull`, `findByIdAndTenantIdAndDeletedAtIsNull`, `existsByTenantIdAndNameAndDeletedAtIsNull`.
- `src/main/java/com/changelog/business/intelligence/service/FunnelDefinitionService.java` — CRUD + validation + version bump on steps/window changes.
- `src/main/java/com/changelog/business/intelligence/service/FunnelComputeService.java` — pipeline described in Architecture.
- `src/main/java/com/changelog/business/intelligence/service/FunnelScheduler.java` — nightly `@Scheduled` job.
- `src/main/java/com/changelog/business/intelligence/controller/AdminFunnelController.java` — all `/api/v1/admin/funnels` routes.
- `src/main/java/com/changelog/business/intelligence/dto/FunnelDefinitionRequest.java`, `FunnelDefinitionResponse.java`, `FunnelListItemResponse.java`, `FunnelResultResponse.java`, `FunnelResultsPageResponse.java`, `FunnelStepDto.java`, `FunnelWindowDto.java`.
- `src/test/java/com/changelog/business/intelligence/service/FunnelComputeServiceTest.java` — deterministic compute tests (fixture input → asserted output).
- `src/test/java/com/changelog/business/intelligence/controller/AdminFunnelControllerIT.java` — MockMvc tests against the admin API.
- `src/test/java/com/changelog/business/intelligence/repository/FunnelQueryExplainIT.java` — asserts `EXPLAIN` on the per-step SELECT uses `idx_analytics_events_tenant_name_time` (`Index Scan` or `Bitmap Index Scan`, never `Seq Scan`).

**Modify**
- `src/main/java/com/changelog/business/intelligence/model/FunnelAnalytics.java` — add `private UUID funnelId;` and `private Integer definitionVersion;` (both nullable) mapping the new columns.
- `src/main/java/com/changelog/business/intelligence/repository/AnalyticsEventRepository.java` — add a native query `findStepHits(tenantId, eventName, from, to, predicateJson)` returning `(attrKey, minOccurredAt)` rows. Use `@Query(nativeQuery=true)` with `properties @> cast(:predicate as jsonb)` (predicate parameter nullable ⇒ short-circuit in SQL with `:predicate IS NULL`).
- `src/main/java/com/changelog/business/intelligence/service/FunnelAnalyticsService.java` — split: keep `getLatest`, `getAllFunnels`, `getFunnel` read-only helpers; delegate compute to the new `FunnelComputeService`; mark legacy `calculateFunnel(String funnelName, ...)` as `@Deprecated` but leave it working for `AnalyticsController.java:63`.
- `src/main/java/com/changelog/business/intelligence/controller/AnalyticsController.java` — no contract change; existing tenant-side endpoints stay.
- `src/main/java/com/changelog/config/SecurityConfig.java` — add `@EnableMethodSecurity` (and corresponding import) so `@PreAuthorize("hasAuthority('admin')")` on the new `AdminFunnelController` is honoured. No route-level rule change needed because `/api/v1/**` is already authenticated (`SecurityConfig.java:27`).

**Do not touch**
- `V1`–`V4` migrations (immutable once applied).
- `docker-compose.yml` (Postgres already on 5433).

## Implementation steps

1. **DB migration.** Add `V5__funnel_definitions.sql`. Apply locally via `./mvnw flyway:migrate` against the Docker Postgres on 5433. Verify `\d funnels`, `\d funnel_analytics`, `\di idx_analytics_events_tenant_name_time`.
2. **Entity + field additions on `FunnelAnalytics`.** Add `funnelId` and `definitionVersion` as nullable columns. Run `SchemaValidationIT` → green.
3. **New `Funnel` entity + embedded JSONB POJOs.** Mirror the pattern at `FunnelAnalytics.java:44` (`@JdbcTypeCode(SqlTypes.JSON)`).
4. **Repositories.** `FunnelRepository` for CRUD; extend `AnalyticsEventRepository` with the `findStepHits` native query.
5. **`FunnelDefinitionService`.** Validation (Bean Validation annotations on DTOs + explicit checks for uniqueness and step cardinality). On update, compare `steps`/`windowConfig` JSON to old value; bump `definitionVersion` only when different.
6. **`FunnelComputeService`.** Implement the pipeline exactly as diagrammed. Key details:
   - Instantiate a `Set<String>` eligible attribution keys after step 0 (all keys that hit step 0).
   - For each subsequent step, stream (`List<Object[]>`) its `(attrKey, hitAt)` rows, intersect with eligible keys, apply ordering/gap/span constraints, and narrow the set.
   - Build `stepCounts`, `conversionRates`, `overallConversion`, `bottleneckStep` (min rate; tie-break by earlier edge index), `dropoffPoints` (rate < 50).
   - Persist one `FunnelAnalytics` row; include `funnelId`, `definitionVersion`, `calculatedAt=now()`.
7. **`FunnelScheduler`.** `@Scheduled(cron = "0 30 2 * * *")` iterates funnels tenant-agnostically: `funnelRepository.findAll()` filtered `active=true AND deleted_at IS NULL`. For each, call `computeService.compute(funnel.getId(), today.minusDays(30), today.minusDays(1))`. Wrap each call in try/catch and log at `ERROR` with funnel id + tenant id.
8. **`AdminFunnelController`.** All endpoints annotated `@PreAuthorize("hasAuthority('admin')")`. Always resolve `tenantId` via `tenantResolver.getTenantId(jwt)`; the service layer filters by that tenant for every read/write.
9. **Enable method security.** Add `@EnableMethodSecurity` on `SecurityConfig`. The `local` profile uses `SecurityConfigLocal` with permit-all, so admin tests use the non-local profile via `@SpringBootTest` with `application.yml` defaults.
10. **DTOs + mappers.** Keep DTOs separate from entities (existing code does this inconsistently; follow the cleaner pattern used in `LandingPageController`).
11. **Error handling.** Add a `@RestControllerAdvice` class `AdminFunnelExceptionHandler` returning `ProblemDetail` for `IllegalArgumentException`, `FunnelNotFoundException`, `MethodArgumentNotValidException`. Status codes: 400 (malformed JSON), 404 (not found / wrong tenant), 422 (validation), 409 (name conflict).
12. **Tests.** Write `FunnelComputeServiceTest` with the deterministic fixture (see Test plan), `AdminFunnelControllerIT` for HTTP contract, and `FunnelQueryExplainIT` for the index-use assertion.
13. **Smoke.** Manual curl examples from the Test plan against a locally running instance. Confirm the legacy `POST /api/v1/analytics/funnels/{funnelName}/calculate` still works end-to-end.
14. **Run `SchemaValidationIT`.** Must pass before PR.

## Test plan

### Deterministic compute test (unit, no Spring)

`FunnelComputeServiceTest`:
- Build 3 steps: `s1=signup`, `s2=trial_started`, `s3=subscribed`. `ordering=strict`, no gap/span.
- Fixture `AnalyticsEvent` list (inject via a stubbed repository):
  - 100 users emit `signup` within the window.
  - 40 of those 100 emit `trial_started` strictly after.
  - 10 of those 40 emit `subscribed` strictly after.
  - Add 20 users who emit `trial_started` but never `signup` in-window (must be excluded).
  - Add 5 users who emit `subscribed` **before** `signup` (must be excluded by strict ordering).
- Stub `AnalyticsEventRepository.findStepHits` to return the min-hit per user per event name.
- Run `FunnelComputeService.compute`.
- **Assertions (exact):**
  - `stepCounts = { "s1": 100, "s2": 40, "s3": 10 }`
  - `conversionRates = { "s1_to_s2": 40.0, "s2_to_s3": 25.0 }`
  - `overallConversion = 10.0`
  - `bottleneckStep = "s2"` (the step whose outgoing rate is lowest; tie-break-free here)
  - `dropoffPoints = [ "s1_to_s2", "s2_to_s3" ]` (both < 50%)
- Second case: repeat with `ordering="any"` and assert the 5 out-of-order users now count — `stepCounts.s3 = 15`.
- Third case: `maxStepGapSeconds=60`; users with `trial_started` more than 60 seconds after `signup` are dropped — assert the count drops accordingly.

### HTTP contract test (Spring MockMvc IT)

`AdminFunnelControllerIT` (runs with default profile; JWT mocked via `@WithMockUser(roles={"admin"})` plus a test-only `TenantResolver` bean):
- `POST` with valid body → 201 + funnel returned.
- `POST` with empty `steps` → 422.
- `POST` with duplicate name (same tenant) → 409.
- `GET` list → one funnel.
- `GET` by id → 200; by wrong tenant's id → 404.
- `PUT` updating description only → no `definitionVersion` change.
- `PUT` updating `steps` → `definitionVersion` becomes 2.
- `DELETE` → 204; subsequent `GET` → 404; historical `funnel_analytics` row persists.
- `POST .../recompute` without query params → defaults applied; one new `funnel_analytics` row.
- `GET .../results?from=...&to=...` → returns `{latest, series, definitionVersionsSeen}`.
- Non-admin role → 403 on every endpoint.

### Index-use EXPLAIN test

`FunnelQueryExplainIT` (boots Spring + Docker Postgres like `SchemaValidationIT`):
- Seed 10k `analytics_events` for one tenant across 2 event names.
- Run `EXPLAIN (FORMAT JSON) <step SELECT>` via `JdbcTemplate`.
- Assert:
  - Top-level `Node Type` is `Aggregate` or `Group`, with a child `Index Scan using idx_analytics_events_tenant_name_time` or `Bitmap Index Scan on idx_analytics_events_tenant_name_time`.
  - **No** `Seq Scan on analytics_events` anywhere in the plan.
- If the assertion fails after V5 applies, the test message instructs the developer to verify the index was created and `ANALYZE analytics_events;` was run.

### Manual smoke (curl)

Create a funnel:
```bash
curl -X POST "http://localhost:8081/api/v1/admin/funnels" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name":"Onboarding",
    "steps":[
      {"name":"Signed Up","eventName":"user_signup"},
      {"name":"Completed Profile","eventName":"profile_completed"},
      {"name":"Active","eventName":"feature_used"}
    ],
    "window":{"attribution":"user","ordering":"strict","maxStepGapSeconds":604800,"maxTotalSpanSeconds":2592000},
    "active":true
  }'
```

List:
```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" "http://localhost:8081/api/v1/admin/funnels"
```

Recompute:
```bash
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8081/api/v1/admin/funnels/$FUNNEL_ID/recompute?from=2026-03-01&to=2026-03-31"
```

Read results:
```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8081/api/v1/admin/funnels/$FUNNEL_ID/results?from=2026-01-01&to=2026-04-16"
```

Delete:
```bash
curl -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8081/api/v1/admin/funnels/$FUNNEL_ID"
```

## Risks & open questions

1. **Index bloat on `analytics_events`.** We add two indexes: a composite `(tenant_id, event_name, occurred_at DESC)` and a GIN `properties jsonb_path_ops`. On a high-write event table (`AnalyticsService.java:49` writes synchronously on every tracked event), each additional index slows inserts. Mitigation: the composite index replaces the need for `idx_analytics_events_name` (global, no tenant — strictly dominated by the new one), so we can schedule dropping it in a later migration. The GIN index is optional for MVP; if write latency regresses visibly, drop it and filter `propertiesMatch` in the application.

2. **Definition drift breaking historical comparability.** If an admin edits `steps` in a funnel, older `funnel_analytics` rows are measured against a different definition — comparing `overallConversion` over time becomes misleading. Mitigations: (a) `definition_version` is stamped on every result; (b) `GET .../results` returns `definitionVersionsSeen` so clients can surface a "definition changed" badge; (c) follow-up work could fork funnels rather than mutate in place.

3. **Time-zone in date-range filtering.** `period_start`/`period_end` are `LocalDate` (UTC-interpreted here). An admin in America/Los_Angeles who queries "yesterday" and `analytics_events.occurred_at` is `TIMESTAMPTZ` can see counts that disagree with their local-day intuition by up to 8 hours. Decision: **store and query in UTC for v1**, document explicitly in the API response and the admin UI copy. Add a follow-up to accept a `tz` query param.

4. **Computation cost on large tenants.** 30-day window over an event-heavy tenant could return millions of rows from step 1 alone. Mitigations: (a) `MIN(occurred_at) GROUP BY attr_key` already collapses duplicates Postgres-side; (b) we bound by tenant via index; (c) if this is still too slow, pre-aggregate into a materialised view keyed on `(tenant_id, event_name, day, attr_key, min_occurred_at)` refreshed by the scheduler — planned but not in v1.

5. **`session_id` cardinality.** `AnalyticsService.trackEvent` generates a **new** `UUID.randomUUID().toString()` for `sessionId` on **every** event (`AnalyticsService.java:58`). Session-attribution funnels will therefore always have a 1-event-per-session distribution and report nonsense rates. **Open question:** is this a bug in `AnalyticsService` (sessions should span many events), or is `user_id` the only meaningful attribution today? Plan: default to `attribution="user"`, document the `session` caveat in the admin UI, and file a separate issue to fix session tracking.

6. **Legacy `POST /api/v1/analytics/funnels/{funnelName}/calculate` creates unlinked `funnel_analytics` rows** (`funnel_id IS NULL`). The admin list endpoint hides them. Decision: leave them; optionally add a backfill query in V6 that matches by `tenant_id + funnel_name`.

7. **Admin role source.** `JwtTenantResolver` reads `roles` via `SecurityConfig.java:41` (`authoritiesClaimName="roles"`). It is assumed Keycloak issues `roles` in the JWT with `admin` string for admin users (per `DEVELOPMENT.md:26`). If the claim is in fact `realm_access.roles`, the `@PreAuthorize` check needs to be updated — this needs one Keycloak token dump to confirm before merging.

8. **Transaction boundaries.** The compute step can run for seconds on large tenants. Wrapping it in a single `@Transactional` keeps a DB connection pinned. Mitigation: read events with `@Transactional(readOnly=true)` or outside a transaction, compute in memory, then persist the single `funnel_analytics` row in a short writeable transaction.

9. **Admin endpoint CORS.** Existing `/api/v1/**` config does not restrict origin at the Spring level. Out of scope for this plan; mirror whatever the other admin-ish endpoints do.

## Definition of done

- `V5__funnel_definitions.sql` applied and reviewed; `./mvnw flyway:info` shows it at version 5.
- `Funnel` entity + updated `FunnelAnalytics` entity both validate under `ddl-auto=validate`.
- `SchemaValidationIT` passes (`./mvnw -Dtest=SchemaValidationIT verify`).
- All new endpoints under `/api/v1/admin/funnels/**` exist, authenticate via JWT, and enforce `hasAuthority('admin')`.
- `FunnelComputeServiceTest` passes — deterministic output for the three fixture scenarios (strict, any, gap-constrained).
- `AdminFunnelControllerIT` passes for all listed cases including the non-admin 403 case.
- `FunnelQueryExplainIT` passes — no `Seq Scan` in the step-SELECT plan.
- Scheduler is registered and a manual trigger (via actuator or a test-only endpoint) recomputes an existing funnel end-to-end.
- `README.md` "Current State" table updated from "Intelligence module: funnel analysis" to "Intelligence module: admin-defined funnel analytics with time-series" (one-line edit).
- Manual smoke (the four curl calls above) all return 2xx against a fresh local environment.
- No changes to `V1`–`V4`; no change to the public `/changelog`, `/p`, `/widget`, `/subscribe` surface; no change to the existing `AnalyticsController` contract.
