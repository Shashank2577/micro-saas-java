# Changelog Platform REST API

## Authentication

All endpoints require a valid JWT token in the `Authorization` header:

```
Authorization: Bearer <jwt-token>
```

### Generate Token

**POST** `/api/auth/token`

Request body:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 3600
}
```

---

## Projects Endpoint

### List All Projects (Tenant-Isolated)

**GET** `/api/projects`

**Headers:** Authorization

**Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "550e8400-e29b-41d4-a716-446655440001",
    "name": "Changelog Platform",
    "slug": "changelog-platform",
    "description": "Public changelog and release notes",
    "branding": {
      "primaryColor": "#0066FF",
      "logoUrl": "https://..."
    },
    "createdAt": "2026-04-17T10:30:00Z",
    "updatedAt": "2026-04-17T10:30:00Z"
  }
]
```

### Get Project by ID

**GET** `/api/projects/{projectId}`

**Headers:** Authorization

**Response:** Single project object (same schema as list endpoint)

### Create Project

**POST** `/api/projects`

**Headers:** Authorization, Content-Type: application/json

Request body:
```json
{
  "name": "New Project",
  "slug": "new-project",
  "description": "Project description",
  "branding": {
    "primaryColor": "#FF6600"
  }
}
```

Response: Created project object with `id` and `tenantId`

---

## Analytics Events

### Track Event

**POST** `/api/analytics/events`

**Headers:** Authorization, Content-Type: application/json

Request body:
```json
{
  "eventName": "product_viewed",
  "eventType": "business",
  "eventCategory": "acquisition",
  "properties": {
    "source": "product_hunt",
    "campaign": "launch_2024"
  },
  "sessionId": "session-123",
  "ipAddress": "192.168.1.1",
  "userAgent": "Mozilla/5.0"
}
```

### Get Funnel Analytics

**GET** `/api/analytics/funnels/{funnelId}`

**Headers:** Authorization

**Response:**
```json
{
  "id": "...",
  "funnelName": "onboarding_flow",
  "steps": [
    {"step_name": "landing_page", "order": 1},
    {"step_name": "signup", "order": 2}
  ],
  "stepCounts": {
    "landing_page": 1000,
    "signup": 450
  },
  "conversionRates": {
    "landing_page_to_signup": 45.0
  },
  "overallConversion": 45.0
}
```

---

## Stripe Integration

### List Stripe Products

**GET** `/api/stripe/products`

**Headers:** Authorization

### List Subscriptions

**GET** `/api/stripe/subscriptions`

**Headers:** Authorization

### Webhook Handler

**POST** `/api/stripe/webhooks`

Stripe sends webhook events to this endpoint. Signature validation is performed automatically.

---

## Support Tickets

### Create Ticket

**POST** `/api/support/tickets`

**Headers:** Authorization, Content-Type: application/json

Request body:
```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440002",
  "title": "API Rate Limiting Issue",
  "description": "Customer experiencing 429 errors",
  "priority": "high",
  "category": "technical"
}
```

### Get Customer Health Score

**GET** `/api/health/customers/{customerId}`

**Headers:** Authorization

**Response:**
```json
{
  "id": "...",
  "customerId": "...",
  "overallScore": 82.5,
  "riskLevel": "low",
  "signals": {
    "usage_trend": "increasing",
    "api_errors_last_7d": 12,
    "license_utilization": 85.5
  }
}
```

---

## Error Responses

All error responses follow this format:

```json
{
  "timestamp": "2026-04-17T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid project slug format",
  "path": "/api/projects"
}
```

### Common Status Codes

- `200` OK
- `201` Created
- `400` Bad Request
- `401` Unauthorized
- `403` Forbidden (tenant isolation violation)
- `404` Not Found
- `500` Internal Server Error
