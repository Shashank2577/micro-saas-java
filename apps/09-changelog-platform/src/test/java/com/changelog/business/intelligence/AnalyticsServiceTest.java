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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        
        jdbcTemplate.update("INSERT INTO cc.tenants (id, name, slug, plan_tier) VALUES (?, 'Test Tenant', ?, 'startup')", tenantId, "test-tenant-" + tenantId);

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

        List<AnalyticsEvent> events = analyticsEventRepository.findAll();
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