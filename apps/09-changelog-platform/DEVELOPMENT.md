# Development Guide

## Local Setup

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port 5432
- Keycloak on port 8080
- MinIO on port 9000 (API) and 9001 (Console)

### 2. Configure Keycloak

1. Access Keycloak: http://localhost:8080
2. Login: admin/admin
3. Create realm: `changelog`
4. Create client: `changelog-api`
   - Client authentication: ON
   - Authorization: OFF
   - Valid redirect URIs: `http://localhost:8080/*`
   - Web origins: `http://localhost:8080`
5. Create user role: `editor`, `admin`
6. Create a test user and add roles
7. Add custom attribute to JWT: `tenant_id`

### 3. Run Application

```bash
./mvnw spring-boot:run
```

Or run from IDE:
```
ChangelogPlatformApplication.java
```

### 4. Verify

```bash
curl http://localhost:8080/health
curl http://localhost:8080/
```

## Testing the API

### Get Auth Token (Keycloak)

```bash
export TOKEN=$(curl -X POST "http://localhost:8080/realms/changelog/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=changelog-api" \
  -d "client_secret=your-client-secret" \
  -d "username=admin@demo.com" \
  -d "password=password" \
  -d "grant_type=password" | jq -r '.access_token')
```

### Create Project

```bash
curl -X POST "http://localhost:8080/api/v1/projects" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Product",
    "slug": "my-product",
    "description": "My awesome product changelog",
    "branding": {
      "primaryColor": "#4F46E5"
    }
  }'
```

### Create Post

```bash
curl -X POST "http://localhost:8080/api/v1/projects/{projectId}/posts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Welcome to our changelog!",
    "summary": "First post",
    "content": "# Welcome\n\nThis is our first changelog post.",
    "status": "PUBLISHED"
  }'
```

### Access Public Changelog

```bash
curl http://localhost:8080/p/demo-company/demo-product
```

## Database Migrations

```bash
# View migration status
./mvnw flyway:info

# Run migrations manually
./mvnw flyway:migrate

# Clean database (WARNING: deletes all data)
./mvnw flyway:clean
```

## Troubleshooting

### Port Conflicts
- If port 8080 is taken, stop the conflicting service or change the port:
  ```
  export SERVER_PORT=8081
  ./mvnw spring-boot:run
  ```

### Database Connection Issues
- Ensure PostgreSQL is running: `docker ps | grep postgres`
- Check credentials in `application.yml`

### Keycloak Issues
- Verify Keycloak is healthy: `curl http://localhost:8080/health/ready`
- Check realm and client configuration
- Verify JWT contains `tenant_id` claim

## Code Structure

```
src/main/java/com/changelog/
├── config/          # Security, CORS, etc.
├── controller/      # REST endpoints
├── dto/             # Request/Response objects
├── model/           # JPA entities
├── repository/      # Data access
├── service/         # Business logic
└── ChangelogPlatformApplication.java
```

## Next Steps

1. ✅ Core CRUD operations
2. ⏳ Add email notifications (Resend)
3. ⏳ Build widget JavaScript
4. ⏳ Create public changelog HTML pages
5. ⏳ Add RSS feed generation
6. ⏳ Implement AI writing features
