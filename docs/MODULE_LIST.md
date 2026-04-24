# Module List (SaaS Operating System)

The SaaS Operating System is shared across all 10 applications.

1. **Acquisition Module**
   - Landing pages generator.
   - A/B testing variants.
   - Conversion tracking.

2. **Monetisation Module**
   - Stripe integration.
   - Subscription management and checkout sessions.
   - Webhook processing.

3. **Retention Module**
   - Drip email campaign engine.
   - Lifecycle event triggers.
   - Hourly step processing for enrollments.

4. **Success Module**
   - Customer health scoring (0-100 and risk signals).
   - AI-classified support tickets with sentiment scoring.

5. **Intelligence Module**
   - Analytics events and funnel tracking.
   - Unit economics dashboard (MRR, ARR, CAC, LTV).

6. **Core Infrastructure Modules**
   - Multi-tenancy (TenantResolver, tenant_id scoped).
   - Auth (Keycloak, JWT).
   - Local Dev (Docker Compose for Postgres, MinIO).
