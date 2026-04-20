# 05 — Drip Campaigns: Enrollment + Step-Execution Worker

## Overview

V4 laid down two tables (`drip_campaigns`, `drip_enrollments`) and the skeleton `DripCampaignService` (`src/main/java/com/changelog/business/retention/service/DripCampaignService.java:21`) already polls for due enrollments on an hourly cron and logs a stubbed `DRIP EMAIL:` line. Nothing in the product actually enrolls subscribers automatically, nothing sends real email, nothing survives a crash mid-batch, and nothing prevents two application instances from double-claiming the same enrollment row.

This plan finishes the retention module: a real campaign authoring API, a trigger-registration mechanism based on Spring `ApplicationEventPublisher`, a poll-loop worker that uses `SELECT ... FOR UPDATE SKIP LOCKED` for multi-instance safety, idempotent step execution with retry + DLQ semantics, and an explicit enrollment state machine.

Non-trivial constraints carried from the environment:

- Spring Boot 3.3.5, Java 21.
- Flyway is source of truth — the next migration must be `V5__drip_campaign_runs_and_locking.sql`.
- Hibernate `ddl-auto=validate` and `SchemaValidationIT` (`src/test/java/com/changelog/SchemaValidationIT.java:44`) must stay green, which means every new column on an existing table and every new table must have a matching `@Entity`/`@Column`.
- Postgres 16 on host port **5433** (see `README.md:217-228`).
- Outbound email uses `JavaMailSender` exactly as `SubscriberNotificationService` (`src/main/java/com/changelog/service/SubscriberNotificationService.java:25`) already does.
- `@EnableScheduling` + `@EnableAsync` are already declared on `ChangelogPlatformApplication` (`src/main/java/com/changelog/ChangelogPlatformApplication.java:11-12`), so no config changes needed to register a cron.

## Goals & Non-goals

**Goals**

1. Admin REST for creating, listing, updating, pausing, archiving drip campaigns.
2. Admin REST for manually enrolling a customer into a campaign.
3. Automatic enrollment on (a) subscriber signup (`Subscriber` row inserted with `ACTIVE`), (b) Stripe subscription transitioning `trialing → active`, (c) Stripe `subscription_created`, (d) Stripe `subscription_canceled` — via a published-event/listener pattern so new triggers don't touch the worker.
4. A per-minute `@Scheduled` worker that claims due enrollments with `FOR UPDATE SKIP LOCKED`, renders the step template, sends a real email via `JavaMailSender`, and advances state in a single transaction.
5. A formal state machine for `drip_enrollments.status` (`PENDING → ACTIVE → COMPLETED | EXITED | FAILED`) with transitions protected at the service layer.
6. Idempotency: a step is never sent twice, even if the worker crashes mid-batch.
7. Retry with exponential backoff (max N attempts) and a Dead-Letter status (`FAILED`) when attempts are exhausted.
8. A per-step audit trail (`drip_campaign_runs`) so we can prove "step 2 of campaign X was sent to customer Y at time T" and debug retries.
9. Template rendering via Spring's `StringSubstitutor` (Apache Commons Text is transitive via Spring) or Spring's own SpEL — but we will standardize on **`org.springframework.util.PropertyPlaceholderHelper`** (already on classpath, no new dependency) to keep the dependency footprint flat.

**Non-goals**

- Open/click tracking (that's the `EMAIL_OPENED`/`EMAIL_CLICKED` events in `BusinessEvent.BusinessEventType` — separate plan).
- A visual campaign builder / WYSIWYG.
- Unsubscribe tokens per enrollment (subscriber-level opt-out on `changelog_subscribers` already exists; we honor it but don't generate per-email tokens here).
- Multi-channel delivery (SMS, push, in-app). Channel field stays as a string so we can extend, but only `email` is wired.
- Running campaigns against arbitrary customer lists (segments); enrollment is always per-customer.

## Acceptance criteria

1. `POST /api/v1/admin/drip-campaigns` with a JSON body defining `name`, `triggerEvent`, `status`, and an ordered `steps[]` (each with `stepNumber`, `delayDays`, `subject`, `bodyTemplate`, `channel`) persists the campaign and returns `201 Created` with the campaign UUID.
2. `POST /api/v1/admin/drip-campaigns/{id}/enroll` with body `{customerId, email}` inserts a new `drip_enrollments` row, sets `next_step_at` to `now() + steps[0].delayDays`, and returns `200 OK` with the enrollment id. Re-calling the endpoint with the same `customerId` is idempotent (returns the existing enrollment, no duplicate row).
3. Creating a `Subscriber` row with `status = ACTIVE` causes `DripEnrollmentService.processEnrollmentTrigger(tenantId, "subscriber_signup", ...)` to fire via a Spring event published by `SubscriberService.subscribe(...)`. Stripe webhook handling for `customer.subscription.updated` with previous status `trialing` and new status `active` publishes a `trial_converted` event, which enrolls the customer.
4. The worker runs every 60 seconds (cron `0 * * * * *`). It claims at most 50 rows per tick using `SELECT ... FOR UPDATE SKIP LOCKED` and processes them in a single transaction per row. Running two instances of the app simultaneously never results in the same step being sent twice (verified by the concurrency IT in the test plan).
5. Each step execution inserts a `drip_campaign_runs` row with `status='sent' | 'failed'` and `attempt_count`. A failed send bumps `attempt_count`, schedules a retry with exponential backoff (`1m, 5m, 30m, 2h, 12h` for attempts 1–5), and after the 5th failure sets the enrollment to `FAILED` and stops retrying.
6. A broken template (e.g. missing placeholder, runtime exception) fails only the current enrollment's current step; it does not poison the whole batch or the cron.
7. Hibernate `ddl-auto=validate` passes on boot. `SchemaValidationIT` passes.
8. An admin can `POST /api/v1/admin/drip-enrollments/{id}/pause` and `POST .../resume`; worker skips paused enrollments.
9. Template rendering supports `{firstName}`, `{email}`, `{campaignName}`, `{stepNumber}`, `{unsubscribeUrl}` placeholders. Unresolved placeholders render as empty string (not `${firstName}`).
10. Unit test `DripEnrollmentServiceTest#advancesThroughAllSteps` drives a fresh enrollment through N steps using a `Clock` fake and asserts terminal state is `COMPLETED`.

## User-facing surface

### REST (admin, behind `ROLE_ADMIN` once Keycloak is enabled; `permitAll` on `local` profile)

```
POST   /api/v1/admin/drip-campaigns                      Create campaign (with steps)
GET    /api/v1/admin/drip-campaigns                      List for current tenant
GET    /api/v1/admin/drip-campaigns/{id}                  Single campaign + steps
PUT    /api/v1/admin/drip-campaigns/{id}                  Update (name, trigger, steps, status)
POST   /api/v1/admin/drip-campaigns/{id}/pause            status = paused
POST   /api/v1/admin/drip-campaigns/{id}/archive          status = archived
DELETE /api/v1/admin/drip-campaigns/{id}                  Hard delete (cascades enrollments)

POST   /api/v1/admin/drip-campaigns/{id}/enroll           {customerId, email, firstName?} → idempotent
GET    /api/v1/admin/drip-campaigns/{id}/enrollments      List enrollments for campaign
GET    /api/v1/admin/drip-enrollments/{id}                Single enrollment (with run history)
POST   /api/v1/admin/drip-enrollments/{id}/pause          status = PAUSED
POST   /api/v1/admin/drip-enrollments/{id}/resume         status = ACTIVE
POST   /api/v1/admin/drip-enrollments/{id}/exit           status = EXITED (customer unsub, etc.)
```

### Request/response shape (create campaign)

```json
POST /api/v1/admin/drip-campaigns
{
  "name": "Trial → Paid onboarding",
  "triggerEvent": "trial_converted",
  "status": "active",
  "steps": [
    {
      "stepNumber": 1,
      "delayDays": 0,
      "channel": "email",
      "subject": "Welcome, {firstName}!",
      "bodyTemplate": "Hi {firstName},\n\nYou just upgraded to paid. Here's what to do first…"
    },
    {
      "stepNumber": 2,
      "delayDays": 3,
      "channel": "email",
      "subject": "Have you tried integrations yet?",
      "bodyTemplate": "Hi {firstName}, one of our most-used features is…"
    }
  ]
}
```

### Trigger registration (programmatic, not user-facing)

Other modules publish `DripTriggerEvent` on `ApplicationEventPublisher`. The listener in `DripEnrollmentService` looks up all active campaigns for `(tenantId, triggerEvent)` and enrolls. Publishers today will be: `SubscriberService` (after insert), `StripeWebhookService` (after trial→active detection and on created/canceled).

## Architecture & data flow

### Package layout (fits the existing `business/retention/**` tree)

```
business/retention/
├── config/
│   └── RetentionProperties.java           @ConfigurationProperties("retention")
├── controller/
│   ├── DripCampaignAdminController.java   /api/v1/admin/drip-campaigns/**
│   └── DripEnrollmentAdminController.java /api/v1/admin/drip-enrollments/**
├── dto/
│   ├── CreateDripCampaignRequest.java
│   ├── UpdateDripCampaignRequest.java
│   ├── DripCampaignResponse.java
│   ├── EnrollCustomerRequest.java
│   └── DripEnrollmentResponse.java
├── event/
│   └── DripTriggerEvent.java              tenantId, triggerEvent, customerId, email, attrs
├── listener/
│   └── DripTriggerListener.java           @EventListener → DripEnrollmentService.enroll
├── model/
│   ├── DripCampaign.java                  (exists)
│   ├── DripEnrollment.java                (exists — extend with new cols)
│   ├── DripStep.java                      (exists — add channel)
│   ├── DripCampaignRun.java               NEW — audit/run history
│   ├── EnrollmentStatus.java              enum: PENDING, ACTIVE, PAUSED, COMPLETED, EXITED, FAILED
│   └── CampaignStatus.java                enum: ACTIVE, PAUSED, ARCHIVED
├── repository/
│   ├── DripCampaignRepository.java        (exists)
│   ├── DripEnrollmentRepository.java      (exists — add claim query)
│   └── DripCampaignRunRepository.java     NEW
├── service/
│   ├── DripCampaignService.java           (exists — split: campaign CRUD only)
│   ├── DripEnrollmentService.java         NEW — enrollment + trigger handling
│   ├── DripStepExecutor.java              NEW — renders + sends a single step
│   ├── DripWorker.java                    NEW — @Scheduled poll loop
│   └── DripTemplateRenderer.java          NEW — PropertyPlaceholderHelper wrapper
└── (no new config — reuses ChangelogPlatformApplication's @EnableScheduling)
```

### Enrollment state machine

```
                     enrollCustomer()
                           │
                           ▼
                      ┌─────────┐
                      │ PENDING │   (row inserted, not yet sent step 1)
                      └────┬────┘
                           │  worker tick #1 claims row
                           │  step 1 send SUCCESS
                           ▼
                      ┌─────────┐                ┌────────┐
   admin pause ────►  │ ACTIVE  │ ◄───────────── │ PAUSED │
                      └────┬────┘   admin resume └────────┘
                           │
         ┌─────────────────┼───────────────────┐
         │                 │                   │
 send SUCCESS &        send FAILS        admin exit /
 more steps left       attempt < max     customer unsub
         │                 │                   │
         │ (stay ACTIVE,   │ (stay ACTIVE,     │
         │  advance step,  │  bump attempt,    │
         │  schedule next) │  backoff sched)   │
         │                 │                   │
         │            ┌────┴────┐              │
         │            │         │              │
         │     attempts < max   attempts = max │
         │            │         │              │
         │            │         ▼              ▼
         │            │    ┌────────┐    ┌────────┐
         │            │    │ FAILED │    │ EXITED │
         │            │    └────────┘    └────────┘
         │            │
         └──► no more steps
              │
              ▼
         ┌──────────┐
         │ COMPLETED│
         └──────────┘

Invariant: PENDING/ACTIVE/PAUSED are "live"; COMPLETED/EXITED/FAILED are terminal.
Worker only touches rows where status IN ('PENDING','ACTIVE') AND next_step_at <= now().
```

### Worker poll-loop sequence

```
every 60s (cron "0 * * * * *"):

  1. DripWorker.tick()
       │
       ▼
  2. [TX #1: claim]
       BEGIN;
       SELECT id, tenant_id, campaign_id, current_step, attempt_count, ...
         FROM drip_enrollments
         WHERE status IN ('PENDING','ACTIVE')
           AND next_step_at <= now()
           AND (locked_until IS NULL OR locked_until < now())
         ORDER BY next_step_at ASC
         LIMIT 50
         FOR UPDATE SKIP LOCKED;
       UPDATE drip_enrollments
         SET locked_until = now() + INTERVAL '5 minutes',
             locked_by    = :instanceId
         WHERE id IN (...ids claimed above...);
       COMMIT;
       │
       ▼
  3. For each claimed enrollment (parallel-safe — this instance owns it for 5 min):
       │
       ├─► [TX #2: execute + advance]  DripStepExecutor.execute(enrollment)
       │     BEGIN;
       │     a. Load campaign (cached) + step[current_step]
       │     b. INSERT drip_campaign_runs (enrollment_id, step_number, status='attempting', attempt=N)
       │     c. Render template via DripTemplateRenderer
       │     d. mailSender.send(SimpleMailMessage)
       │     e. On success:
       │          UPDATE drip_campaign_runs  SET status='sent', sent_at=now();
       │          UPDATE drip_enrollments
       │            SET current_step  = current_step + 1,
       │                attempt_count = 0,
       │                next_step_at  = next_step_of(campaign, current_step + 1),
       │                status        = CASE WHEN no more steps THEN 'COMPLETED' ELSE 'ACTIVE' END,
       │                completed_at  = CASE WHEN no more steps THEN now() END,
       │                locked_until  = NULL;
       │     f. On failure (exception from render/send):
       │          UPDATE drip_campaign_runs SET status='failed', error_message=..., failed_at=now();
       │          UPDATE drip_enrollments
       │            SET attempt_count = attempt_count + 1,
       │                next_step_at  = now() + backoff(attempt_count + 1),
       │                status        = CASE WHEN attempt+1 >= max_attempts THEN 'FAILED' ELSE status END,
       │                locked_until  = NULL;
       │     COMMIT;
       │
       ▼
  4. Any unhandled exception in the per-enrollment block is caught at DripWorker,
     logged, and the loop continues to the next claimed row. locked_until
     expires after 5 min so a worker that dies between claim and execute
     doesn't permanently jam the enrollment.
```

Key point: **the claim transaction is tiny** — it only runs the SELECT + UPDATE locked_until. The per-enrollment transaction is separate and bounded (no cross-enrollment work in one transaction). This keeps `FOR UPDATE SKIP LOCKED` cheap and predictable.

### Trigger registration (event-driven)

```
┌──────────────────────┐                    ┌───────────────────────┐
│  SubscriberService   │                    │  StripeWebhookService │
│   .subscribe()       │                    │   .handle*()          │
└──────────┬───────────┘                    └───────────┬───────────┘
           │  applicationEventPublisher.publishEvent   │
           │  DripTriggerEvent("subscriber_signup",    │
           │                    tenantId, customerId,  │
           │                    email)                 │
           │                                           │
           └────────────────────┬──────────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │ DripTriggerListener   │
                    │  @EventListener       │
                    │  @Async               │   (fire-and-forget;
                    │                       │    publisher tx commits
                    │                       │    before listener runs
                    │                       │    because @TransactionalEventListener
                    │                       │    phase = AFTER_COMMIT)
                    └───────────┬───────────┘
                                │
                                ▼
                    DripEnrollmentService
                      .processEnrollmentTrigger(
                         tenantId, triggerEvent,
                         customerId, email, attrs)
                         │
                         └─► for each active campaign matching
                             (tenantId, triggerEvent): enrollCustomer()
```

Why `@TransactionalEventListener(phase=AFTER_COMMIT)` and not plain `@EventListener`: we don't want to enroll into a drip if the signup transaction rolls back. Why `@Async`: enrollment should not block the HTTP request that triggered signup.

### Template rendering

Use `org.springframework.util.PropertyPlaceholderHelper` (already on classpath via Spring Core — no new dependency):

```java
PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("{", "}", null, true);
String rendered = helper.replacePlaceholders(bodyTemplate, placeholderMap::get);
```

Supported placeholders (map is built per-enrollment in `DripTemplateRenderer`):

- `{firstName}` — from enrollment attrs or subscriber row
- `{email}` — enrollment.customerEmail
- `{campaignName}` — campaign.name
- `{stepNumber}` — current step number
- `{unsubscribeUrl}` — `${app.public-url}/changelog/{projectSlug}/unsubscribe` (derived when available, else empty)

`ignoreUnresolvablePlaceholders=true` means `{unknownKey}` renders as empty rather than throwing. A deliberately malformed template (e.g. stray `{`) throws a `TemplateRenderException`, which the executor catches and treats as a normal step failure (retry → eventual `FAILED`), so one broken campaign can't poison the worker.

## Database changes

### Audit of V4 schema

`V4__drip_campaigns.sql` is **insufficient** for production. Gaps:

| Gap | Consequence | Fix |
|---|---|---|
| No FK from `drip_campaigns.tenant_id` to `cc.tenants(id)` | Orphaned rows on tenant delete | Add `REFERENCES cc.tenants(id) ON DELETE CASCADE` |
| No FK from `drip_enrollments.tenant_id` to `cc.tenants(id)` | Same | Same |
| No `UNIQUE(customer_id, campaign_id)` on enrollments | Duplicate enrollment races on concurrent triggers | Add unique constraint |
| No `attempt_count`, `last_error`, `locked_until`, `locked_by` on enrollments | Can't retry safely, can't use SKIP LOCKED with lease | Add via V5 |
| No per-step run history | Can't debug "did step 2 actually send?" | Add `drip_campaign_runs` table |
| `DripStep` JSON has no `channel` field | Can't extend to SMS/push later without breaking | Add to JSON shape (no migration — it's JSONB — but update entity + docs) |
| `drip_campaigns.status` is free-text, not CHECK-constrained | Typos accepted silently | Add `CHECK (status IN ('active','paused','archived'))` |
| `drip_enrollments.status` likewise | Same | Add CHECK with new enum values |
| No index on `(tenant_id, customer_id)` for "all campaigns for this user" admin lookups | Slow admin UI once > 100k rows | Add index |

### V5 migration (full DDL)

```sql
-- V5__drip_campaign_runs_and_locking.sql

-- 1. FKs retroactively
ALTER TABLE drip_campaigns
    ADD CONSTRAINT fk_drip_campaigns_tenant
        FOREIGN KEY (tenant_id) REFERENCES cc.tenants(id) ON DELETE CASCADE;

ALTER TABLE drip_enrollments
    ADD CONSTRAINT fk_drip_enrollments_tenant
        FOREIGN KEY (tenant_id) REFERENCES cc.tenants(id) ON DELETE CASCADE;

-- 2. Uniqueness (idempotent enrollment)
ALTER TABLE drip_enrollments
    ADD CONSTRAINT uq_drip_enrollments_customer_campaign
        UNIQUE (customer_id, campaign_id);

-- 3. Retry + locking columns
ALTER TABLE drip_enrollments
    ADD COLUMN attempt_count  INT          NOT NULL DEFAULT 0,
    ADD COLUMN last_error     TEXT,
    ADD COLUMN locked_until   TIMESTAMPTZ,
    ADD COLUMN locked_by      TEXT;

-- 4. CHECK constraints (relax existing free-text enums)
ALTER TABLE drip_campaigns
    ADD CONSTRAINT ck_drip_campaigns_status
        CHECK (status IN ('active','paused','archived'));

-- Map legacy values before locking down.
UPDATE drip_enrollments SET status = 'ACTIVE'    WHERE status = 'active';
UPDATE drip_enrollments SET status = 'COMPLETED' WHERE status = 'completed';
UPDATE drip_enrollments SET status = 'EXITED'    WHERE status = 'unsubscribed';

ALTER TABLE drip_enrollments
    ADD CONSTRAINT ck_drip_enrollments_status
        CHECK (status IN ('PENDING','ACTIVE','PAUSED','COMPLETED','EXITED','FAILED'));

-- 5. Index for admin "all campaigns for this customer" lookup
CREATE INDEX idx_drip_enrollments_tenant_customer
    ON drip_enrollments(tenant_id, customer_id);

-- 6. Index to support the worker claim query
--    (status, next_step_at) already exists as idx_drip_enrollments_status_next;
--    we extend it to include locked_until so the claim is a single-index scan.
DROP INDEX IF EXISTS idx_drip_enrollments_status_next;
CREATE INDEX idx_drip_enrollments_claim
    ON drip_enrollments(status, next_step_at, locked_until)
    WHERE status IN ('PENDING','ACTIVE');

-- 7. Per-step run history (audit trail, powers retry logic & debugging)
CREATE TABLE drip_campaign_runs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   UUID        NOT NULL REFERENCES drip_enrollments(id) ON DELETE CASCADE,
    tenant_id       UUID        NOT NULL REFERENCES cc.tenants(id) ON DELETE CASCADE,
    campaign_id     UUID        NOT NULL REFERENCES drip_campaigns(id) ON DELETE CASCADE,
    step_number     INT         NOT NULL,
    attempt         INT         NOT NULL DEFAULT 1,
    status          TEXT        NOT NULL,   -- attempting | sent | failed
    channel         TEXT        NOT NULL DEFAULT 'email',
    rendered_subject TEXT,
    error_message   TEXT,
    attempted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    CONSTRAINT ck_drip_campaign_runs_status CHECK (status IN ('attempting','sent','failed'))
);

CREATE INDEX idx_drip_runs_enrollment ON drip_campaign_runs(enrollment_id, step_number, attempt);
CREATE INDEX idx_drip_runs_tenant_time ON drip_campaign_runs(tenant_id, attempted_at DESC);
```

### Entity changes required to keep `ddl-auto=validate` green

- `DripEnrollment`: add `@Column Integer attemptCount`, `@Column String lastError`, `@Column LocalDateTime lockedUntil`, `@Column String lockedBy`. Change `status` to `@Enumerated(EnumType.STRING) EnumerationStatus status` with the new enum (and update the default).
- `DripCampaign`: change `status` to `@Enumerated(EnumType.STRING) CampaignStatus status`.
- New `DripCampaignRun` entity mapped to `drip_campaign_runs`.
- `DripStep` POJO: add `String channel` field (default `"email"` in builder).

The enum-as-string mapping has to match the CHECK constraint values exactly — this is why the V5 migration includes the `UPDATE` statements to normalize legacy `active`/`completed`/`unsubscribed` text values to the new upper-case enum names.

## Files to create or modify

**Create**

- `src/main/resources/db/migration/V5__drip_campaign_runs_and_locking.sql`
- `src/main/java/com/changelog/business/retention/model/CampaignStatus.java`
- `src/main/java/com/changelog/business/retention/model/EnrollmentStatus.java`
- `src/main/java/com/changelog/business/retention/model/DripCampaignRun.java`
- `src/main/java/com/changelog/business/retention/repository/DripCampaignRunRepository.java`
- `src/main/java/com/changelog/business/retention/event/DripTriggerEvent.java`
- `src/main/java/com/changelog/business/retention/listener/DripTriggerListener.java`
- `src/main/java/com/changelog/business/retention/service/DripEnrollmentService.java`
- `src/main/java/com/changelog/business/retention/service/DripStepExecutor.java`
- `src/main/java/com/changelog/business/retention/service/DripWorker.java`
- `src/main/java/com/changelog/business/retention/service/DripTemplateRenderer.java`
- `src/main/java/com/changelog/business/retention/config/RetentionProperties.java`
- `src/main/java/com/changelog/business/retention/controller/DripCampaignAdminController.java`
- `src/main/java/com/changelog/business/retention/controller/DripEnrollmentAdminController.java`
- `src/main/java/com/changelog/business/retention/dto/CreateDripCampaignRequest.java`
- `src/main/java/com/changelog/business/retention/dto/UpdateDripCampaignRequest.java`
- `src/main/java/com/changelog/business/retention/dto/DripCampaignResponse.java`
- `src/main/java/com/changelog/business/retention/dto/EnrollCustomerRequest.java`
- `src/main/java/com/changelog/business/retention/dto/DripEnrollmentResponse.java`
- `src/test/java/com/changelog/business/retention/service/DripEnrollmentServiceTest.java`
- `src/test/java/com/changelog/business/retention/service/DripWorkerConcurrencyIT.java`
- `src/test/java/com/changelog/business/retention/service/DripTemplateRendererTest.java`

**Modify**

- `src/main/java/com/changelog/business/retention/model/DripCampaign.java` — swap `String status` for `CampaignStatus` enum.
- `src/main/java/com/changelog/business/retention/model/DripEnrollment.java` — add retry/lock columns, swap status for enum.
- `src/main/java/com/changelog/business/retention/model/DripStep.java` — add `channel` field.
- `src/main/java/com/changelog/business/retention/repository/DripEnrollmentRepository.java` — add `claimDueEnrollments(int limit)` native query with `FOR UPDATE SKIP LOCKED`.
- `src/main/java/com/changelog/business/retention/service/DripCampaignService.java` — strip worker + execute code (move to `DripEnrollmentService` + `DripStepExecutor` + `DripWorker`), leave CRUD only.
- `src/main/java/com/changelog/business/monetization/service/StripeWebhookService.java` (`:107-109`, `:137-140`) — stop calling `dripCampaignService.processEnrollmentTrigger(...)` directly; publish `DripTriggerEvent` instead. Detect `trialing → active` transition in `handleSubscriptionEvent` and publish `trial_converted` event.
- `src/main/java/com/changelog/service/SubscriberService.java` (if present — else the subscribe controller) — publish `DripTriggerEvent("subscriber_signup", ...)` after inserting a `Subscriber` with `ACTIVE`. Note: `Subscriber` has no `tenantId` column (`src/main/java/com/changelog/model/Subscriber.java:22`); resolve tenant via `projectRepository.findById(projectId).getTenantId()` before publishing.
- `src/main/resources/application.yml` — add `retention:` block (see below).

### New `application.yml` keys (bound to `RetentionProperties`)

```yaml
retention:
  worker:
    enabled: ${RETENTION_WORKER_ENABLED:true}
    cron: "0 * * * * *"           # every minute
    batch-size: 50
    lease-duration: PT5M          # Duration — how long a claim lock is held
    instance-id: ${HOSTNAME:local}
  retry:
    max-attempts: 5
    backoff: ["PT1M","PT5M","PT30M","PT2H","PT12H"]
  mail:
    from: ${DRIP_MAIL_FROM:${app.notification-email}}
```

Disabling the worker (`retention.worker.enabled=false`) is how tests and staging opt out without removing `@EnableScheduling` globally; implement via a `@ConditionalOnProperty` on `DripWorker`.

## Implementation steps

Work top-to-bottom; each step should compile and pass tests before the next.

1. **Migration V5** — write `V5__drip_campaign_runs_and_locking.sql` with the DDL from the *Database changes* section. Run `mvn -Dspring.profiles.active=local flyway:migrate` against the dev database (host port 5433). Verify via `psql -p 5433 -U changelog -d changelog -c "\d drip_enrollments"` that the new columns are present.
2. **Enums and entity updates** — create `EnrollmentStatus`, `CampaignStatus`; update `DripCampaign`, `DripEnrollment`, `DripStep`; create `DripCampaignRun` entity + `DripCampaignRunRepository`. Boot the app locally to confirm `ddl-auto=validate` passes.
3. **Claim query** — add to `DripEnrollmentRepository`:

   ```java
   @Query(value = """
       SELECT * FROM drip_enrollments
       WHERE status IN ('PENDING','ACTIVE')
         AND next_step_at <= now()
         AND (locked_until IS NULL OR locked_until < now())
       ORDER BY next_step_at ASC
       LIMIT :limit
       FOR UPDATE SKIP LOCKED
       """, nativeQuery = true)
   List<DripEnrollment> claimDueEnrollments(@Param("limit") int limit);
   ```

   The claim method must be called from inside a `@Transactional` block that also issues the `UPDATE ... SET locked_until = now() + ...` immediately after, before the transaction commits. (Otherwise the locks release and a sibling instance could re-claim.)
4. **`DripTemplateRenderer`** — small wrapper around `PropertyPlaceholderHelper`. Takes `(DripCampaign, DripEnrollment, DripStep, Map<String,String> extras)` and returns `RenderedMessage(subject, body)`. Throws `TemplateRenderException` on unresolvable syntax errors.
5. **`DripStepExecutor`** — method `execute(DripEnrollment)` that opens its own `@Transactional(REQUIRES_NEW)`, re-reads the enrollment inside the tx (single-row), loads the campaign, renders, sends via `JavaMailSender`, inserts `DripCampaignRun`, advances state. All in one transaction so a failure between "send email" and "update DB" cannot cause duplicate sends — see *Idempotency* below for the exact ordering.
6. **`DripWorker`** — `@Component @ConditionalOnProperty("retention.worker.enabled")`. One method, `@Scheduled(cron = "${retention.worker.cron}")`:

   ```java
   @Transactional   // for the claim step only
   public void tick() {
       List<DripEnrollment> claimed = enrollmentRepository.claimDueEnrollments(props.batchSize);
       String instanceId = props.instanceId;
       LocalDateTime leaseUntil = LocalDateTime.now().plus(props.leaseDuration);
       claimed.forEach(e -> {
           e.setLockedUntil(leaseUntil);
           e.setLockedBy(instanceId);
       });
       enrollmentRepository.saveAll(claimed);  // flush locks before commit
       // claim tx commits here → locks visible to other instances
       for (DripEnrollment e : claimed) {
           try { stepExecutor.execute(e.getId()); }
           catch (Exception ex) { log.error("Step execution crashed for {}", e.getId(), ex); }
       }
   }
   ```

   The "save the lease inside the claim tx" pattern is what makes SKIP LOCKED cooperate across instances: other workers see `locked_until > now()` on their own next poll and skip.
7. **`DripEnrollmentService`** — `enrollCustomer(tenantId, campaignId, customerId, email, attrs)` (idempotent via `UNIQUE(customer_id, campaign_id)` — catch `DataIntegrityViolationException` and return existing); `processEnrollmentTrigger(tenantId, triggerEvent, customerId, email, attrs)` (iterates active campaigns); `pause`, `resume`, `exit` mutators. Uses `DripCampaignRepository.findByTenantIdAndTriggerEventAndStatus(..., CampaignStatus.ACTIVE)`.
8. **`DripTriggerEvent` + `DripTriggerListener`** — event is a plain immutable record. Listener method is `@Async @TransactionalEventListener(phase = AFTER_COMMIT)` → calls `enrollmentService.processEnrollmentTrigger(...)`.
9. **Wire publishers** — in `SubscriberService.subscribe` (or equivalent): after save, `applicationEventPublisher.publishEvent(new DripTriggerEvent("subscriber_signup", tenantId, customerId, email, Map.of()))`. In `StripeWebhookService.handleSubscriptionEvent`, detect `previous_attributes.status == "trialing" && new status == "active"` and publish `trial_converted`. Keep firing `subscription_created` and `subscription_canceled` via events too; remove the direct service calls.
10. **Admin controllers + DTOs** — straightforward CRUD + enroll endpoints. Return `DripCampaignResponse` with expanded steps array. Enforce tenant scoping via the existing `TenantResolver` (`src/main/java/com/changelog/config/TenantResolver.java`).
11. **`RetentionProperties`** — `@ConfigurationProperties("retention")` with nested `Worker`, `Retry`, `Mail` records. Register via `@ConfigurationPropertiesScan` or explicit `@EnableConfigurationProperties`.
12. **Tests** — unit (fake clock), concurrency IT (two workers, one DB), template renderer, curl smoke. See *Test plan*.
13. **Delete legacy code** — remove `@Scheduled` cron + `processSingleEnrollment` from the old `DripCampaignService` (now split into `DripEnrollmentService` + `DripStepExecutor` + `DripWorker`). Keep the class for campaign CRUD only, or delete entirely if all its methods moved.
14. **Docs** — extend `README.md` *Drip Campaign Triggers* table with `trial_converted` and `subscriber_signup` rows; note the admin endpoints.

### Idempotency (ordering inside `DripStepExecutor.execute`)

The canonical order for "send email and don't double-send" is:

```
BEGIN tx;
  1. INSERT drip_campaign_runs (attempting, attempt=N);              -- tx-local row
  2. render(template);                                                 -- in memory
  3. mailSender.send(msg);                                             -- SMTP call
  4. UPDATE drip_campaign_runs SET status='sent', sent_at=now();       -- step audit
  5. UPDATE drip_enrollments   SET current_step+=1, next_step_at=...,  -- advance
                                    status=...,  locked_until=NULL;    -- release lock
COMMIT;
```

Failure modes analyzed:

- **Crash between 3 and 4**: email was sent but DB didn't record it. On next tick, `locked_until` expires after 5 min; the same enrollment is claimed again; `current_step` is unchanged; **the step is resent**. This is "at-least-once" delivery. Acceptable trade-off for email campaigns; document it. Mitigation: fast commit after send (the SMTP call is the slow part; the UPDATE is microseconds). If we ever need "exactly-once," we would need SMTP idempotency keys on the provider side, which is out of scope for `smtp.gmail.com`/`JavaMailSender`.
- **Crash between 1 and 3**: `attempting` row exists but `sent_at` is null. Next tick re-sends. Admin dashboard shows the `attempting` row as an unfinished attempt (easy to query: `status='attempting' AND attempted_at < now() - INTERVAL '5 minutes'`).
- **Two instances both claim the same row**: impossible. `FOR UPDATE SKIP LOCKED` excludes rows already locked in an uncommitted transaction; `locked_until > now()` excludes rows already leased by a live sibling.
- **Same customer, same campaign, two concurrent `enroll` calls**: the `UNIQUE(customer_id, campaign_id)` constraint rejects the second insert; service catches `DataIntegrityViolationException` and returns the existing row.

### Backoff schedule

Exponential-ish but capped:

```
attempt 1 failure  → retry in 1 minute
attempt 2 failure  → retry in 5 minutes
attempt 3 failure  → retry in 30 minutes
attempt 4 failure  → retry in 2 hours
attempt 5 failure  → mark enrollment status = FAILED, do not retry
```

Implemented as a `List<Duration>` in `RetentionProperties.retry.backoff`. `DripStepExecutor.computeNextAttemptAt(attempt)` = `now() + backoff[min(attempt-1, backoff.size()-1)]`.

## Test plan

### Unit tests (fast, no DB)

**`DripEnrollmentServiceTest#advancesThroughAllSteps`**

- Given: a `DripCampaign` with 3 steps (delays 0, 2, 5 days) and a fresh enrollment.
- Uses a `java.time.Clock` fake injected into the service (refactor: service takes `Clock` via constructor; production bean wires `Clock.systemUTC()`).
- Mocks `JavaMailSender` (verify `send(...)` called 3 times with expected subjects).
- Mocks `DripEnrollmentRepository` (in-memory) and `DripCampaignRunRepository`.
- Advance the fake clock: t=0 → first tick → step 1 sends → enrollment.currentStep=1, next_step_at=t+2d.
- Advance clock to t+2d → second tick → step 2 sends → currentStep=2, next_step_at=t+7d.
- Advance clock to t+7d → third tick → step 3 sends → status=COMPLETED, completed_at set.
- One more tick → no rows claimed (status is now COMPLETED which is not IN ('PENDING','ACTIVE')).

**`DripEnrollmentServiceTest#retryBackoffOnFailure`**

- `JavaMailSender.send` throws `MailException` always.
- Tick 5 times advancing the clock by the backoff duration each time.
- Assert `attemptCount` grows 1→5, `status=FAILED` after the 5th.
- Assert exactly 5 rows in `drip_campaign_runs` all with `status='failed'`.

**`DripTemplateRendererTest`**

- Renders `"Hi {firstName}"` with `firstName=Alice` → `"Hi Alice"`.
- Renders `"Hi {firstName}"` with empty map → `"Hi "` (ignoreUnresolvable=true).
- Rendering `"{unclosed"` → throws `TemplateRenderException`.

### Integration tests (real Postgres on port 5433)

**`DripWorkerConcurrencyIT#skipLockedPreventsDoubleClaim`**

- Bootstraps Spring against local postgres (same setup as `SchemaValidationIT`).
- Seeds one campaign with one step (delay 0), and **50 enrollments** all with `next_step_at = now() - 1 second, status='PENDING'`.
- Instead of booting two Spring contexts (expensive), spin up two worker **threads** each running `dripWorker.tick()` simultaneously from the same process. Because each tick opens its own transaction and uses `FOR UPDATE SKIP LOCKED`, this correctly exercises the contention path.
- Assert: after both ticks complete, exactly 50 `drip_campaign_runs` rows exist with `status='sent'`, and each enrollment has `current_step=1`, `status='COMPLETED'`. **No duplicate runs** (`SELECT enrollment_id, step_number, attempt, COUNT(*) FROM drip_campaign_runs GROUP BY 1,2,3 HAVING COUNT(*) > 1` returns empty).
- Uses a Mockito `JavaMailSender` that just counts calls (no real SMTP).

**`DripTriggerListenerIT#subscriberSignupEnrolls`**

- Seed a campaign with `triggerEvent = 'subscriber_signup'`, status='active'.
- Call `subscriberService.subscribe(projectId, email)`.
- Wait for `@Async` + `AFTER_COMMIT` listener to run (use `Awaitility` with 2s timeout).
- Assert one `drip_enrollments` row exists for that customer + campaign.

**`SchemaValidationIT`** — must continue to pass unchanged. This is the canary.

### Manual smoke (curl + dev SMTP catcher)

Use [MailHog](https://github.com/mailhog/MailHog) or [MailCatcher](https://mailcatcher.me/) locally:

```bash
# Start MailHog (SMTP :1025, UI :8025)
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# Point the app at it (override via env)
export MAIL_HOST=localhost
export MAIL_PORT=1025
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Flow:

```bash
# 1. Create campaign with two steps, both delay=0 for fast feedback
curl -X POST http://localhost:8081/api/v1/admin/drip-campaigns \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Smoke test",
        "triggerEvent": "manual_smoke",
        "status": "active",
        "steps": [
          {"stepNumber":1,"delayDays":0,"channel":"email","subject":"Step 1","bodyTemplate":"Hi {firstName}"},
          {"stepNumber":2,"delayDays":0,"channel":"email","subject":"Step 2","bodyTemplate":"Bye {firstName}"}
        ]
      }'
# → {"id":"<CAMP>"}

# 2. Enroll a customer manually
curl -X POST http://localhost:8081/api/v1/admin/drip-campaigns/<CAMP>/enroll \
  -H "Content-Type: application/json" \
  -d '{"customerId":"00000000-0000-0000-0000-000000000099","email":"ada@example.com","firstName":"Ada"}'

# 3. Wait ~60s for the worker
sleep 65

# 4. Open MailHog UI at http://localhost:8025 — confirm "Step 1" arrived
# 5. Wait another ~60s, confirm "Step 2" arrived
# 6. Confirm terminal status via DB
psql -h localhost -p 5433 -U changelog -d changelog \
     -c "SELECT current_step, status FROM drip_enrollments WHERE customer_id = '00000000-0000-0000-0000-000000000099';"
# → expect: current_step=2, status=COMPLETED

# 7. Inspect run history
psql -h localhost -p 5433 -U changelog -d changelog \
     -c "SELECT step_number, attempt, status, sent_at FROM drip_campaign_runs ORDER BY attempted_at;"
# → expect: two rows, both status=sent
```

## Risks & open questions

| Risk | Mitigation |
|---|---|
| **Unbounded retry loops** | Hard cap at `retention.retry.max-attempts=5`; after that, status=FAILED and the worker stops claiming. `drip_campaign_runs` preserves the full attempt history for post-mortem. |
| **Time-zone bugs in next-step-due math** | All schema columns are `TIMESTAMPTZ`. All Java `LocalDateTime` → `Instant` conversions happen in `DripStepExecutor.computeNextStepAt` using a single injected `Clock`. No `new Date()` calls anywhere. Unit test explicitly drives the fake `Clock` across a DST boundary (spring-forward in America/Los_Angeles) and asserts the computed `next_step_at` is exactly N days later in UTC — not N days minus 1 hour. |
| **Broken template blocks worker** | `TemplateRenderException` is caught in `DripStepExecutor` and treated as a normal step failure: logs the error, inserts a `drip_campaign_runs` row with `status='failed'`, bumps `attempt_count`, schedules the backoff. The worker loop itself never throws. One bad campaign can at worst fill its own audit table. |
| **Large campaigns fan-out** | A campaign with 500k enrollments could enqueue 500k rows with `next_step_at = now()` at campaign activation time. The worker's `batch-size=50` caps per-tick work, but the SMTP server could still be rate-limited. Mitigation (phase 2, not this plan): a per-tenant rate limiter and a "stagger enrollment" flag that spreads initial `next_step_at` across a configurable window. For now: document in `README.md` that activating a campaign with > 1000 existing eligible customers is unsupported. |
| **At-least-once email delivery** | Documented above in *Idempotency*. Acceptable for marketing email; would require provider-side idempotency keys to harden further. |
| **`@Async` listener swallows exceptions silently** | Configure a `SimpleAsyncUncaughtExceptionHandler` (or use `ThreadPoolTaskExecutor` with a logger) globally — or at minimum, `DripTriggerListener` wraps its body in try/catch and logs to structured logs so we can alert on it. |
| **Tenant deletion cascades** | `V5` adds `ON DELETE CASCADE` from `drip_campaigns.tenant_id` and `drip_enrollments.tenant_id` to `cc.tenants`. Open question: is cascade the right default, or should we soft-delete? Defer to whoever owns tenant lifecycle — for now cascade matches every other V3/V4 table's implicit behavior (which is actually *no FK at all* in V4, so cascade is strictly safer). |
| **Existing legacy data** | V4 has already run against the dev DB. Legacy rows may have `status='active'` lower-case. The `UPDATE` statements in V5 normalize them before applying the CHECK constraint. If any environments contain values outside {active, completed, unsubscribed}, the migration will fail the CHECK and surface them — that's desirable. |
| **Worker runs at every instance** | `@ConditionalOnProperty("retention.worker.enabled")` is global. To run on only one of N instances set the env var on the others. Alternative (out of scope): a distributed leader election via Postgres advisory locks — future work if we actually need a singleton worker. |
| **Keycloak role check for admin endpoints** | Local profile currently permit-all (`SecurityConfigLocal`). Production `SecurityConfig` needs `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`. Out of scope for this plan but noted — file an issue. |
| **`DripStep` is JSONB inside `drip_campaigns`** | No version column on the JSON blob. Editing steps in-place on a campaign with live enrollments is racy: an enrollment might refer to step #N and the campaign now only has N-1 steps. Short-term: the executor handles "step index out of bounds" → COMPLETED (same as today). Medium-term open question: do we version campaigns (immutable, `v2` = new campaign) or snapshot the `steps` into each enrollment at enrollment time? Lean toward the latter but deferred. |

## Definition of done

- [ ] `V5__drip_campaign_runs_and_locking.sql` merged; `mvn flyway:info` shows V5 applied on dev DB.
- [ ] `SchemaValidationIT` passes.
- [ ] `DripEnrollmentServiceTest#advancesThroughAllSteps` passes.
- [ ] `DripEnrollmentServiceTest#retryBackoffOnFailure` passes.
- [ ] `DripTemplateRendererTest` passes.
- [ ] `DripWorkerConcurrencyIT#skipLockedPreventsDoubleClaim` passes; no duplicate runs.
- [ ] `DripTriggerListenerIT#subscriberSignupEnrolls` passes.
- [ ] Manual curl + MailHog smoke documented above completes successfully on `local` profile.
- [ ] `StripeWebhookService` no longer calls `dripCampaignService.processEnrollmentTrigger` directly; uses `ApplicationEventPublisher` for all drip triggers.
- [ ] `trial_converted` transition detection present in `StripeWebhookService.handleSubscriptionEvent`.
- [ ] `SubscriberService.subscribe` publishes `DripTriggerEvent("subscriber_signup", ...)` after successful insert.
- [ ] `README.md` *Drip Campaign Triggers* table updated to include `subscriber_signup` and `trial_converted`.
- [ ] All new admin endpoints return 2xx under `local` profile via curl; 4xx for malformed inputs (bean validation).
- [ ] `retention.*` configuration block documented in `application.yml` and wired through `RetentionProperties`.
- [ ] Old `@Scheduled` cron + `processSingleEnrollment` removed from `DripCampaignService`; no two workers in the codebase.
- [ ] `ddl-auto=validate` passes at boot against V5-migrated database.
