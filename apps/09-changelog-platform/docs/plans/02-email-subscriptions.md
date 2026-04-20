# 02 — Email Subscriptions Delivery

## Overview

Today the subscription API persists `Subscriber` rows and there is a
partial `SubscriberNotificationService`
(`src/main/java/com/changelog/service/SubscriberNotificationService.java:21`)
that sends plain-text notifications on `PostService.publishPost`
(`src/main/java/com/changelog/service/PostService.java:136`) and from
`ScheduledPublishingJob.publishScheduledPosts`
(`src/main/java/com/changelog/service/ScheduledPublishingJob.java:24`).

That implementation is incomplete and unsafe:

1. There is **no welcome email** on `POST /changelog/{slug}/subscribe`
   (`PublicChangelogController.subscribe`
   `src/main/java/com/changelog/controller/PublicChangelogController.java:111`).
2. The unsubscribe link in existing mails
   (`SubscriberNotificationService.buildEmailBody`
   `src/main/java/com/changelog/service/SubscriberNotificationService.java:80`)
   points at the form endpoint, which **requires the user to retype their
   email** — we want a **tokenised one-click** link.
3. `@Async` on `notifySubscribers`
   (`src/main/java/com/changelog/service/SubscriberNotificationService.java:33`)
   is called from **inside** a `@Transactional` method; the send is
   dispatched before the transaction commits and, if it throws before
   the async boundary is crossed, it can still surface into the publish
   flow. We need a **true post-commit** dispatch.
4. There is **no local SMTP catcher**; `application-local.yml:17-27`
   points at `localhost:1025` but `docker-compose.yml`
   (`docker-compose.yml:3-66`) has no mail service.
5. There is **no token column or table** for one-click unsubscribe.

This plan converts the existing stub into a production-ready path:
welcome emails, post-publish fan-out via a transactional event +
`@Async`, tokenised unsubscribe, a MailHog dev container, and an HTML +
text multipart body.

## Goals & Non-goals

**Goals**

- Send a **welcome email** synchronously-triggered but asynchronously-sent
  when a new or reactivated subscriber is created.
- Send a **new-post email** to every `ACTIVE` subscriber of a project
  when a `Post` transitions to `PUBLISHED` (both manual publish and the
  scheduled job).
- Every email contains a **one-click tokenised unsubscribe link** the
  user can click without typing anything.
- A **failed mail send must never** roll back the post-publish
  transaction or the subscribe transaction.
- **Local development** can exercise the full flow without real SMTP
  credentials, via a MailHog container exposed on the docker-compose
  network.
- Flyway V5 migration; `SchemaValidationIT`
  (`src/test/java/com/changelog/SchemaValidationIT.java:44`) stays green
  with `ddl-auto=validate`.

**Non-goals**

- No **HTML templating engine** (Thymeleaf/Freemarker) — we inline a
  minimal HTML body with `MimeMessageHelper`. Rich templates belong to a
  later plan.
- No **click/open tracking pixel** — the `subscriber_notifications`
  table already has `opened_at`/`clicked_at` columns
  (`V1__init.sql:97-104`) but wiring tracking is out of scope.
- No **rate-limiting / per-tenant quota** — out of scope.
- No **bounce / complaint handling** (SNS / SES webhooks) — out of scope.
- No **retry queue / outbox table** persisted to Postgres. We implement
  an in-process retry (1 immediate retry with backoff) and rely on
  idempotent tokens + the `subscriber_notifications` log to dedupe if
  upgraded to an outbox later.
- **Drip-campaign email sending** (`DripCampaignService`) is NOT
  refactored here, but the `EmailService` abstraction we introduce is
  designed so the drip module can adopt it later.

## Acceptance criteria

1. `POST /changelog/{slug}/subscribe` with a valid email returns `200`
   **and** causes MailHog (local) or SMTP (prod) to receive a message
   addressed to that email whose subject is
   `Welcome to <Project Name> updates`.
2. `POST /api/v1/posts/{id}/publish` on a project with N `ACTIVE`
   subscribers causes N messages to be received, each with subject
   `[<Project Name>] <Post Title>`.
3. Every message body contains a URL of the form
   `{app.public-url}/changelog/{slug}/unsubscribe?token=<opaque>`
   and issuing `GET` against that URL flips the subscriber to
   `UNSUBSCRIBED` and returns a human-readable confirmation page
   (HTML, `200`).
4. The token is **unguessable** (≥ 128 bits of entropy, base64url).
5. Reusing the same token after unsubscribe still returns `200` with a
   "You are already unsubscribed" body (idempotent).
6. If the SMTP server is unreachable during `publishPost`, the post is
   still persisted as `PUBLISHED`, the endpoint returns `200`, and
   errors are logged.
7. `mvn -Dtest=SchemaValidationIT test` (with docker compose postgres
   up) passes after the V5 migration is added.
8. A new `SubscribeFlowIT` using **GreenMail** boots the Spring context,
   hits `/subscribe` and the publish endpoint, and asserts the welcome
   and notification messages appeared in the in-memory SMTP server.
9. `docker compose up -d mailhog` exposes a UI on `http://localhost:8025`
   that captures every outgoing mail during local dev.

## User-facing surface

### Endpoints

| Method | Path | Auth | Change |
|--------|------|------|--------|
| `POST` | `/changelog/{slug}/subscribe` | Public | Existing — now also enqueues welcome email |
| `POST` | `/changelog/{slug}/unsubscribe` | Public | Existing form endpoint left intact for backwards-compat |
| `GET`  | `/changelog/unsubscribe?token=<t>` | Public | **New** — tokenised one-click unsubscribe, returns HTML |

The new GET endpoint is slug-agnostic (`/changelog/unsubscribe`, not
`/changelog/{slug}/unsubscribe?token=`) because the token resolves to
exactly one subscriber across the system.

### Mail content

- **Welcome**: subject `Welcome to <ProjectName> updates`; body
  (HTML + text alternative) thanking the subscriber and including the
  tokenised unsubscribe URL.
- **New post**: subject `[<ProjectName>] <PostTitle>`; body shows
  summary, link to read the full post (`{publicUrl}/changelog/{slug}`
  — path mirrors existing stub, anchored by post id), and the tokenised
  unsubscribe URL.

Existing stub already builds the subject this way at
`SubscriberNotificationService.java:52`.

## Architecture & data flow

### Event emission point

`PostService.publishPost`
(`src/main/java/com/changelog/service/PostService.java:136`) and
`ScheduledPublishingJob.publishScheduledPosts`
(`src/main/java/com/changelog/service/ScheduledPublishingJob.java:33`)
currently call
`subscriberNotificationService.notifySubscribers(updated)` directly.

We replace that call with publishing a Spring `ApplicationEvent`:

```
applicationEventPublisher.publishEvent(new PostPublishedEvent(post.getId(), post.getProjectId()));
```

A new `PostPublishedEvent` holds only IDs (NOT the JPA entity — the
entity may be detached by the time the listener runs).

### Async / queue mechanism — chosen: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`

The listener is in a new class
`EmailEventListener`:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async("mailTaskExecutor")
public void onPostPublished(PostPublishedEvent event) { ... }
```

Why this over alternatives:

- Plain `@Async`: fires immediately, before the publish transaction has
  committed. If the TX rolls back, we've already emailed about a post
  that does not exist. This is the bug in the current code.
- **Outbox table + polling worker**: more durable, but a full order of
  magnitude more code and another Flyway table. Deferred; the token
  table we introduce is additive and will happily coexist with an
  outbox if we adopt one later.
- `@TransactionalEventListener(AFTER_COMMIT)` **guarantees** the
  publish TX already committed; adding `@Async` moves the fan-out off
  the HTTP worker thread. `@EnableAsync` is already present at
  `src/main/java/com/changelog/ChangelogPlatformApplication.java:12`.

For the **welcome email** there is no publish transaction to wait on
(the `subscriber.save()` is its own tiny transaction). We fire a
`SubscriberCreatedEvent` from `PublicChangelogController.subscribe`
handled by the same listener, also with
`@TransactionalEventListener(AFTER_COMMIT)` so that if save fails we
don't send a welcome to a phantom row. Inside the listener we make a
fresh call to `subscriberRepository.findById` — defence in depth
against detached entities.

### Dedicated `TaskExecutor`

We register a named executor bean `mailTaskExecutor`:

```java
@Bean("mailTaskExecutor")
public TaskExecutor mailTaskExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(2);
    ex.setMaxPoolSize(8);
    ex.setQueueCapacity(500);
    ex.setThreadNamePrefix("mail-");
    ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    ex.initialize();
    return ex;
}
```

Reason for `CallerRunsPolicy`: if the queue fills during a large
fan-out, the listener thread (already detached from the HTTP request
by `AFTER_COMMIT`) simply runs the task itself instead of dropping
emails.

### Retry policy

Per-recipient: a single retry with 2-second sleep on
`MailException` subclasses that indicate transient failures
(`MailSendException`, `MailAuthenticationException` excluded from
retry). Permanent failures are logged at `ERROR` and recorded as
`status='failed'` in an optional row of `subscriber_notifications`
(table already exists — `V1__init.sql:97-104`).

### Failure isolation

Three layers:

1. **Transaction boundary**: `AFTER_COMMIT` ensures the publish TX is
   already durable before we start sending.
2. **Listener try/catch**: the `@Async` method wraps **every** per-
   recipient send in `try/catch` so one bad address can never poison
   the fan-out. (This matches current behaviour at
   `SubscriberNotificationService.java:57-69` — we keep it.)
3. **Executor thread**: the async method runs on `mailTaskExecutor`,
   not the Tomcat worker thread, so an `OutOfMemoryError` or infinite
   retry in a future refactor cannot lock up `/api/v1/posts/.../publish`.

### Data flow diagram

```
Admin POST /api/v1/posts/{id}/publish
        │
        ▼
PostService.publishPost  [@Transactional]
  ├── post.setStatus(PUBLISHED)
  ├── postRepository.save(post)
  └── applicationEventPublisher.publishEvent(PostPublishedEvent)   ◄── inside TX
      │
      ▼ (spring holds event until commit)
TX COMMIT
      │
      ▼ (spring delivers event)
EmailEventListener.onPostPublished  [@Async mailTaskExecutor]
      │
      ├── load Post + Project fresh from repo
      ├── load active subscribers for projectId
      └── for each subscriber:
            ├── ensure unsubscribe token row exists (create if missing)
            ├── build MimeMessage (HTML + text alternative)
            ├── try { mailSender.send }  catch { log + 1 retry }
            └── record subscriber_notifications row (best-effort)

Public POST /changelog/{slug}/subscribe
        │
        ▼
PublicChangelogController.subscribe  [now @Transactional]
  ├── save Subscriber (new or reactivate)
  └── publishEvent(SubscriberCreatedEvent)
      │
      ▼
TX COMMIT
      │
      ▼
EmailEventListener.onSubscriberCreated  [@Async]
  └── send welcome email (same listener pattern)

Public GET /changelog/unsubscribe?token=<opaque>
        │
        ▼
SubscriberController.unsubscribeByToken
  └── SubscriberService.unsubscribeByToken(token)
        ├── look up SubscriberToken by tokenHash
        ├── mark linked Subscriber UNSUBSCRIBED (idempotent)
        └── render confirmation HTML
```

## Database changes

### New migration: `V5__email_subscription_tokens.sql`

One new table, no modifications to existing tables:

```sql
-- One-click unsubscribe tokens. One active token per subscriber; rotated
-- if ever compromised. Hashed at rest to prevent token theft from a DB
-- leak (same pattern as session cookies).
CREATE TABLE subscriber_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_id   UUID NOT NULL REFERENCES changelog_subscribers(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,                   -- SHA-256 hex of the random token
    purpose         TEXT NOT NULL DEFAULT 'unsubscribe',  -- future: 'verify_email' etc.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    UNIQUE(subscriber_id, purpose)
);

CREATE INDEX idx_subscriber_tokens_hash ON subscriber_tokens(token_hash);
```

Design notes:

- `UNIQUE(subscriber_id, purpose)` means **one unsubscribe token per
  subscriber** — the email always carries the same stable link. If we
  ever want rotation, `revoked_at` handles it without a schema change.
- Hashing at rest (we store SHA-256 of the random 32-byte value; we
  email the raw value). A DB snapshot alone cannot unsubscribe people.
- No `expires_at` — an unsubscribe link is useful for the lifetime of
  the subscriber row.
- `ON DELETE CASCADE` from `changelog_subscribers` mirrors the
  existing FK pattern at `V1__init.sql:87`.

### Entity

`SubscriberToken` JPA entity maps to this table. `@Entity`,
`@Table(name = "subscriber_tokens")`, fields matching the columns
exactly. No JSON, no enum — keeps `SchemaValidationIT` happy.

### No changes to existing tables

We deliberately do **not** add a `unsubscribe_token` column to
`changelog_subscribers`; a separate table is additive, avoids rewrites
of `Subscriber.java` (`src/main/java/com/changelog/model/Subscriber.java:22`),
and leaves room for more token types later.

## Files to create or modify

### Create

| Path | Purpose |
|------|---------|
| `src/main/resources/db/migration/V5__email_subscription_tokens.sql` | Flyway V5 migration (above) |
| `src/main/java/com/changelog/model/SubscriberToken.java` | JPA entity for `subscriber_tokens` |
| `src/main/java/com/changelog/repository/SubscriberTokenRepository.java` | `findByTokenHash`, `findBySubscriberIdAndPurpose`, `existsBySubscriberIdAndPurpose` |
| `src/main/java/com/changelog/events/PostPublishedEvent.java` | Immutable record `(UUID postId, UUID projectId)` |
| `src/main/java/com/changelog/events/SubscriberCreatedEvent.java` | Immutable record `(UUID subscriberId)` |
| `src/main/java/com/changelog/service/email/EmailService.java` | New abstraction: `sendWelcome(subscriber, project)`, `sendPostPublished(post, project, subscribers)`. Uses `JavaMailSender` + `MimeMessageHelper`. |
| `src/main/java/com/changelog/service/email/EmailEventListener.java` | `@TransactionalEventListener(AFTER_COMMIT) @Async("mailTaskExecutor")` methods for both events |
| `src/main/java/com/changelog/service/email/UnsubscribeTokenService.java` | `getOrCreate(subscriberId)` returning the **raw** token; `resolve(rawToken)` returning an `Optional<Subscriber>` |
| `src/main/java/com/changelog/config/MailExecutorConfig.java` | `@Bean("mailTaskExecutor") ThreadPoolTaskExecutor` |
| `src/main/java/com/changelog/controller/UnsubscribeController.java` | `GET /changelog/unsubscribe?token=...` → HTML response |
| `src/test/java/com/changelog/email/SubscribeFlowIT.java` | GreenMail-backed integration test (welcome + post-publish) |
| `src/test/java/com/changelog/email/UnsubscribeTokenServiceTest.java` | Unit test: token entropy, hash-at-rest, idempotent resolve |

### Modify

| Path | Change |
|------|--------|
| `docker-compose.yml` | Add `mailhog` service (`mailhog/mailhog:v1.0.1`, ports `1025:1025` SMTP, `8025:8025` UI) |
| `src/main/resources/application-local.yml` | Confirm `spring.mail.host=localhost`, `port=1025`; already correct |
| `src/main/java/com/changelog/controller/PublicChangelogController.java:111-146` | After `subscriberRepository.save`, publish `SubscriberCreatedEvent`. Wrap the method body in an internal service call so it becomes `@Transactional` cleanly (or annotate the existing method — current code is already effectively one TX per save). |
| `src/main/java/com/changelog/service/PostService.java:136-157` | Remove direct `subscriberNotificationService.notifySubscribers(updated)` call; replace with `applicationEventPublisher.publishEvent(new PostPublishedEvent(updated.getId(), updated.getProjectId()))`. |
| `src/main/java/com/changelog/service/ScheduledPublishingJob.java:40-44` | Same replacement — publish `PostPublishedEvent`. |
| `src/main/java/com/changelog/service/SubscriberNotificationService.java` | Delete OR shrink to a thin delegator calling `EmailService` + remove `@Async` (event listener is now the async boundary). Recommend **delete** once listener + `EmailService` ship; fewer moving parts. |
| `pom.xml:133-159` | Add `<dependency><groupId>com.icegreen</groupId><artifactId>greenmail-junit5</artifactId><version>2.1.0</version><scope>test</scope></dependency>` for the IT |
| `src/main/resources/application.yml:23-34` | Add `spring.mail.properties.mail.smtp.timeout=5000`, `connectiontimeout=5000`, `writetimeout=5000` so a hanging SMTP server cannot stall the mail thread indefinitely |
| `README.md` Roadmap §1 | Mark "Email delivery" as done once merged |
| `DEVELOPMENT.md` | Add a "Testing email locally via MailHog" section pointing at `http://localhost:8025` |

### Delete (optional, after follow-up cleanup)

`src/main/java/com/changelog/service/SubscriberNotificationService.java`
— once its logic is fully absorbed by `EmailService` + `EmailEventListener`.
Keep during migration to avoid a big-bang PR.

## Implementation steps

Ordered so each step is independently compilable/testable.

1. **Migration + entity (schema change)**
   - Write `V5__email_subscription_tokens.sql`.
   - Create `SubscriberToken` entity + repository.
   - Run `mvn -Dtest=SchemaValidationIT test` → must pass.

2. **Token service**
   - Implement `UnsubscribeTokenService.getOrCreate(UUID subscriberId)`:
     - If `subscriber_tokens` row exists for `(subscriberId, 'unsubscribe')`
       and `revoked_at IS NULL`, there is no stored raw token — we
       cannot recover it; emit a **new** row (and revoke old) OR
       return the row and accept that we only ever emit the raw at
       creation time. **Chosen**: generate once on first
       subscribe-event, store hash; keep the raw in memory during the
       event handler, do not attempt to "recover" it later. On
       subsequent publishes we re-read the hash row — but to **send**
       the link we need the raw. Resolution: **token is generated at
       subscribe time AND cached on the Subscriber row via a
       transient field is not durable**. Correct fix: store the raw
       token **also** on the row? No — defeats the hash-at-rest
       benefit. Final design: store only the hash, but **regenerate +
       rotate** the token on every notification event. That is
       wasteful. Simpler final design:
   - **Decision**: store `token_hash` only, and on every send call
     `getOrCreate` which:
     - If a row exists, return the row but **mint a fresh raw
       token**, update `token_hash` (rotation on every email).
     - If not, insert new row.
     Old emails' links stop working after a new post ships. Users who
     want to unsubscribe click the **most recent** email; this is
     normal behaviour for transactional mail providers (SendGrid one-
     click works the same way — the list-unsubscribe header is
     regenerated). Document this in code comments.
   - `resolve(rawToken)`: SHA-256 hash raw token, look up by hash,
     return `Optional<Subscriber>`. Idempotent — even if the subscriber
     is already `UNSUBSCRIBED`, return them.

3. **Email service**
   - `EmailService.sendWelcome(Subscriber, Project)`: builds a
     `MimeMessage` via `MimeMessageHelper(mimeMessage, true, "UTF-8")`,
     sets `text(plainBody, htmlBody)` for multipart alternative,
     uses the token from `UnsubscribeTokenService` in both.
   - `EmailService.sendPostPublished(Post, Project, Subscriber)`:
     same shape; one subscriber per call (called in a loop by the
     listener) so failure of one recipient does not short-circuit.
   - Sanitisation: keep `replaceAll("[\\r\\n]", " ")` for subject
     (existing code at
     `SubscriberNotificationService.java:77`).

4. **Events + listener**
   - Add `PostPublishedEvent` and `SubscriberCreatedEvent` records.
   - `EmailEventListener` with two handlers, both
     `@TransactionalEventListener(phase = AFTER_COMMIT) @Async("mailTaskExecutor")`.
   - Both handlers load their target rows via the repository — do NOT
     accept the entity in the event payload.

5. **Executor config**
   - `MailExecutorConfig` bean as shown above. No `@Primary` — leave
     Spring's default `SimpleAsyncTaskExecutor` for other `@Async`
     call sites.
   - Qualify: `@Async("mailTaskExecutor")` on the listener.

6. **Wire publishers**
   - Inject `ApplicationEventPublisher` in
     `PostService`, `ScheduledPublishingJob`, and
     `PublicChangelogController` (or better: extract a
     `SubscriberService.subscribe(...)` so the controller stays thin
     and the event publish lives in the service layer alongside the
     `save`). Recommended: extract `SubscriberService` — the
     controller is already doing service work.
   - Replace the existing `notifySubscribers(updated)` calls.

7. **Unsubscribe endpoint**
   - `UnsubscribeController.unsubscribe(@RequestParam String token)`:
     calls `SubscriberService.unsubscribeByToken(token)`, returns an
     HTML `ResponseEntity<String>` with `Content-Type: text/html`.
   - Status is always `200` (avoids enumeration; matches existing
     unsubscribe form behaviour at
     `PublicChangelogController.java:148-169`).

8. **MailHog in docker-compose**
   - Append to `docker-compose.yml`:
     ```yaml
     mailhog:
       image: mailhog/mailhog:v1.0.1
       container_name: changelog-mailhog
       ports:
         - "1025:1025"
         - "8025:8025"
     ```
   - `application-local.yml` already points at `localhost:1025`.

9. **Tests** — see "Test plan".

10. **Docs** — update `README.md` and `DEVELOPMENT.md`.

## Test plan

### Unit tests

- `UnsubscribeTokenServiceTest`
  - Generates tokens with ≥ 32 bytes of entropy (`SecureRandom` not
    `Random`).
  - `resolve(raw)` hashes and looks up; matches the row.
  - Calling `getOrCreate` twice for the same subscriber rotates the
    hash (new raw, old raw no longer resolves).
  - `resolve` returns `Optional.empty()` for unknown/malformed tokens.

- `EmailServiceTest` (mocks `JavaMailSender`)
  - Welcome subject is `Welcome to <name> updates`.
  - Post-published subject is `[<name>] <title>`.
  - Body contains the `{publicUrl}/changelog/unsubscribe?token=...`
    URL exactly once.
  - Multipart message has both `text/plain` and `text/html` parts.
  - CRLF in project name or post title is stripped.

### Integration test — `SubscribeFlowIT` using GreenMail

GreenMail (`com.icegreen:greenmail-junit5:2.1.0`) starts an in-JVM
SMTP server on a random port. Use `@DynamicPropertySource` to point
`spring.mail.host`/`port` at GreenMail. Reuses the same
docker-compose postgres as `SchemaValidationIT`.

```java
@SpringBootTest
@ActiveProfiles("local")
class SubscribeFlowIT {
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry r) {
        r.add("spring.mail.host", () -> "localhost");
        r.add("spring.mail.port", greenMail::getSmtpPort);
    }

    @Test void subscribe_triggersWelcomeEmail() { ... }
    @Test void publishPost_notifiesActiveSubscribers() { ... }
    @Test void unsubscribeTokenLink_deactivatesSubscriber() { ... }
    @Test void smtpDown_doesNotRollBackPublish() { ... }
}
```

Notes:

- For the "SMTP down" case, stop GreenMail mid-test with
  `greenMail.stop()` before hitting publish; assert the post row is
  `status='PUBLISHED'` afterwards and the endpoint returned `200`.
- Because the listener is `@Async`, each test calls
  `GreenMail.waitForIncomingEmail(timeoutMs, expectedCount)`.

### Manual smoke test (local dev)

```bash
# Terminal 1
docker compose up -d postgres mailhog
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2
# 1. Subscribe to demo project
curl -X POST http://localhost:8081/changelog/demo-project/subscribe \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice"}'

# → Open http://localhost:8025 — welcome email visible in MailHog UI.

# 2. Create + publish a post
PID=$(curl -s http://localhost:8081/changelog/demo-project | jq -r '.projects[0].id')  # or use V2 seed
POST_ID=$(curl -sX POST http://localhost:8081/api/v1/projects/$PID/posts \
    -H 'Content-Type: application/json' \
    -d '{"title":"v2.0","content":"# Big update"}' | jq -r '.id')
curl -X POST http://localhost:8081/api/v1/posts/$POST_ID/publish

# → Open http://localhost:8025 — notification email visible.

# 3. Click the unsubscribe link from the MailHog UI.
# → Browser shows "You have been unsubscribed" HTML page.

# 4. Re-publish another post — no new mail to alice@example.com
```

### Schema validation

`mvn -Dtest=SchemaValidationIT test` must pass with the V5 migration
applied. The new `SubscriberToken` entity is the only new JPA class
backed by the new table; validator compares column names/types.

## Risks & open questions

1. **Token rotation on every notification** means if a user reads an
   older email and clicks that unsubscribe link, it will no longer
   work. Mitigation: document it in the mail footer ("Use the latest
   email to unsubscribe"), and/or change the design to `UPSERT` on
   conflict and **keep** the hash stable — but that requires us to
   store the raw somewhere (defeats hash-at-rest) or to never
   regenerate. **Pragmatic alternative**: store the raw token once in
   a memory-only cache keyed by subscriberId, evicted on app restart;
   regenerate only on cache miss. Decide during implementation review.

2. **No outbox** → if the JVM crashes between TX commit and mail
   send, subscribers lose that single notification. Acceptable at
   current scale; revisit when we cross ~1000 subs/project.

3. **`PublicChangelogController.subscribe` is currently not
   explicitly `@Transactional`** — relies on Spring Data's
   per-`save` TX. For the event listener semantics to hold we must
   ensure the event is published **inside** the same TX as the save.
   Extracting a `SubscriberService.subscribe` method annotated
   `@Transactional` is the clean fix; keep that in step 6.

4. **Existing `SubscriberNotificationService`** is called from both
   `PostService` and `ScheduledPublishingJob`. Plan removes both
   call sites; ensure no third caller exists before deletion
   (`grep -r subscriberNotificationService src` — at time of writing
   only those two call sites + the `@Autowired` injection point in
   `PostService.java:38`).

5. **MailHog image `mailhog/mailhog:v1.0.1`** is unmaintained. It is
   still the standard for dev. If the team prefers, `maildev/maildev`
   or `axllent/mailpit` are drop-in replacements on ports 1025/8025.
   Note for reviewer.

6. **Production SMTP credentials** remain empty in `application.yml`
   (`MAIL_USERNAME`/`MAIL_PASSWORD` default to `""`). Deployment must
   set these; otherwise Gmail SMTP will reject `AUTH`. Out of scope
   for this plan, but mention in the commit message / deploy runbook.

7. **HTML email rendering**: we hand-roll a minimal template. If
   stakeholders want branded HTML, a follow-up plan should introduce
   Thymeleaf (or Mustache) with the project's `branding` JSONB
   (colours) — the `Project.branding` map is already present.

8. **Rate of fan-out**: a project with 10 000 subscribers triggers
   10 000 SMTP transactions in one `@Async` call. Today that is fine;
   at scale switch to a batched provider (SES/SendGrid) via a new
   implementation of `EmailService`.

## Definition of done

- [ ] `V5__email_subscription_tokens.sql` lands; `mvn -Dtest=SchemaValidationIT test` green with docker-compose postgres up.
- [ ] `SubscribeFlowIT` green — welcome, post-published, unsubscribe-link, and SMTP-down cases all pass.
- [ ] All unit tests for `EmailService` + `UnsubscribeTokenService` green.
- [ ] `docker compose up -d mailhog` starts MailHog; manual smoke test from "Test plan" produces visible emails in the UI.
- [ ] `GET /changelog/unsubscribe?token=...` returns HTML `200` and flips the subscriber to `UNSUBSCRIBED` exactly once; subsequent clicks stay `200`.
- [ ] Publishing a post with SMTP unreachable still returns `200` from `/api/v1/posts/{id}/publish`, the post row is `PUBLISHED`, and the only artifact is an `ERROR` log line.
- [ ] Both `PostService.publishPost` and `ScheduledPublishingJob.publishScheduledPosts` publish `PostPublishedEvent` (no direct call into notification code).
- [ ] No call site of the old `SubscriberNotificationService` remains (or the file is deleted).
- [ ] `README.md` roadmap item §1 "Email delivery" marked done; `DEVELOPMENT.md` has a MailHog section.
- [ ] `mvn -q verify` green.
