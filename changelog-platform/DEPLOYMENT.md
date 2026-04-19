# Deployment Guide

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 16+
- Docker (for containerization)
- Stripe API keys (production environment)

## Local Development

### 1. Start PostgreSQL

```bash
docker run --name changelog-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=changelog \
  -p 5433:5432 \
  postgres:16-alpine
```

**Note:** Port 5433 is used because port 5432 is occupied by the pgvector container.

### 2. Build the Application

```bash
cd changelog-platform
mvn clean package -DskipTests
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

Access API: `http://localhost:8081`

### 4. Run Tests

```bash
mvn test
```

## Deployment Checklist

- [ ] All tests passing (`mvn test`)
- [ ] No compiler warnings or errors
- [ ] Flyway migrations tested on target database
- [ ] Stripe API keys configured in environment variables
- [ ] JWT secret key configured
- [ ] Email service configured (SMTP or SES)
- [ ] MinIO/S3 file storage configured
- [ ] LiteLLM gateway endpoint configured

## Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/changelog
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<secure-password>

# JWT
JWT_SECRET=<secure-256-bit-key>
JWT_EXPIRATION=3600000

# Stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Email
SPRING_MAIL_HOST=smtp.sendgrid.net
SPRING_MAIL_USERNAME=apikey
SPRING_MAIL_PASSWORD=<sendgrid-api-key>

# MinIO
MINIO_URL=https://minio.example.com
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=<secure-key>

# LiteLLM
LITELLM_API_URL=https://litellm.example.com
LITELLM_API_KEY=<api-key>
```

## Docker Deployment

### Build Docker Image

```bash
docker build -t changelog-platform:latest .
```

### Run Container

```bash
docker run -d \
  --name changelog-app \
  -p 8081:8081 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/changelog \
  -e JWT_SECRET=<key> \
  changelog-platform:latest
```

## Database Migrations

Flyway automatically runs migrations on startup. Current schema version:

- `V1__init.sql` — Core tables (projects, users, sessions, views)
- `V2__sample_data.sql` — Sample data for testing
- `V3__business_modules.sql` — Business module tables (analytics, Stripe, support, AI, legal)

To manually validate migrations:

```bash
mvn flyway:validate
```

## Monitoring & Logging

Application logs are output to `STDOUT`. For production, configure:

- Log aggregation (ELK, Splunk, Datadog)
- Health check endpoint: `GET /actuator/health`
- Metrics endpoint: `GET /actuator/metrics`

## Rollback Procedures

If a migration fails:

1. Identify the problematic version:
   ```bash
   SELECT * FROM flyway_schema_history;
   ```

2. Rollback the version (manual SQL):
   ```sql
   DELETE FROM flyway_schema_history WHERE success = false;
   ```

3. Fix the migration SQL file
4. Re-run: `mvn spring-boot:run`
