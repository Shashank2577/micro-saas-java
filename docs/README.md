# Micro-SaaS Applications — Master Index

This repository documents **10 production-ready micro-SaaS applications** built with Spring Boot 3.3.5 and Java 25.

---

## App Status

| # | App | Status | Code | Spec |
|---|-----|--------|------|------|
| **09** | **[Changelog & Release Notes Platform](apps/09-changelog-platform.md)** | **In Progress** | [README](../changelog-platform/README.md) | [spec](apps/09-changelog-platform.md) |
| 02 | [Team Feedback & Roadmap](apps/02-team-feedback-roadmap.md) | Planned (next) | — | [spec](apps/02-team-feedback-roadmap.md) |
| 07 | [Lightweight Issue Tracker](apps/07-lightweight-issue-tracker.md) | Planned | — | [spec](apps/07-lightweight-issue-tracker.md) |
| 08 | [API Key Management Portal](apps/08-api-key-management-portal.md) | Planned | — | [spec](apps/08-api-key-management-portal.md) |
| 01 | [Client Portal Builder](apps/01-client-portal-builder.md) | Planned | — | [spec](apps/01-client-portal-builder.md) |
| 03 | [AI Knowledge Base](apps/03-ai-knowledge-base.md) | Planned | — | [spec](apps/03-ai-knowledge-base.md) |
| 04 | [Invoice & Payment Tracker](apps/04-invoice-payment-tracker.md) | Planned | — | [spec](apps/04-invoice-payment-tracker.md) |
| 05 | [Document Approval Workflow](apps/05-document-approval-workflow.md) | Planned | — | [spec](apps/05-document-approval-workflow.md) |
| 06 | [Employee Onboarding Orchestrator](apps/06-employee-onboarding-orchestrator.md) | Planned | — | [spec](apps/06-employee-onboarding-orchestrator.md) |
| 10 | [OKR & Goal Tracker](apps/10-okr-goal-tracker.md) | Planned | — | [spec](apps/10-okr-goal-tracker.md) |

---

## How Each Spec Is Structured

Every app document covers:

1. **Problem Statement** — the specific pain being solved and why now
2. **Target Users** — buyer persona, user persona, company profile
3. **Core Value Proposition** — the one-line reason someone pays
4. **Feature Set** — MVP features, Phase 2 features, AI enhancements
5. **Data Model** — key entities, relationships, and schema notes
6. **Cross-Cutting Module Mapping** — which shared modules are used and how
7. **API Design** — key REST endpoints
8. **Frontend Screens** — key pages and their purpose
9. **Monetization** — pricing tiers and billing model
10. **Competitive Landscape** — who exists, and how this app differentiates
11. **Build Phases** — phased delivery plan (MVP → growth → scale)
12. **Success Metrics** — what good looks like at each stage

---

## Shared Infrastructure

All 10 apps inherit these capabilities from the foundation built in app 09:

| Layer | What it does |
|-------|-------------|
| **Multi-tenancy** | All data scoped to `tenant_id`; Keycloak JWT extracts tenant context |
| **Auth** | Keycloak OAuth2/OIDC resource server |
| **Acquisition** | Landing pages with A/B testing, conversion tracking |
| **Monetisation** | Stripe subscriptions, webhooks, product catalogue |
| **Retention** | Drip email campaigns triggered by lifecycle events |
| **Success** | Customer health scoring, support tickets |
| **Intelligence** | Analytics events, funnel analysis, unit economics (MRR/ARR/CAC/LTV) |
| **Storage** | MinIO (S3-compatible) for file uploads |
| **Email** | Spring Mail (SMTP) |
| **Database** | PostgreSQL 16 + Flyway migrations |

---

## Back to Root

[← Repository README](../README.md)
