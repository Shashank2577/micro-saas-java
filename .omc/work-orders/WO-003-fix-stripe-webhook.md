# WO-003: Fix Stripe Webhook — Tenant/Customer ID Extraction

## Problem
`StripeWebhookService.handleSubscriptionEvent()` and `updateSubscriptionFromStripe()` have two critical bugs:

### Bug 1 — Hardcoded zero UUIDs
File: `saas-os-core/src/main/java/com/changelog/business/monetization/service/StripeWebhookService.java`

Lines ~81–82:
```java
UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // TODO: extract from metadata
UUID customerId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // TODO: extract
```

When a real Stripe webhook fires (subscription created/updated/deleted), the tenant and customer IDs are zero — all billing records are corrupted.

Stripe checkout sessions can include `metadata` — the `StripeCheckoutService.createCheckoutSession()` already sets:
```java
.putMetadata("tenant_id", tenantId.toString())
.putMetadata("customer_id", customerId.toString())
```

The subscription object returned in the webhook contains the Stripe customer ID. The metadata is on the subscription's related checkout session, NOT directly on the subscription object. The correct way to extract the IDs:

For `customer.subscription.created` / `customer.subscription.updated`:
1. The event object is a `Subscription`
2. `subscription.getMetadata()` may have `tenant_id` if set during creation
3. If not in subscription metadata, look up the Checkout Session that created this subscription:
   ```java
   String checkoutSessionId = subscription.getMetadata().get("checkout_session_id");
   // OR query Stripe API for checkout sessions by subscription ID
   ```

**Practical approach for THIS codebase**: When creating the checkout session in `StripeCheckoutService.createCheckoutSession()`, also add the IDs to the Subscription metadata via `SubscriptionCreateParams`. The simplest reliable approach:

In `handleSubscriptionEvent()`:
```java
Map<String, String> metadata = subscription.getMetadata();
String tenantIdStr = metadata.get("tenant_id");
String customerIdStr = metadata.get("customer_id");
if (tenantIdStr == null || customerIdStr == null) {
    log.warn("Webhook subscription {} missing tenant/customer metadata — skipping", subscription.getId());
    return; // Do not corrupt DB with null UUIDs
}
UUID tenantId = UUID.fromString(tenantIdStr);
UUID customerId = UUID.fromString(customerIdStr);
```

### Bug 2 — LocalDateTime.parse() on Unix epoch integers
File: `saas-os-core/src/main/java/com/changelog/business/monetization/service/StripeWebhookService.java`
Lines ~233, 236, 243, 249, 253

The code calls `LocalDateTime.parse(someObject.toString())` where `someObject` is a `Long` (Unix epoch seconds from the Stripe API). `Long.toString()` produces `"1713654000"` which is not a parseable date — this throws `DateTimeParseException` on every real webhook.

Fix:
```java
// Stripe returns Unix epoch seconds as Long
private LocalDateTime epochToLocalDateTime(Long epochSeconds) {
    if (epochSeconds == null) return null;
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
}
```
Replace all `LocalDateTime.parse(...)` calls in `StripeWebhookService` with `epochToLocalDateTime(longValue)`.

### Bug 3 — Webhook signature verification is commented out
File: `saas-os-core/src/main/java/com/changelog/business/monetization/controller/StripeWebhookController.java`
Lines ~37–38

Stripe sends a `Stripe-Signature` header. The commented-out code should be enabled:
```java
@Value("${stripe.webhook-secret:}")
private String webhookSecret;

// In the handler method:
if (!webhookSecret.isBlank()) {
    try {
        event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
```
If `stripe.webhook-secret` is blank (local dev), skip verification. In production, the env var must be set.

Add `stripe.webhook-secret: ${STRIPE_WEBHOOK_SECRET:}` to all application.yml files that include monetization (saas-os-core provides the controller so it affects all apps). Actually add it only to the saas-os-core default application.yml or document it as a required env var in a `CONFIGURATION.md`.

## What to Do

1. In `StripeWebhookService.handleSubscriptionEvent()`:
   - Replace zero-UUID lines with proper metadata extraction
   - Add null/missing check with a warning log and early return
   
2. In `StripeWebhookService`:
   - Add private `epochToLocalDateTime(Long)` helper method
   - Replace all `LocalDateTime.parse(...)` calls with `epochToLocalDateTime(...)`
   
3. In `StripeWebhookController`:
   - Add `@Value("${stripe.webhook-secret:}")` field
   - Re-enable signature verification with the blank-check guard

4. In `StripeCheckoutService.createCheckoutSession()` — verify that `putMetadata("tenant_id", ...)` and `putMetadata("customer_id", ...)` are already being set on the Subscription (not just the Session). The current code sets them on the Session params. To also set them on the Subscription, add:
   ```java
   .setSubscriptionData(
       SessionCreateParams.SubscriptionData.builder()
           .putMetadata("tenant_id", tenantId.toString())
           .putMetadata("customer_id", customerId.toString())
           .build()
   )
   ```

## Acceptance Criteria
1. `StripeWebhookService.handleSubscriptionEvent()` extracts `tenant_id` and `customer_id` from `subscription.getMetadata()` — no hardcoded zero UUIDs
2. When metadata is missing, the method logs a warning and returns without writing to the database
3. All `LocalDateTime.parse()` calls on Stripe epoch timestamps are replaced with `epochToLocalDateTime()` 
4. `StripeWebhookController` verifies `Stripe-Signature` when `stripe.webhook-secret` is non-blank
5. `StripeCheckoutService.createCheckoutSession()` also sets tenant/customer IDs in Subscription metadata
6. `mvn compile -pl saas-os-core` succeeds
7. `mvn install -pl saas-os-core` succeeds

## Tech Stack
- Java 21, Spring Boot 3.3.5
- Stripe Java SDK (version as declared in saas-os-core pom.xml — check `<stripe.version>` property)
- `com.stripe.model.Subscription` — `getMetadata()` returns `Map<String, String>`
- `com.stripe.net.Webhook.constructEvent(String payload, String sigHeader, String secret)` — signature verification
- `java.time.Instant`, `java.time.LocalDateTime`, `java.time.ZoneOffset` — for epoch conversion
- `com.stripe.model.Event` — deserialized by `Webhook.constructEvent()`
- Do NOT upgrade the Stripe SDK version
