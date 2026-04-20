# Plan 04 — Customer Health Scoring Scheduled Job

Owner area: `com.changelog.business.success.*`
Status: Ready for implementation
Target migration: `V5__customer_health_scoring_job.sql`

## Overview

The platform has the *shape* of customer health scoring — tables
(`customer_health_scores`, `customer_health_history`), an entity
(`CustomerHealthScore`), a repository, a controller at
`/api/v1/health-scores`, and a service
`CustomerHealthScoringService` — but nothing actually computes scores
on a schedule over the real customer population. The only scheduled
hook in the service today is a stub
(`src/main/java/com/changelog/business/success/service/CustomerHealthScoringService.java:249-257`)
whose body is a `TODO`. The six signal analyzers at
`CustomerHealthScoringService.java:214-243` all return hard-coded dummy
values regardless of input. `customer_health_history` exists in V3
(`src/main/resources/db/migration/V3__business_modules.sql:408-417`)
but has no matching `@Entity` and is never written to.

This plan builds the missing production piece: a nightly, idempotent,
per-tenant fan-out scoring job that reads real signals from existing
tables, calls LiteLLM to generate `recommended_actions`, persists to
both `customer_health_scores` (latest, one row per tenant+customer+day)
and `customer_health_history` (append-only trail), emits
`HEALTH_SCORE_CHANGED` and `CHURN_RISK_DETECTED` business events on
risk-tier transitions, exposes an on-demand admin recompute endpoint,
and is safe to re-run on the same day without double-writing.

## Goals & Non-goals

### Goals

1. Nightly `@Scheduled` job that iterates every tenant, then every
   active (subscribed) customer within that tenant, and recomputes
   a health score.
2. Replace stubbed signal analyzers with real queries against
   `analytics_events`, `support_tickets`, `stripe_subscriptions`,
   `stripe_webhooks`, and `nps_responses`. Clearly mark which signals
   remain stubbed and why.
3. Persist:
   - upsert-semantics write to `customer_health_scores` (latest snapshot)
   - unconditional append to `customer_health_history` (monotonic trail)
   - populate `recommended_actions` JSONB via a LiteLLM call, with a
     deterministic fallback when the LLM is unavailable.
4. Emit `HEALTH_SCORE_CHANGED` on any score change and
   `CHURN_RISK_DETECTED` on risk-tier transitions upward
   (low→medium, medium→high, anything→critical). Stop firing on every
   tick when the tier is unchanged.
5. Admin on-demand recompute endpoint
   `POST /api/v1/admin/customers/{id}/health/recompute`, guarded by
   the `ADMIN` authority.
6. Idempotency: running the job twice on the same UTC date for the same
   tenant+customer must be a no-op on `customer_health_scores` (update
   same row, not insert duplicate) and must not append a second
   history row with the same score on the same day. A cluster-safe
   advisory lock prevents two app instances from fan-out-scoring the
   same tenant concurrently.

### Non-goals

- Real-time scoring (this remains the responsibility of the existing
  synchronous `calculateHealthScore(...)` called from webhooks —
  nothing in its hot path changes).
- Migrating scheduling to Quartz. We evaluated Quartz for
  cluster-aware locking; Postgres advisory locks get us there with
  zero new dependencies. Decision documented under *Risks*.
- Backfill of history rows. History starts accumulating on first
  successful run.
- Changing the `CustomerHealthScore` entity schema (the existing
  JSONB-backed `signals` and `recommendedActions` columns are correct).

## Acceptance criteria

1. Booting the app with a clean DB, waiting for the next cron tick,
   and reading from `customer_health_scores` yields one row per
   active customer per tenant, `signals` populated with real data
   (non-zero impacts where negative signals exist in test fixtures),
   `recommended_actions` a non-empty JSONB array, `calculated_at`
   within the last minute.
2. `customer_health_history` has exactly one row per active customer
   after the first run, two rows per customer after two runs on
   *different* calendar dates, and still one row per customer after
   two runs on the *same* UTC date (idempotency).
3. A customer whose score drops from 85 (low) to 55 (medium) between
   two runs causes exactly one `HEALTH_SCORE_CHANGED` event and
   zero `CHURN_RISK_DETECTED` events. A drop to 35 (critical) causes
   one of each. A run that produces the same tier as the prior run
   emits no event.
4. `POST /api/v1/admin/customers/{id}/health/recompute` with an admin
   JWT returns `200` and the recomputed `CustomerHealthScore` body.
   The same call from a non-admin returns `403`.
5. `SchemaValidationIT` (`src/test/java/com/changelog/SchemaValidationIT.java`)
   stays green: new migration `V5` applies cleanly and new entities
   validate under `ddl-auto=validate`.
6. A job run over 1,000 synthetic customers in a single tenant
   completes in under 60 seconds on a dev laptop with LLM calls
   mocked (budget: ~60ms per customer for signal aggregation; LLM is
   batched or cached — see *Implementation steps #7*).
7. With two app instances pointed at the same DB, cron ticks on both
   do **not** double-write — the non-leader instance observes
   `pg_try_advisory_lock` returning false and exits cleanly.

## User-facing surface

### Scheduled (internal, no user surface)

- Cron: `0 30 2 * * *` (02:30 UTC daily). Offset 30 min past the hour
  to avoid colliding with `ScheduledPublishingJob`
  (`src/main/java/com/changelog/service/ScheduledPublishingJob.java:22`)
  which runs every minute, and with Stripe webhook retry waves.
  Configurable via `health.scoring.cron` in `application.yml`.

### HTTP

- `POST /api/v1/admin/customers/{customerId}/health/recompute` — new.
  Body: empty. Response: `CustomerHealthScore` JSON. Role: `ADMIN`.
- `GET /api/v1/health-scores/...` — existing endpoints at
  `src/main/java/com/changelog/business/success/controller/CustomerHealthScoreController.java`
  stay as-is. The stale per-customer `GET /{customerId}/recalculate`
  there (`CustomerHealthScoreController.java:46`) is *not* the admin
  endpoint; it is tenant-scoped and will be left alone for now.
  `POST /admin/.../recompute` is a deliberate parallel surface for
  support staff with cross-tenant admin authority.

### Observability

- Structured log line per tenant: `"health.scoring.tenant.start"`,
  `"health.scoring.tenant.complete"` with tenant id, customer count,
  duration, event count.
- Micrometer counters: `health.scoring.runs`,
  `health.scoring.customers.scored`, `health.scoring.risk.detected`,
  `health.scoring.llm.errors`, `health.scoring.lock.skipped`.

## Architecture & data flow

### Per-tenant scoring pipeline

```
  cron tick (02:30 UTC, single JVM thread)
       │
       ▼
  HealthScoringJob.runNightly()                    [no transaction; orchestrator]
       │
       │  pg_try_advisory_lock(HEALTH_SCORING_LOCK_KEY)
       │      └── false → log + return (cluster leader already running)
       ▼
  iterate tenants:  SELECT DISTINCT tenant_id FROM cc.tenants
       │
       ▼
  for each tenantId (SERIAL — one tenant at a time to bound LLM QPS):
       │
       │    HealthScoringOrchestrator.scoreTenant(tenantId)       [no tx]
       │         │
       │         │   fetch active customer ids:
       │         │     stripeSubscriptionRepository.findActiveByTenantId(tenantId)
       │         │       → stripe_subscriptions.customer_id (UUID → stripe_customers.id)
       │         │
       │         │   parallel fan-out (bounded thread pool, maxConcurrency=8):
       │         ▼
       │    for each customerId:
       │         │
       │         │   HealthScoringService.scoreOneCustomer(tenantId, customerId)
       │         │        ── @Transactional(REQUIRES_NEW)    [tx begin]
       │         │        │
       │         │        │  1. aggregate signals (READ):
       │         │        │       - analytics_events (login_frequency, usage_decline, feature_adoption)
       │         │        │       - support_tickets (ticket count, sentiment)
       │         │        │       - stripe_webhooks (payment_failed events in last 30d)
       │         │        │       - stripe_subscriptions (age, trial/active)
       │         │        │       - nps_responses (latest score)
       │         │        │  2. compute score + riskLevel
       │         │        │  3. call LiteLLM.chatCompletions  ← OUTSIDE tx ideally; see note
       │         │        │  4. read previous: findLatestByTenantAndCustomer
       │         │        │  5. UPSERT customer_health_scores (on unique (tenant,customer,date))
       │         │        │  6. INSERT into customer_health_history IF date-of-last-row != today
       │         │        │  7. publish events (HEALTH_SCORE_CHANGED / CHURN_RISK_DETECTED)
       │         │        │     ← BusinessEventPublisher.publish (synchronous today; fine inside tx)
       │         │        ── tx commit
       │         ▼
       │         aggregate per-tenant metrics
       ▼
  pg_advisory_unlock(HEALTH_SCORING_LOCK_KEY)
```

### Transaction boundaries

- **Outer orchestrator** (`HealthScoringJob.runNightly`,
  `HealthScoringOrchestrator.scoreTenant`) runs *without* a
  transaction so one slow customer or one LLM failure does not roll
  back the batch.
- **Inner per-customer worker** (`scoreOneCustomer`) uses
  `@Transactional(propagation = REQUIRES_NEW)` — each customer commits
  or rolls back on its own. The step numbers 1 + 4–7 execute inside
  the tx; the LLM call in step 3 is placed **before** the tx begins
  by splitting the method into two methods (one non-transactional
  that calls LLM, then delegates to a `@Transactional` persistence
  method). This keeps DB connections from being held across the
  multi-hundred-ms LLM RTT.
- **Serial vs parallel**: tenants are processed serially, customers
  within a tenant in parallel (bounded 8). Serial tenant iteration
  bounds LLM QPS to LiteLLM and simplifies per-tenant metrics.
  Parallel customer fan-out uses a `ThreadPoolTaskExecutor` bean
  (`healthScoringExecutor`, coreSize=8, queueCapacity=256).
- **Cluster safety**: a Postgres session-scoped advisory lock
  `pg_try_advisory_lock(813472)` at the start of `runNightly`. Key
  `813472` is documented in the migration as a constant. If the
  lock is unavailable, the non-leader exits with a single INFO log
  line. This is cheaper than pulling Quartz in; if we ever add a
  second scheduled job that needs leadership, we can migrate to
  ShedLock in one PR.

### On-demand recompute

```
POST /api/v1/admin/customers/{id}/health/recompute
  └── AdminHealthController (ADMIN role) → HealthScoringService.scoreOneCustomer(...)
        (reuses same method the nightly job uses; bypasses the advisory lock)
```

## Database changes

### Decision: new migration `V5__customer_health_scoring_job.sql` is required

Three reasons:

1. **Job-run audit**: we need a `health_scoring_job_runs` table so the
   job can see its own history (support ops need "when did the last
   run happen?") and so tests can assert idempotency deterministically
   without clock manipulation.
2. **Idempotency constraint**: the existing unique constraint on
   `customer_health_scores` is
   `UNIQUE(tenant_id, customer_id, calculated_at)`
   (`V3__business_modules.sql:400`). `calculated_at` is a
   `TIMESTAMPTZ` — two runs at different times create two rows. For
   idempotent *daily* behaviour we need a generated `scored_date DATE`
   column and a unique constraint on `(tenant_id, customer_id, scored_date)`.
3. **History guard**: `customer_health_history` (V3:408) lacks an
   index on `(tenant_id, customer_id, DATE(calculated_at))` which we
   need for the "did we already append a history row today?" check.

### V5 contents

```sql
-- V5__customer_health_scoring_job.sql

-- 1. Daily idempotency on customer_health_scores
ALTER TABLE customer_health_scores
  ADD COLUMN scored_date DATE
    GENERATED ALWAYS AS ((calculated_at AT TIME ZONE 'UTC')::date) STORED;

-- Drop old unique (which allowed multiple rows/day) and replace.
-- The V3 unique was (tenant_id, customer_id, calculated_at) — we keep
-- calculated_at but only allow one row per UTC day.
ALTER TABLE customer_health_scores
  DROP CONSTRAINT customer_health_scores_tenant_id_customer_id_calculated_at_key;

ALTER TABLE customer_health_scores
  ADD CONSTRAINT uq_health_scores_daily
  UNIQUE (tenant_id, customer_id, scored_date);

-- 2. History idempotency helper index
CREATE INDEX idx_health_history_daily
  ON customer_health_history(tenant_id, customer_id, ((calculated_at AT TIME ZONE 'UTC')::date));

-- 3. Job-run audit table
CREATE TABLE health_scoring_job_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES cc.tenants(id),    -- NULL = top-level run
    run_date        DATE NOT NULL,                     -- UTC scored_date
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    customers_scored INT NOT NULL DEFAULT 0,
    risk_transitions INT NOT NULL DEFAULT 0,
    llm_errors      INT NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'running',   -- running | success | partial | failed
    error_message   TEXT
);

CREATE INDEX idx_health_job_runs_date ON health_scoring_job_runs(run_date DESC);
CREATE INDEX idx_health_job_runs_tenant ON health_scoring_job_runs(tenant_id, run_date DESC);

-- 4. Document the advisory-lock key used by HealthScoringJob.
COMMENT ON TABLE health_scoring_job_runs IS
  'Cluster-wide advisory lock key for nightly scoring: 813472.';
```

**Hibernate-side implication**: `CustomerHealthScore` entity at
`src/main/java/com/changelog/business/success/model/CustomerHealthScore.java:21`
must get a new `scored_date` field mapped `insertable=false,
updatable=false` (generated column). Without it, `ddl-auto=validate`
fails because Hibernate sees an unmapped column.

### What we do NOT need

- No change to `customer_health_history` columns — its append-only
  schema already fits. The index is new, the table is not.
- No new column for LLM prompt/response auditing; if we need that
  later it goes in its own migration.

## Files to create or modify

### Create (production)

- `src/main/java/com/changelog/business/success/model/CustomerHealthHistory.java`
  New `@Entity` for `customer_health_history`. Fields: `id`,
  `tenantId`, `customerId`, `score`, `riskLevel`, `calculatedAt`.
  Needed so we can write history rows via JPA, not raw SQL.
- `src/main/java/com/changelog/business/success/repository/CustomerHealthHistoryRepository.java`
  Methods:
  - `Optional<CustomerHealthHistory> findTopByTenantIdAndCustomerIdAndCalculatedAtAfterOrderByCalculatedAtDesc(UUID tenantId, UUID customerId, LocalDateTime startOfDayUtc)`
    — used to decide "append history row today?"
  - Standard `save`, `findAll`.
- `src/main/java/com/changelog/business/success/model/HealthScoringJobRun.java`
  New `@Entity` for `health_scoring_job_runs`.
- `src/main/java/com/changelog/business/success/repository/HealthScoringJobRunRepository.java`
- `src/main/java/com/changelog/business/success/service/HealthScoringJob.java`
  The top-level `@Component` with the `@Scheduled` method. Owns the
  advisory lock. Calls `HealthScoringOrchestrator`.
- `src/main/java/com/changelog/business/success/service/HealthScoringOrchestrator.java`
  Per-tenant fan-out. Reads active customer ids from
  `StripeSubscriptionRepository.findActiveByTenantId` +
  `StripeCustomerRepository` (new — see below). Submits each customer
  to `healthScoringExecutor`. Maintains per-tenant metrics.
- `src/main/java/com/changelog/business/success/service/HealthSignalAggregator.java`
  Pure signal-extraction logic, unit-testable. Takes `(tenantId,
  customerId, Clock)` and returns `List<HealthSignal>`. Uses:
  - `AnalyticsEventRepository` (new queries, see below)
  - `SupportTicketRepository.findByCustomerId` +
    `findByTenantIdAndSentiment`
  - `StripeSubscriptionRepository.findActiveByCustomerId` for age
  - `NpsResponseRepository` (new — see below)
  - `StripeWebhookRepository` (new — see below; for payment_failed)
- `src/main/java/com/changelog/business/success/service/HealthActionRecommender.java`
  LiteLLM-backed recommender. Takes signals + score; returns
  `List<String>` actions. Falls back to the existing deterministic
  mapping in `CustomerHealthScoringService.generateRecommendedActions`
  on LLM error or when `health.scoring.llm.enabled=false`.
- `src/main/java/com/changelog/business/success/controller/AdminHealthController.java`
  `POST /api/v1/admin/customers/{customerId}/health/recompute`,
  secured `@PreAuthorize("hasAuthority('ADMIN')")`.
- `src/main/java/com/changelog/business/monetization/repository/StripeCustomerRepository.java`
  New — currently absent (confirmed: no file matches `StripeCustomer*`
  under `src/main/java`). Needed to resolve customer ids per tenant
  and to iterate customers that lack an active subscription (for
  optional coverage of trial/lapsed customers).
- `src/main/java/com/changelog/business/monetization/model/StripeCustomer.java`
  New entity mapping `stripe_customers` (V3:149). Likewise absent today.
- `src/main/java/com/changelog/business/monetization/repository/StripeWebhookRepository.java`
  New — queries `stripe_webhooks` for `event_type='invoice.payment_failed'`
  in the trailing 30 days for a given tenant/customer.
- `src/main/java/com/changelog/business/success/repository/NpsResponseRepository.java`
  New — reads `nps_responses` (V3:451).
- `src/main/java/com/changelog/business/success/model/NpsResponse.java`
  New entity — if missing today (verified: no matches for
  `NpsResponse` under `src/main/java`).
- `src/main/resources/db/migration/V5__customer_health_scoring_job.sql`
  Migration described above.

### Modify

- `src/main/java/com/changelog/business/success/model/CustomerHealthScore.java`
  Add `@Column(name = "scored_date", insertable = false, updatable = false) private LocalDate scoredDate;`
- `src/main/java/com/changelog/business/success/service/CustomerHealthScoringService.java`
  - Delete stubbed `analyzeLoginFrequency` … `analyzeFeatureAdoption`
    (lines 214–243). The real logic moves to `HealthSignalAggregator`.
  - Delete the stub `@Scheduled` method
    (`CustomerHealthScoringService.java:249-257`). Scheduling moves to
    `HealthScoringJob`.
  - Replace `calculateHealthScore(...)` body to delegate to the new
    pipeline: call `HealthSignalAggregator` + `HealthActionRecommender`
    + persist via repositories; emit events. Preserve method signature
    so existing webhook callers keep working.
  - Add overload `recompute(UUID tenantId, UUID customerId)` returning
    the persisted `CustomerHealthScore` for the admin endpoint.
- `src/main/java/com/changelog/business/orchestration/service/BusinessEventPublisher.java:133-144`
  `handleChurnRiskDetected` currently casts `recommendedActions` to
  `String[]` while the producer puts a `List<String>` in the map (see
  `CustomerHealthScoringService.java:85`). This throws `ClassCastException`
  the moment the event fires. Fix the cast to `List<String>`.
- `src/main/java/com/changelog/business/intelligence/repository/AnalyticsEventRepository.java`
  Add queries:
  - `long countByTenantIdAndUserIdAndEventNameAndOccurredAtAfter(UUID tenantId, UUID userId, String eventName, Instant since)`
    — login counts
  - `long countByTenantIdAndUserIdAndEventCategoryAndOccurredAtAfter(...)` — usage counts
  - `Optional<Instant> findLastOccurredAtByTenantIdAndUserIdAndEventName(...)`
- `src/main/resources/application.yml:75`
  Add under `ai:`:
  ```yaml
  health:
    scoring:
      cron: "0 30 2 * * *"
      llm:
        enabled: true
        timeout-ms: 10000
      max-concurrency: 8
  ```
- `src/main/java/com/changelog/config/` — new
  `HealthScoringExecutorConfig.java` defining the
  `healthScoringExecutor` `ThreadPoolTaskExecutor` bean.

### Tests

- `src/test/java/com/changelog/business/success/HealthSignalAggregatorTest.java`
  Unit test with fixed `Clock.fixed(...)`, in-memory fakes for each
  repository — asserts each signal produces the expected
  `(type, impact, description)` for fixed inputs.
- `src/test/java/com/changelog/business/success/HealthActionRecommenderTest.java`
  Mocks `LiteLlmApi`; asserts fallback path and happy path.
- `src/test/java/com/changelog/business/success/CustomerHealthHistoryRepositoryIT.java`
  `@DataJpaTest` slice, asserts append + no-double-append-same-day via
  `findTop...CalculatedAtAfter`.
- `src/test/java/com/changelog/business/success/HealthScoringJobIT.java`
  Full `@SpringBootTest` against the dev Postgres on 5433, seeds
  fixtures via SQL, runs the job twice, asserts:
  (1) one row per customer in `customer_health_scores`,
  (2) one row per customer in `customer_health_history` after two
  same-day runs,
  (3) two rows after the clock advances to the next UTC day (use
  `@MockBean Clock`).

## Implementation steps

Work top-down; each step is one small PR. Steps 1–3 are schema and
entity plumbing; 4–6 are business logic; 7–9 are job + API + tests.

1. **Write V5 migration.** Apply locally against the port-5433
   Postgres, confirm Flyway picks it up. Run `SchemaValidationIT`;
   expect it to fail on unmapped `scored_date` — this is step 2.

2. **Map `scored_date` on `CustomerHealthScore`.** Add the
   `@Column(insertable=false, updatable=false) LocalDate scoredDate`
   field. Re-run `SchemaValidationIT`; must pass.

3. **Create missing entities.** `StripeCustomer`, `NpsResponse`,
   `CustomerHealthHistory`, `HealthScoringJobRun`. Add their
   repositories. Re-run `SchemaValidationIT`; must pass.

4. **Write `HealthSignalAggregator`.** Inject:
   `AnalyticsEventRepository`, `SupportTicketRepository`,
   `StripeSubscriptionRepository`, `StripeWebhookRepository`,
   `NpsResponseRepository`, `Clock`. For each signal type, write one
   private method. Concrete mapping per signal:

   | Signal | Source | Query | Scoring rule |
   |---|---|---|---|
   | `login_frequency` | `analytics_events` where `event_name='user_login'` | count occurrences in trailing 14 days for `user_id=customerId` | 0 logins → impact −30; 1–2 → −10; ≥3 → 0 |
   | `usage_decline` | `analytics_events` where `event_category='retention'` or `event_name='api_call'` | compare count in trailing 7d vs prior 7d | drop >50% → −25; 25–50% → −15; <25% → 0 |
   | `support_tickets` | `support_tickets` for customer, status `open` or `in_progress` | `findByCustomerId` filtered by status | >3 open → −20; 1–3 → −10; 0 → 0 |
   | `support_sentiment` | `support_tickets.sentiment` | most recent 5 tickets | ≥2 `negative`/`angry` → −15 |
   | `payment_failed` | `stripe_webhooks` where `event_type='invoice.payment_failed'` in 30d | count | 1 → −10; ≥2 → −25 |
   | `subscription_age` | `stripe_subscriptions.created_at` | days since | ≥365d → +10; ≥90d → +5; <30d → 0 |
   | `feature_adoption` | `analytics_events` distinct `event_name` in 30d | count | ≥5 distinct → +10; 3–4 → +5; <3 → 0 |
   | `nps_score` | `nps_responses.score` | latest response | 9–10 → +10; 7–8 → 0; ≤6 → −15 |

   Signals explicitly marked **stubbed for now** if their source data
   isn't reliably populated by current webhook code: this plan
   assumes `analytics_events` is populated (it is, by
   `FunnelAnalyticsService` and the acquisition module). If during
   implementation a signal has no real source rows in dev fixtures,
   return a zero-impact "insufficient_data" signal rather than a fake
   value — the audit trail matters.

5. **Write `HealthActionRecommender`.** Two methods:
   `List<String> recommend(List<HealthSignal> signals, int score,
   String riskLevel)`. Prompt template:
   ```
   You are a customer success AI. Given these signals as JSON:
     <signals>
   The customer score is <score>/100 (risk: <riskLevel>).
   Return ONLY a JSON array of up to 5 action slugs drawn from:
   ["send_20_percent_off", "schedule_ceo_call",
    "create_retention_ticket", "send_checkin_email",
    "offer_extended_trial", "schedule_success_call",
    "send_feature_tips", "offer_upgrade_incentive",
    "send_reengagement_email", "resolve_outstanding_tickets",
    "update_payment_method"]
   No prose, no markdown.
   ```
   Parse exactly like `AiService.parseTitlesFromResponse`
   (`src/main/java/com/changelog/ai/AiService.java:76-96`). On any
   failure (HTTP error, parse error, timeout, or
   `health.scoring.llm.enabled=false`), fall back to the deterministic
   branching from the original
   `CustomerHealthScoringService.generateRecommendedActions`
   (`CustomerHealthScoringService.java:145-185`). Log at WARN and
   increment `health.scoring.llm.errors` counter.

6. **Rewrite `CustomerHealthScoringService.calculateHealthScore`.**
   Split into:
   - `calculateHealthScore(tenantId, customerId)` — public, keeps
     existing callers happy, runs the full pipeline non-transactionally
     (gathers signals, calls LLM) then calls...
   - `persistScore(tenantId, customerId, signals, score, riskLevel, actions, confidence)` —
     `@Transactional(REQUIRES_NEW)`, does UPSERT + history append +
     event publish.

   UPSERT strategy for `customer_health_scores`: look up by
   `(tenantId, customerId, today UTC)` via a new repo method
   `findByTenantIdAndCustomerIdAndScoredDate(tenantId, customerId, LocalDate)`.
   If present, mutate its fields and save (JPA merge); else insert new.
   History append: call
   `findTopByTenantIdAndCustomerIdAndCalculatedAtAfterOrderByCalculatedAtDesc`
   with `startOfDayUtc`; if empty, insert; else skip (idempotency).

   Fix the event cast bug in `BusinessEventPublisher.handleChurnRiskDetected`
   as part of this step (single-line change at
   `BusinessEventPublisher.java:135`). Include a smoke test that
   previously reproduced the `ClassCastException`.

7. **Write `HealthScoringOrchestrator` + `healthScoringExecutor`.**
   For a given tenant: query active customer ids, iterate, submit
   `scoreOneCustomer` to the executor, collect `Future`s, wait with
   a per-customer timeout of 30s (LLM is the only slow path; signal
   aggregation is <100ms of DB reads). Any per-customer exception is
   logged and counted; it never fails the tenant. Update
   `HealthScoringJobRun` row at end with counts.

   Also: implement an **LLM budget guard**. If `llm.errors` exceeds
   `max-errors-per-run` (default 20), disable LLM for the rest of the
   run — fall back to deterministic recommendations and log
   `"health.scoring.llm.circuit.open"`.

8. **Write `HealthScoringJob`.**
   ```java
   @Component
   @RequiredArgsConstructor
   public class HealthScoringJob {
       private final JdbcTemplate jdbc;          // for pg_try_advisory_lock
       private final TenantRepository tenantRepo;
       private final HealthScoringOrchestrator orchestrator;
       private final HealthScoringJobRunRepository runRepo;

       private static final long ADVISORY_LOCK_KEY = 813472L;

       @Scheduled(cron = "${health.scoring.cron:0 30 2 * * *}")
       public void runNightly() {
           Boolean acquired = jdbc.queryForObject(
               "SELECT pg_try_advisory_lock(?)", Boolean.class, ADVISORY_LOCK_KEY);
           if (!Boolean.TRUE.equals(acquired)) {
               log.info("health.scoring.lock.skipped");
               return;
           }
           try {
               for (UUID tenantId : tenantRepo.findAllIds()) {
                   orchestrator.scoreTenant(tenantId);
               }
           } finally {
               jdbc.queryForObject(
                   "SELECT pg_advisory_unlock(?)", Boolean.class, ADVISORY_LOCK_KEY);
           }
       }
   }
   ```
   `@EnableScheduling` is already on
   `ChangelogPlatformApplication.java:11`, so no wiring needed.

9. **Add `AdminHealthController`.** Single endpoint. Invoke
   `customerHealthScoringService.calculateHealthScore(tenantId, customerId)`
   (tenant resolved either from JWT or, for cross-tenant admins, from
   a query param — match whatever pattern the existing admin
   controllers use; if none exist, default to tenant-from-JWT and
   require admins to act inside a tenant context).

10. **Write tests per the *Test plan* section.** Do this as the final
    step before merging; they are the acceptance gate.

11. **Manual smoke.** `docker compose up -d postgres`, `mvn spring-boot:run`,
    seed one tenant + three customers with varying signals, call the
    recompute endpoint, observe DB state and logs. Then let the cron
    fire (or temporarily set `health.scoring.cron` to `*/30 * * * * *`)
    and verify identical behaviour.

## Test plan

### Unit tests (deterministic, fast)

- `HealthSignalAggregatorTest` — fixed `Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), UTC)`.
  Mocked repositories. One test per signal type with three inputs
  (green / yellow / red) asserting the returned `HealthSignal.impact`
  exactly. Total ~24 test methods; must complete in <1s.
- `HealthActionRecommenderTest` — MockBean `LiteLlmApi`.
  - returns well-formed JSON → parsed list returned
  - returns malformed JSON → fallback list
  - throws IOException → fallback list
  - `llm.enabled=false` → fallback without calling API (verify no
    interaction)
- `CustomerHealthScoringServiceTest` — MockBean everything; asserts
  event-publishing rules: `HEALTH_SCORE_CHANGED` only on score delta,
  `CHURN_RISK_DETECTED` only on tier escalation, no events on flat
  re-run.

### Repository slice tests (`@DataJpaTest` against dev Postgres 5433)

- `CustomerHealthHistoryRepositoryIT` — insert one row at
  `2026-05-01T10:00:00Z`, call `findTop...CalculatedAtAfter(startOfDayUtc=2026-05-01T00:00:00Z)`,
  assert present. Same call with `startOfDayUtc=2026-05-02T00:00:00Z`
  returns empty.
- `CustomerHealthScoreRepositoryIT` — exercise the new UPSERT-style
  method `findByTenantIdAndCustomerIdAndScoredDate` — confirm the
  generated column `scored_date` is populated by the DB without JPA
  touching it.

### Job-runner integration test (`@SpringBootTest`)

- `HealthScoringJobIT` — **must** assert no double-write.
  1. Seed tenant T, customers C1…C3 with varying signals.
  2. Stub `Clock` to `2026-05-01T02:30:00Z`.
  3. Invoke `healthScoringJob.runNightly()` directly (don't wait for
     cron; just bypass `@Scheduled`).
  4. Assert `customer_health_scores` has 3 rows, `customer_health_history`
     has 3 rows.
  5. Invoke `runNightly()` a second time with the same clock.
  6. Assert `customer_health_scores` still has 3 rows (UPSERT), but
     `calculated_at` advanced. Assert `customer_health_history` still
     has 3 rows (append skipped).
  7. Advance clock to `2026-05-02T02:30:00Z`. Invoke again.
  8. Assert `customer_health_scores` now has 6 rows (one per day per
     customer), `customer_health_history` has 6 rows.
  9. With two `HealthScoringJob` beans (one per test application
     context, both pointing at same DB), invoke in parallel and
     assert only one advances `customers_scored` in
     `health_scoring_job_runs`. The other logs
     `health.scoring.lock.skipped`.

### Non-regression

- `SchemaValidationIT` — already exists and *must stay green* after
  V5 applies and after the new entities are added. Run it as the
  gate on every step.

## Risks & open questions

### Risks

1. **Large-tenant fan-out.** A single tenant with 10k active
   customers at 60ms/customer + a 300ms LLM call each = ~1h wall time
   serially. Mitigations:
   - Customer-level parallelism (executor, core=8) cuts that to ~7min.
   - LLM circuit-breaker at 20 errors per run prevents a LiteLLM
     outage from dragging the job out.
   - The advisory lock is released only after the whole run, so a
     long run blocks the next nightly tick — acceptable because cron
     is 24h apart.
   - Future: batch score+signals in the prompt (score 10 customers
     per LLM call) or sample — out of scope here.

2. **LLM latency/cost per recompute.** LiteLLM via Retrofit is a
   synchronous HTTP call; default timeout is effectively unbounded.
   We set `health.scoring.llm.timeout-ms: 10000`. Cost: one
   `chat.completions` call per customer per day. At gpt-4 pricing
   with the short prompt above this is cheap (~$0.002/customer/day);
   for tenants with 100k customers this becomes $200/day — switch
   the model to `gpt-4o-mini` via `ai.model` override or
   `health.scoring.llm.model` if we add a scoped property. Flagged as
   **open question**: do we want a per-health-job model override?

3. **Scheduler lock across multiple app instances.** Solved with
   `pg_try_advisory_lock(813472)`. Evaluated ShedLock; rejected for
   now because it needs a new table and dep for a single lock point.
   If a second cluster-wide scheduled job appears, revisit.

4. **Timezone drift.** "Daily" is defined as UTC (the generated
   `scored_date` casts `calculated_at AT TIME ZONE 'UTC'`). Any
   timezone policy change for end-user display is a presentation
   concern, not a scoring concern — the idempotency key stays UTC.

5. **Signals queryable but empty in dev.** If a dev DB has no
   `analytics_events`, every customer scores 100 with
   "insufficient_data" signals, and every recompute emits zero
   `CHURN_RISK_DETECTED` events. This is correct behaviour but may
   surprise QA; noted in acceptance criteria (#1 expects signals
   populated in *test fixtures*, not a fresh DB).

6. **Event-publisher `ClassCastException`.** Existing bug at
   `BusinessEventPublisher.java:135` — today's producer already sends
   `List<String>`. Fix lands with this plan; pre-existing event
   emitters in `CustomerHealthScoringService` would have crashed the
   handler had they fired. Verified by the unit test in step 6.

7. **`customer_health_scores.signals` is marked `NOT NULL` (V3:379).**
   The existing builder call at
   `CustomerHealthScoringService.java:49` passes `null`, then sets
   signals via setter at line 58 before save. This works by accident
   of Lombok builder order. The new code sets `signals` directly in
   the builder; risk avoided.

8. **`StripeCustomer` entity does not exist today.** Creating it
   now means `SchemaValidationIT` will start validating its columns.
   Any drift between `V3:149-166` and our new entity will break CI.
   Remediation: mirror the migration columns exactly; run the test
   as part of step 3.

### Open questions for the implementer

- Should `recommended_actions` be empty for `low` risk customers
  (today the deterministic code returns `[]`), or should we always
  populate a "grow the account" suggestion? Default to empty for
  parity with existing behaviour; product can change later.
- Admin endpoint tenant scoping — does `ADMIN` authority carry a
  specific tenant or cross-tenant? Match the existing
  `/api/v1/admin/...` conventions if any; otherwise default to
  tenant-from-JWT and require the customer to belong to that tenant.
- Confirm `AI_GATEWAY_URL` is reachable from prod pods at 02:30 UTC
  (no scheduled maintenance window collision).

## Definition of done

- [ ] `V5__customer_health_scoring_job.sql` applied; `mvn test -Dtest=SchemaValidationIT` green.
- [ ] New entities (`StripeCustomer`, `NpsResponse`,
      `CustomerHealthHistory`, `HealthScoringJobRun`) validated by
      Hibernate under `ddl-auto=validate`.
- [ ] `CustomerHealthScoringService.calculateHealthScore` pulls real
      signals (no TODO comments remaining in
      `CustomerHealthScoringService.java`).
- [ ] `HealthScoringJob.runNightly` executes on schedule and is
      advisory-lock-guarded.
- [ ] `customer_health_history` receives exactly one row per
      customer per UTC day (verified by `HealthScoringJobIT`).
- [ ] `recommended_actions` column populated via LiteLLM with
      deterministic fallback; LLM errors counted, not fatal.
- [ ] `BusinessEventPublisher.handleChurnRiskDetected` cast bug fixed;
      `CHURN_RISK_DETECTED` fires exactly on tier escalation.
- [ ] `POST /api/v1/admin/customers/{id}/health/recompute` returns
      200 for admins, 403 otherwise.
- [ ] Running the job twice in the same UTC day does not duplicate
      rows (verified by IT).
- [ ] Two app instances against the same DB do not double-write
      (verified by IT).
- [ ] All new code has unit and IT test coverage per *Test plan*.
- [ ] `docs/plans/04-customer-health-scoring.md` (this file) linked
      from any running index of plans (out of scope here if no such
      index exists).
