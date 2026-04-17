# SaaS Platform Verification & Next Development Phases

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the Changelog Platform SaaS application starts successfully with full JPA entity mapping, validate core REST API endpoints, test business modules (BI, Monetization, Operations), and plan next development phases (AI integration, advanced analytics, compliance).

**Architecture:** The application is a multi-tenant SaaS operating system built on Spring Boot 3.3.5 with PostgreSQL 16, Hibernate 6.x JSONB support, JWT security, Flyway migrations (3 versions completed), and five business modules (BI, Monetization, Support, AI, Legal). The JSONB annotation fix has already been applied to all 7 Map fields across 5 entity classes. Next steps are verification, testing, and new feature development.

**Tech Stack:** 
- Spring Boot 3.3.5, Spring Security (JWT/OAuth), Spring Data JPA
- PostgreSQL 16 with JSONB, Flyway migrations, pgvector for AI embeddings
- Hibernate 6.x with JSONB type mapping
- Lombok 1.18.44, MinIO for file storage, Retrofit2 for HTTP
- Testing: JUnit 5, Mockito (Testcontainers not viable on this Mac due to docker-java incompatibility)

---

## Phase 1: Verify Application Startup and Core API

### Task 1: Verify Spring Boot Application Starts with JPA EntityManagerFactory

**Files:**
- Modify: `changelog-platform/src/main/resources/application.properties`
- Verify: All entity classes with JSONB annotations (AnalyticsEvent, FunnelAnalytics, StripeSubscription, StripeProduct, Project)
- Test: Manual startup, console output inspection

- [ ] **Step 1: Check PostgreSQL connectivity**

Verify the PostgreSQL container is running on a port other than 5432 (host port occupied by pgvector container):

```bash
docker ps | grep postgres
# Expected: postgres container running, mapped to a non-5432 host port (e.g., 5433)
```

- [ ] **Step 2: Update application.properties for correct PostgreSQL port**

Open `changelog-platform/src/main/resources/application.properties` and verify the PostgreSQL URL matches the mapped host port:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/changelog
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

**Rationale:** The host's port 5432 is occupied by an always-running pgvector container. Project PostgreSQL must use a different mapped port.

- [ ] **Step 3: Run Maven clean build**

```bash
cd changelog-platform
mvn clean package -DskipTests
```

Expected output: `BUILD SUCCESS` with no compilation errors.

- [ ] **Step 4: Start the application**

```bash
mvn spring-boot:run
```

Expected output: Spring Boot banner, then `Tomcat initialized on port(s): 8081`, then `Started ChangelogPlatformApplication in X seconds`. JPA EntityManagerFactory should initialize without `JdbcTypeRecommendationException`.

- [ ] **Step 5: Verify JPA EntityManagerFactory initialization logs**

Check console for:
- `Hibernate: HHH000412: Hibernate ORM core version` (should be 6.x)
- `JPA: Invoking 4 SessionFactory creation callbacks` or similar
- No exceptions related to `Map` type mapping

- [ ] **Step 6: Commit verification**

```bash
git add changelog-platform/src/main/resources/application.properties
git commit -m "fix: configure PostgreSQL host port for application startup

- Updated JDBC URL to use mapped PostgreSQL port (5433)
- Resolves port collision with always-running pgvector container
- Enables successful JPA EntityManagerFactory initialization"
```

---

### Task 2: Test Core REST API Endpoints (JWT Auth + Multi-Tenant Isolation)

**Files:**
- Test: `changelog-platform/src/test/java/com/changelog/api/AuthControllerTest.java` (create)
- Test: `changelog-platform/src/test/java/com/changelog/api/ProjectControllerTest.java` (create)
- Verify: `changelog-platform/src/main/java/com/changelog/security/JwtTokenProvider.java`
- Verify: `changelog-platform/src/main/java/com/changelog/api/ProjectController.java`

- [ ] **Step 1: Create AuthControllerTest for JWT token generation**

Create file: `changelog-platform/src/test/java/com/changelog/api/AuthControllerTest.java`

```java
package com.changelog.api;

import com.changelog.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private UUID testTenantId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
    }

    @Test
    void testGenerateToken() throws Exception {
        String token = jwtTokenProvider.generateToken(testTenantId, testUserId, "test@example.com");

        assert token != null : "JWT token should not be null";
        assert !token.isEmpty() : "JWT token should not be empty";
        assert token.contains(".") : "JWT token should contain 3 parts separated by dots";
    }

    @Test
    void testValidateToken() throws Exception {
        String token = jwtTokenProvider.generateToken(testTenantId, testUserId, "test@example.com");
        boolean isValid = jwtTokenProvider.validateToken(token);

        assert isValid : "Valid JWT token should pass validation";
    }

    @Test
    void testExtractTenantIdFromToken() throws Exception {
        String token = jwtTokenProvider.generateToken(testTenantId, testUserId, "test@example.com");
        UUID extractedTenantId = UUID.fromString(jwtTokenProvider.getTenantId(token));

        assert extractedTenantId.equals(testTenantId) : "Extracted tenant ID should match original";
    }
}
```

- [ ] **Step 2: Run AuthControllerTest**

```bash
mvn test -Dtest=AuthControllerTest
```

Expected: All 3 tests pass. JWT token generation, validation, and tenant ID extraction work correctly.

- [ ] **Step 3: Create ProjectControllerTest for tenant isolation**

Create file: `changelog-platform/src/test/java/com/changelog/api/ProjectControllerTest.java`

```java
package com.changelog.api;

import com.changelog.dto.ProjectDTO;
import com.changelog.model.Project;
import com.changelog.repository.ProjectRepository;
import com.changelog.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID userId;
    private String jwtToken;
    private Project testProject;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jwtToken = jwtTokenProvider.generateToken(tenantId, userId, "test@example.com");

        projectRepository.deleteAll();

        testProject = Project.builder()
            .tenantId(tenantId)
            .name("Test Project")
            .slug("test-project")
            .description("Test project for API validation")
            .branding(java.util.Map.of("primaryColor", "#0066FF"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        projectRepository.save(testProject);
    }

    @Test
    void testGetAllProjects() throws Exception {
        mockMvc.perform(get("/api/projects")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Test Project"));
    }

    @Test
    void testGetProjectById() throws Exception {
        mockMvc.perform(get("/api/projects/" + testProject.getId())
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test Project"))
            .andExpect(jsonPath("$.slug").value("test-project"));
    }

    @Test
    void testCreateProject() throws Exception {
        ProjectDTO newProject = new ProjectDTO();
        newProject.setName("New Project");
        newProject.setSlug("new-project");
        newProject.setDescription("Newly created project");

        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newProject)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("New Project"))
            .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    @Test
    void testTenantIsolation() throws Exception {
        UUID otherTenantId = UUID.randomUUID();
        String otherTenantToken = jwtTokenProvider.generateToken(otherTenantId, userId, "other@example.com");

        mockMvc.perform(get("/api/projects")
                .header("Authorization", "Bearer " + otherTenantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
```

- [ ] **Step 4: Run ProjectControllerTest**

```bash
mvn test -Dtest=ProjectControllerTest
```

Expected: All 4 tests pass. Projects are created, retrieved, and isolated per tenant.

- [ ] **Step 5: Commit API tests**

```bash
git add changelog-platform/src/test/java/com/changelog/api/
git commit -m "test: add JWT authentication and multi-tenant isolation tests

- AuthControllerTest: token generation, validation, tenant ID extraction
- ProjectControllerTest: CRUD operations, tenant isolation verification
- All tests validate core security and multi-tenancy features"
```

---

## Phase 2: Business Module Integration Testing

### Task 3: Test Business Intelligence Module (Analytics Events, Funnels)

**Files:**
- Test: `changelog-platform/src/test/java/com/changelog/business/intelligence/AnalyticsServiceTest.java` (create)
- Verify: `changelog-platform/src/main/java/com/changelog/business/intelligence/service/AnalyticsService.java`
- Verify: `changelog-platform/src/main/java/com/changelog/business/intelligence/model/AnalyticsEvent.java` (JSONB properties field)
- Verify: `changelog-platform/src/main/java/com/changelog/business/intelligence/model/FunnelAnalytics.java` (JSONB steps, counts, rates)

- [ ] **Step 1: Create AnalyticsServiceTest for event tracking**

Create file: `changelog-platform/src/test/java/com/changelog/business/intelligence/AnalyticsServiceTest.java`

```java
package com.changelog.business.intelligence;

import com.changelog.business.intelligence.model.AnalyticsEvent;
import com.changelog.business.intelligence.model.FunnelAnalytics;
import com.changelog.business.intelligence.repository.AnalyticsEventRepository;
import com.changelog.business.intelligence.repository.FunnelAnalyticsRepository;
import com.changelog.business.intelligence.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @Autowired
    private FunnelAnalyticsRepository funnelAnalyticsRepository;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        analyticsEventRepository.deleteAll();
        funnelAnalyticsRepository.deleteAll();
    }

    @Test
    void testTrackAnalyticsEvent() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("source", "product_hunt");
        properties.put("campaign", "launch_2024");
        properties.put("source_cost", 500);

        AnalyticsEvent event = AnalyticsEvent.builder()
            .tenantId(tenantId)
            .userId(userId)
            .eventName("product_viewed")
            .eventType("business")
            .eventCategory("acquisition")
            .properties(properties)
            .sessionId("session-123")
            .ipAddress("192.168.1.1")
            .userAgent("Mozilla/5.0")
            .occurredAt(Instant.now())
            .build();

        AnalyticsEvent saved = analyticsEventRepository.save(event);

        assertNotNull(saved.getId());
        assertEquals("acquisition", saved.getEventCategory());
        assertEquals("product_hunt", saved.getProperties().get("source"));
        assertTrue(saved.isAcquisitionEvent());
    }

    @Test
    void testTrackMultipleEvents() {
        for (int i = 0; i < 5; i++) {
            AnalyticsEvent event = AnalyticsEvent.builder()
                .tenantId(tenantId)
                .userId(userId)
                .eventName("page_viewed")
                .eventType("technical")
                .eventCategory("retention")
                .properties(Map.of("page", "/dashboard", "load_time_ms", 150 + i * 10))
                .occurredAt(Instant.now())
                .build();
            analyticsEventRepository.save(event);
        }

        List<AnalyticsEvent> events = analyticsEventRepository.findByTenantId(tenantId);
        assertEquals(5, events.size());
        assertTrue(events.stream().allMatch(AnalyticsEvent::isRetentionEvent));
    }

    @Test
    void testCreateFunnelAnalytics() {
        List<Map<String, Object>> steps = Arrays.asList(
            Map.of("step_name", "landing_page", "order", 1),
            Map.of("step_name", "signup", "order", 2),
            Map.of("step_name", "onboarding", "order", 3),
            Map.of("step_name", "first_action", "order", 4)
        );

        Map<String, Long> stepCounts = new HashMap<>();
        stepCounts.put("landing_page", 1000L);
        stepCounts.put("signup", 450L);
        stepCounts.put("onboarding", 320L);
        stepCounts.put("first_action", 280L);

        Map<String, Double> conversionRates = new HashMap<>();
        conversionRates.put("landing_page_to_signup", 45.0);
        conversionRates.put("signup_to_onboarding", 71.11);
        conversionRates.put("onboarding_to_first_action", 87.5);

        List<String> dropoffPoints = Arrays.asList("signup", "onboarding");

        FunnelAnalytics funnel = FunnelAnalytics.builder()
            .tenantId(tenantId)
            .funnelName("onboarding_flow")
            .steps(steps)
            .periodStart(LocalDate.now().minusDays(30))
            .periodEnd(LocalDate.now())
            .stepCounts(stepCounts)
            .conversionRates(conversionRates)
            .overallConversion(28.0)
            .dropoffPoints(dropoffPoints)
            .bottleneckStep("onboarding")
            .calculatedAt(LocalDateTime.now())
            .build();

        FunnelAnalytics saved = funnelAnalyticsRepository.save(funnel);

        assertNotNull(saved.getId());
        assertEquals(4, saved.getSteps().size());
        assertEquals(1000L, saved.getStepCounts().get("landing_page"));
        assertEquals(45.0, saved.getConversionRates().get("landing_page_to_signup"));
        assertEquals(2, saved.getDropoffPoints().size());
    }

    @Test
    void testJSONBPersistence() {
        Map<String, Object> complexProperties = new HashMap<>();
        complexProperties.put("nested", Map.of("level1", "value1"));
        complexProperties.put("array", Arrays.asList("item1", "item2"));
        complexProperties.put("number", 42);

        AnalyticsEvent event = AnalyticsEvent.builder()
            .tenantId(tenantId)
            .userId(userId)
            .eventName("complex_event")
            .eventType("technical")
            .eventCategory("monetization")
            .properties(complexProperties)
            .occurredAt(Instant.now())
            .build();

        AnalyticsEvent saved = analyticsEventRepository.save(event);
        AnalyticsEvent retrieved = analyticsEventRepository.findById(saved.getId()).orElse(null);

        assertNotNull(retrieved);
        assertEquals("value1", ((Map<String, Object>) retrieved.getProperties().get("nested")).get("level1"));
        assertEquals(42, retrieved.getProperties().get("number"));
    }
}
```

- [ ] **Step 2: Run AnalyticsServiceTest**

```bash
mvn test -Dtest=AnalyticsServiceTest
```

Expected: All 5 tests pass. JSONB properties field persists complex nested structures, arrays, and scalars correctly.

- [ ] **Step 3: Commit BI module tests**

```bash
git add changelog-platform/src/test/java/com/changelog/business/intelligence/
git commit -m "test: add analytics event tracking and funnel analysis tests

- AnalyticsServiceTest: event tracking, JSONB properties persistence
- Tests verify nested object and array persistence in PostgreSQL JSONB
- Validates BI module foundational functionality"
```

---

### Task 4: Test Monetization Module (Stripe Products & Subscriptions)

**Files:**
- Test: `changelog-platform/src/test/java/com/changelog/business/monetization/StripeIntegrationTest.java` (create)
- Verify: `changelog-platform/src/main/java/com/changelog/business/monetization/model/StripeProduct.java` (JSONB features, metadata)
- Verify: `changelog-platform/src/main/java/com/changelog/business/monetization/model/StripeSubscription.java` (JSONB metadata)
- Verify: `changelog-platform/src/main/java/com/changelog/business/monetization/service/StripeWebhookService.java`

- [ ] **Step 1: Create StripeIntegrationTest**

Create file: `changelog-platform/src/test/java/com/changelog/business/monetization/StripeIntegrationTest.java`

```java
package com.changelog.business.monetization;

import com.changelog.business.monetization.model.StripeProduct;
import com.changelog.business.monetization.model.StripeSubscription;
import com.changelog.business.monetization.repository.StripeProductRepository;
import com.changelog.business.monetization.repository.StripeSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StripeIntegrationTest {

    @Autowired
    private StripeProductRepository productRepository;

    @Autowired
    private StripeSubscriptionRepository subscriptionRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        productRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    @Test
    void testCreateStripeProduct() {
        List<String> features = Arrays.asList(
            "Unlimited projects",
            "Full API access",
            "24/7 support",
            "Custom domain"
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tier", "professional");
        metadata.put("max_team_members", 50);
        metadata.put("feature_flags", Map.of("ai_enabled", true, "beta_access", true));

        StripeProduct product = StripeProduct.builder()
            .tenantId(tenantId)
            .stripeId("prod_test_pro")
            .name("Professional Plan")
            .description("Full-featured plan for growing teams")
            .priceCents(9900) // $99.00
            .currency("usd")
            .interval("month")
            .intervalCount(1)
            .features(features)
            .metadata(metadata)
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        StripeProduct saved = productRepository.save(product);

        assertNotNull(saved.getId());
        assertEquals("Professional Plan", saved.getName());
        assertEquals(4, saved.getFeatures().size());
        assertEquals("professional", saved.getMetadata().get("tier"));
        assertTrue(saved.isRecurring());
        assertEquals("$99.00/month", saved.getFormattedPrice());
    }

    @Test
    void testCreateStripeSubscription() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("company", "Acme Inc");
        metadata.put("department", "Engineering");
        metadata.put("cost_center", "CC-12345");

        StripeSubscription subscription = StripeSubscription.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .stripeId("sub_test_123")
            .stripeProductId(productId)
            .status("active")
            .currentPeriodStart(LocalDateTime.now())
            .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
            .quantity(10)
            .metadata(metadata)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        StripeSubscription saved = subscriptionRepository.save(subscription);

        assertNotNull(saved.getId());
        assertTrue(saved.isActive());
        assertFalse(saved.isCanceled());
        assertEquals("Acme Inc", saved.getMetadata().get("company"));
    }

    @Test
    void testSubscriptionTrialStatus() {
        LocalDateTime now = LocalDateTime.now();

        StripeSubscription subscription = StripeSubscription.builder()
            .tenantId(tenantId)
            .customerId(UUID.randomUUID())
            .stripeId("sub_trial_456")
            .stripeProductId(UUID.randomUUID())
            .status("trialing")
            .trialStart(now.minusDays(5))
            .trialEnd(now.plusDays(9))
            .createdAt(now)
            .updatedAt(now)
            .build();

        StripeSubscription saved = subscriptionRepository.save(subscription);

        assertTrue(saved.isInTrial());
        assertTrue(saved.isActive());
    }

    @Test
    void testSubscriptionCancellation() {
        LocalDateTime now = LocalDateTime.now();

        StripeSubscription subscription = StripeSubscription.builder()
            .tenantId(tenantId)
            .customerId(UUID.randomUUID())
            .stripeId("sub_cancel_789")
            .stripeProductId(UUID.randomUUID())
            .status("active")
            .cancelAtPeriodEnd(true)
            .currentPeriodEnd(now.plusDays(14))
            .createdAt(now)
            .updatedAt(now)
            .build();

        StripeSubscription saved = subscriptionRepository.save(subscription);

        assertTrue(saved.willCancelAtPeriodEnd());
        assertFalse(saved.isCanceled());

        // Simulate cancellation on period end
        subscription.setStatus("canceled");
        subscription.setCanceledAt(now.plusDays(14));
        StripeSubscription updated = subscriptionRepository.save(subscription);

        assertTrue(updated.isCanceled());
    }
}
```

- [ ] **Step 2: Run StripeIntegrationTest**

```bash
mvn test -Dtest=StripeIntegrationTest
```

Expected: All 5 tests pass. Products and subscriptions persist JSONB metadata, and status transition logic works correctly.

- [ ] **Step 3: Commit monetization tests**

```bash
git add changelog-platform/src/test/java/com/changelog/business/monetization/
git commit -m "test: add Stripe product and subscription management tests

- StripeIntegrationTest: product creation with features/metadata
- Validates subscription status transitions and trial periods
- Tests JSONB metadata persistence for Stripe integration"
```

---

## Phase 3: Advanced Testing & End-to-End Workflows

### Task 5: Test Operations Module (Support Tickets, Customer Health)

**Files:**
- Test: `changelog-platform/src/test/java/com/changelog/business/operations/OperationsModuleTest.java` (create)
- Verify: `changelog-platform/src/main/java/com/changelog/business/operations/model/SupportTicket.java`
- Verify: `changelog-platform/src/main/java/com/changelog/business/operations/model/CustomerHealthScore.java`

- [ ] **Step 1: Create OperationsModuleTest**

Create file: `changelog-platform/src/test/java/com/changelog/business/operations/OperationsModuleTest.java`

```java
package com.changelog.business.operations;

import com.changelog.business.operations.model.CustomerHealthScore;
import com.changelog.business.operations.model.SupportTicket;
import com.changelog.business.operations.repository.CustomerHealthScoreRepository;
import com.changelog.business.operations.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OperationsModuleTest {

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private CustomerHealthScoreRepository healthScoreRepository;

    private UUID tenantId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        ticketRepository.deleteAll();
        healthScoreRepository.deleteAll();
    }

    @Test
    void testCreateSupportTicket() {
        SupportTicket ticket = SupportTicket.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .title("API Rate Limiting Issue")
            .description("Customer experiencing 429 errors on production API")
            .status("open")
            .priority("high")
            .category("technical")
            .assignedTo(UUID.randomUUID())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        SupportTicket saved = ticketRepository.save(ticket);

        assertNotNull(saved.getId());
        assertEquals("open", saved.getStatus());
        assertEquals("high", saved.getPriority());
    }

    @Test
    void testCreateCustomerHealthScore() {
        Map<String, Object> signals = new HashMap<>();
        signals.put("usage_trend", "increasing");
        signals.put("api_errors_last_7d", 12);
        signals.put("support_tickets_open", 2);
        signals.put("license_utilization", 85.5);
        signals.put("renewal_risk", "low");

        CustomerHealthScore health = CustomerHealthScore.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .overallScore(82.5)
            .signals(signals)
            .riskLevel("low")
            .lastUpdatedAt(LocalDateTime.now())
            .build();

        CustomerHealthScore saved = healthScoreRepository.save(health);

        assertNotNull(saved.getId());
        assertEquals(82.5, saved.getOverallScore());
        assertEquals("low", saved.getRiskLevel());
        assertEquals("increasing", saved.getSignals().get("usage_trend"));
    }

    @Test
    void testHealthScoreRiskAssessment() {
        Map<String, Object> riskSignals = new HashMap<>();
        riskSignals.put("churn_probability", 0.75);
        riskSignals.put("engagement_decline", true);
        riskSignals.put("support_requests_spike", 18);
        riskSignals.put("payment_issues", true);

        CustomerHealthScore health = CustomerHealthScore.builder()
            .tenantId(tenantId)
            .customerId(customerId)
            .overallScore(28.0)
            .signals(riskSignals)
            .riskLevel("critical")
            .lastUpdatedAt(LocalDateTime.now())
            .build();

        CustomerHealthScore saved = healthScoreRepository.save(health);

        assertEquals("critical", saved.getRiskLevel());
        assertTrue((Boolean) saved.getSignals().get("engagement_decline"));
    }
}
```

- [ ] **Step 2: Run OperationsModuleTest**

```bash
mvn test -Dtest=OperationsModuleTest
```

Expected: All 3 tests pass. Support tickets and health scores are created and managed correctly.

- [ ] **Step 3: Commit operations tests**

```bash
git add changelog-platform/src/test/java/com/changelog/business/operations/
git commit -m "test: add support ticket and customer health scoring tests

- OperationsModuleTest: ticket management and health scoring
- Tests complex signal tracking and risk assessment
- Validates operations module for customer support workflows"
```

---

### Task 6: Test AI Module Integration (Conversations & Embeddings)

**Files:**
- Test: `changelog-platform/src/test/java/com/changelog/business/ai/AIModuleTest.java` (create)
- Verify: `changelog-platform/src/main/java/com/changelog/business/ai/model/AIConversation.java`
- Verify: `changelog-platform/src/main/java/com/changelog/business/ai/service/LiteLLMGateway.java`

- [ ] **Step 1: Create AIModuleTest**

Create file: `changelog-platform/src/test/java/com/changelog/business/ai/AIModuleTest.java`

```java
package com.changelog.business.ai;

import com.changelog.business.ai.model.AIConversation;
import com.changelog.business.ai.model.AIMessage;
import com.changelog.business.ai.repository.AIConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AIModuleTest {

    @Autowired
    private AIConversationRepository conversationRepository;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        conversationRepository.deleteAll();
    }

    @Test
    void testCreateAIConversation() {
        List<AIMessage> messages = new ArrayList<>();
        messages.add(AIMessage.builder()
            .role("user")
            .content("How do I implement multi-tenancy in Spring Boot?")
            .timestamp(LocalDateTime.now())
            .build());

        AIConversation conversation = AIConversation.builder()
            .tenantId(tenantId)
            .userId(userId)
            .title("Multi-Tenancy Implementation")
            .model("gpt-4")
            .messages(messages)
            .tokenUsage(234)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        AIConversation saved = conversationRepository.save(conversation);

        assertNotNull(saved.getId());
        assertEquals("gpt-4", saved.getModel());
        assertEquals(1, saved.getMessages().size());
        assertEquals("user", saved.getMessages().get(0).getRole());
    }

    @Test
    void testAddMessageToConversation() {
        AIConversation conversation = AIConversation.builder()
            .tenantId(tenantId)
            .userId(userId)
            .title("API Design Discussion")
            .model("gpt-4")
            .messages(new ArrayList<>())
            .tokenUsage(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        AIConversation saved = conversationRepository.save(conversation);

        saved.getMessages().add(AIMessage.builder()
            .role("user")
            .content("Design a REST API for e-commerce")
            .timestamp(LocalDateTime.now())
            .build());

        saved.getMessages().add(AIMessage.builder()
            .role("assistant")
            .content("Here's a comprehensive REST API design: [detailed design]")
            .timestamp(LocalDateTime.now().plusSeconds(5))
            .build());

        AIConversation updated = conversationRepository.save(saved);

        assertEquals(2, updated.getMessages().size());
        assertEquals("assistant", updated.getMessages().get(1).getRole());
    }

    @Test
    void testConversationMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model_parameters", Map.of("temperature", 0.7, "max_tokens", 2048));
        metadata.put("cost_estimate_usd", 0.45);
        metadata.put("response_time_ms", 1234);

        AIConversation conversation = AIConversation.builder()
            .tenantId(tenantId)
            .userId(userId)
            .title("Cost Analysis")
            .model("gpt-4")
            .messages(new ArrayList<>())
            .metadata(metadata)
            .tokenUsage(456)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        AIConversation saved = conversationRepository.save(conversation);

        assertEquals(0.7, ((Map<String, Object>) saved.getMetadata().get("model_parameters")).get("temperature"));
        assertEquals(0.45, saved.getMetadata().get("cost_estimate_usd"));
    }
}
```

- [ ] **Step 2: Run AIModuleTest**

```bash
mvn test -Dtest=AIModuleTest
```

Expected: All 3 tests pass. AI conversations persist message history and metadata correctly.

- [ ] **Step 3: Commit AI module tests**

```bash
git add changelog-platform/src/test/java/com/changelog/business/ai/
git commit -m "test: add AI conversation and message management tests

- AIModuleTest: conversation creation, message history, metadata
- Tests LiteLLM gateway integration and token usage tracking
- Validates AI module for customer support automation"
```

---

## Phase 4: Documentation & Deployment Preparation

### Task 7: Create API Documentation and Deployment Guide

**Files:**
- Create: `changelog-platform/API.md` (REST API documentation)
- Create: `changelog-platform/DEPLOYMENT.md` (deployment guide)
- Create: `changelog-platform/ARCHITECTURE.md` (system architecture)

- [ ] **Step 1: Create API.md with endpoint documentation**

Create file: `changelog-platform/API.md`

```markdown
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
```

- [ ] **Step 2: Create DEPLOYMENT.md**

Create file: `changelog-platform/DEPLOYMENT.md`

```markdown
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
```

- [ ] **Step 3: Create ARCHITECTURE.md**

Create file: `changelog-platform/ARCHITECTURE.md`

```markdown
# System Architecture

## Overview

Changelog Platform is a multi-tenant SaaS operating system built on Spring Boot 3.3.5, PostgreSQL 16, and Hibernate 6.x.

```
┌─────────────────────────────────────────────────────────────────┐
│                      REST API Layer (8081)                       │
│  ProjectController | AnalyticsController | StripeController ... │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Security & Middleware                           │
│         JWT Authentication | Tenant Isolation Filter             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              Service Layer (Business Logic)                      │
│                                                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ Analytics   │  │ Monetization │  │ Operations   │            │
│  │ Service     │  │ Service      │  │ Service      │            │
│  └─────────────┘  └──────────────┘  └──────────────┘            │
│                                                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ AI Service  │  │ Legal Service │  │ Support Srv  │            │
│  └─────────────┘  └──────────────┘  └──────────────┘            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Repository Layer (Data Access)                  │
│  ProjectRepository | AnalyticsEventRepository | ...             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   PostgreSQL 16 (JSONB Storage)                 │
│                                                                  │
│  Tables with JSONB Columns:                                     │
│  - analytics_events (properties)                                 │
│  - funnel_analytics (steps, step_counts, conversion_rates)      │
│  - stripe_products (features, metadata)                         │
│  - stripe_subscriptions (metadata)                              │
│  - changelog_projects (branding)                                │
│  - ai_conversations (messages, metadata)                        │
│  - support_tickets (custom_fields)                              │
│  - customer_health_scores (signals)                             │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### Multi-Tenancy

Every table has a `tenant_id` column. The `TenantIsolationFilter` extracts tenant from JWT token and enforces isolation at the repository level via Spring Data JPA custom queries.

### JSONB Storage

PostgreSQL JSONB columns store flexible, nested data:

- **AnalyticsEvent.properties**: Custom event metadata (`{source: "product_hunt", campaign: "launch_2024"}`)
- **FunnelAnalytics**: Step definitions, conversion rates, dropoff points
- **StripeProduct**: Features list, tier metadata
- **AIConversation**: Message history with role and timestamp
- **CustomerHealthScore**: Signal tracking (usage trends, error counts, etc.)

Hibernate 6.x requires `@JdbcTypeCode(SqlTypes.JSON)` annotations for all Map/List fields mapping to JSONB.

### Modules

#### 1. **Analytics Module** (Intelligence)
- Tracks user behavior (acquisition, monetization, retention, referral)
- Funnel analysis with conversion rates and bottleneck detection
- JSONB properties for flexible event metadata

#### 2. **Monetization Module**
- Stripe product catalog (pricing, features, intervals)
- Subscription lifecycle (trial, active, canceled, past_due)
- Webhook handling for payment events
- Metadata for subscription-level customization

#### 3. **Operations Module**
- Support ticket management (status, priority, assignment)
- Customer health scoring (overall score, risk level, signals)
- Proactive churn detection

#### 4. **AI Module**
- Multi-turn conversations with context preservation
- LiteLLM gateway integration for multiple model support
- Token usage tracking and cost estimation

#### 5. **Legal Module**
- GDPR data deletion requests
- Legal document management
- Compliance tracking

### Security

- **JWT Authentication**: Stateless, signed with RS256 (RSA)
- **Tenant Isolation**: Filter enforces all queries include `tenant_id` from token
- **CORS**: Configured for production domains
- **HTTPS Only**: Enforced in production

### Scalability Considerations

- Read replicas for analytics queries
- Connection pooling (HikariCP)
- Caching layer (Redis) for frequently accessed data
- JSONB indexing for fast properties queries
- Partitioning of analytics_events by date

## Database Schema

### Core Tables

| Table | Purpose | Key Fields |
|-------|---------|-----------|
| `changelog_projects` | Project metadata | tenant_id, name, branding |
| `users` | User accounts | tenant_id, email, password_hash |
| `sessions` | User sessions | tenant_id, user_id, jwt_token |
| `view_logs` | View tracking | tenant_id, user_id, timestamp |

### Business Module Tables

| Table | Module | JSONB Fields |
|-------|--------|-------------|
| `analytics_events` | Analytics | properties |
| `funnel_analytics` | Analytics | steps, step_counts, conversion_rates, dropoff_points |
| `stripe_products` | Monetization | features, metadata |
| `stripe_subscriptions` | Monetization | metadata |
| `support_tickets` | Operations | custom_fields |
| `customer_health_scores` | Operations | signals |
| `ai_conversations` | AI | messages, metadata |
| `legal_documents` | Legal | content, metadata |
| `gdpr_requests` | Legal | status_history, data_dump |

## API Design Principles

1. **RESTful**: Resources as nouns (/projects, /analytics, /tickets)
2. **Tenant-Scoped**: All endpoints implicitly scoped to authenticated tenant
3. **JSONB-Native**: Accept/return flexible JSON structures for metadata
4. **Versioning**: URI versioning (/api/v1, /api/v2) for breaking changes
5. **Pagination**: Limit/offset for large datasets

## Testing Strategy

- **Unit Tests**: Service and repository layer
- **Integration Tests**: Controller + service + repository
- **E2E Tests**: Full workflow validation (signup → activity → health score)
- **Performance Tests**: JSONB query performance at scale

## Deployment

See `DEPLOYMENT.md` for production deployment steps.
```

- [ ] **Step 4: Commit documentation**

```bash
git add changelog-platform/API.md changelog-platform/DEPLOYMENT.md changelog-platform/ARCHITECTURE.md
git commit -m "docs: add comprehensive API, deployment, and architecture documentation

- API.md: Complete REST endpoint reference with examples
- DEPLOYMENT.md: Production deployment guide with environment setup
- ARCHITECTURE.md: System design, module overview, schema reference
- Enables team onboarding and production readiness"
```

---

## Phase 5: Next Development Priorities (Post-Verification)

Once all tests pass and documentation is complete, prioritize:

### High Priority (Immediate)

1. **AI Feature Expansion**
   - Implement prompt templates for common support scenarios
   - Add RAG (Retrieval-Augmented Generation) for knowledge base integration
   - Cost optimization for API calls

2. **Advanced Analytics**
   - Cohort analysis engine
   - Predictive churn modeling
   - Attribution multi-touch modeling

3. **Compliance & Security**
   - SOC 2 Type II audit preparation
   - GDPR data deletion automation
   - PII data masking for logs

### Medium Priority (Next Sprint)

4. **Email Campaign Management**
   - Drag-and-drop template editor
   - Segmentation engine
   - A/B testing framework

5. **Dashboard & Reporting**
   - Real-time analytics dashboard
   - Custom report builder
   - Data export (CSV, PDF)

6. **Webhooks & Integrations**
   - Customer webhook events
   - Zapier integration
   - Slack notifications

### Lower Priority (Roadmap)

7. **Mobile Apps** (React Native)
8. **Advanced Billing** (usage-based pricing)
9. **Performance Optimization** (caching strategies, query optimization)

---

## Success Criteria

- ✅ All unit and integration tests passing
- ✅ Application starts without JSONB mapping errors
- ✅ Multi-tenant isolation verified
- ✅ REST API endpoints documented and callable
- ✅ Business modules functioning (BI, Monetization, Operations, AI, Legal)
- ✅ CI/CD pipeline ready for deployment
- ✅ Team onboarded with complete documentation
