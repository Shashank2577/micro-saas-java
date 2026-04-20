-- ============================================
-- SaaS Operating System - Business Operations Modules
-- ============================================

-- ============================================
-- Customer Acquisition Modules
-- ============================================

CREATE TABLE landing_pages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'draft',  -- draft | active | paused | archived
    primary_domain  TEXT,                            -- custom domain
    platform_hosted TEXT,                            -- product.saasops.io
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE landing_variants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id         UUID NOT NULL REFERENCES landing_pages(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    is_control      BOOLEAN NOT NULL DEFAULT false,
    traffic_split   INT NOT NULL DEFAULT 50,         -- percentage 0-100
    status          TEXT NOT NULL DEFAULT 'active',

    -- Landing page content
    headline        TEXT NOT NULL,
    subheadline     TEXT,
    cta_text        TEXT NOT NULL,
    cta_link        TEXT,
    body_content    TEXT,                            -- markdown
    logo_url        TEXT,
    hero_image_url  TEXT,
    testimonial     TEXT,                            -- social proof
    features        JSONB,                           -- array of {name, description, icon}

    -- SEO
    meta_title      TEXT,
    meta_description TEXT,
    og_image_url    TEXT,

    -- Performance tracking
    visitors        INT NOT NULL DEFAULT 0,
    conversions     INT NOT NULL DEFAULT 0,
    conversion_rate NUMERIC(5,2),                     -- calculated

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_landing_variants_page ON landing_variants(page_id);
CREATE INDEX idx_landing_variants_status ON landing_variants(status);

CREATE TABLE email_campaigns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    type            TEXT NOT NULL,                    -- drip | announcement | reactivation | onboarding
    status          TEXT NOT NULL DEFAULT 'draft',    -- draft | scheduled | running | paused | completed

    -- Campaign settings
    trigger_event   TEXT,                             -- signup | trial_end | payment_failed | churn_risk
    delay_hours     INT,                              -- delay after trigger event

    -- Email content
    subject         TEXT NOT NULL,
    preview_text    TEXT,
    body_html       TEXT,
    body_text       TEXT,

    -- A/B testing
    subject_variants TEXT[],                          -- alternative subjects

    -- Scheduling
    scheduled_for   TIMESTAMPTZ,

    -- Performance
    sent_count      INT NOT NULL DEFAULT 0,
    open_count      INT NOT NULL DEFAULT 0,
    click_count     INT NOT NULL DEFAULT 0,
    unsubscribe_count INT NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_campaigns_tenant ON email_campaigns(tenant_id);
CREATE INDEX idx_email_campaigns_status ON email_campaigns(status);

-- Email campaign recipients (for tracking)
CREATE TABLE email_campaign_recipients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID NOT NULL REFERENCES email_campaigns(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    email           TEXT NOT NULL,

    -- Variant (if A/B testing)
    subject_variant INT,

    -- Tracking
    sent_at         TIMESTAMPTZ,
    opened_at       TIMESTAMPTZ,
    clicked_at      TIMESTAMPTZ,
    unsubscribed_at TIMESTAMPTZ,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_recipients_campaign ON email_campaign_recipients(campaign_id);
CREATE INDEX idx_email_recipients_user ON email_campaign_recipients(user_id);

-- ============================================
-- Monetization Modules
-- ============================================

CREATE TABLE stripe_products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    stripe_id       TEXT NOT NULL,                    -- from Stripe (price_12345)
    name            TEXT NOT NULL,
    description     TEXT,

    -- Pricing
    price_cents     INT NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'usd',
    interval        TEXT,                             -- month | year | null (one-time)
    interval_count  INT DEFAULT 1,                    -- e.g., 3 for every 3 months

    -- Features
    features        JSONB,                           -- array of feature strings

    -- Metadata
    metadata        JSONB,
    active          BOOLEAN NOT NULL DEFAULT true,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, stripe_id)
);

CREATE INDEX idx_stripe_products_tenant ON stripe_products(tenant_id);

CREATE TABLE stripe_customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    user_id         UUID NOT NULL REFERENCES cc.users(id),
    stripe_id       TEXT NOT NULL,                    -- from Stripe (cus_12345)

    -- Customer info
    email           TEXT NOT NULL,
    name            TEXT,

    -- Metadata
    metadata        JSONB,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, stripe_id),
    UNIQUE(tenant_id, user_id)
);

CREATE TABLE stripe_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    customer_id     UUID NOT NULL REFERENCES stripe_customers(id),
    stripe_id       TEXT NOT NULL,                    -- from Stripe (sub_12345)
    stripe_session_id TEXT,                            -- Stripe Checkout session id
    stripe_product_id UUID NOT NULL REFERENCES stripe_products(id),

    status          TEXT NOT NULL,                    -- active | canceled | past_due | trialing | incomplete | incomplete_expired

    -- Period
    current_period_start TIMESTAMPTZ,
    current_period_end   TIMESTAMPTZ,

    -- Cancellation
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
    cancel_at       TIMESTAMPTZ,
    canceled_at     TIMESTAMPTZ,

    -- Trial
    trial_start     TIMESTAMPTZ,
    trial_end       TIMESTAMPTZ,

    -- Quantity (for seats)
    quantity        INT NOT NULL DEFAULT 1,

    -- Metadata
    metadata        JSONB,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, stripe_id)
);

CREATE INDEX idx_stripe_subscriptions_customer ON stripe_subscriptions(customer_id);
CREATE INDEX idx_stripe_subscriptions_status ON stripe_subscriptions(status);
CREATE INDEX idx_stripe_subscriptions_tenant ON stripe_subscriptions(tenant_id);

-- Stripe webhooks (for audit trail)
CREATE TABLE stripe_webhooks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),

    -- Webhook info
    stripe_id       TEXT NOT NULL,                    -- Stripe webhook event ID
    event_type      TEXT NOT NULL,                    -- customer.subscription.created, invoice.payment_succeeded, etc.

    -- Payload
    payload         JSONB NOT NULL,                   -- full Stripe event payload

    -- Processing
    processed       BOOLEAN NOT NULL DEFAULT false,
    processed_at    TIMESTAMPTZ,
    error_message   TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stripe_webhooks_tenant ON stripe_webhooks(tenant_id);
CREATE INDEX idx_stripe_webhooks_processed ON stripe_webhooks(processed);

CREATE TABLE pricing_experiments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'running',  -- running | completed | stopped

    -- Experiment config
    control_product_id UUID NOT NULL REFERENCES stripe_products(id),
    test_product_id UUID NOT NULL REFERENCES stripe_products(id),
    traffic_split   INT NOT NULL DEFAULT 50,           -- percentage to control

    -- Results
    control_visitors INT NOT NULL DEFAULT 0,
    control_conversions INT NOT NULL DEFAULT 0,
    test_visitors   INT NOT NULL DEFAULT 0,
    test_conversions INT NOT NULL DEFAULT 0,

    -- Statistical analysis
    control_conversion_rate NUMERIC(5,2),
    test_conversion_rate     NUMERIC(5,2),
    uplift          NUMERIC(5,2),                     -- percentage improvement
    statistical_significance NUMERIC(5,2),            -- p-value
    confidence_interval JSONB,                       -- lower_bound, upper_bound

    winner          TEXT,                             -- control | test | inconclusive

    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE INDEX idx_pricing_experiments_tenant ON pricing_experiments(tenant_id);

-- ============================================
-- Customer Success Modules
-- ============================================

CREATE TABLE support_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    customer_id     UUID NOT NULL,                    -- can reference cc.users or external customer

    subject         TEXT NOT NULL,
    description     TEXT NOT NULL,
    priority        TEXT NOT NULL DEFAULT 'normal',  -- low | normal | high | urgent
    status          TEXT NOT NULL DEFAULT 'open',    -- open | in_progress | waiting | resolved | closed

    -- AI assignment
    category        TEXT,                             -- AI-classified: billing | technical | feature_request | bug
    sentiment       TEXT,                             -- AI-detected: positive | neutral | negative | angry
    sentiment_score NUMERIC(3,2),                     -- -1.0 to 1.0

    -- AI assistance
    suggested_reply TEXT,                             -- AI-generated response
    suggested_category TEXT,                         -- AI-suggested category
    confidence_score NUMERIC(3,2),                    -- 0-1, how confident is AI

    -- Assignment
    assigned_to      UUID REFERENCES cc.users(id),
    assigned_at      TIMESTAMPTZ,

    -- Metadata
    channel         TEXT NOT NULL,                    -- email | chat | widget | api
    source_url      TEXT,

    -- Resolution
    resolution      TEXT,
    resolved_at     TIMESTAMPTZ,
    resolved_by     UUID REFERENCES cc.users(id),

    -- SLA tracking
    first_response_at TIMESTAMPTZ,
    sla_breached    BOOLEAN NOT NULL DEFAULT false,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_tickets_tenant ON support_tickets(tenant_id);
CREATE INDEX idx_support_tickets_customer ON support_tickets(customer_id);
CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_priority ON support_tickets(priority);

-- Support ticket comments (conversation history)
CREATE TABLE support_ticket_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,

    -- Comment
    content         TEXT NOT NULL,
    is_internal     BOOLEAN NOT NULL DEFAULT false,  -- true = only team can see

    -- Author
    author_id       UUID REFERENCES cc.users(id),
    author_name     TEXT,                             -- for external comments
    author_type     TEXT,                             -- user | agent | system

    -- Attachments
    attachments     JSONB,                           -- array of {url, filename, size}

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_comments_ticket ON support_ticket_comments(ticket_id);

CREATE TABLE ai_conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    session_id      TEXT NOT NULL,                    -- unique session identifier

    -- Conversation
    messages        JSONB NOT NULL,                   -- [{role, content, timestamp, confidence}]
    /*
    Example:
    [
      {"role": "user", "content": "How do I reset my password?", "timestamp": "2024-01-01T10:00:00Z"},
      {"role": "assistant", "content": "To reset your password...", "timestamp": "2024-01-01T10:00:01Z", "confidence": 0.92}
    ]
    */

    -- Handoff logic
    confidence_score NUMERIC(3,2),                    -- average confidence across session
    human_handoff   BOOLEAN NOT NULL DEFAULT false,
    handoff_reason  TEXT,

    -- Metadata
    channel         TEXT NOT NULL,                    -- widget | api
    user_id         UUID,                             -- if authenticated
    user_email      TEXT,                             -- for analytics

    -- Resolution
    resolved        BOOLEAN NOT NULL DEFAULT false,
    resolution_time INT,                              -- seconds until resolved

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_conversations_tenant ON ai_conversations(tenant_id);
CREATE INDEX idx_ai_conversations_session ON ai_conversations(session_id);

CREATE TABLE customer_health_scores (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    customer_id     UUID NOT NULL,                    -- references stripe_customers

    score           INT NOT NULL,                     -- 0-100
    risk_level      TEXT NOT NULL,                    -- low | medium | high | critical

    -- Signals (AI-generated)
    signals         JSONB NOT NULL,                   -- array of {type, value, impact, description}
    /*
    Example:
    [
      {"type": "login_frequency", "value": 0, "impact": -30, "description": "No logins in 14 days"},
      {"type": "support_tickets", "value": 5, "impact": -20, "description": "5 unresolved tickets"},
      {"type": "usage_decline", "value": -60, "impact": -25, "description": "60% drop in usage"},
      {"type": "payment_failed", "value": true, "impact": -15, "description": "Recent payment failure"}
    ]
    */

    -- AI recommendations
    recommended_actions JSONB,                        -- ["send_20_percent_off", "schedule_ceo_call"]
    action_confidence NUMERIC(3,2),                   -- 0-1

    -- Trend
    previous_score  INT,                              -- for tracking improvement/decline
    score_trend     TEXT,                             -- improving | stable | declining

    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, customer_id, calculated_at)
);

CREATE INDEX idx_health_scores_tenant ON customer_health_scores(tenant_id);
CREATE INDEX idx_health_scores_customer ON customer_health_scores(customer_id);
CREATE INDEX idx_health_scores_risk ON customer_health_scores(risk_level);

-- Health score history (for trending)
CREATE TABLE customer_health_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    score           INT NOT NULL,
    risk_level      TEXT NOT NULL,
    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_health_history_customer ON customer_health_history(customer_id, calculated_at DESC);

-- NPS surveys
CREATE TABLE nps_surveys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    name            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'draft',    -- draft | active | paused | completed

    -- Survey config
    question        TEXT NOT NULL DEFAULT 'How likely are you to recommend us to a friend or colleague?',
    follow_up_question TEXT,                         -- shown after score

    -- Trigger
    trigger_event   TEXT,                             -- signup | subscription_created | after_support_ticket
    delay_days      INT DEFAULT 0,

    -- Distribution
    sent_count      INT NOT NULL DEFAULT 0,
    response_count  INT NOT NULL DEFAULT 0,

    -- Results
    score_average   NUMERIC(3,2),                     -- 0-10
    promoters       INT NOT NULL DEFAULT 0,            -- score 9-10
    passives        INT NOT NULL DEFAULT 0,            -- score 7-8
    detractors      INT NOT NULL DEFAULT 0,            -- score 0-6
    nps_score       INT,                              -- promoters - detractors

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_nps_surveys_tenant ON nps_surveys(tenant_id);

CREATE TABLE nps_responses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id       UUID NOT NULL REFERENCES nps_surveys(id) ON DELETE CASCADE,
    customer_id     UUID NOT NULL,
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),

    score           INT NOT NULL,                     -- 0-10
    category        TEXT NOT NULL,                    -- promoter | passive | detractor
    follow_up_answer TEXT,

    -- Metadata
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at    TIMESTAMPTZ,
    channel         TEXT DEFAULT 'email',

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_nps_responses_survey ON nps_responses(survey_id);
CREATE INDEX idx_nps_responses_customer ON nps_responses(customer_id);

-- ============================================
-- Business Intelligence Modules
-- ============================================

CREATE TABLE analytics_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    user_id         UUID,

    event_name      TEXT NOT NULL,                    -- signup | subscription_created | churn_risk | landing_page_viewed
    event_type      TEXT NOT NULL,                    -- business | technical
    event_category  TEXT,                             -- acquisition | monetization | retention | referral

    -- Event properties (flexible schema)
    properties      JSONB NOT NULL,                   -- {key: value pairs}
    /*
    Examples:
    {"source": "product_hunt", "campaign": "launch_2024"}
    {"plan": "pro", "interval": "monthly"}
    {"previous_plan": "free", "new_plan": "pro"}
    */

    -- Context
    session_id      TEXT,
    ip_address      INET,
    user_agent      TEXT,
    referrer        TEXT,

    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_analytics_events_tenant ON analytics_events(tenant_id, occurred_at DESC);
CREATE INDEX idx_analytics_events_user ON analytics_events(user_id, occurred_at DESC);
CREATE INDEX idx_analytics_events_name ON analytics_events(event_name, occurred_at DESC);
CREATE INDEX idx_analytics_events_type ON analytics_events(event_type, occurred_at DESC);

-- Pre-aggregated analytics (for performance)
CREATE TABLE analytics_events_daily (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    event_date      DATE NOT NULL,
    event_name      TEXT NOT NULL,

    count           BIGINT NOT NULL,
    unique_users    INT NOT NULL,

    properties      JSONB,

    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, event_date, event_name)
);

CREATE INDEX idx_analytics_daily_tenant ON analytics_events_daily(tenant_id, event_date DESC);

CREATE TABLE funnel_analytics (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    funnel_name     TEXT NOT NULL,                    -- "Free to Paid", "Onboarding", "Trial to Conversion"

    -- Funnel definition
    steps           JSONB NOT NULL,                   -- ordered array of steps
    /*
    Example:
    [
      {"step": 1, "name": "visited_landing_page", "event_name": "landing_page_viewed"},
      {"step": 2, "name": "signed_up", "event_name": "user_signup"},
      {"step": 3, "name": "started_trial", "event_name": "trial_started"},
      {"step": 4, "name": "subscribed", "event_name": "subscription_created"}
    ]
    */

    -- Funnel data
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,

    -- Results (pre-calculated)
    step_counts     JSONB NOT NULL,                   -- {step_1: 10000, step_2: 2000, step_3: 800, step_4: 400}
    conversion_rates JSONB NOT NULL,                 -- {step_1_to_2: 20, step_2_to_3: 40, step_3_to_4: 50}
    overall_conversion NUMERIC(5,2),                 -- 4.0 (from step 1 to final)

    -- Analysis
    dropoff_points  JSONB,                           -- steps with high dropoff
    bottleneck_step VARCHAR(255),                    -- worst performing step (name)

    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_funnel_analytics_tenant ON funnel_analytics(tenant_id, period_start DESC);

CREATE TABLE unit_economics (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),

    period          DATE NOT NULL,                    -- month (first day)

    -- Revenue metrics
    mrr             NUMERIC(12,2) NOT NULL,           -- Monthly Recurring Revenue
    arr             NUMERIC(12,2) NOT NULL,           -- Annual Recurring Revenue
    new_mrr         NUMERIC(12,2) NOT NULL,           -- New customer MRR
    expansion_mrr   NUMERIC(12,2) NOT NULL,           -- Upsell/cross-sell MRR
    churn_mrr       NUMERIC(12,2) NOT NULL,           -- Lost MRR from churn
    reactivation_mrr NUMERIC(12,2),                   -- Recovered MRR from churned customers

    -- Customer metrics
    total_customers INT NOT NULL,
    new_customers   INT NOT NULL,
    churned_customers INT NOT NULL,

    -- Unit economics
    cac             NUMERIC(12,2),                    -- Customer Acquisition Cost
    ltv             NUMERIC(12,2),                    -- Lifetime Value
    ltv_cac_ratio   NUMERIC(3,2),                    -- LTV:CAC (ideal > 3)
    payback_period  INT,                              -- months to recover CAC

    -- Churn metrics
    customer_churn_rate NUMERIC(5,2),                -- percentage
    revenue_churn_rate   NUMERIC(5,2),                -- percentage

    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(tenant_id, period)
);

CREATE INDEX idx_unit_economics_tenant ON unit_economics(tenant_id, period DESC);

-- ============================================
-- Compliance & Operations Modules
-- ============================================

CREATE TABLE legal_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    type            TEXT NOT NULL,                    -- tos | privacy_policy | dpa | sla | refund_policy
    status          TEXT NOT NULL DEFAULT 'draft',    -- draft | active | archived

    title           TEXT NOT NULL,
    content         TEXT NOT NULL,
    version         TEXT NOT NULL,

    -- AI-generated
    ai_generated    BOOLEAN NOT NULL DEFAULT false,
    ai_confidence   NUMERIC(3,2),                     -- 0-1

    -- Effective date
    effective_date  TIMESTAMPTZ,
    acceptance_required BOOLEAN NOT NULL DEFAULT true,

    -- Versioning
    previous_version_id UUID REFERENCES legal_documents(id),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_legal_documents_tenant ON legal_documents(tenant_id);
CREATE INDEX idx_legal_documents_type ON legal_documents(type);

-- Legal document acceptances (audit trail)
CREATE TABLE legal_document_acceptances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES legal_documents(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES cc.users(id),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),

    accepted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address      INET,
    user_agent      TEXT,

    UNIQUE(document_id, user_id)
);

CREATE INDEX idx_legal_acceptances_document ON legal_document_acceptances(document_id);
CREATE INDEX idx_legal_acceptances_user ON legal_document_acceptances(user_id);

CREATE TABLE gdpr_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES cc.tenants(id),
    user_id         UUID NOT NULL REFERENCES cc.users(id),

    type            TEXT NOT NULL,                    -- export | delete | access | correct
    status          TEXT NOT NULL DEFAULT 'pending',  -- pending | processing | completed | failed

    -- Request details
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,                      -- 30 days from request (GDPR requirement)

    -- Processing
    result_url      TEXT,                             -- for export requests
    error_message   TEXT,

    -- Verification
    verified        BOOLEAN NOT NULL DEFAULT false,
    verification_method TEXT,                         -- email | sms

    metadata        JSONB
);

CREATE INDEX idx_gdpr_requests_tenant ON gdpr_requests(tenant_id);
CREATE INDEX idx_gdpr_requests_status ON gdpr_requests(status);

-- ============================================
-- Indexes for performance
-- ============================================

-- Composite indexes for common queries
CREATE INDEX idx_support_tickets_tenant_status ON support_tickets(tenant_id, status);
CREATE INDEX idx_support_tickets_tenant_priority ON support_tickets(tenant_id, priority);
CREATE INDEX idx_stripe_subscriptions_tenant_status ON stripe_subscriptions(tenant_id, status);
CREATE INDEX idx_analytics_events_tenant_type_date ON analytics_events(tenant_id, event_type, occurred_at DESC);

-- Full-text search
CREATE INDEX idx_support_tickets_search ON support_tickets USING GIN(to_tsvector('english', coalesce(subject,'') || ' ' || coalesce(description,'')));
CREATE INDEX idx_landing_variants_search ON landing_variants USING GIN(to_tsvector('english', coalesce(headline,'') || ' ' || coalesce(subheadline,'')));

-- ============================================
-- Row Level Security (RLS) policies
-- ============================================

-- Enable RLS on key tables (multi-tenancy enforcement)
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_health_scores ENABLE ROW LEVEL SECURITY;

-- Policy: Users can only see tickets from their tenant
CREATE POLICY support_tickets_tenant_policy ON support_tickets
    FOR ALL
    USING (tenant_id IN (SELECT tenant_id FROM cc.users WHERE id = current_setting('app.user_id')::uuid));

-- Similar policies for other tables...
-- (Note: Actual RLS implementation depends on auth system integration)
