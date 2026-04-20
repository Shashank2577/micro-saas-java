# 03 — Stripe Monetization (Checkout, Webhooks, Portal)

> Status: draft / ready to build
> Owner: platform
> Target release: 0.1 MVP billing

## Overview

The `monetization` package has entities and a partial webhook controller
(`StripeWebhookController.java:21`, `StripeWebhookService.java:24`), but no
checkout flow, no customer portal, no `StripeCustomer`/`StripeWebhookEvent`
JPA entities, no signature verification, and no idempotency guard. The goal
of this plan is to bring the billing loop end-to-end:

1. Authenticated tenant user starts a Stripe Checkout Session for a seeded
   product and is redirected to Stripe.
2. Stripe posts webhook events back to the platform. Every event is
   persisted verbatim in `stripe_webhooks`, signature-verified, and
   idempotent on Stripe's event id.
3. `stripe_subscriptions` state is kept in sync with Stripe (status,
   period, cancellation, trial).
4. Authenticated user can open the Stripe-hosted Customer Portal to manage
   their subscription.
5. A developer can run `stripe listen --forward-to
   localhost:8081/webhooks/stripe` and drive this end-to-end locally.

The Stripe Java SDK (`com.stripe:stripe-java:24.0.0`) is already present
in `pom.xml:114-117`, so no dependency changes are needed.

## Goals & Non-goals

**Goals**

- `POST /api/v1/billing/checkout` returns a Stripe Checkout Session URL
  tied to the caller's tenant + user and a tenant-owned `stripe_products`
  row.
- `POST /webhooks/stripe` is public, signature-verified, idempotent, and
  persists the raw event in `stripe_webhooks` *before* any business logic
  runs.
- Webhook handlers for `checkout.session.completed`,
  `customer.subscription.created`, `customer.subscription.updated`,
  `customer.subscription.deleted`, `invoice.payment_failed` keep
  `stripe_subscriptions` consistent with Stripe.
- `POST /api/v1/billing/portal` returns a Stripe Customer Portal URL for
  the caller.
- Clean local dev story with seeded fixture price IDs and Stripe CLI
  instructions.
- `SchemaValidationIT` stays green (Hibernate `ddl-auto=validate` against
  fresh Flyway-applied schema).

**Non-goals**

- Usage-based / metered billing.
- Proration UI (Stripe handles it inside Customer Portal).
- Multi-currency or tax (Stripe Tax) — out of scope for 0.1.
- Pricing experiments (`pricing_experiments` table exists but is not
  wired; separate plan).
- Invoice PDF generation or emailing receipts (Stripe does this).
- Kafka / async event bus — continues to use the in-process
  `BusinessEventPublisher` (`BusinessEventPublisher.java:16`).

## Acceptance criteria

1. `./mvnw -Dtest=SchemaValidationIT verify` passes with new V5 migration
   and new `StripeCustomer` / `StripeWebhookEvent` entities.
2. `POST /api/v1/billing/checkout` with body `{ "productId": "<uuid>" }`
   + Keycloak JWT returns `200 { "url": "https://checkout.stripe.com/..." }`
   and creates or reuses a `stripe_customers` row for the caller.
3. `POST /webhooks/stripe` with an invalid or missing `Stripe-Signature`
   header returns `400` and writes nothing.
4. `POST /webhooks/stripe` with a valid signature always returns `200`
   once the event is persisted, even if downstream processing fails
   (Stripe will otherwise retry forever — we want retries only on raw
   persist failure).
5. Replaying the same webhook event twice results in a single row in
   `stripe_webhooks` (`UNIQUE(stripe_id)`) and a single business side
   effect.
6. `checkout.session.completed` + `customer.subscription.created` in
   either order converge to one `stripe_subscriptions` row with matching
   `stripe_session_id`, `stripe_id`, `status`, `current_period_start`,
   `current_period_end`.
7. `customer.subscription.updated` changing `status` to `past_due` (from
   `invoice.payment_failed`) is reflected in the row within one webhook
   delivery.
8. `POST /api/v1/billing/portal` returns `200 { "url": "https://billing.stripe.com/..." }`
   for an authenticated user who has a `stripe_customers` row.
9. Running `stripe listen --forward-to localhost:8081/webhooks/stripe`
   and `stripe trigger checkout.session.completed` produces a green
   "200 OK" in Stripe CLI output and a row in `stripe_webhooks`.

## User-facing surface

| Method | Path                        | Auth        | Request body                 | Response                         |
|--------|-----------------------------|-------------|------------------------------|----------------------------------|
| POST   | `/api/v1/billing/checkout`  | JWT         | `{ productId: UUID }`        | `{ url: string, sessionId: str }`|
| POST   | `/api/v1/billing/portal`    | JWT         | `{}` (optional `returnUrl`)  | `{ url: string }`                |
| GET    | `/api/v1/billing/products`  | JWT         | —                            | `List<StripeProduct>` for tenant |
| GET    | `/api/v1/billing/subscription` | JWT      | —                            | current `StripeSubscription` or 404 |
| POST   | `/webhooks/stripe`          | Public (sig)| raw Stripe event JSON        | `200` on accepted, `400` on bad sig |

The existing `POST /api/v1/webhooks/stripe` in
`StripeWebhookController.java:18` is wrong: webhook must be **unauthenticated**
because Stripe cannot present a JWT. Move the path to `/webhooks/stripe`
and permit it in `SecurityConfig`.

## Architecture & data flow

### Checkout flow

```
+-----------+        POST /api/v1/billing/checkout        +------------------+
|  Browser  | -------------------------------------------> |  BillingCtrl     |
|  (SPA)    |  Authorization: Bearer <jwt>                 |  (authed)        |
+-----------+  { productId }                               +---------+--------+
                                                                     |
                                                         tenantId=jwt.tenant_id
                                                         userId=jwt.sub
                                                                     |
                                                                     v
                                                         +-----------+---------+
                                                         | BillingService      |
                                                         | 1. load StripeProduct
                                                         |    by (tenantId, id)
                                                         | 2. find-or-create
                                                         |    StripeCustomer
                                                         |    (Customer.create
                                                         |     via Stripe SDK)
                                                         | 3. Session.create
                                                         |    mode=subscription
                                                         |    client_reference_id
                                                         |    = "<tenantId>:<userId>"
                                                         |    customer=cus_xxx
                                                         |    line_items[0].price
                                                         |    = product.stripeId
                                                         |    success_url, cancel_url
                                                         +-----------+---------+
                                                                     |
                                                                     v
                                                         +-----------+---------+
                                                         |   Stripe API         |
                                                         +-----------+---------+
                                                                     |
                                                         returns { id, url }
                                                                     |
                                             <------------------------
                                             200 { url, sessionId }
+-----------+                                                        
|  Browser  | <------ 302 redirect to checkout.stripe.com/c/pay/cs_...
+-----------+
```

User completes payment on Stripe. Stripe then fires webhook events.

### Webhook / subscription-update flow

```
+--------+    POST /webhooks/stripe                     +------------------+
| Stripe | --------------------------------------------> | StripeWebhookCtrl |
|        |  Stripe-Signature: t=ts,v1=sig               |  (public)        |
|        |  raw body (bytes)                            +---------+--------+
+--------+                                                        |
                                                  (1) Webhook.constructEvent
                                                      (sig + secret + tolerance)
                                                      --> throws if invalid --> 400
                                                                  |
                                              +-------- TX #1 BEGIN --------+
                                              |                             |
                                              | (2) INSERT INTO             |
                                              |     stripe_webhooks         |
                                              |     (stripe_id UNIQUE,      |
                                              |      event_type, payload,   |
                                              |      tenant_id, processed=f)|
                                              |     ON CONFLICT DO NOTHING  |
                                              |                             |
                                              |  if 0 rows inserted:        |
                                              |    --> duplicate, COMMIT    |
                                              |        return 200 (idempotent)
                                              |                             |
                                              +----------- COMMIT ----------+
                                                                  |
                                                      return 200 to Stripe
                                                   (NEVER delay the 200 on
                                                    business logic - Stripe
                                                    retries aggressively)
                                                                  |
                                              +-------- TX #2 BEGIN --------+  async @TransactionalEventListener
                                              |                             |  OR same request, AFTER 200
                                              | (3) route by event_type:    |  in @Async handler
                                              |  checkout.session.completed |
                                              |    -> link customer+session |
                                              |  customer.subscription.*    |
                                              |    -> upsert StripeSub row  |
                                              |  invoice.payment_failed     |
                                              |    -> set status=past_due   |
                                              |                             |
                                              | (4) UPDATE stripe_webhooks  |
                                              |     SET processed=true,     |
                                              |     processed_at=now()      |
                                              |     (or error_message on    |
                                              |      failure, processed=f)  |
                                              +----------- COMMIT ----------+
```

Key design rules:

- **Raw persist is transactional and happens BEFORE processing.** We ACK
  Stripe as soon as the row is safe. Business logic failure must not
  keep us from returning 200 — if we 500, Stripe retries and we end up
  with the same event processed N times. The `UNIQUE(stripe_id)` in the
  `stripe_webhooks` table (already in V3 migration) is our idempotency
  key.
- **`ON CONFLICT (stripe_id) DO NOTHING`** is the idempotency primitive.
  The INSERT either succeeds (first time) or inserts zero rows
  (duplicate replay). Only the first path runs handlers.
- **Use the Stripe SDK's `Webhook.constructEvent`**, not a hand-rolled
  HMAC. It handles the `t=`, `v1=`, timestamp tolerance (5 min default),
  and raises `SignatureVerificationException` on bad signatures.
  `StripeWebhookController.java:72` has a stub — replace it.
- **Handlers are idempotent by construction**: every upsert keys on
  `stripe_id` (the Stripe subscription id). Out-of-order events (e.g.
  `subscription.created` arriving after `subscription.updated`) merge
  cleanly.

## Database changes

Create **`V5__stripe_customer_and_webhook_entities.sql`**. The Stripe
tables exist from V3 (`V3__business_modules.sql:149-228`). V5 does NOT
redefine them. V5 adds:

1. **Trigger `updated_at`** on `stripe_subscriptions` (V3 defines the
   column but no `NOT NULL DEFAULT now()` trigger — confirm by reading
   V3 again before writing V5; schema already uses `DEFAULT now()` on
   create but not on update).

2. **Index for webhook replay detection:** V3 already has
   `UNIQUE(tenant_id, stripe_id)` on `stripe_webhooks` implicitly via
   `CREATE TABLE`? Re-check: V3 line 208-225 defines the table with **no
   UNIQUE constraint on stripe_id**. We MUST add:

   ```sql
   ALTER TABLE stripe_webhooks
     ADD CONSTRAINT uq_stripe_webhooks_stripe_id UNIQUE (stripe_id);
   ```

   (Stripe event ids are globally unique across all tenants, so no
   `(tenant_id, stripe_id)` composite — we want one row per Stripe
   event even if tenant resolution is deferred.)

3. **Make `tenant_id` nullable on `stripe_webhooks`** — we persist the
   raw event *before* we know the tenant (tenant is derived from
   `client_reference_id` or `customer` metadata). V3 declares
   `tenant_id UUID NOT NULL`. Change:

   ```sql
   ALTER TABLE stripe_webhooks ALTER COLUMN tenant_id DROP NOT NULL;
   ```

4. **No new tables.** `stripe_customers`, `stripe_products`,
   `stripe_subscriptions`, `stripe_webhooks` already exist.

The new JPA entities `StripeCustomer` and `StripeWebhookEventEntity` map
to existing tables with the column types and nullability that V5
leaves in place. `SchemaValidationIT` (`SchemaValidationIT.java:44`)
will fail if any `@Column` doesn't match.

## Files to create or modify

### Create

| Path | Purpose |
|------|---------|
| `src/main/resources/db/migration/V5__stripe_customer_and_webhook_entities.sql` | Add `UNIQUE(stripe_id)` to `stripe_webhooks`, drop `NOT NULL` on `tenant_id`. |
| `src/main/java/com/changelog/business/monetization/model/StripeCustomer.java` | JPA entity for `stripe_customers`. |
| `src/main/java/com/changelog/business/monetization/model/StripeWebhookEventEntity.java` | JPA entity for `stripe_webhooks` (rename the DTO or put in `model/` to avoid collision with existing `dto.StripeWebhookEvent`). |
| `src/main/java/com/changelog/business/monetization/repository/StripeCustomerRepository.java` | `findByTenantIdAndUserId`, `findByStripeId`. |
| `src/main/java/com/changelog/business/monetization/repository/StripeWebhookEventRepository.java` | `existsByStripeId`, save. |
| `src/main/java/com/changelog/business/monetization/config/StripeProperties.java` | `@ConfigurationProperties("stripe")` binding `apiKey`, `webhookSecret`, `successUrl`, `cancelUrl`, `portalReturnUrl`. |
| `src/main/java/com/changelog/business/monetization/config/StripeConfig.java` | `@PostConstruct Stripe.apiKey = props.getApiKey()` (Stripe SDK is static-keyed). |
| `src/main/java/com/changelog/business/monetization/service/StripeCheckoutService.java` | `createCheckoutSession(tenantId, userId, productId)` returns `CheckoutSessionResponse(url, sessionId)`. Creates/reuses `stripe_customers`. |
| `src/main/java/com/changelog/business/monetization/service/StripeCustomerPortalService.java` | `createPortalSession(tenantId, userId, returnUrl)`. |
| `src/main/java/com/changelog/business/monetization/service/StripeSignatureVerifier.java` | Thin wrapper around `Webhook.constructEvent` — testable + mockable. |
| `src/main/java/com/changelog/business/monetization/service/StripeEventPersister.java` | `Optional<StripeWebhookEventEntity> persistIfNew(Event event, String rawPayload)` using `ON CONFLICT DO NOTHING`. Runs in its own `@Transactional(REQUIRES_NEW)`. |
| `src/main/java/com/changelog/business/monetization/controller/BillingController.java` | `/api/v1/billing/checkout`, `/portal`, `/products`, `/subscription`. |
| `src/main/java/com/changelog/business/monetization/dto/CreateCheckoutRequest.java` | `{ productId: UUID }`. |
| `src/main/java/com/changelog/business/monetization/dto/CheckoutSessionResponse.java` | `{ url, sessionId }`. |
| `src/main/java/com/changelog/business/monetization/dto/PortalSessionResponse.java` | `{ url }`. |
| `src/test/java/com/changelog/business/monetization/StripeSignatureVerifierTest.java` | Unit test with a known fixture payload + signature. |
| `src/test/java/com/changelog/business/monetization/StripeWebhookIdempotencyIT.java` | Double-post same event, assert single row / single side effect. |
| `src/test/java/com/changelog/business/monetization/StripeCheckoutControllerTest.java` | MockMvc with mocked `Session.create`. |

### Modify

| Path | Change |
|------|--------|
| `src/main/java/com/changelog/business/monetization/controller/StripeWebhookController.java:18` | Change `@RequestMapping("/api/v1/webhooks/stripe")` to `"/webhooks/stripe"`. Take `HttpServletRequest` raw body + `Stripe-Signature` header. Delegate to verifier + persister + router. Return `400` on bad sig, `200` otherwise. Always read body as raw `byte[]` / `String` (do NOT let Jackson parse it — signature verification is over raw bytes). |
| `src/main/java/com/changelog/business/monetization/service/StripeWebhookService.java:24` | Remove JSON parsing + fake UUIDs (`StripeWebhookService.java:81-82`). Rewrite handlers to take `com.stripe.model.Event` and pull typed objects from `event.getDataObjectDeserializer().getObject()`. Add `checkout.session.completed` case. Delete the dummy tenantId lookup. |
| `src/main/java/com/changelog/business/monetization/dto/StripeWebhookEvent.java` | Delete — replaced by typed `com.stripe.model.Event` from SDK. The persisted-row entity lives under `model/`. |
| `src/main/java/com/changelog/config/SecurityConfig.java:22-28` | Add `.requestMatchers("/webhooks/stripe").permitAll()` **before** the `/api/v1/**` matcher. |
| `src/main/resources/application.yml:48` | Add `stripe:` config block (keys below). |
| `docker-compose.yml` | Optional: add `stripe-cli` service comment / README snippet (we use host-installed CLI to avoid shipping Docker Hub image with secrets). |
| `pom.xml:114-117` | No change — `stripe-java:24.0.0` already present. |

### `application.yml` additions

```yaml
stripe:
  api-key: ${STRIPE_API_KEY:sk_test_missing}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:whsec_missing}
  success-url: ${STRIPE_SUCCESS_URL:http://localhost:5173/billing/success?session_id={CHECKOUT_SESSION_ID}}
  cancel-url: ${STRIPE_CANCEL_URL:http://localhost:5173/billing/cancel}
  portal-return-url: ${STRIPE_PORTAL_RETURN_URL:http://localhost:5173/settings/billing}
```

(Defaults are intentionally non-functional placeholders so a bare boot
fails loudly if env vars aren't set — never silently hit Stripe with a
"missing" key.)

## Implementation steps

Do the work in this order. Each step is verifiable in isolation.

### Step 1 — Config plumbing (no business logic yet)

- Add `StripeProperties` (`@ConfigurationProperties("stripe")`, `@Validated`,
  `@NotBlank` on `apiKey` and `webhookSecret`).
- Add `StripeConfig` with a `@PostConstruct` that sets
  `com.stripe.Stripe.apiKey = props.getApiKey();`.
- Register `@EnableConfigurationProperties(StripeProperties.class)` on the
  `StripeConfig` class.
- Update `application.yml` as above.
- Verify: app still boots against Flyway-applied schema.

### Step 2 — V5 migration + new entities

- Write `V5__stripe_customer_and_webhook_entities.sql` (ALTER TABLE
  only, see "Database changes").
- Create `StripeCustomer` entity — mirror
  `V3__business_modules.sql:149-166` columns, use
  `@JdbcTypeCode(SqlTypes.JSON)` on `metadata` to match `StripeProduct`
  style.
- Create `StripeWebhookEventEntity` — map table `stripe_webhooks`,
  fields: `id`, `tenantId` (nullable), `stripeId` (unique), `eventType`,
  `payload` (JSONB → `Map<String,Object>` or `String`), `processed`,
  `processedAt`, `errorMessage`, `createdAt`.
- Create `StripeCustomerRepository` and `StripeWebhookEventRepository`.
- Run `./mvnw -Dtest=SchemaValidationIT verify` — MUST be green before
  continuing.

### Step 3 — Signature verifier + event persister

- Write `StripeSignatureVerifier`:
  ```java
  public Event verify(String rawPayload, String signature) {
      try {
          return Webhook.constructEvent(rawPayload, signature, props.getWebhookSecret());
      } catch (SignatureVerificationException e) {
          throw new InvalidWebhookSignatureException(e);
      }
  }
  ```
- Write `StripeEventPersister.persistIfNew(Event event, String rawPayload)`
  using `@Transactional(propagation = REQUIRES_NEW)`. Uses
  `repository.existsByStripeId(event.getId())` + `save(..)` — relies on
  `UNIQUE(stripe_id)` to prevent race between two parallel deliveries.
  Return `Optional.empty()` on duplicate (UNIQUE violation caught &
  swallowed) or `Optional.of(entity)` on first-time persist.
- Unit-test verifier with a hand-crafted payload + signature computed
  from a known secret (see Test plan).

### Step 4 — Rewrite webhook controller

- Move `@RequestMapping` to `/webhooks/stripe`.
- Read body as `String` via `@RequestBody String payload` (Spring gives
  raw string because no `@RequestMapping(consumes=...)` forces JSON).
- Flow:
  1. `Event event = verifier.verify(payload, signatureHeader);` — on
     exception return `ResponseEntity.badRequest().build()`.
  2. `Optional<...> saved = persister.persistIfNew(event, payload);`
  3. If `saved.isEmpty()` (duplicate), return `200` immediately.
  4. If present, dispatch to `webhookService.handle(event)` inside a
     try/catch. On success mark `processed=true, processed_at=now()`.
     On failure, log + `error_message = ex.getMessage()` and leave
     `processed=false` so a back-office job can retry. Still return
     `200` — we already ACK'd the safe copy.
- Add permit rule in `SecurityConfig.java:22`:
  `.requestMatchers("/webhooks/stripe").permitAll()`.

### Step 5 — Rewrite `StripeWebhookService`

- Signature change: `public void handle(com.stripe.model.Event event)`.
- Use `event.getDataObjectDeserializer().getObject()` to get
  `Subscription`, `Invoice`, `Session`, etc.
- Cases to implement:
  - `checkout.session.completed` → resolve tenant+user from
    `session.getClientReferenceId()` (format `tenantId:userId`). Upsert
    `stripe_customers` by `(tenantId, stripe_id=session.getCustomer())`.
    If `session.getSubscription()` is already set, also pre-seed the
    `stripe_subscriptions` row with `stripe_session_id` for later
    `subscription.created` to find.
  - `customer.subscription.created` / `.updated` →
    `subscriptionRepository.findByStripeId(sub.getId()).orElseGet(...)`
    — upsert status, period, cancellation, trial. Tenant + customer
    resolved via `stripe_customers.stripe_id = sub.getCustomer()`.
  - `customer.subscription.deleted` → set status=`canceled`,
    `canceled_at=now()`.
  - `invoice.payment_failed` → find sub by
    `invoice.getSubscription()`, set status=`past_due`, publish
    `PAYMENT_FAILED` business event.
- Replace the fake UUIDs at `StripeWebhookService.java:81-82` with real
  lookups. Throw a typed `TenantResolutionException` if neither
  `client_reference_id` nor `stripe_customers` row yields a tenant —
  controller will log + leave the row `processed=false` for manual
  reconciliation.

### Step 6 — Checkout + Portal services and controller

- `StripeCheckoutService.createCheckoutSession(tenantId, userId, productId)`:
  1. Load `StripeProduct` scoped by tenant. 404 if missing.
  2. Load `StripeCustomer` by `(tenantId, userId)`. If missing, call
     `Customer.create(...)` on Stripe with `email` + metadata
     `{tenantId, userId}`, persist locally.
  3. `SessionCreateParams`:
     - `mode = SUBSCRIPTION` (or `PAYMENT` when `product.interval ==
       null`).
     - `customer = stripeCustomer.getStripeId()`.
     - `clientReferenceId = tenantId + ":" + userId`.
     - `lineItems[0].price = product.getStripeId()` + quantity 1.
     - `successUrl = props.getSuccessUrl()`.
     - `cancelUrl = props.getCancelUrl()`.
     - `subscriptionData.metadata = {tenantId, userId, productUuid}`.
  4. Return `{ url: session.getUrl(), sessionId: session.getId() }`.
- `StripeCustomerPortalService.createPortalSession(tenantId, userId,
  returnUrl)` — `PortalSession.create` with `customer = ...`. If the
  user has no `stripe_customers` row, return 409.
- `BillingController`:
  - `POST /api/v1/billing/checkout` → `CheckoutSessionResponse`.
  - `POST /api/v1/billing/portal` → `PortalSessionResponse`.
  - `GET /api/v1/billing/products` → `stripeProductRepository.findByTenantIdAndActive(tenantId, true)`.
  - `GET /api/v1/billing/subscription` → current active sub for user, 404 otherwise.
  - All use `@AuthenticationPrincipal Jwt` + `TenantResolver`
    (`SupportTicketController.java:26` is the pattern to follow).

### Step 7 — Local dev seeding + Stripe CLI

- Document (in `DEVELOPMENT.md` update — not created here, left to
  implementer):
  - Export `STRIPE_API_KEY=sk_test_...`,
    `STRIPE_WEBHOOK_SECRET=whsec_...`.
  - Create fixture price on Stripe dashboard (`price_test_xxx`).
  - Seed row via psql or SQL snippet in `V2__sample_data.sql` peer:
    ```sql
    INSERT INTO stripe_products (tenant_id, stripe_id, name, price_cents, currency, interval)
    VALUES ('<tenant-uuid>', 'price_test_xxx', 'Pro Monthly', 1900, 'usd', 'month');
    ```
  - Run app on 8081 (or keep 8080; Keycloak is on 8080 in docker-compose
    — consider switching app to `server.port=8081` locally). Plan uses
    8081 to match the prompt.
  - `stripe listen --forward-to localhost:8081/webhooks/stripe` prints
    the `whsec_...` secret and forwards events.
  - `stripe trigger checkout.session.completed` / `stripe trigger
    customer.subscription.created` / etc. to fire fixtures.

### Step 8 — Cleanup

- Delete `src/main/java/com/changelog/business/monetization/dto/StripeWebhookEvent.java` (replaced by SDK types).
- Drop the `/test` GET endpoint in the webhook controller
  (`StripeWebhookController.java:60-66`) — not needed.

## Test plan

### Unit — signature verification

`StripeSignatureVerifierTest`:

- Fixture: `payload = "{\"id\":\"evt_test\",\"type\":\"ping\"}"`,
  `secret = "whsec_unit_test"`, known timestamp `1700000000`.
- Compute expected signature:
  `v1 = HMAC_SHA256(secret, "1700000000." + payload)`.
- Build `Stripe-Signature` header:
  `"t=1700000000,v1=" + hex(v1)`.
- Assert `verifier.verify(payload, header)` returns a non-null `Event`
  with `id == "evt_test"`.
- Assert `verifier.verify(payload, header_with_wrong_v1)` throws
  `InvalidWebhookSignatureException`.
- Assert timestamp tolerance (default 300s) — payload with
  `t=1000000000` (older than tolerance) throws.

### Integration — idempotency

`StripeWebhookIdempotencyIT` (Spring `@SpringBootTest` + `@ActiveProfiles("local")`,
same pattern as `SchemaValidationIT.java:33`):

- Set `stripe.webhook-secret` to a known test secret via
  `@TestPropertySource`.
- Build a valid signed payload for `customer.subscription.created` with
  a real Stripe-shaped JSON (fixture file under
  `src/test/resources/stripe/subscription_created.json`).
- POST it to `/webhooks/stripe` via MockMvc. Assert 200.
- Count `stripe_webhooks` rows with that `stripe_id` → 1.
- Count `stripe_subscriptions` rows with matching stripe id → 1.
- POST the same payload again. Assert 200.
- Row counts unchanged. No second `SUBSCRIPTION_CREATED` business
  event published (verify by spying on `BusinessEventPublisher`).

### Integration — checkout flow

`StripeCheckoutControllerTest`:

- MockMvc with `@MockBean` on `Stripe.apiKey`-using static calls
  (use `mockito-inline` or wrap `Session.create` behind an injectable
  `StripeSessionClient` to make it mockable).
- Stub `Customer.create` → returns `cus_test_1`.
- Stub `Session.create` → returns session with `id=cs_test_1`,
  `url=https://checkout.stripe.com/c/pay/cs_test_1`.
- POST `/api/v1/billing/checkout` with body `{productId: <seeded>}` and
  a mock `Jwt` principal (use `jwt()` from `spring-security-test`).
- Assert 200, response contains the stubbed URL.
- Assert `stripe_customers` has a row for `(tenantId, userId)` with
  `stripe_id=cus_test_1`.

### Manual smoke — Stripe CLI

Runbook (verify during PR):

1. `docker compose up -d postgres keycloak`.
2. `./mvnw spring-boot:run` on port 8081.
3. `stripe login` (once).
4. `stripe listen --forward-to localhost:8081/webhooks/stripe` — copy
   the `whsec_...` into `STRIPE_WEBHOOK_SECRET` and restart the app.
5. `curl -X POST localhost:8081/api/v1/billing/checkout -H
   "Authorization: Bearer $JWT" -d '{"productId":"<uuid>"}'` → open
   `url` in browser, pay with `4242 4242 4242 4242`.
6. Observe in `stripe listen` output:
   `checkout.session.completed --> 200`,
   `customer.subscription.created --> 200`,
   `invoice.paid --> 200`.
7. `psql changelog -c "select stripe_id, event_type, processed from
   stripe_webhooks order by created_at desc limit 10;"` — all
   `processed=true`.
8. `stripe trigger invoice.payment_failed` → app row shows
   `status=past_due` and one new `stripe_webhooks` entry.

## Risks & open questions

1. **Out-of-order webhooks.** Stripe does NOT guarantee event order.
   `subscription.updated` can arrive before `subscription.created`, and
   `invoice.paid` can arrive before `subscription.created`. Mitigation:
   every handler does `findByStripeId(...).orElseGet(createNew(...))`
   and upserts full state. Never assume a prior event has landed. Add a
   regression test that replays events in reversed order.
2. **Race between `checkout.session.completed` and
   `customer.subscription.created`.** Two deliveries can hit in
   parallel, both trying to insert the same `stripe_subscriptions` row.
   Mitigation: rely on `UNIQUE(tenant_id, stripe_id)` in
   `stripe_subscriptions` (V3 line 200) + retry on
   `DataIntegrityViolationException` by re-reading and upserting. Or
   wrap in `SERIALIZABLE` transaction for these two types.
3. **Webhook secret rotation.** Stripe allows rolling secrets. The SDK
   accepts only one secret per `constructEvent` call. Mitigation: make
   `stripe.webhook-secret` a `List<String>` — verifier tries each
   until one works. Defer to follow-up if zero-downtime rotation isn't
   required for 0.1.
4. **Dev vs prod webhook secrets.** Stripe CLI issues an *ephemeral*
   `whsec_...` that differs from the dashboard-registered endpoint
   secret. Operators must set `STRIPE_WEBHOOK_SECRET` correctly per
   environment. Document in `DEVELOPMENT.md` and fail loudly on
   startup if it's the placeholder value.
5. **Tenant resolution for events before the session completes.** For
   `setup_intent.*` or pre-checkout events, there's no
   `client_reference_id`. We persist with `tenant_id=null` (V5 drops
   NOT NULL) and reconcile later. Flag for operator review.
6. **Stripe SDK static API key.** `Stripe.apiKey` is a global. If we
   ever add a second Stripe account (e.g. per-tenant Connect), this
   design breaks. Out of scope — document the assumption.
7. **Raw payload handling.** Spring must give us the exact bytes Stripe
   signed. `@RequestBody String` works only if no filter mutates the
   body. Confirm no CORS/compression filter rewrites the request before
   the controller — in practice Spring Boot's defaults are safe, but
   verify with a failing test if signature ever fails unexpectedly.
8. **Existing `StripeWebhookService.java:225` period parsing is broken.**
   The current code calls `LocalDateTime.parse((String) map.get("instant"))`
   on fields that Stripe sends as Unix epoch seconds (longs), not ISO
   strings. Step 5 rewrites this using `Subscription.getCurrentPeriodStart()`
   returning `Long`, converted via `Instant.ofEpochSecond(...)`.
9. **`customer_id` FK in `stripe_subscriptions`.** V3 line 171 makes it
   `NOT NULL REFERENCES stripe_customers(id)`. A subscription event can
   arrive before we've persisted `stripe_customers` (e.g. if
   `customer.created` webhook is delayed). Mitigation: the checkout
   flow creates `stripe_customers` synchronously *before* returning the
   Stripe URL, so by the time subscription events fire, the customer
   row exists. For pure-API-created subs (Stripe dashboard), we'll have
   to lazy-create the customer row from `customer.created` webhook
   first — add that handler in follow-up.

## Definition of done

- [ ] V5 migration applied; `SchemaValidationIT` green.
- [ ] `StripeCustomer` + `StripeWebhookEventEntity` entities, repos
      committed.
- [ ] `POST /webhooks/stripe` public, signature-verified, idempotent,
      persists before processing.
- [ ] Five required event types handled
      (`checkout.session.completed`, `customer.subscription.created`,
      `customer.subscription.updated`, `customer.subscription.deleted`,
      `invoice.payment_failed`) with `stripe_subscriptions.status` and
      period kept in sync.
- [ ] `POST /api/v1/billing/checkout` returns a real Stripe URL end-to-
      end (local Stripe test mode).
- [ ] `POST /api/v1/billing/portal` returns a real Stripe Customer
      Portal URL.
- [ ] `StripeSignatureVerifierTest` unit test green with a hand-crafted
      HMAC fixture.
- [ ] `StripeWebhookIdempotencyIT` integration test green against the
      docker-compose Postgres.
- [ ] Manual smoke via `stripe listen --forward-to
      localhost:8081/webhooks/stripe` + `stripe trigger` for each event
      type returns 200 and writes one row per event id.
- [ ] `SecurityConfig.java` permits `/webhooks/stripe`.
- [ ] Old `dto/StripeWebhookEvent.java` removed; old
      `/api/v1/webhooks/stripe` path removed.
- [ ] `application.yml` contains `stripe:` block; startup fails loudly
      on placeholder `sk_test_missing` / `whsec_missing`.
- [ ] `DEVELOPMENT.md` updated with Stripe CLI quickstart (separate PR
      acceptable).
