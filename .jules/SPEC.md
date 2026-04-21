# Specification - WO-003 Fix Stripe Webhook

## Problem
- Stripe webhook events use hardcoded zero UUIDs for tenant and customer IDs.
- LocalDateTime.parse() is called on Unix epoch integers, causing parsing errors.
- Webhook signature verification is commented out.

## Proposed Changes
1. Update StripeCheckoutService to include tenant_id and customer_id in Subscription metadata.
2. Update StripeWebhookController to implement signature verification using Stripe SDK.
3. Refactor StripeWebhookService to use typed Stripe models and properly extract metadata.
4. Implement epochToLocalDateTime helper for date conversion.
5. Promote gson dependency to compile scope to support Stripe SDK.
